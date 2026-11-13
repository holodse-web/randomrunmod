import { DurableObject } from 'cloudflare:workers';
import MOD_HASHES from './mod-hashes.json';
import { ApiError, fail, randomToken, digest, constantEqual, username, password, passwordHash, body, json, validTarget } from './security.mjs';

/** Trust Score: new accounts start below the leaderboard bar and earn it by playing honestly. */
const TRUST = Object.freeze({
  start: 20, max: 200, threshold: 30, dailyCap: 15, minRunMs: 60000,
  cleanRun: 1, victory: 3, battleVictory: 5,
  penalties: Object.freeze({ CHEAT_MOD: 25, CREATIVE: 10, LAN: 5, UNTRUSTED_RUN: 5 })
});
const clampTrust = value => Math.max(0, Math.min(TRUST.max, Math.round(value)));

const SESSION_MS = 12 * 60 * 60 * 1000;
const ROOM_MS = 6 * 60 * 60 * 1000;
const dictionary = () => Object.create(null);
const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
const encoder = new TextEncoder();
const finite = (value, min, max) => typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!url.pathname.startsWith('/v1/')) return json({ error: 'NOT_FOUND' }, 404);
    const headers = new Headers(request.headers);
    headers.set('x-client-ip', request.headers.get('cf-connecting-ip') ?? 'local');
    return env.HUB.get(env.HUB.idFromName(env.HUB_NAME ?? 'randomrun-v1'), { locationHint: 'eeur' }).fetch(new Request(request, { headers }));
  }
};

export class RandomRunHub extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS accounts (uid TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE, salt TEXT NOT NULL, hash TEXT NOT NULL, profile TEXT NOT NULL, created INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS sessions (hash TEXT PRIMARY KEY, uid TEXT NOT NULL, expires INTEGER NOT NULL)`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS sessions_owner ON sessions(uid)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS rooms (code TEXT PRIMARY KEY, owner TEXT NOT NULL UNIQUE, data TEXT NOT NULL, salt TEXT, hash TEXT, expires INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS membership (uid TEXT PRIMARY KEY, code TEXT NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS runs (uid TEXT NOT NULL, target TEXT NOT NULL, stamp INTEGER NOT NULL, data TEXT NOT NULL, attempt INTEGER NOT NULL, PRIMARY KEY(uid,target,stamp))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS limits (key TEXT PRIMARY KEY, count INTEGER NOT NULL, expires INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS offline (uid TEXT PRIMARY KEY, since INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS friends (a TEXT NOT NULL, b TEXT NOT NULL, accepted INTEGER NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(a,b))`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS friends_b ON friends(b)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS nonces (nonce TEXT PRIMARY KEY, expires INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS survivors (code TEXT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, at INTEGER NOT NULL, PRIMARY KEY(code,name))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS tickets (id TEXT PRIMARY KEY, uid TEXT NOT NULL, target TEXT NOT NULL, seed TEXT NOT NULL, begun INTEGER NOT NULL, started INTEGER, used INTEGER NOT NULL DEFAULT 0)`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS tickets_owner ON tickets(uid)`);
    this.sql.exec("INSERT OR IGNORE INTO metadata VALUES ('verify_since',?)", Date.now());
    this.sql.exec("INSERT OR IGNORE INTO metadata VALUES ('latest_since_27_1',?)", Date.now());
    this.sql.exec(`CREATE TABLE IF NOT EXISTS legacy_runs (name TEXT NOT NULL COLLATE NOCASE, target TEXT NOT NULL, stamp INTEGER NOT NULL, data TEXT NOT NULL, PRIMARY KEY(name,target,stamp))`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS legacy_runs_target ON legacy_runs(target)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS invites (sender TEXT NOT NULL, target TEXT NOT NULL, code TEXT NOT NULL, expires INTEGER NOT NULL, PRIMARY KEY(sender,target))`);
  }
  one(query, ...args) { return this.sql.exec(query, ...args).toArray()[0] ?? null; }
  all(query, ...args) { return this.sql.exec(query, ...args).toArray(); }
  limit(key, max, period) {
    const now = Date.now(), current = this.one('SELECT * FROM limits WHERE key=?', key);
    if (current && current.expires > now && current.count >= max) fail(429, 'RATE_LIMITED');
    this.sql.exec('INSERT OR REPLACE INTO limits VALUES (?,?,?)', key, current && current.expires > now ? current.count + 1 : 1, current && current.expires > now ? current.expires : now + period);
  }
  account(uid) {
    const account = this.one('SELECT * FROM accounts WHERE uid=?', uid);
    if (!account) fail(401, 'SESSION_EXPIRED');
    return account;
  }
  async authenticate(request) {
    const header = request.headers.get('authorization') ?? '';
    if (!/^Bearer [0-9a-f]{64}$/.test(header)) fail(401, 'AUTH_REQUIRED');
    const hash = await digest(header.slice(7));
    const session = this.one('SELECT * FROM sessions WHERE hash=? AND expires>?', hash, Date.now());
    if (!session) fail(401, 'SESSION_EXPIRED');
    const account = this.account(session.uid);
    if (JSON.parse(account.profile).sec.b) fail(403, 'ACCOUNT_BANNED');
    return { ...account, session: hash, expires: session.expires };
  }
  assertSession(user) {
    if (!this.one('SELECT hash FROM sessions WHERE hash=? AND uid=? AND expires>?', user.session, user.uid, Date.now())) fail(401, 'SESSION_EXPIRED');
    if (JSON.parse(this.account(user.uid).profile).sec.b) fail(403, 'ACCOUNT_BANNED');
  }
  async issue(account) {
    const token = randomToken(), expires = Date.now() + SESSION_MS;
    const hashed = await digest(token), current = this.account(account.uid);
    if (current.hash !== account.hash) fail(401, 'CREDENTIALS_CHANGED');
    if (JSON.parse(current.profile).sec.b) fail(403, 'ACCOUNT_BANNED');
    this.sql.exec('INSERT INTO sessions VALUES (?,?,?)', hashed, account.uid, expires);
    const sessions = this.all('SELECT hash FROM sessions WHERE uid=? ORDER BY expires DESC', account.uid);
    for (const session of sessions.slice(3)) this.sql.exec('DELETE FROM sessions WHERE hash=?', session.hash);
    await this.ensureAlarm();
    return json({ token, expires, user: { id: account.uid, name: account.name }, profile: JSON.parse(account.profile) });
  }
  async fetch(request) {
    try {
      const url = new URL(request.url), path = url.pathname.slice(3), method = request.method;
      // Clients up to 27.1 treat "latest" as the minimum they must have, so it carries MIN_MOD_VERSION:
      // older supported versions keep working after a new release is deployed. "newest" is the actual newest version.
      if (method === 'GET' && path === '/version') return json({ latest: this.env.MIN_MOD_VERSION, newest: this.env.MOD_VERSION, min_required: this.env.MIN_MOD_VERSION, download_url: '', changelog: 'RandomRun online services' });
      if (method === 'GET' && path === '/health') return json({ ok: true, protocol: 1, serverTime: Date.now() });
      if (method === 'GET' && path === '/auth/challenge') {
        this.limit('challenge:' + await digest(request.headers.get('x-client-ip') ?? 'local'), 30, 60000);
        const nonce = randomToken(16), now = Date.now();
        this.sql.exec('DELETE FROM nonces WHERE expires<?', now);
        this.sql.exec('INSERT INTO nonces VALUES (?,?)', nonce, now + 120000);
        return json({ nonce });
      }
      if (method === 'POST' && (path === '/auth/register' || path === '/auth/login')) return await this.login(request, path.endsWith('register'));
      if (method === 'POST' && path === '/admin/import') return await this.importLegacy(request);
      const existsMatch = method === 'GET' && path.match(/^\/auth\/exists\/([A-Za-z0-9_]{3,16})$/);
      if (existsMatch) {
        this.limit('exists:' + await digest(request.headers.get('x-client-ip') ?? 'local'), 30, 60000);
        return json({ exists: !!this.one('SELECT uid FROM accounts WHERE name=? COLLATE NOCASE', existsMatch[1]) });
      }
      const payload = ['POST','PUT','PATCH'].includes(method) ? await body(request) : null;
      const user = await this.authenticate(request);
      this.limit('api:' + user.uid, 240, 60000);
      if (method === 'GET' && path === '/auth/session') return json({ user: { id: user.uid, name: user.name }, expires: user.expires });
      if (method === 'POST' && path === '/auth/logout') {
        this.leave(user);
        this.sql.exec('DELETE FROM sessions WHERE hash=?', user.session);
        this.closeSession(user.session);
        return json({ ok: true });
      }
      if (method === 'POST' && path === '/auth/password') return await this.changePassword(payload, user);
      if (method === 'GET' && path === '/events') return this.connect(request, user);
      if (method === 'POST' && path === '/ice') return await this.ice(user);
      if (method === 'GET' && path === '/friends') return json(this.social(user));
      if (method === 'POST' && path === '/runs/begin') return json(this.beginRun(payload, user));
      if (method === 'POST' && path === '/runs/start') return json(this.startTicket(payload, user));
      const friendMatch = method === 'POST' && path.match(/^\/friends\/(request|accept|decline|remove)$/);
      if (friendMatch) return this.friendAction(friendMatch[1], payload, user);
      if (method === 'POST' && path === '/invites') return this.invite(payload, user);
      if (method === 'POST' && path === '/invites/decline') {
        const sender = this.one('SELECT uid FROM accounts WHERE name=? COLLATE NOCASE', username(payload.name));
        if (sender) this.sql.exec('DELETE FROM invites WHERE sender=? AND target=?', sender.uid, user.uid);
        this.broadcast();
        return json({ ok: true });
      }
      if (method === 'GET' && path === '/rooms') return json(this.roomList(user));
      if (method === 'POST' && path === '/rooms') return await this.createRoom(payload, user);
      const roomMatch = path.match(/^\/rooms\/([0-9]{5})(?:\/(join|leave|disconnect))?$/);
      if (roomMatch) {
        const [, code, action] = roomMatch;
        if (method === 'POST' && action === 'join') return await this.joinRoom(code, payload, user);
        if (method === 'POST' && action === 'leave') { this.requireMember(this.room(code), user); this.leave(user); return json({ ok: true }); }
        const room = this.room(code);
        if (method === 'GET' && !action) return json(this.publicRoom(room, user));
        this.requireMember(room, user);
        if (method === 'POST' && action === 'disconnect') {
          if (room.owner !== user.uid) fail(403, 'HOST_REQUIRED');
          const member = this.one('SELECT accounts.* FROM membership JOIN accounts USING(uid) WHERE code=? AND name=? COLLATE NOCASE', code, username(payload.player));
          if (!member || member.uid === user.uid) fail(400, 'INVALID_PLAYER');
          this.leave(member); return json({ ok: true });
        }
        if (method === 'DELETE' && !action) { this.leave(user); return json({ ok: true }); }
        if (method === 'PATCH' && !action) return this.patchRoom(room, payload, user);
      }
      const profileMatch = path.match(/^\/profiles\/([A-Za-z0-9_]{3,16})(?:\/(profile|history)(?:\/([a-z0-9_]+)\/([0-9]+))?)?$/);
      if (profileMatch) {
        const [, name, part, target] = profileMatch;
        if (name.toLowerCase() !== user.name.toLowerCase()) fail(403, 'OWNER_REQUIRED');
        if (method === 'GET' && part !== 'history') return json(JSON.parse(user.profile));
        if (method === 'PUT' && part === 'profile') {
          const value = payload, profile = JSON.parse(user.profile);
          if (value.stats && typeof value.stats.fts === 'boolean') profile.stats.fts = value.stats.fts;
          profile.info.ls = Date.now();
          this.sql.exec('UPDATE accounts SET profile=? WHERE uid=?', JSON.stringify(profile), user.uid);
          return json({ ok: true });
        }
        if (method === 'GET' && part === 'history' && !target) return json(this.history(user.uid));
        if (method === 'PUT' && part === 'history' && target) return this.saveRun(user, target, payload);
      }
      if (method === 'GET' && (path === '/leaderboards' || /^\/leaderboards\/[a-z0-9_]+$/.test(path))) return json(this.leaderboards(path.split('/')[2]));
      if (method === 'GET' && (path === '/latest_runs' || /^\/latest_runs\/[A-Za-z0-9_]{3,16}$/.test(path))) return json(this.latestRuns(path.split('/')[2]));
      if (method === 'GET' && path === '/stats/global') return json({ total_runs: this.one('SELECT COUNT(*) AS n FROM runs').n + this.one('SELECT COUNT(*) AS n FROM legacy_runs').n, total_rooms_created: (this.one("SELECT value FROM metadata WHERE key='rooms'")?.value ?? 0) + (this.one("SELECT value FROM metadata WHERE key='legacy_rooms'")?.value ?? 0), online: this.online().length });
      if (method === 'POST' && path === '/security/report') {
        const penalty = TRUST.penalties[payload?.reason];
        if (penalty == null) fail(400, 'INVALID_REPORT');
        this.limit('report:' + user.uid, 30, 3600000);
        const profile = JSON.parse(user.profile);
        profile.sec.ts = clampTrust(profile.sec.ts - penalty);
        this.sql.exec('UPDATE accounts SET profile=? WHERE uid=?', JSON.stringify(profile), user.uid);
        return json({ ok: true, ts: profile.sec.ts });
      }
      fail(404, 'NOT_FOUND');
    } catch (error) {
      if (error instanceof ApiError) return json({ error: error.code }, error.status);
      console.error('API operation failed', error?.name ?? 'Error');
      return json({ error: 'INTERNAL_ERROR' }, 500);
    }
  }
  async login(request, register) {
    const ip = await digest(request.headers.get('x-client-ip') ?? 'local');
    this.limit('auth-ip:' + ip, 20, 60000);
    this.limit('auth-global', 120, 60000);
    const value = await body(request, 2048), name = username(value.username);
    password(value.password);
    await this.checkIntegrity(value, name);
    this.limit('auth-user:' + name.toLowerCase(), 8, 60000);
    let account = this.one('SELECT * FROM accounts WHERE name=? COLLATE NOCASE', name);
    if (register) {
      this.limit('register:' + ip, 5, 3600000);
      if (account) fail(409, 'USERNAME_TAKEN');
      const result = await passwordHash(value.password), uid = randomToken(16), now = Date.now();
      const profile = { info: { u: uid, ca: now, ls: now, mv: this.env.MOD_VERSION }, sec: { ts: TRUST.start, b: false }, stats: { tr: 0, fts: false } };
      if (this.one('SELECT uid FROM accounts WHERE name=? COLLATE NOCASE', name)) fail(409, 'USERNAME_TAKEN');
      this.sql.exec('INSERT INTO accounts VALUES (?,?,?,?,?,?)', uid, name, result.salt, result.hash, JSON.stringify(profile), now);
      this.claimLegacy(uid, name);
      account = this.account(uid);
    } else {
      const result = await passwordHash(value.password, account?.salt ?? 'unregistered-account-salt');
      if (!account || !constantEqual(result.hash, account.hash) || this.account(account.uid).hash !== account.hash) fail(401, 'INVALID_CREDENTIALS');
    }
    if (JSON.parse(account.profile).sec.b) fail(403, 'ACCOUNT_BANNED');
    return this.issue(this.account(account.uid));
  }
  async checkIntegrity(value, name) {
    const hashes = Array.isArray(MOD_HASHES) ? MOD_HASHES.filter(hash => typeof hash === 'string' && /^[0-9a-f]{64}$/.test(hash)) : [];
    const nonce = typeof value.nonce === 'string' && /^[0-9a-f]{32}$/.test(value.nonce) ? value.nonce : null;
    const known = nonce ? this.one('SELECT expires FROM nonces WHERE nonce=?', nonce) : null;
    if (nonce) this.sql.exec('DELETE FROM nonces WHERE nonce=?', nonce);
    if (!hashes.length) return;
    if (!known || known.expires < Date.now() || typeof value.proof !== 'string') fail(403, 'MOD_MODIFIED');
    const message = encoder.encode(nonce + ':' + name.toLowerCase());
    for (const hash of hashes) {
      const key = await crypto.subtle.importKey('raw', encoder.encode(hash), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
      const expected = Array.from(new Uint8Array(await crypto.subtle.sign('HMAC', key, message)), b => b.toString(16).padStart(2, '0')).join('');
      if (constantEqual(expected, value.proof)) return;
    }
    fail(403, 'MOD_MODIFIED');
  }
  async changePassword(value, user) {
    this.limit('password:' + user.uid, 3, 3600000);
    password(value.newPassword);
    if (typeof value.currentPassword !== 'string' || value.currentPassword.length > 128) fail(400, 'INVALID_PASSWORD');
    const check = await passwordHash(value.currentPassword, user.salt);
    if (!constantEqual(check.hash, user.hash)) fail(401, 'INVALID_CREDENTIALS');
    const next = await passwordHash(value.newPassword);
    this.assertSession(user);
    if (this.account(user.uid).hash !== user.hash) fail(409, 'CREDENTIALS_CHANGED');
    this.sql.exec('UPDATE accounts SET salt=?, hash=? WHERE uid=?', next.salt, next.hash, user.uid);
    this.sql.exec('DELETE FROM sessions WHERE uid=?', user.uid);
    for (const socket of this.ctx.getWebSockets()) if (socket.deserializeAttachment()?.uid === user.uid) socket.close(4001, 'SESSION_REVOKED');
    return this.issue(this.account(user.uid));
  }
  connect(request, user) {
    if (request.headers.get('upgrade')?.toLowerCase() !== 'websocket') fail(426, 'WEBSOCKET_REQUIRED');
    this.limit('connect:' + user.uid, 20, 60000);
    const existing = this.ctx.getWebSockets().filter(socket => socket.deserializeAttachment()?.uid === user.uid);
    for (const socket of existing) socket.close(4000, 'REPLACED');
    this.sql.exec('DELETE FROM offline WHERE uid=?', user.uid);
    const pair = new WebSocketPair(), [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ uid: user.uid, name: user.name, session: user.session, expires: user.expires });
    server.send(this.snapshot(user, this.context()));
    this.broadcast();
    return new Response(null, { status: 101, webSocket: client });
  }
  online() {
    return [...this.context().online.values()];
  }
  closeSession(hash) {
    for (const socket of this.ctx.getWebSockets()) if (socket.deserializeAttachment()?.session === hash) socket.close(4001, 'SESSION_REVOKED');
    this.broadcast();
  }
  room(code) {
    const row = this.one('SELECT * FROM rooms WHERE code=? AND expires>?', code, Date.now());
    if (!row) fail(404, 'ROOM_NOT_FOUND');
    const value = JSON.parse(row.data);
    for (const key of ["h","g","ep","dd"]) value[key] = Object.assign(dictionary(), value[key]);
    return { ...row, value };
  }
  requireMember(room, user) {
    if (this.one('SELECT code FROM membership WHERE uid=?', user.uid)?.code !== room.code) fail(403, 'ROOM_MEMBER_REQUIRED');
  }
  publicRoom(room, user, context) {
    const value = structuredClone(room.value);
    value.roomCode = room.code;
    value.revision = context ? context.revision : this.one("SELECT value FROM metadata WHERE key='revision'")?.value ?? 0;
    value.hostId = room.owner;
    value.members = context ? (context.members.get(room.code) ?? []).map(member => ({ ...member })) : this.all('SELECT accounts.uid,accounts.name FROM membership JOIN accounts USING(uid) WHERE code=?', room.code);
    delete value.pwd;
    const member = value.members.some(member => member.uid === user.uid);
    if (!member && value.pr) { delete value.s; delete value.t; }
    return value;
  }
  roomList(user) {
    return JSON.parse(this.roomsJson(user, this.context()));
  }
  context() {
    const now = Date.now();
    const valid = new Set(this.all('SELECT hash FROM sessions WHERE expires>?', now).map(row => row.hash));
    const sockets = [], online = new Map(), presence = new Map();
    for (const socket of this.ctx.getWebSockets()) {
      const info = socket.deserializeAttachment();
      const alive = !!info && info.expires > now && valid.has(info.session);
      sockets.push({ socket, info, alive });
      if (alive) online.set(info.uid, info.name);
      if (info && info.expires > now && !presence.has(info.uid)) presence.set(info.uid, info.presence ?? { activity: 'menu' });
    }
    const members = new Map(), memberOf = new Map();
    for (const row of this.all('SELECT membership.code,accounts.uid,accounts.name FROM membership JOIN accounts USING(uid)')) {
      if (!members.has(row.code)) members.set(row.code, []);
      members.get(row.code).push({ uid: row.uid, name: row.name });
      memberOf.set(row.uid, row.code);
    }
    const rooms = this.all('SELECT * FROM rooms WHERE expires>? ORDER BY code', now).map(row => ({ ...row, value: JSON.parse(row.data) }));
    const revision = this.one("SELECT value FROM metadata WHERE key='revision'")?.value ?? 0;
    return { now, sockets, online, onlineJson: JSON.stringify([...online.values()]), presence, members, memberOf, rooms, roomByCode: new Map(rooms.map(room => [room.code, room])), revision, roomsCache: new Map() };
  }
  roomsJson(user, context) {
    const code = context.memberOf.get(user.uid);
    const key = code && context.roomByCode.get(code)?.value.pr ? code : '';
    let cached = context.roomsCache.get(key);
    if (cached == null) {
      cached = '{' + context.rooms.map(room => JSON.stringify(room.code) + ':' + JSON.stringify(this.publicRoom(room, user, context))).join(',') + '}';
      context.roomsCache.set(key, cached);
    }
    return cached;
  }
  snapshot(user, context) {
    return '{"type":"snapshot","revision":' + context.revision + ',"rooms":' + this.roomsJson(user, context) + ',"online":' + context.onlineJson + ',"social":' + JSON.stringify(this.social(user, context)) + ',"serverTime":' + Date.now() + '}';
  }
  storeRoom(room) {
    room.value.upd = Date.now();
    this.sql.exec('UPDATE rooms SET data=? WHERE code=?', JSON.stringify(room.value), room.code);
    this.broadcast();
  }
  async createRoom(value, user) {
    this.limit('create:' + user.uid, 10, 3600000);
    if (this.one('SELECT code FROM membership WHERE uid=?', user.uid)) fail(409, 'ALREADY_IN_ROOM');
    const max = value.maxPlayers ?? 2;
    if (!Number.isInteger(max) || max < 2 || max > 6) fail(400, 'INVALID_CAPACITY');
    if (value.target != null) validTarget(value.target);
    if (value.seed != null && (typeof value.seed !== 'string' || value.seed.length > 64)) fail(400, 'INVALID_SEED');
    let secret = null;
    if (value.password) { if (typeof value.password !== 'string' || value.password.length > 64) fail(400, 'INVALID_ROOM_PASSWORD'); secret = await passwordHash(value.password); }
    this.assertSession(user);
    if (this.one('SELECT code FROM membership WHERE uid=?', user.uid)) fail(409, 'ALREADY_IN_ROOM');
    let code;
    do { code = String(10000 + crypto.getRandomValues(new Uint32Array(1))[0] % 90000); } while (this.one('SELECT code FROM rooms WHERE code=?', code));
    const room = { h: { [user.name]: { r: false, l: false, t: 0 } }, g: dictionary(), s: value.seed ?? '0', t: value.target ?? null, st: 'WAITING', pr: !!secret, cm: value.mode === 'sw' ? 'sw' : 'webrtc', mp: max, gm: value.randomDrop ? 'random_drop' : 'default', ug: value.upgrader === true, vo: value.voice === true, ca: Date.now(), upd: Date.now(), dd: dictionary(), ep: dictionary(), start: 0, srv_start: 0, swt: 0 };
    this.ctx.storage.transactionSync(() => {
      this.sql.exec('INSERT INTO rooms VALUES (?,?,?,?,?,?)', code, user.uid, JSON.stringify(room), secret?.salt ?? null, secret?.hash ?? null, Date.now() + ROOM_MS);
      this.sql.exec('INSERT INTO membership VALUES (?,?)', user.uid, code);
      this.sql.exec("INSERT INTO metadata VALUES ('rooms',1) ON CONFLICT(key) DO UPDATE SET value=value+1");
    });
    await this.ensureAlarm(); this.broadcast();
    return json(this.publicRoom({ code, owner: user.uid, value: room }, user), 201);
  }
  async joinRoom(code, value, user) {
    this.limit('join:' + user.uid, 15, 60000);
    let room = this.room(code);
    const invited = !!this.one('SELECT invites.sender FROM invites JOIN membership ON membership.uid=invites.sender WHERE invites.target=? AND invites.code=? AND membership.code=? AND invites.expires>?', user.uid, code, code, Date.now());
    if (room.hash && !invited) {
      if (typeof value.password !== 'string' || value.password.length > 64) fail(403, 'WRONG_ROOM_PASSWORD');
      const result = await passwordHash(value.password, room.salt);
      if (!constantEqual(result.hash, room.hash)) fail(403, 'WRONG_ROOM_PASSWORD');
    }
    this.assertSession(user);
    const original = room;
    room = this.room(code);
    if (original.owner !== room.owner || original.hash !== room.hash || original.value.ca !== room.value.ca) fail(409, 'ROOM_CHANGED');
    const existing = this.one('SELECT code FROM membership WHERE uid=?', user.uid);
    if (existing?.code === code) return json(this.publicRoom(room, user));
    if (existing) fail(409, 'ALREADY_IN_ROOM');
    if (room.value.st !== 'WAITING') fail(409, 'GAME_ALREADY_STARTED');
    if (this.one('SELECT COUNT(*) AS n FROM membership WHERE code=?', code).n >= room.value.mp) fail(409, 'ROOM_FULL');
    room.value.g[user.name] = { r: false, l: false, t: 0 };
    this.ctx.storage.transactionSync(() => {
      this.sql.exec('INSERT INTO membership VALUES (?,?)', user.uid, code);
      this.sql.exec('DELETE FROM invites WHERE target=?', user.uid);
      room.value.upd = Date.now();
      this.sql.exec('UPDATE rooms SET data=? WHERE code=?', JSON.stringify(room.value), room.code);
    });
    this.broadcast();
    return json(this.publicRoom(room, user));
  }
  leave(user) {
    const member = this.one('SELECT code FROM membership WHERE uid=?', user.uid);
    if (!member) return;
    let room;
    try { room = this.room(member.code); } catch { this.sql.exec('DELETE FROM membership WHERE uid=?', user.uid); return; }
    if (room.owner === user.uid) {
      this.sql.exec('DELETE FROM membership WHERE code=?', room.code);
      this.sql.exec('DELETE FROM rooms WHERE code=?', room.code);
    } else {
      this.sql.exec('DELETE FROM membership WHERE uid=?', user.uid);
      delete room.value.g[user.name];
      if (room.value.st !== 'WAITING') { room.value.st = 'DRAW'; room.value.dr = 'PLAYER_DISCONNECTED'; }
      this.storeRoom(room);
    }
    this.broadcast();
  }
  patchRoom(room, updates, user) {
    this.limit('room:' + user.uid, 120, 60000);
    const value = room.value, host = room.owner === user.uid, initial = value.st;
    const changes = Object.entries(updates).sort(([a], [b]) => (a === 'st' ? 1 : 0) - (b === 'st' ? 1 : 0));
    if (changes.length > 20) fail(400, 'TOO_MANY_FIELDS');
    for (const [path, entry] of changes) {
      const parts = path.split('/');
      if (parts.length === 3 && ['h','g'].includes(parts[0]) && parts[1].toLowerCase() === user.name.toLowerCase() && ['r','l'].includes(parts[2])) {
        if (typeof entry !== 'boolean' || !own(value[parts[0]], user.name) || !['LOADING','FROZEN','WAITING'].includes(initial)) fail(403, 'INVALID_PLAYER_STATE');
        value[parts[0]][user.name][parts[2]] = entry;
      } else if (parts.length === 2 && parts[0] === 'g' && host && initial === 'WAITING' && entry === null && own(value.g, parts[1])) {
        const kicked = this.one('SELECT uid FROM accounts WHERE name=?', parts[1]);
        if (!kicked) fail(400, 'INVALID_PLAYER');
        delete value.g[parts[1]];
      } else if (path === 't' && host && initial === 'WAITING') value.t = validTarget(entry);
      else if (path === 's' && host && initial === 'LOADING' && typeof entry === 'string' && entry.length <= 64) value.s = entry;
      else if (path === 'ip' && host && initial === 'LOADING' && entry === 'webrtc:' + room.code) value.ip = entry;
      else if (path === 'st' && host && entry === 'LOADING' && initial === 'WAITING') {
        if (!value.t || this.one('SELECT COUNT(*) AS n FROM membership WHERE code=?', room.code).n < 2) fail(409, 'ROOM_NOT_READY');
        value.st = 'LOADING';
        for (const player of [...Object.values(value.h), ...Object.values(value.g)]) { player.r = false; player.l = false; }
      } else if (path === 'st' && host && entry === 'FROZEN' && initial === 'LOADING') {
        if (![...Object.values(value.h), ...Object.values(value.g)].every(player => player.l)) fail(409, 'PLAYERS_NOT_LOADED');
        value.st = 'FROZEN';
      } else if (path === 'st' && host && entry === 'STARTED' && initial === 'FROZEN') {
        if (![...Object.values(value.h), ...Object.values(value.g)].every(player => player.r)) fail(409, 'PLAYERS_NOT_READY');
        value.st = 'STARTED'; value.srv_start = Date.now() + 5000; value.start = value.srv_start;
      } else if (path === 'w' && initial === 'STARTED' && entry === user.name && !value.w && !value.ep[user.name] && Date.now() >= value.srv_start) value.w = user.name;
      else if (path === 'st' && entry === 'FINISHED' && initial === 'STARTED' && value.w === user.name) value.st = 'FINISHED';
      else if (parts.length === 2 && parts[0] === 'ep' && parts[1].toLowerCase() === user.name.toLowerCase() && entry === true && initial === 'STARTED' && Date.now() >= value.srv_start) value.ep[user.name] = true;
      else if (parts.length === 3 && ['h','g'].includes(parts[0]) && parts[1].toLowerCase() === user.name.toLowerCase() && parts[2] === 't' && own(value[parts[0]], user.name) && finite(entry, 0, 21600000) && ['STARTED','FINISHED'].includes(initial)) value[parts[0]][user.name].t = Math.round(entry);
      else if (path === 'st' && host && entry === 'DRAW' && !['FINISHED','DRAW'].includes(initial)) value.st = 'DRAW';
      else if (path === 'dr' && host && typeof entry === 'string' && entry.length <= 100) value.dr = entry;
      else if (path === 'upd' && finite(entry, 0, Number.MAX_SAFE_INTEGER)) continue;
      else fail(403, 'ROOM_CHANGE_FORBIDDEN');
    }
    if (value.st === 'STARTED' && Object.values(value.ep).filter(Boolean).length >= this.one('SELECT COUNT(*) AS n FROM membership WHERE code=?', room.code).n - 1) {
      const survivor = [...Object.keys(value.h), ...Object.keys(value.g)].find(name => !value.ep[name]);
      value.w = survivor ?? null; value.st = survivor ? 'FINISHED' : 'DRAW';
      if (survivor) this.sql.exec('INSERT OR REPLACE INTO survivors VALUES (?,?,?)', room.code, survivor, Date.now());
    }
    if (value.w && value.st === 'STARTED') value.st = 'FINISHED';
    this.ctx.storage.transactionSync(() => {
      for (const [path, entry] of changes) if (host && initial === 'WAITING' && path.startsWith('g/') && entry === null) {
        this.sql.exec('DELETE FROM membership WHERE code=? AND uid=(SELECT uid FROM accounts WHERE name=?)', room.code, path.slice(2));
      }
      value.upd = Date.now();
      this.sql.exec('UPDATE rooms SET data=? WHERE code=?', JSON.stringify(value), room.code);
    });
    this.broadcast();
    return json({ ok: true, room: this.publicRoom(room, user), serverTime: Date.now() });
  }
  accountByName(name) {
    const account = this.one('SELECT uid,name FROM accounts WHERE name=? COLLATE NOCASE', username(name));
    if (!account) fail(404, 'PLAYER_NOT_FOUND');
    return account;
  }
  friendAction(action, value, user) {
    this.limit('friends:' + user.uid, 60, 60000);
    const other = this.accountByName(value?.name);
    if (other.uid === user.uid) fail(400, 'CANNOT_FRIEND_SELF');
    const outgoing = this.one('SELECT * FROM friends WHERE a=? AND b=?', user.uid, other.uid);
    const incoming = this.one('SELECT * FROM friends WHERE a=? AND b=?', other.uid, user.uid);
    if (action === 'request') {
      if (outgoing?.accepted || incoming?.accepted) fail(409, 'ALREADY_FRIENDS');
      if (incoming) this.sql.exec('UPDATE friends SET accepted=1 WHERE a=? AND b=?', other.uid, user.uid);
      else if (!outgoing) {
        this.limit('friend-request:' + user.uid, 30, 3600000);
        if (this.one('SELECT COUNT(*) AS n FROM friends WHERE a=? OR b=?', user.uid, user.uid).n >= 200) fail(409, 'FRIEND_LIMIT');
        this.sql.exec('INSERT INTO friends VALUES (?,?,?,?)', user.uid, other.uid, 0, Date.now());
      }
    } else if (action === 'accept') {
      if (!incoming) fail(404, 'REQUEST_NOT_FOUND');
      this.sql.exec('UPDATE friends SET accepted=1 WHERE a=? AND b=?', other.uid, user.uid);
    } else if (action === 'decline') {
      this.sql.exec('DELETE FROM friends WHERE accepted=0 AND ((a=? AND b=?) OR (a=? AND b=?))', user.uid, other.uid, other.uid, user.uid);
    } else {
      this.sql.exec('DELETE FROM friends WHERE (a=? AND b=?) OR (a=? AND b=?)', user.uid, other.uid, other.uid, user.uid);
      this.sql.exec('DELETE FROM invites WHERE (sender=? AND target=?) OR (sender=? AND target=?)', user.uid, other.uid, other.uid, user.uid);
    }
    this.broadcast();
    return json(this.social(user));
  }
  invite(value, user) {
    this.limit('invite:' + user.uid, 20, 60000);
    const other = this.accountByName(value?.name);
    if (!this.one('SELECT a FROM friends WHERE accepted=1 AND ((a=? AND b=?) OR (a=? AND b=?))', user.uid, other.uid, other.uid, user.uid)) fail(403, 'NOT_FRIENDS');
    const member = this.one('SELECT code FROM membership WHERE uid=?', user.uid);
    if (!member) fail(409, 'NOT_IN_ROOM');
    const room = this.room(member.code);
    if (room.value.st !== 'WAITING') fail(409, 'GAME_ALREADY_STARTED');
    if (this.one('SELECT COUNT(*) AS n FROM membership WHERE code=?', room.code).n >= room.value.mp) fail(409, 'ROOM_FULL');
    if (this.one('SELECT code FROM membership WHERE uid=?', other.uid)?.code === room.code) fail(409, 'ALREADY_IN_ROOM');
    this.sql.exec('INSERT OR REPLACE INTO invites VALUES (?,?,?,?)', user.uid, other.uid, room.code, Date.now() + 300000);
    this.broadcast();
    return json({ ok: true });
  }
  social(user, context = this.context()) {
    const now = Date.now(), online = context.online;
    const friends = [], incoming = [], outgoing = [];
    for (const row of this.all('SELECT friends.a,friends.b,friends.accepted,accounts.uid,accounts.name FROM friends JOIN accounts ON accounts.uid=(CASE WHEN friends.a=? THEN friends.b ELSE friends.a END) WHERE friends.a=? OR friends.b=? ORDER BY accounts.name COLLATE NOCASE', user.uid, user.uid, user.uid)) {
      if (!row.accepted) { (row.a === user.uid ? outgoing : incoming).push(row.name); continue; }
      const entry = { name: row.name, online: online.has(row.uid), activity: 'offline' };
      if (entry.online) {
        const presence = context.presence.get(row.uid) ?? { activity: 'menu' };
        entry.activity = presence.activity;
        if (presence.target) entry.target = presence.target;
        const room = context.roomByCode.get(context.memberOf.get(row.uid));
        if (room) {
          entry.activity = room.value.st === 'WAITING' ? 'room' : 'battle';
          entry.room = room.code;
          entry.roomPlayers = (context.members.get(room.code) ?? []).length;
          entry.roomMax = room.value.mp;
          entry.roomPrivate = !!room.value.pr;
          if (room.value.t && (!room.value.pr || context.memberOf.get(user.uid) === room.code)) entry.target = room.value.t;
        }
      }
      friends.push(entry);
    }
    friends.sort((x, y) => (y.online - x.online) || x.name.localeCompare(y.name));
    const invites = this.all('SELECT accounts.name,invites.code,invites.expires FROM invites JOIN accounts ON accounts.uid=invites.sender WHERE invites.target=? AND invites.expires>?', user.uid, now)
      .map(row => ({ from: row.name, code: row.code, expires: row.expires }));
    return { friends, incoming, outgoing, invites };
  }
  history(uid) {
    const result = dictionary();
    for (const row of this.all('SELECT target,stamp,data,attempt FROM runs WHERE uid=? ORDER BY stamp DESC LIMIT 1000', uid)) (result[row.target] ??= dictionary())[row.attempt] = JSON.parse(row.data);
    return result;
  }
  saveRun(user, target, value) {
    validTarget(target); this.limit('run:' + user.uid, 60, 60000);
    if (!finite(value.d, 0, 7200000) || !finite(value.t, 1, Date.now() + 60000) || !['V','D','G','T','F'].includes(value.r)) fail(400, 'INVALID_RUN');
    if (typeof value.s !== 'string' || value.s.length > 64 || (value.cs != null && (typeof value.cs !== 'string' || value.cs.length > 64))) fail(400, 'INVALID_SEED');
    const clean = { rd: value.rd === true, d: Math.round(value.d), t: Math.round(value.t), r: value.r, s: value.s, cs: value.cs ?? '', b: value.b === true, h: value.h === true, m: this.env.MOD_VERSION };
    if (value.br != null) {
      if (typeof value.br !== 'string' || !/^[0-9]{5}$/.test(value.br)) fail(400, 'INVALID_ROOM');
      clean.br = value.br;
      if (value.sv === true || this.one('SELECT 1 AS x FROM survivors WHERE code=? AND name=? AND at>?', value.br, user.name, Date.now() - 21600000)) clean.sv = true;
    }
    if (typeof value.rid === 'string') clean.v = this.verifyTicket(user, target, value, clean);
    const attempt = this.one('SELECT COALESCE(MAX(attempt),0)+1 AS n FROM runs WHERE uid=? AND target=?', user.uid, target).n;
    const before = this.one('SELECT COUNT(*) AS n FROM runs WHERE uid=?', user.uid).n;
    this.sql.exec('INSERT OR IGNORE INTO runs VALUES (?,?,?,?,?)', user.uid, target, clean.t, JSON.stringify(clean), attempt);
    const profile = JSON.parse(user.profile);
    profile.stats.tr = this.one('SELECT COUNT(*) AS n FROM runs WHERE uid=?', user.uid).n;
    if (profile.stats.tr > before) this.rewardRun(profile, clean, value);
    this.sql.exec('UPDATE accounts SET profile=? WHERE uid=?', JSON.stringify(profile), user.uid);
    return json({ ok: true, ts: profile.sec.ts });
  }
  /** Trust grows with honest play: a little per finished run, more per verified win, capped per day. */
  rewardRun(profile, clean, value) {
    if (value.u === true || value.vo === true) return;
    let gain = 0;
    if (clean.r === 'V' && clean.br) gain = TRUST.battleVictory;
    else if (clean.r === 'V' && clean.v === true && !clean.cs && !clean.rd) gain = TRUST.victory;
    else if (clean.r !== 'G' && clean.d >= TRUST.minRunMs) gain = TRUST.cleanRun;
    if (gain <= 0) return;
    const day = Math.floor(Date.now() / 86400000);
    if (profile.sec.gd !== day) { profile.sec.gd = day; profile.sec.gs = 0; }
    gain = Math.min(gain, TRUST.dailyCap - (profile.sec.gs ?? 0));
    if (gain <= 0) return;
    profile.sec.gs = (profile.sec.gs ?? 0) + gain;
    profile.sec.ts = clampTrust(profile.sec.ts + gain);
  }
  beginRun(value, user) {
    this.limit('begin:' + user.uid, 30, 60000);
    const target = validTarget(value.target), now = Date.now();
    this.sql.exec('DELETE FROM tickets WHERE uid=? AND begun<?', user.uid, now - 21600000);
    const bytes = crypto.getRandomValues(new Uint8Array(8));
    let seed = 0n;
    for (const byte of bytes) seed = (seed << 8n) | BigInt(byte);
    seed = BigInt.asIntN(64, seed);
    const id = randomToken(16);
    this.sql.exec('INSERT INTO tickets (id,uid,target,seed,begun) VALUES (?,?,?,?,?)', id, user.uid, target, seed.toString(), now);
    return { id, seed: seed.toString() };
  }
  startTicket(value, user) {
    if (typeof value.id !== 'string' || value.id.length > 64) fail(400, 'INVALID_TICKET');
    const ticket = this.one('SELECT * FROM tickets WHERE id=? AND uid=?', value.id, user.uid), now = Date.now();
    if (!ticket || ticket.used) fail(404, 'TICKET_NOT_FOUND');
    if (ticket.started) return { ok: true, started: ticket.started };
    if (now - ticket.begun > 900000) fail(409, 'TICKET_EXPIRED');
    this.sql.exec('UPDATE tickets SET started=? WHERE id=?', now, ticket.id);
    return { ok: true, started: now };
  }
  verifyTicket(user, target, value, clean) {
    const ticket = this.one('SELECT * FROM tickets WHERE id=? AND uid=?', value.rid.slice(0, 64), user.uid);
    if (!ticket || ticket.used || !ticket.started || ticket.target !== target) return false;
    this.sql.exec('UPDATE tickets SET used=1 WHERE id=?', ticket.id);
    if (clean.s !== ticket.seed || clean.cs) return false;
    const elapsed = Date.now() - ticket.started;
    const rta = finite(value.rta, 0, 360000000) ? Math.round(value.rta) : null;
    if (rta == null || clean.d > rta + 1000) return false;
    const tolerance = 15000 + elapsed * 0.03;
    if (Math.abs(rta - elapsed) > tolerance) return false;
    return true;
  }
  leaderboards(target) {
    const result = dictionary();
    const key = target ? validTarget(target) : null;
    const trust = new Map();
    const trusted = name => {
      if (!trust.has(name)) trust.set(name, JSON.parse(this.one('SELECT profile FROM accounts WHERE name=?', name).profile).sec.ts >= TRUST.threshold);
      return trust.get(name);
    };
    const since = this.one("SELECT value FROM metadata WHERE key='verify_since'")?.value ?? 0;
    const consider = (name, row, legacy) => {
      const run = JSON.parse(row.data);
      if (run.r !== 'V' || run.d <= 0 || !run.h || run.cs || run.rd || run.u || run.sv) return;
      if (!legacy && !run.br && run.t >= since && run.v !== true) return;
      if (!legacy && !trusted(name)) return;
      const table = result[row.target] ??= dictionary();
      if (!table[name] || run.d < table[name].t) table[name] = { t: run.d, ts: run.t, s: run.s, tt: 'ITEM' };
    };
    const rows = key ? this.all('SELECT accounts.name,runs.target,runs.data FROM runs JOIN accounts USING(uid) WHERE target=?', key) : this.all('SELECT accounts.name,runs.target,runs.data FROM runs JOIN accounts USING(uid) ORDER BY stamp DESC LIMIT 10000');
    for (const row of rows) consider(row.name, row, false);
    const legacy = key ? this.all('SELECT name,target,data FROM legacy_runs WHERE target=?', key) : this.all('SELECT name,target,data FROM legacy_runs');
    for (const row of legacy) consider(row.name, row, true);
    return key ? result[key] ?? {} : result;
  }
  latestRuns(name) {
    const since = this.one("SELECT value FROM metadata WHERE key='latest_since_27_1'")?.value ?? 0;
    const rows = name ? this.all('SELECT accounts.name,runs.target,runs.stamp,runs.data FROM runs JOIN accounts USING(uid) WHERE accounts.name=? COLLATE NOCASE AND runs.stamp>? ORDER BY stamp DESC LIMIT 10', username(name), since) : this.all('SELECT accounts.name,runs.target,runs.stamp,runs.data FROM runs JOIN accounts USING(uid) WHERE runs.stamp>? ORDER BY stamp DESC LIMIT 100', since);
    const result = dictionary();
    for (const row of rows) {
      const run = JSON.parse(row.data);
      if (run.r !== 'V') continue;
      const list = result[row.name] ??= [];
      if (list.length < 10) list.push({ ti: row.target, d: run.d, ts: run.t, tt: 'ITEM' });
    }
    if (!name) return result;
    const match = Object.keys(result).find(key => key.toLowerCase() === name.toLowerCase());
    return match ? result[match] : [];
  }
  claimLegacy(uid, name) {
    const rows = this.all('SELECT target,stamp,data FROM legacy_runs WHERE name=? ORDER BY stamp', name);
    if (!rows.length) return 0;
    for (const row of rows) {
      const attempt = this.one('SELECT COALESCE(MAX(attempt),0)+1 AS n FROM runs WHERE uid=? AND target=?', uid, row.target).n;
      this.sql.exec('INSERT OR IGNORE INTO runs VALUES (?,?,?,?,?)', uid, row.target, row.stamp, row.data, attempt);
    }
    this.sql.exec('DELETE FROM legacy_runs WHERE name=?', name);
    const account = this.account(uid), profile = JSON.parse(account.profile);
    profile.stats.tr = this.one('SELECT COUNT(*) AS n FROM runs WHERE uid=?', uid).n;
    this.sql.exec('UPDATE accounts SET profile=? WHERE uid=?', JSON.stringify(profile), uid);
    return rows.length;
  }
  async importLegacy(request) {
    const secret = (this.env.ADMIN_TOKEN ?? '').trim();
    const header = request.headers.get('authorization') ?? '';
    if (!secret || secret.length < 32 || !constantEqual(header, 'Bearer ' + secret)) fail(403, 'FORBIDDEN');
    const value = await body(request, 2000000);
    if (!Array.isArray(value.runs) || value.runs.length > 2000) fail(400, 'INVALID_IMPORT');
    let stored = 0, skipped = 0;
    const touched = new Map();
    for (const entry of value.runs) {
      try {
        const name = username(entry.name), target = validTarget(entry.target), run = entry.run ?? {};
        if (!finite(run.d, 0, 360000000) || !finite(run.t, 1, Date.now() + 60000) || !['V','D','G','T','F'].includes(run.r)) { skipped++; continue; }
        const clean = { rd: run.rd === true, d: Math.round(run.d), t: Math.round(run.t), r: run.r, s: String(run.s ?? '').slice(0, 64), cs: String(run.cs ?? '').slice(0, 64), b: run.b === true, h: run.h === true, m: String(run.m ?? 'legacy').slice(0, 16), legacy: true, u: run.u === true };
        if (typeof run.br === 'string' && /^[0-9]{5}$/.test(run.br)) clean.br = run.br;
        const before = this.one('SELECT COUNT(*) AS n FROM legacy_runs WHERE name=? AND target=? AND stamp=?', name, target, clean.t).n;
        this.sql.exec('INSERT OR REPLACE INTO legacy_runs VALUES (?,?,?,?)', name, target, clean.t, JSON.stringify(clean));
        if (!before) stored++; else skipped++;
        const account = this.one('SELECT uid,name FROM accounts WHERE name=? COLLATE NOCASE', name);
        if (account) touched.set(account.uid, account.name);
      } catch { skipped++; }
    }
    let claimed = 0;
    for (const [uid, name] of touched) claimed += this.claimLegacy(uid, name);
    if (finite(value.rooms, 1, 10000000)) this.sql.exec("INSERT INTO metadata VALUES ('legacy_rooms',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", Math.round(value.rooms));
    return json({ stored, skipped, claimed, legacy: this.one('SELECT COUNT(*) AS n FROM legacy_runs').n });
  }
  async ice(user) {
    this.limit('ice:' + user.uid, 12, 3600000);
    const member = this.one('SELECT code FROM membership WHERE uid=?', user.uid);
    if (!member) fail(403, 'ROOM_MEMBER_REQUIRED');
    this.room(member.code);
    const iceServers = [{ urls: ['stun:stun.cloudflare.com:3478'] }];
    if (this.env.TURN_KEY_ID && this.env.TURN_API_TOKEN) {
      const response = await fetch(`https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(this.env.TURN_KEY_ID)}/credentials/generate-ice-servers`, { method: 'POST', headers: { authorization: 'Bearer ' + this.env.TURN_API_TOKEN, 'content-type': 'application/json' }, body: JSON.stringify({ ttl: 21600 }) });
      if (!response.ok) fail(503, 'RELAY_UNAVAILABLE');
      const value = await response.json();
      const servers = Array.isArray(value.iceServers) ? value.iceServers : [value.iceServers];
      for (const server of servers) if (server?.urls) iceServers.push(server);
    }
    return json({ iceServers, relayAvailable: iceServers.length > 1, expires: Date.now() + ROOM_MS });
  }
  broadcast() {
    if (this.broadcastQueued) return;
    this.broadcastQueued = true;
    setTimeout(() => {
      this.broadcastQueued = false;
      this.sql.exec("INSERT INTO metadata VALUES ('revision',1) ON CONFLICT(key) DO UPDATE SET value=value+1");
      const context = this.context();
      for (const { socket, info, alive } of context.sockets) {
        try {
          if (!alive) { socket.close(4001, 'SESSION_EXPIRED'); continue; }
          socket.send(this.snapshot(info, context));
        } catch { try { socket.close(1011, 'DISCONNECTED'); } catch {} }
      }
    }, 40);
  }
  async webSocketMessage(socket, message) {
    try {
      if (typeof message !== 'string' || message.length > 65536) fail(400, 'INVALID_MESSAGE');
      const info = socket.deserializeAttachment();
      if (!info || info.expires <= Date.now() || !this.one('SELECT hash FROM sessions WHERE hash=?', info.session)) fail(401, 'SESSION_EXPIRED');
      const value = JSON.parse(message);
      if (value.type === 'ping') {
        const now = Date.now();
        if (now - (info.pingAt ?? 0) < 1000 || !finite(value.sentAt, 0, Number.MAX_SAFE_INTEGER)) fail(429, 'RATE_LIMITED');
        socket.serializeAttachment({ ...info, pingAt: now });
        socket.send(JSON.stringify({ type: 'pong', sentAt: value.sentAt, serverTime: now })); return;
      }
      this.limit('ws:' + info.uid, 240, 60000);
      if (value.type === 'presence') {
        const activity = ['menu', 'solo', 'lobby'].includes(value.activity) ? value.activity : 'menu';
        const presence = { activity };
        if (activity === 'solo' && typeof value.target === 'string' && /^(?:minecraft:)?[a-z0-9_]{1,100}$/.test(value.target)) presence.target = value.target.replace(/^minecraft:/, '');
        const previous = info.presence ?? { activity: 'menu' };
        socket.serializeAttachment({ ...info, presence });
        if (previous.activity !== presence.activity || previous.target !== presence.target) this.broadcast();
        return;
      }
      if (value.type !== 'signal' || !['offer','answer','candidate','close'].includes(value.kind) || typeof value.peer !== 'string' || !/^[0-9a-f-]{16,64}$/.test(value.connection)) fail(400, 'INVALID_SIGNAL');
      const member = this.one('SELECT code FROM membership WHERE uid=?', info.uid);
      if (!member || this.one('SELECT code FROM membership WHERE uid=?', value.peer)?.code !== member.code) fail(403, 'PEER_NOT_IN_ROOM');
      const room = this.room(member.code);
      if (!['LOADING','FROZEN','STARTED'].includes(room.value.st) || (room.owner !== info.uid && room.owner !== value.peer) || value.peer === info.uid) fail(403, 'SIGNAL_FORBIDDEN');
      const peer = this.ctx.getWebSockets().find(other => {
        const target = other.deserializeAttachment();
        return target?.uid === value.peer && target.expires > Date.now() && this.one('SELECT hash FROM sessions WHERE hash=?', target.session);
      });
      if (!peer) fail(409, 'PEER_OFFLINE');
      peer.send(JSON.stringify({ type: 'signal', kind: value.kind, peer: info.uid, connection: value.connection, data: value.data, room: room.code }));
    } catch (error) {
      try { socket.send(JSON.stringify({ type: 'error', error: error instanceof ApiError ? error.code : 'INVALID_MESSAGE' })); } catch {}
      if (error instanceof ApiError && error.status === 401) socket.close(4001, error.code);
    }
  }
  async webSocketClose(socket) {
    const info = socket.deserializeAttachment();
    if (info && !this.onlineIds().has(info.uid)) this.sql.exec('INSERT OR IGNORE INTO offline VALUES (?,?)', info.uid, Date.now());
    this.broadcast(); await this.ensureAlarm();
  }
  onlineIds() {
    return new Set(this.context().online.keys());
  }
  async webSocketError(socket) { try { socket.close(1011, 'DISCONNECTED'); } catch {} this.broadcast(); }
  async ensureAlarm() { if (await this.ctx.storage.getAlarm() == null) await this.ctx.storage.setAlarm(Date.now() + 60000); }
  async alarm() {
    const now = Date.now(), online = this.onlineIds();
    for (const member of this.all('SELECT uid FROM membership')) {
      if (online.has(member.uid)) this.sql.exec('DELETE FROM offline WHERE uid=?', member.uid);
      else this.sql.exec('INSERT OR IGNORE INTO offline VALUES (?,?)', member.uid, now);
    }
    for (const lost of this.all('SELECT uid FROM offline WHERE since<=?', now - 60000)) {
      if (!online.has(lost.uid)) this.leave(this.account(lost.uid));
      this.sql.exec('DELETE FROM offline WHERE uid=?', lost.uid);
    }
    this.sql.exec('DELETE FROM tickets WHERE begun<?', now - 21600000);
    this.sql.exec('DELETE FROM survivors WHERE at<?', now - 21600000);
    for (const room of this.all('SELECT code FROM rooms WHERE expires<=?', now)) {
      this.sql.exec('DELETE FROM membership WHERE code=?', room.code);
      this.sql.exec('DELETE FROM rooms WHERE code=?', room.code);
    }
    this.sql.exec('DELETE FROM limits WHERE expires<=?', now);
    this.sql.exec('DELETE FROM invites WHERE expires<=?', now);
    this.sql.exec('DELETE FROM sessions WHERE expires<=?', now);
    this.broadcast();
    if (this.one('SELECT COUNT(*) AS n FROM sessions').n || this.one('SELECT COUNT(*) AS n FROM rooms').n) await this.ctx.storage.setAlarm(now + 60000);
  }
}

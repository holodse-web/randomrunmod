import { DurableObject } from 'cloudflare:workers';
import MOD_HASHES from './mod-hashes.json';
import { ApiError, fail, randomToken, digest, constantEqual, username, password, passwordHash, verifyPassword, body, json, validTarget } from './security.mjs';

const TRUST = Object.freeze({
  start: 30, max: 200, threshold: 30, dailyCap: 15,
  victory: 3, battleVictory: 5,
  reports: Object.freeze(['CHEAT_MOD', 'CREATIVE', 'LAN', 'UNTRUSTED_RUN'])
});
const clampTrust = value => Math.max(0, Math.min(TRUST.max, Math.round(value)));

const SESSION_MS = 12 * 60 * 60 * 1000;
const SESSION_EXTEND_MS = 6 * 60 * 60 * 1000;
const REFRESH_MS = 5 * 24 * 60 * 60 * 1000;
const ROOM_MS = 6 * 60 * 60 * 1000;
const STALE_MS = 40 * 1000;
const TICKET_START_MS = 300000;
const MIN_PROTOCOL = 2;
const LEGACY_TICKET_START_MS = 900000;
const LEGACY_SUBMIT_MS = 8000;
const RUN_TIME_TOLERANCE = ms => 2000 + ms * 0.005;
const BATTLE_LATENCY_MS = 3000;
const TRACE_MAX_SPEED = 100;
const TRACE_FREE_JUMP = 160;
const TRACE_MAX_BAD = 2;
const TRACE_GAP_MS = 20000;
const BOARD_TTL_MS = 30 * 60 * 1000;
const PERSISTENT_LIMITS = /^(auth[a-z-]*|register|password|challenge|exists|refresh|report|create|friend-request|ice):/;
const clientVersion = value => typeof value === 'string' && /^[0-9]{1,3}(\.[0-9]{1,3}){0,2}$/.test(value) ? value : null;
const protocolOf = request => Number.parseInt(request.headers.get('x-rrm-protocol') ?? '0', 10) || 0;
const dictionary = () => Object.create(null);
const phraseText = value => typeof value === 'string' ? value.replace(/[\u0000-\u001f\u007f\u00a7]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 160) : '';
const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
const encoder = new TextEncoder();
const finite = (value, min, max) => typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max;
const streamerCode = value => typeof value === 'string' && /^[A-Z][A-Z0-9_]{0,31}$/.test(value) ? value : null;
const roomPlayer = streamer => { const player = { r: false, l: false, t: 0 }; const sc = streamerCode(streamer); if (sc) player.sc = sc; return player; };
const CHAT_KEEP = 60;
const chatText = value => typeof value === 'string' ? value.replace(/[\u0000-\u001f\u007f\u00a7\u200b-\u200f\u202a-\u202e\u2066-\u2069]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 140) : '';

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
    this.sql.exec(`CREATE TABLE IF NOT EXISTS battles (code TEXT NOT NULL, winner TEXT NOT NULL COLLATE NOCASE, start INTEGER NOT NULL, finish INTEGER NOT NULL, PRIMARY KEY(code,start))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS seen (uid TEXT PRIMARY KEY, at INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS tickets (id TEXT PRIMARY KEY, uid TEXT NOT NULL, target TEXT NOT NULL, seed TEXT NOT NULL, begun INTEGER NOT NULL, started INTEGER, used INTEGER NOT NULL DEFAULT 0)`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS tickets_owner ON tickets(uid)`);
    this.sql.exec("INSERT OR IGNORE INTO metadata VALUES ('verify_since',?)", Date.now());
    this.sql.exec(`CREATE TABLE IF NOT EXISTS legacy_runs (name TEXT NOT NULL COLLATE NOCASE, target TEXT NOT NULL, stamp INTEGER NOT NULL, data TEXT NOT NULL, PRIMARY KEY(name,target,stamp))`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS legacy_runs_target ON legacy_runs(target)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS invites (sender TEXT NOT NULL, target TEXT NOT NULL, code TEXT NOT NULL, expires INTEGER NOT NULL, PRIMARY KEY(sender,target))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS chat (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, text TEXT NOT NULL, at INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS refresh (hash TEXT PRIMARY KEY, uid TEXT NOT NULL, expires INTEGER NOT NULL)`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS refresh_owner ON refresh(uid)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS traces (code TEXT NOT NULL, uid TEXT NOT NULL, n INTEGER NOT NULL, x REAL NOT NULL, z REAL NOT NULL, dim TEXT NOT NULL, at INTEGER NOT NULL, bad INTEGER NOT NULL, item INTEGER, PRIMARY KEY(code,uid))`);
    const ticketColumns = new Set(this.all('PRAGMA table_info(tickets)').map(column => column.name));
    if (!ticketColumns.has('finished')) this.sql.exec('ALTER TABLE tickets ADD COLUMN finished INTEGER');
    if (!ticketColumns.has('proto')) this.sql.exec('ALTER TABLE tickets ADD COLUMN proto INTEGER NOT NULL DEFAULT 0');
    const battleColumns = new Set(this.all('PRAGMA table_info(battles)').map(column => column.name));
    if (!battleColumns.has('target')) this.sql.exec('ALTER TABLE battles ADD COLUMN target TEXT');
    if (!battleColumns.has('claimed')) this.sql.exec('ALTER TABLE battles ADD COLUMN claimed INTEGER NOT NULL DEFAULT 0');
    if (!this.one("SELECT value FROM metadata WHERE key='trust_v2'")) {
      this.ctx.storage.transactionSync(() => {
        for (const row of this.all('SELECT uid,profile FROM accounts')) {
          const profile = JSON.parse(row.profile);
          profile.sec.ts = clampTrust((profile.sec.ts ?? 20) + 10);
          this.sql.exec('UPDATE accounts SET profile=? WHERE uid=?', JSON.stringify(profile), row.uid);
        }
        this.sql.exec("INSERT INTO metadata VALUES ('trust_v2',1)");
      });
    }
    this.memoryLimits = new Map();
    this.sent = new Map();
  }
  one(query, ...args) { return this.sql.exec(query, ...args).toArray()[0] ?? null; }
  all(query, ...args) { return this.sql.exec(query, ...args).toArray(); }

  get legacy() { return this.env.LEGACY_CLIENTS !== '0'; }
  limit(key, max, period) {
    const now = Date.now();
    if (!PERSISTENT_LIMITS.test(key)) {
      const current = this.memoryLimits.get(key);
      if (current && current.expires > now) {
        if (current.count >= max) fail(429, 'RATE_LIMITED');
        current.count++;
      } else {
        this.memoryLimits.set(key, { count: 1, expires: now + period });
        if (this.memoryLimits.size > 20000) for (const [name, value] of this.memoryLimits) if (value.expires <= now) this.memoryLimits.delete(name);
      }
      return;
    }
    const current = this.one('SELECT * FROM limits WHERE key=?', key);
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
    return { ...account, session: hash, expires: this.extendSession(session) };
  }
  extendSession(session) {
    const now = Date.now();
    if (session.expires - now >= SESSION_EXTEND_MS) return session.expires;
    const expires = now + SESSION_MS;
    this.sql.exec('UPDATE sessions SET expires=? WHERE hash=?', expires, session.hash);
    for (const socket of this.ctx.getWebSockets()) {
      const info = socket.deserializeAttachment();
      if (info?.session === session.hash) socket.serializeAttachment({ ...info, expires });
    }
    return expires;
  }
  assertSession(user) {
    if (!this.one('SELECT hash FROM sessions WHERE hash=? AND uid=? AND expires>?', user.session, user.uid, Date.now())) fail(401, 'SESSION_EXPIRED');
    if (JSON.parse(this.account(user.uid).profile).sec.b) fail(403, 'ACCOUNT_BANNED');
  }
  async issue(account, remember = false, refreshUntil = null) {
    const token = randomToken(), expires = Date.now() + SESSION_MS;
    const refresh = remember ? randomToken() : null;
    const hashed = await digest(token), refreshHash = refresh ? await digest(refresh) : null, current = this.account(account.uid);
    this.ctx.storage.transactionSync(() => {
      if (this.account(account.uid).hash !== account.hash) fail(401, 'CREDENTIALS_CHANGED');
      if (JSON.parse(current.profile).sec.b) fail(403, 'ACCOUNT_BANNED');
      this.sql.exec('INSERT INTO sessions VALUES (?,?,?)', hashed, account.uid, expires);
      const sessions = this.all('SELECT hash FROM sessions WHERE uid=? ORDER BY expires DESC', account.uid);
      for (const session of sessions.slice(3)) this.sql.exec('DELETE FROM sessions WHERE hash=?', session.hash);
      if (refreshHash) {
        this.sql.exec('INSERT INTO refresh VALUES (?,?,?)', refreshHash, account.uid, refreshUntil ?? Date.now() + REFRESH_MS);
        const tokens = this.all('SELECT hash FROM refresh WHERE uid=? ORDER BY expires DESC', account.uid);
        for (const stale of tokens.slice(5)) this.sql.exec('DELETE FROM refresh WHERE hash=?', stale.hash);
      }
    });
    await this.ensureAlarm();
    const result = { token, expires, user: { id: account.uid, name: account.name }, profile: JSON.parse(current.profile) };
    if (refresh) result.refresh = refresh;
    return json(result);
  }
  async refreshSession(request) {
    const ip = await digest(request.headers.get('x-client-ip') ?? 'local');
    this.limit('refresh-ip:' + ip, 20, 60000);
    const value = await body(request, 2048);
    if (typeof value.refresh !== 'string' || !/^[0-9a-f]{64}$/.test(value.refresh)) fail(401, 'REFRESH_INVALID');
    const hash = await digest(value.refresh), now = Date.now();
    const stored = this.one('SELECT * FROM refresh WHERE hash=? AND expires>?', hash, now);
    if (!stored) fail(401, 'REFRESH_INVALID');
    const account = this.account(stored.uid);
    await this.checkIntegrity(value, account.name);
    this.sql.exec('DELETE FROM refresh WHERE hash=?', hash);
    if (JSON.parse(account.profile).sec.b) fail(403, 'ACCOUNT_BANNED');
    return this.issue(account, true, stored.expires);
  }
  async fetch(request) {
    const state = {};
    const response = await this.route(request, state);
    if (!state.expires || response.status === 101) return response;
    const headers = new Headers(response.headers);
    headers.set('x-session-expires', String(state.expires));
    return new Response(response.body, { status: response.status, headers });
  }
  async route(request, state) {
    try {
      const url = new URL(request.url), path = url.pathname.slice(3), method = request.method;
      if (method === 'GET' && path === '/version') return json({ latest: this.env.MIN_MOD_VERSION, newest: this.env.MOD_VERSION, min_required: this.env.MIN_MOD_VERSION, download_url: '', changelog: 'RandomRun online services' });
      if (method === 'GET' && path === '/health') return json({ ok: true, protocol: MIN_PROTOCOL, serverTime: Date.now() });
      if (method === 'GET' && path === '/auth/challenge') {
        this.limit('challenge:' + await digest(request.headers.get('x-client-ip') ?? 'local'), 30, 60000);
        const nonce = randomToken(16), now = Date.now();
        this.sql.exec('DELETE FROM nonces WHERE expires<?', now);
        this.sql.exec('INSERT INTO nonces VALUES (?,?)', nonce, now + 120000);
        return json({ nonce });
      }
      const auth = method === 'POST' && ['/auth/register', '/auth/login', '/auth/refresh'].includes(path);
      const open = method === 'GET' && (path === '/auth/challenge' || path.startsWith('/auth/exists/'));
      if (!open && !this.legacy && protocolOf(request) < MIN_PROTOCOL) fail(426, 'UPDATE_REQUIRED');
      if (auth && path !== '/auth/refresh') return await this.login(request, path.endsWith('register'));
      if (method === 'POST' && path === '/auth/refresh') return await this.refreshSession(request);
      const existsMatch = method === 'GET' && path.match(/^\/auth\/exists\/([A-Za-z0-9_]{3,16})$/);
      if (existsMatch) {
        this.limit('exists:' + await digest(request.headers.get('x-client-ip') ?? 'local'), 30, 60000);
        return json({ exists: !!this.one('SELECT uid FROM accounts WHERE name=? COLLATE NOCASE', existsMatch[1]) });
      }
      const payload = ['POST','PUT','PATCH'].includes(method) ? await body(request) : null;
      const user = await this.authenticate(request);
      user.proto = protocolOf(request);
      state.expires = user.expires;
      this.limit('api:' + user.uid, 240, 60000);
      if (method === 'GET' && path === '/auth/session') return json({ user: { id: user.uid, name: user.name }, expires: user.expires });
      if (method === 'POST' && path === '/auth/logout') {
        this.leave(user);
        this.sql.exec('DELETE FROM sessions WHERE hash=?', user.session);
        if (typeof payload?.refresh === 'string' && /^[0-9a-f]{64}$/.test(payload.refresh)) this.sql.exec('DELETE FROM refresh WHERE hash=? AND uid=?', await digest(payload.refresh), user.uid);
        this.closeSession(user.session);
        return json({ ok: true });
      }
      if (method === 'POST' && path === '/auth/password') return await this.changePassword(payload, user);
      if (method === 'GET' && path === '/events') return this.connect(request, user);
      if (method === 'POST' && path === '/ice') return await this.ice(user);
      if (method === 'GET' && path === '/friends') return json(this.social(user));
      if (method === 'POST' && path === '/runs/begin') return json(this.beginRun(payload, user));
      if (method === 'POST' && path === '/runs/start') return json(this.startTicket(payload, user));
      if (method === 'POST' && path === '/runs/finish') return json(this.finishTicket(payload, user));
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
        if (method === 'GET' && !action) {
          if (this.one('SELECT code FROM membership WHERE uid=?', user.uid)?.code === code) {
            this.sql.exec('DELETE FROM offline WHERE uid=?', user.uid);
          }
          return json(this.publicRoom(room, user));
        }
        this.requireMember(room, user);
        if (method === 'POST' && action === 'disconnect') {
          if (room.owner !== user.uid) fail(403, 'HOST_REQUIRED');
          const member = this.one('SELECT accounts.* FROM membership JOIN accounts USING(uid) WHERE code=? AND name=? COLLATE NOCASE', code, username(payload.player));
          if (!member || member.uid === user.uid) fail(400, 'INVALID_PLAYER');
          if (room.value.st === 'STARTED' && this.onlineIds().has(member.uid)) fail(409, 'PLAYER_ONLINE');
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
        const reason = payload?.reason;
        if (typeof reason !== 'string' || !TRUST.reports.includes(reason)) fail(400, 'INVALID_REPORT');
        this.limit('report:' + user.uid, 30, 3600000);
        const profile = JSON.parse(user.profile);
        profile.sec.sf = (profile.sec.sf ?? 0) + 1;
        profile.sec.lcd = Date.now();
        this.sql.exec('UPDATE accounts SET profile=? WHERE uid=?', JSON.stringify(profile), user.uid);
        console.warn('[Report]', user.name, reason);
        return json({ ok: true, ts: profile.sec.ts });
      }
      fail(404, 'NOT_FOUND');
    } catch (error) {
      if (error instanceof ApiError) {
        return json({ error: error.code }, error.status);
      }
      console.error('[API] Request failed:', error?.name ?? 'Error');
      return json({ error: 'INTERNAL_ERROR' }, 500);
    }
  }
  async login(request, register) {
    const ip = await digest(request.headers.get('x-client-ip') ?? 'local');
    this.limit('auth-ip:' + ip, 20, 60000);
    const value = await body(request, 2048), name = username(value.username);
    password(value.password);
    await this.checkIntegrity(value, name);
    this.limit('auth-user:' + name.toLowerCase() + ':' + ip, 8, 60000);
    this.limit('auth-name:' + name.toLowerCase(), 40, 600000);
    let account = this.one('SELECT * FROM accounts WHERE name=? COLLATE NOCASE', name);
    if (register) {
      this.limit('register:' + ip, 5, 3600000);
      if (account) fail(409, 'USERNAME_TAKEN');
      const result = await passwordHash(value.password), uid = randomToken(16), now = Date.now();
      const profile = { info: { u: uid, ca: now, ls: now, mv: this.env.MOD_VERSION }, sec: { ts: TRUST.start, b: false }, stats: { tr: 0, fts: false } };
      if (this.one('SELECT uid FROM accounts WHERE name=? COLLATE NOCASE', name)) fail(409, 'USERNAME_TAKEN');
      this.sql.exec('INSERT INTO accounts VALUES (?,?,?,?,?,?)', uid, name, result.salt, result.hash, JSON.stringify(profile), now);
      account = this.account(uid);
    } else {
      const check = await verifyPassword(value.password, account?.salt ?? 'unregistered-account-salt', account?.hash);
      if (!account || !check.ok || this.account(account.uid).hash !== account.hash) fail(401, 'INVALID_CREDENTIALS');
      if (check.upgrade) {
        const next = await passwordHash(value.password);
        const updated = this.one('UPDATE accounts SET salt=?, hash=? WHERE uid=? AND hash=? RETURNING *', next.salt, next.hash, account.uid, account.hash);
        if (!updated) fail(401, 'CREDENTIALS_CHANGED');
        account = updated;
      }
    }
    if (JSON.parse(account.profile).sec.b) fail(403, 'ACCOUNT_BANNED');
    return this.issue(account, value.remember === true);
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
    if (!(await verifyPassword(value.currentPassword, user.salt, user.hash)).ok) fail(401, 'INVALID_CREDENTIALS');
    const next = await passwordHash(value.newPassword);
    const account = this.ctx.storage.transactionSync(() => {
      this.assertSession(user);
      const updated = this.one('UPDATE accounts SET salt=?, hash=? WHERE uid=? AND hash=? RETURNING *', next.salt, next.hash, user.uid, user.hash);
      if (!updated) fail(409, 'CREDENTIALS_CHANGED');
      this.sql.exec('DELETE FROM sessions WHERE uid=?', user.uid);
      this.sql.exec('DELETE FROM refresh WHERE uid=?', user.uid);
      return updated;
    });
    for (const socket of this.ctx.getWebSockets()) if (socket.deserializeAttachment()?.uid === user.uid) socket.close(4001, 'SESSION_REVOKED');
    return this.issue(account, value.remember === true);
  }
  connect(request, user) {
    if (request.headers.get('upgrade')?.toLowerCase() !== 'websocket') fail(426, 'WEBSOCKET_REQUIRED');
    this.limit('connect:' + user.uid, 20, 60000);
    const existing = this.ctx.getWebSockets().filter(socket => socket.deserializeAttachment()?.uid === user.uid);
    for (const socket of existing) socket.close(4000, 'REPLACED');
    this.sql.exec('DELETE FROM offline WHERE uid=?', user.uid);
    this.sql.exec('INSERT OR REPLACE INTO seen VALUES (?,?)', user.uid, Date.now());
    const pair = new WebSocketPair(), [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ uid: user.uid, name: user.name, session: user.session, expires: user.expires, aliveAt: Date.now(), proto: user.proto });
    const context = this.context();
    this.sent.set(user.session, this.signature(user, context));
    server.send(this.snapshot(user, context));
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
      const stale = !!info && now - (info.aliveAt ?? now) >= STALE_MS;
      const fresh = !!info && info.expires > now && !stale;
      const alive = fresh && valid.has(info.session);
      sockets.push({ socket, info, alive, stale });
      if (alive) online.set(info.uid, info.name);
      if (fresh && !presence.has(info.uid)) presence.set(info.uid, info.presence ?? { activity: 'menu' });
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
  snapshot(user, context, social = JSON.stringify(this.social(user, context))) {
    return '{"type":"snapshot","revision":' + context.revision + ',"rooms":' + this.roomsJson(user, context) + ',"online":' + context.onlineJson + ',"social":' + social + ',"serverTime":' + Date.now() + '}';
  }
  signature(user, context, social = JSON.stringify(this.social(user, context))) {
    return this.roomsJson(user, context).replace(/"revision":[0-9]+,?/g, '') + context.onlineJson + social;
  }
  storeRoom(room) {
    room.value.upd = Date.now();
    this.sql.exec('UPDATE rooms SET data=? WHERE code=?', JSON.stringify(room.value), room.code);
    this.broadcast();
  }
  async createRoom(value, user) {
    this.limit('create:' + user.uid, 20, 3600000);
    const max = value.maxPlayers ?? 2;
    if (!Number.isInteger(max) || max < 2 || max > 6) fail(400, 'INVALID_CAPACITY');
    if (value.target != null) validTarget(value.target);
    if (value.seed != null && (typeof value.seed !== 'string' || value.seed.length > 64)) fail(400, 'INVALID_SEED');
    let secret = null;
    if (value.password) { if (typeof value.password !== 'string' || value.password.length > 64) fail(400, 'INVALID_ROOM_PASSWORD'); secret = await passwordHash(value.password); }
    this.assertSession(user);
    let code;
    do { code = String(10000 + crypto.getRandomValues(new Uint32Array(1))[0] % 90000); } while (this.one('SELECT code FROM rooms WHERE code=?', code));
    const room = { h: { [user.name]: roomPlayer(value.streamer) }, g: dictionary(), s: value.seed ?? '0', t: value.target ?? null, st: 'WAITING', pr: !!secret, cm: value.mode === 'sw' ? 'sw' : 'webrtc', mp: max, gm: value.randomDrop ? 'random_drop' : 'default', ug: value.upgrader === true, vo: value.voice === true, ca: Date.now(), upd: Date.now(), dd: dictionary(), ep: dictionary(), start: 0, srv_start: 0, swt: 0 };
    this.ctx.storage.transactionSync(() => {
      this.leave(user);
      const stale = this.one('SELECT code FROM rooms WHERE owner=?', user.uid);
      if (stale) { this.sql.exec('DELETE FROM membership WHERE code=?', stale.code); this.sql.exec('DELETE FROM rooms WHERE code=?', stale.code); }
      this.sql.exec('DELETE FROM offline WHERE uid=?', user.uid);
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
      if (!(await verifyPassword(value.password, room.salt, room.hash)).ok) fail(403, 'WRONG_ROOM_PASSWORD');
    }
    this.assertSession(user);
    const original = room;
    room = this.room(code);
    if (original.owner !== room.owner || original.hash !== room.hash || original.value.ca !== room.value.ca) fail(409, 'ROOM_CHANGED');
    const existing = this.one('SELECT code FROM membership WHERE uid=?', user.uid);
    if (existing?.code === code) return json(this.publicRoom(room, user));
    if (existing) {
      const previous = this.one('SELECT data FROM rooms WHERE code=? AND expires>?', existing.code, Date.now());
      if (previous && !['FINISHED', 'DRAW'].includes(JSON.parse(previous.data).st)) fail(409, 'ALREADY_IN_ROOM');
    }
    if (room.value.st !== 'WAITING') fail(409, 'GAME_ALREADY_STARTED');
    if (this.one('SELECT COUNT(*) AS n FROM membership WHERE code=?', code).n >= room.value.mp) fail(409, 'ROOM_FULL');
    room.value.g[user.name] = roomPlayer(value.streamer);
    this.ctx.storage.transactionSync(() => {
      if (existing) this.leave(user);
      this.sql.exec('DELETE FROM offline WHERE uid=?', user.uid);
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
    const started = room.value.st === 'STARTED';
    if (room.owner === user.uid && !started) {
      this.sql.exec('DELETE FROM membership WHERE code=?', room.code);
      this.sql.exec('DELETE FROM rooms WHERE code=?', room.code);
    } else {
      this.sql.exec('DELETE FROM membership WHERE uid=?', user.uid);
      if (started) {
        if (room.owner === user.uid) {
          const heir = this.one('SELECT uid FROM membership WHERE code=? LIMIT 1', room.code);
          if (!heir) {
            this.sql.exec('DELETE FROM rooms WHERE code=?', room.code);
            this.broadcast();
            return;
          }
          this.sql.exec('DELETE FROM rooms WHERE owner=? AND code<>?', heir.uid, room.code);
          this.sql.exec('UPDATE rooms SET owner=? WHERE code=?', heir.uid, room.code);
          room.owner = heir.uid;
        }
        const name = Object.keys(room.value.h).concat(Object.keys(room.value.g)).find(entry => entry.toLowerCase() === user.name.toLowerCase());
        if (name) room.value.ep[name] = true;
        room.value.dr = 'PLAYER_LEFT';
        this.settle(room);
      } else {
        delete room.value.g[user.name];
        if (!['WAITING', 'FINISHED', 'DRAW'].includes(room.value.st)) {
          room.value.st = 'DRAW'; room.value.dr = 'PLAYER_DISCONNECTED';
        }
      }
      this.storeRoom(room);
    }
    this.broadcast();
  }
  settle(room) {
    const value = room.value;
    if (value.st !== 'STARTED') return;
    const alive = Object.keys(value.h).concat(Object.keys(value.g)).filter(name => !value.ep[name]);
    if (alive.length > 1) return;
    const survivor = alive[0] ?? null;
    value.w = survivor;
    value.st = survivor ? 'FINISHED' : 'DRAW';
    if (survivor) {
      this.sql.exec('INSERT OR REPLACE INTO survivors VALUES (?,?,?)', room.code, survivor, Date.now());
      this.recordBattle(room.code, value);
    }
  }
  claimAllowed(room, user) {

    if (user.proto < MIN_PROTOCOL && this.legacy) return true;
    const trace = this.one('SELECT * FROM traces WHERE code=? AND uid=?', room.code, user.uid), now = Date.now();
    if (!trace || trace.bad > TRACE_MAX_BAD || !trace.item || now - trace.item > 60000) return false;
    return trace.n >= Math.max(1, Math.floor((now - room.value.srv_start) / TRACE_GAP_MS));
  }
  trace(info, value) {
    const member = this.one('SELECT code FROM membership WHERE uid=?', info.uid);
    if (!member) return;
    let room;
    try { room = this.room(member.code); } catch { return; }
    const now = Date.now();
    if (room.value.st !== 'STARTED' || now < room.value.srv_start) return;
    if (!finite(value.x, -30000000, 30000000) || !finite(value.z, -30000000, 30000000)) return;
    const dim = typeof value.dim === 'string' && /^[a-z0-9_:./-]{1,64}$/.test(value.dim) ? value.dim : 'unknown';
    const previous = this.one('SELECT * FROM traces WHERE code=? AND uid=?', room.code, info.uid);
    if (previous && now - previous.at < 2000 && value.item !== true) return;
    let bad = previous?.bad ?? 0;
    if (previous && previous.dim === dim && value.rs !== true) {
      const distance = Math.hypot(value.x - previous.x, value.z - previous.z);
      if (distance > TRACE_FREE_JUMP && distance / Math.max(1, (now - previous.at) / 1000) > TRACE_MAX_SPEED) {
        bad++;
        console.warn('[Trace]', info.name, room.code, Math.round(distance));
      }
    }
    const item = value.item === true ? now : previous?.item ?? null;
    this.sql.exec('INSERT OR REPLACE INTO traces VALUES (?,?,?,?,?,?,?,?,?)', room.code, info.uid, (previous?.n ?? 0) + 1, value.x, value.z, dim, now, bad, item);
  }
  patchRoom(room, updates, user) {
    this.limit('room:' + user.uid, 120, 60000);
    const value = room.value, host = room.owner === user.uid, initial = value.st;
    const rank = key => key === 'trace' ? -1 : key === 'st' ? 1 : 0;
    const changes = Object.entries(updates).sort(([a], [b]) => rank(a) - rank(b));
    if (changes.length > 20) fail(400, 'TOO_MANY_FIELDS');
    for (const [path, entry] of changes) {
      const parts = path.split('/');
      if (parts.length === 3 && ['h','g'].includes(parts[0]) && parts[1].toLowerCase() === user.name.toLowerCase() && ['r','l'].includes(parts[2])) {
        if (typeof entry !== 'boolean' || !own(value[parts[0]], user.name) || !['LOADING','FROZEN','WAITING'].includes(initial)) fail(403, 'INVALID_PLAYER_STATE');
        value[parts[0]][user.name][parts[2]] = entry;
      } else if (parts.length === 3 && ['h','g'].includes(parts[0]) && parts[1].toLowerCase() === user.name.toLowerCase() && parts[2] === 'sc' && own(value[parts[0]], user.name) && ['WAITING','LOADING','FROZEN','STARTED'].includes(initial)) {
        if (entry === '' || entry === null) delete value[parts[0]][user.name].sc;
        else if (streamerCode(entry)) value[parts[0]][user.name].sc = entry;
        else fail(400, 'INVALID_STREAMER');
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
        this.sql.exec('DELETE FROM traces WHERE code=?', room.code);
      } else if (path === 'trace' && initial === 'STARTED' && entry && typeof entry === 'object') {
        this.trace(user, { x: entry.x, z: entry.z, dim: entry.dim, item: entry.item === true });
      } else if (path === 'w' && initial === 'STARTED' && entry === user.name && !value.w && !value.ep[user.name] && Date.now() >= value.srv_start) {
        if (!this.claimAllowed(room, user)) {
          console.warn('[Battle] Claim rejected', user.name, room.code);
          fail(403, 'CLAIM_REJECTED');
        }
        value.w = user.name;
      }
      else if (path === 'st' && entry === 'FINISHED' && initial === 'STARTED' && value.w === user.name) value.st = 'FINISHED';
      else if (parts.length === 2 && parts[0] === 'ep' && parts[1].toLowerCase() === user.name.toLowerCase() && entry === true && initial === 'STARTED' && Date.now() >= value.srv_start) value.ep[user.name] = true;
      else if (parts.length === 3 && ['h','g'].includes(parts[0]) && parts[1].toLowerCase() === user.name.toLowerCase() && parts[2] === 't' && own(value[parts[0]], user.name) && finite(entry, 0, 21600000) && ['STARTED','FINISHED'].includes(initial)) value[parts[0]][user.name].t = Math.round(entry);
      else if (path === 'st' && host && entry === 'DRAW' && ['WAITING','LOADING','FROZEN'].includes(initial)) value.st = 'DRAW';
      else if (path === 'dr' && host && typeof entry === 'string' && entry.length <= 100) value.dr = entry;
      else if (path === 'upd' && finite(entry, 0, Number.MAX_SAFE_INTEGER)) continue;
      else if (initial === 'FINISHED' && value.w === user.name && ((path === 'w' && entry === user.name) || (path === 'st' && entry === 'FINISHED'))) continue;
      else fail(403, 'ROOM_CHANGE_FORBIDDEN');
    }
    if (!value.w) this.settle(room);
    if (value.w && value.st === 'STARTED') value.st = 'FINISHED';
    if (initial !== 'FINISHED' && value.st === 'FINISHED' && value.w) this.recordBattle(room.code, value);
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
    for (const row of this.all('SELECT friends.a,friends.b,friends.accepted,accounts.uid,accounts.name,seen.at AS seen FROM friends JOIN accounts ON accounts.uid=(CASE WHEN friends.a=? THEN friends.b ELSE friends.a END) LEFT JOIN seen ON seen.uid=accounts.uid WHERE friends.a=? OR friends.b=? ORDER BY accounts.name COLLATE NOCASE', user.uid, user.uid, user.uid)) {
      if (!row.accepted) { (row.a === user.uid ? outgoing : incoming).push(row.name); continue; }
      const entry = { name: row.name, online: online.has(row.uid), activity: 'offline' };
      if (!entry.online && row.seen) entry.seen = row.seen;
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
    if (!finite(value.d, 0, 7200000) || !finite(value.t, 1, Date.now() + 60000) || !['V','D','G','T','F','L'].includes(value.r)) fail(400, 'INVALID_RUN');
    if (typeof value.s !== 'string' || value.s.length > 64 || (value.cs != null && (typeof value.cs !== 'string' || value.cs.length > 64))) fail(400, 'INVALID_SEED');
    if (value.br != null && (typeof value.br !== 'string' || !/^[0-9]{5}$/.test(value.br))) fail(400, 'INVALID_ROOM');
    const clean = { rd: value.rd === true, u: value.u === true, vo: value.vo === true, d: Math.round(value.d), t: Math.round(value.t), r: value.r, s: value.s, cs: value.cs ?? '', b: value.b === true, h: value.h === true, m: clientVersion(value.m) ?? this.env.MOD_VERSION };
    let entry = null;
    const response = this.ctx.storage.transactionSync(() => {
      const profile = JSON.parse(this.account(user.uid).profile);
      const existing = this.one('SELECT data FROM runs WHERE uid=? AND target=? AND stamp=?', user.uid, target, clean.t);
      if (existing) return this.runStatus(JSON.parse(existing.data), profile.sec.ts);
      if (value.br != null) {
        if (clean.r !== 'V' || this.confirmWin(value.br, user, clean.d, target)) clean.br = value.br;
        if (clean.br && this.one('SELECT 1 AS x FROM survivors WHERE code=? AND name=? AND at>?', clean.br, user.name, Date.now() - ROOM_MS)) clean.sv = true;
      }
      if (typeof value.rid === 'string' && !clean.br) {
        const failure = this.verifyTicket(user, target, value, clean);
        clean.v = !failure;
        if (failure) clean.vr = failure;
      }
      const attempt = this.one('SELECT COALESCE(MAX(attempt),0)+1 AS n FROM runs WHERE uid=? AND target=?', user.uid, target).n;
      this.sql.exec('INSERT INTO runs VALUES (?,?,?,?,?)', user.uid, target, clean.t, JSON.stringify(clean), attempt);
      profile.stats.tr = this.one('SELECT COUNT(*) AS n FROM runs WHERE uid=?', user.uid).n;
      const before = profile.sec.ts;
      this.rewardRun(profile, clean, value);
      this.sql.exec('UPDATE accounts SET profile=? WHERE uid=?', JSON.stringify(profile), user.uid);
      entry = { before, after: profile.sec.ts };
      return this.runStatus(clean, profile.sec.ts);
    });
    if (entry) this.updateBoards(user.name, target, clean, entry.before, entry.after);
    return json(response);
  }
  runStatus(run, ts) {
    const excluded = this.recordExclusion(run, ts, this.verifySince());
    const result = { ok: true, ts, counted: !excluded };
    if (run.v !== undefined) result.v = run.v === true;
    if (excluded) result.reason = run.vr && excluded === 'unverified' ? run.vr : excluded;
    return result;
  }
  verifySince() {
    return this.since ??= this.one("SELECT value FROM metadata WHERE key='verify_since'")?.value ?? 0;
  }
  updateBoards(name, target, run, before, after) {
    if (!this.boards) return;
    if ((before >= TRUST.threshold) !== (after >= TRUST.threshold)) { this.boards = null; return; }
    if (run.r !== 'V' || this.recordExclusion(run, after, this.verifySince())) return;
    const table = this.boards[target] ??= dictionary();
    if (!table[name] || run.d < table[name].t) table[name] = { t: run.d, ts: run.t, s: run.s, tt: 'ITEM' };
  }
  recordBattle(code, value) {
    this.sql.exec('INSERT OR IGNORE INTO battles VALUES (?,?,?,?,?,0)', code, value.w, value.srv_start, Date.now(), value.t ?? null);
  }
  confirmWin(code, user, duration, target) {
    const now = Date.now();
    let battle = this.one('SELECT * FROM battles WHERE code=? AND winner=? AND finish>? ORDER BY finish DESC LIMIT 1', code, user.name, now - ROOM_MS);
    if (!battle) {
      const row = this.one('SELECT * FROM rooms WHERE code=? AND expires>?', code, now);
      if (!row || this.one('SELECT code FROM membership WHERE uid=?', user.uid)?.code !== code) return false;
      const room = this.room(code), value = room.value;
      if (value.st !== 'STARTED' || value.w || value.ep[user.name] || now < value.srv_start) return false;
      if (!this.claimAllowed(room, user)) {
        console.warn('[Battle] Claim rejected', user.name, code);
        return false;
      }
      value.w = user.name; value.st = 'FINISHED';
      this.recordBattle(code, value);
      this.storeRoom(room);
      battle = this.one('SELECT * FROM battles WHERE code=? AND start=?', code, value.srv_start);
    }
    if (!battle || battle.claimed || (battle.target && battle.target !== target)) return false;
    const elapsed = battle.finish - battle.start;
    if (duration > elapsed + 5000 || duration < elapsed - BATTLE_LATENCY_MS) return false;
    this.sql.exec('UPDATE battles SET claimed=1 WHERE code=? AND start=?', code, battle.start);
    return true;
  }
  rewardRun(profile, clean, value) {
    if (value.u === true || value.vo === true || clean.r !== 'V' || clean.rd) return;
    let gain = 0;
    if (clean.br && !clean.sv) gain = TRUST.battleVictory;
    else if (!clean.br && clean.v === true && !clean.cs) gain = TRUST.victory;
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
    this.sql.exec('INSERT INTO tickets (id,uid,target,seed,begun,proto) VALUES (?,?,?,?,?,?)', id, user.uid, target, seed.toString(), now, user.proto);
    return { id, seed: seed.toString() };
  }
  ticket(value, user) {
    if (typeof value.id !== 'string' || value.id.length > 64) fail(400, 'INVALID_TICKET');
    const ticket = this.one('SELECT * FROM tickets WHERE id=? AND uid=?', value.id, user.uid);
    if (!ticket || ticket.used) fail(404, 'TICKET_NOT_FOUND');
    return ticket;
  }
  startTicket(value, user) {
    const ticket = this.ticket(value, user), now = Date.now();
    if (ticket.started) return { ok: true, started: ticket.started };
    if (now - ticket.begun > (ticket.proto >= MIN_PROTOCOL ? TICKET_START_MS : LEGACY_TICKET_START_MS)) fail(409, 'TICKET_EXPIRED');
    this.sql.exec('UPDATE tickets SET started=? WHERE id=?', now, ticket.id);
    return { ok: true, started: now };
  }
  finishTicket(value, user) {
    const ticket = this.ticket(value, user), now = Date.now();
    if (!ticket.started) fail(409, 'TICKET_NOT_STARTED');
    if (ticket.finished) return { ok: true, finished: ticket.finished };
    this.sql.exec('UPDATE tickets SET finished=? WHERE id=?', now, ticket.id);
    return { ok: true, finished: now };
  }
  verifyTicket(user, target, value, clean) {
    const ticket = this.one('SELECT * FROM tickets WHERE id=? AND uid=?', value.rid.slice(0, 64), user.uid);
    if (!ticket || ticket.used) return 'no_ticket';
    if (!ticket.started) return 'not_started';
    if (ticket.target !== target) return 'target';
    if (clean.s !== ticket.seed || clean.cs) return 'seed';
    const rta = finite(value.rta, 0, 360000000) ? Math.round(value.rta) : null;
    if (rta == null || clean.d > rta + 1000 || clean.d < rta - 1500) return 'time';
    if (ticket.finished) {
      const server = ticket.finished - ticket.started;
      if (Math.abs(clean.d - server) > RUN_TIME_TOLERANCE(server)) return 'time';
    } else {
      if (ticket.proto >= MIN_PROTOCOL || user.proto >= MIN_PROTOCOL) return 'no_finish';
      if (!this.legacy) return 'outdated';
      const elapsed = Date.now() - ticket.started;
      if (rta < elapsed - LEGACY_SUBMIT_MS - elapsed * 0.01 || rta > elapsed + 2000) return 'time';
    }
    this.sql.exec('UPDATE tickets SET used=1 WHERE id=?', ticket.id);
    return null;
  }
  recordExclusion(run, trustScore, since, legacy = false) {
    if (run.r !== 'V' || run.d <= 0) return 'invalid_time';
    if (run.cs && !run.br) return 'custom_seed';
    if (run.rd) return 'random_drop';
    if (run.u) return 'upgrader';
    if (run.sv) return 'survivor';
    if (!legacy && !run.br && run.t >= since && run.v !== true) return 'unverified';
    if (!legacy && trustScore < TRUST.threshold) return 'low_trust';
    return null;
  }
  leaderboards(target) {
    const all = this.allBoards();
    return target ? all[validTarget(target)] ?? {} : all;
  }
  allBoards() {
    const now = Date.now();
    if (this.boards && now - this.boardsAt < BOARD_TTL_MS) return this.boards;
    const result = dictionary();
    const trust = new Map();
    const trusted = name => {
      if (!trust.has(name)) trust.set(name, JSON.parse(this.one('SELECT profile FROM accounts WHERE name=?', name).profile).sec.ts);
      return trust.get(name);
    };
    const since = this.verifySince();
    const consider = (name, row, legacy = false) => {
      const run = JSON.parse(row.data);
      if (this.recordExclusion(run, legacy ? TRUST.threshold : trusted(name), since, legacy)) return;
      const table = result[row.target] ??= dictionary();
      if (!table[name] || run.d < table[name].t) table[name] = { t: run.d, ts: run.t, s: run.s, tt: 'ITEM' };
    };
    const rows = this.all("SELECT accounts.name,runs.target,runs.data FROM runs JOIN accounts USING(uid) WHERE json_extract(runs.data, '$.r')='V' ORDER BY stamp DESC");
    for (const row of rows) consider(row.name, row);
    const legacy = this.all('SELECT name,target,data FROM legacy_runs');
    for (const row of legacy) consider(row.name, row, true);
    this.boards = result;
    this.boardsAt = now;
    return result;
  }
  latestRuns(name) {
    const player = name ? username(name).toLowerCase() : null;
    const entries = [];
    for (const [target, table] of Object.entries(this.leaderboards())) {
      for (const [owner, record] of Object.entries(table)) {
        if (player && owner.toLowerCase() !== player) continue;
        entries.push({ owner, ti: target, d: record.t, ts: record.ts, tt: record.tt });
      }
    }
    entries.sort((a, b) => b.ts - a.ts || a.owner.localeCompare(b.owner) || a.ti.localeCompare(b.ti));
    const latest = entries.slice(0, player ? 10 : 5);
    if (player) return latest.map(({ owner, ...record }) => record);
    const result = dictionary();
    for (const { owner, ...record } of latest) (result[owner] ??= []).push(record);
    return result;
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
      const context = this.context(), active = new Set();
      for (const { socket, info, alive, stale } of context.sockets) {
        try {
          if (stale) { socket.close(1001, 'STALE'); continue; }
          if (!alive) { socket.close(4001, 'SESSION_EXPIRED'); continue; }
          active.add(info.session);
          const social = JSON.stringify(this.social(info, context)), signature = this.signature(info, context, social);
          if (this.sent.get(info.session) === signature) continue;
          this.sent.set(info.session, signature);
          socket.send(this.snapshot(info, context, social));
        } catch { try { socket.close(1011, 'DISCONNECTED'); } catch {} }
      }
      for (const session of this.sent.keys()) if (!active.has(session)) this.sent.delete(session);
    }, 40);
  }
  async webSocketMessage(socket, message) {
    try {
      if (typeof message !== 'string' || message.length > 65536) fail(400, 'INVALID_MESSAGE');
      const info = socket.deserializeAttachment();
      const session = info && info.expires > Date.now() ? this.one('SELECT * FROM sessions WHERE hash=? AND expires>?', info.session, Date.now()) : null;
      if (!session) fail(401, 'SESSION_EXPIRED');
      const value = JSON.parse(message);
      if (value.type === 'ping') {
        const now = Date.now();
        if (now - (info.pingAt ?? 0) < 1000 || !finite(value.sentAt, 0, Number.MAX_SAFE_INTEGER)) fail(429, 'RATE_LIMITED');
        const expires = this.extendSession(session);
        socket.serializeAttachment({ ...info, expires, pingAt: now, aliveAt: now });
        socket.send(JSON.stringify({ type: 'pong', sentAt: value.sentAt, serverTime: now, expires })); return;
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
      if (value.type === 'chat') { this.chat(socket, info, value); return; }
      if (value.type === 'chat_history') { socket.send(this.chatHistory()); return; }
      if (value.type === 'say') { this.relayPhrase(socket, info, value); return; }
      if (value.type === 'trace') { this.trace(info, value); return; }
      if (value.type !== 'signal' || !['offer','answer','candidate','restart','close'].includes(value.kind) || typeof value.peer !== 'string' || !/^[0-9a-f-]{16,64}$/.test(value.connection)) fail(400, 'INVALID_SIGNAL');
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
  chat(socket, info, value) {
    const now = Date.now();
    try {
      const text = chatText(value.text);
      if (!text) fail(400, 'INVALID_MESSAGE');
      if (now - (info.chatAt ?? 0) < 1200 || (text === info.chatLast && now - info.chatAt < 20000)) fail(429, 'RATE_LIMITED');
      this.limit('chat:' + info.uid, 12, 60000);
      const account = this.one('SELECT profile FROM accounts WHERE uid=?', info.uid);
      if (!account || JSON.parse(account.profile).sec.b) fail(403, 'ACCOUNT_BANNED');
      socket.serializeAttachment({ ...info, chatAt: now, chatLast: text });
      const id = this.one('INSERT INTO chat (name,text,at) VALUES (?,?,?) RETURNING id', info.name, text, now).id;
      if (id % 50 === 0) this.sql.exec('DELETE FROM chat WHERE id<=?', id - CHAT_KEEP);
      this.chatCache = null;
      const message = JSON.stringify({ type: 'chat', m: { i: id, n: info.name, t: text, a: now } });
      for (const other of this.ctx.getWebSockets()) {
        const target = other.deserializeAttachment();
        if (!target || target.expires <= now) continue;
        try { other.send(message); } catch {}
      }
    } catch (error) {
      socket.send(JSON.stringify({ type: 'chat_error', error: error instanceof ApiError ? error.code : 'INVALID_MESSAGE' }));
    }
  }
  chatHistory() {
    return this.chatCache ??= JSON.stringify({ type: 'chat_history', m: this.all('SELECT id AS i,name AS n,text AS t,at AS a FROM chat ORDER BY id DESC LIMIT ?', CHAT_KEEP).reverse() });
  }
  relayPhrase(socket, info, value) {
    const now = Date.now();
    if (now - (info.sayAt ?? 0) < 1500) return;
    const id = typeof value.id === 'string' && /^[a-z0-9_.]{1,48}$/.test(value.id) ? value.id : null;
    if (!id && !this.legacy) fail(400, 'INVALID_MESSAGE');
    const text = phraseText(value.text);
    const sound = typeof value.sound === 'string' && /^[a-z0-9_.]{0,48}$/.test(value.sound) ? value.sound : '';
    if (!text && !id) fail(400, 'INVALID_MESSAGE');
    const member = this.one('SELECT code FROM membership WHERE uid=?', info.uid);
    if (!member) return;
    const room = this.room(member.code);
    if (room.value.st === 'WAITING') return;
    const speaker = room.value.h[info.name] ?? room.value.g[info.name];
    if (!speaker?.sc) return;
    socket.serializeAttachment({ ...info, sayAt: now });
    const others = new Set(this.all('SELECT uid FROM membership WHERE code=?', member.code).map(row => row.uid).filter(uid => uid !== info.uid));
    const payload = { type: 'say', from: info.name, streamer: speaker.sc, sound, text };
    if (id) payload.id = id;
    const message = JSON.stringify(payload);
    for (const other of this.ctx.getWebSockets()) {
      const target = other.deserializeAttachment();
      if (!target || !others.has(target.uid) || target.expires <= now) continue;
      try { other.send(message); } catch {}
    }
  }
  async webSocketClose(socket) {
    const info = socket.deserializeAttachment();
    if (info) this.sql.exec('INSERT OR REPLACE INTO seen VALUES (?,?)', info.uid, Date.now());
    if (info && !this.onlineIds().has(info.uid)) this.sql.exec('INSERT OR IGNORE INTO offline VALUES (?,?)', info.uid, Date.now());
    this.broadcast(); await this.ensureAlarm();
  }
  onlineIds() {
    return new Set(this.context().online.keys());
  }
  async webSocketError(socket) { try { socket.close(1011, 'DISCONNECTED'); } catch {} this.broadcast(); }
  async ensureAlarm() { if (await this.ctx.storage.getAlarm() == null) await this.ctx.storage.setAlarm(Date.now() + 20000); }
  async alarm() {
    const now = Date.now(), online = this.onlineIds();
    let changed = false;
    for (const socket of this.ctx.getWebSockets()) {
      const info = socket.deserializeAttachment();
      if (!info || now - (info.aliveAt ?? now) < STALE_MS) continue;
      if (!online.has(info.uid)) this.sql.exec('INSERT OR REPLACE INTO seen VALUES (?,?)', info.uid, info.aliveAt);
      try { socket.close(1001, 'STALE'); } catch {}
      changed = true;
    }
    for (const member of this.all('SELECT uid FROM membership')) {
      if (online.has(member.uid)) this.sql.exec('DELETE FROM offline WHERE uid=?', member.uid);
      else this.sql.exec('INSERT OR IGNORE INTO offline VALUES (?,?)', member.uid, now);
    }
    for (const lost of this.all('SELECT uid FROM offline WHERE since<=?', now - 60000)) {
      if (!online.has(lost.uid)) { this.leave(this.account(lost.uid)); changed = true; }
      this.sql.exec('DELETE FROM offline WHERE uid=?', lost.uid);
    }
    this.sql.exec('DELETE FROM tickets WHERE begun<?', now - 21600000);
    this.sql.exec('DELETE FROM survivors WHERE at<?', now - ROOM_MS);
    this.sql.exec('DELETE FROM battles WHERE finish<?', now - 86400000);
    this.sql.exec('DELETE FROM traces WHERE at<?', now - ROOM_MS);
    this.sql.exec('DELETE FROM refresh WHERE expires<=?', now);
    for (const room of this.all('SELECT code FROM rooms WHERE expires<=?', now)) {
      this.sql.exec('DELETE FROM membership WHERE code=?', room.code);
      this.sql.exec('DELETE FROM rooms WHERE code=?', room.code);
      changed = true;
    }
    this.sql.exec('DELETE FROM limits WHERE expires<=?', now);
    if (this.sql.exec('DELETE FROM invites WHERE expires<=?', now).rowsWritten) changed = true;
    if (this.sql.exec('DELETE FROM sessions WHERE expires<=?', now).rowsWritten) changed = true;
    if (changed) this.broadcast();
    if (this.one('SELECT COUNT(*) AS n FROM sessions').n || this.one('SELECT COUNT(*) AS n FROM rooms').n) await this.ctx.storage.setAlarm(now + (this.ctx.getWebSockets().length ? 20000 : 60000));
  }
}

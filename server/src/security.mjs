import { pbkdf2Async } from '@noble/hashes/pbkdf2.js';
import { sha256 } from '@noble/hashes/sha2.js';

const encoder = new TextEncoder();
export class ApiError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
export const fail = (status, code) => { throw new ApiError(status, code); };
export const randomToken = (size = 32) => Array.from(crypto.getRandomValues(new Uint8Array(size)), b => b.toString(16).padStart(2, '0')).join('');
export const digest = async value => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(value))), b => b.toString(16).padStart(2, '0')).join('');
export function constantEqual(a, b) {
  const x = encoder.encode(a), y = encoder.encode(b);
  let difference = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) difference |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return difference === 0;
}
export function username(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_]{3,16}$/.test(value)) fail(400, 'INVALID_USERNAME');
  return value;
}
export function password(value) {
  if (typeof value !== 'string' || value.length < 8 || encoder.encode(value).length > 128) fail(400, 'INVALID_PASSWORD');
  return value;
}
export async function passwordHash(value, salt = randomToken(16)) {
  const bytes = await pbkdf2Async(sha256, value, salt, { c: 600000, dkLen: 32, asyncTick: 20 });
  return { salt, hash: Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('') };
}
export function json(value, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' } });
}
export async function body(request, limit = 65536) {
  if (!request.body) fail(400, 'BODY_REQUIRED');
  const reader = request.body.getReader(), chunks = [];
  let size = 0;
  while (true) {
    const result = await reader.read();
    if (result.done) break;
    size += result.value.length;
    if (size > limit) { await reader.cancel(); fail(413, 'BODY_TOO_LARGE'); }
    chunks.push(result.value);
  }
  const data = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { data.set(chunk, offset); offset += chunk.length; }
  let value;
  try { value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(data)); } catch { fail(400, 'INVALID_JSON'); }
  if (!value || Array.isArray(value) || typeof value !== 'object') fail(400, 'OBJECT_REQUIRED');
  return value;
}
export function validTarget(value) {
  if (typeof value !== 'string' || !/^(?:minecraft:)?[a-z0-9_]{1,100}$/.test(value)) fail(400, 'INVALID_TARGET');
  return value.replace(/^minecraft:/, '');
}

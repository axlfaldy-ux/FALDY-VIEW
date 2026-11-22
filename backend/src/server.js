import { createHmac, randomBytes, randomInt, randomUUID, timingSafeEqual } from 'node:crypto';
import { createServer as createHttpServer } from 'node:http';
import { pathToFileURL } from 'node:url';

const CODE_DIGITS = 6;
const MAX_BODY_BYTES = 4 * 1024;
const MAX_SESSIONS = 10_000;
const MAX_RATE_KEYS = 10_000;
const RATE_WINDOW_MS = 10 * 60 * 1000;

function json(response, status, payload) {
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
  });
  response.end(JSON.stringify(payload));
}

function digest(secret, value) {
  return createHmac('sha256', secret).update(value).digest();
}

function token() {
  return randomBytes(32).toString('base64url');
}

function constantTimeMatches(expected, presented) {
  if (typeof presented !== 'string' || presented.length > 256) return false;
  const actual = digest(expected.secret, presented);
  return timingSafeEqual(expected.hash, actual);
}

async function readJson(request) {
  let size = 0;
  const chunks = [];
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw Object.assign(new Error('Request body too large'), { status: 413 });
    chunks.push(chunk);
  }
  if (size === 0) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw Object.assign(new Error('Malformed JSON'), { status: 400 });
  }
}

export function createPairingServer({ secret, ttlMs = 5 * 60_000, now = Date.now } = {}) {
  if (typeof secret !== 'string' || Buffer.byteLength(secret) < 32) {
    throw new Error('PAIRING_HMAC_SECRET must contain at least 32 bytes');
  }
  if (!Number.isFinite(ttlMs) || ttlMs < 1000 || ttlMs > 5 * 60_000) {
    throw new Error('ttlMs must be between 1000 and 300000 milliseconds');
  }

  const sessions = new Map();
  const rateLimits = new Map();

  function cleanup() {
    const time = now();
    for (const [id, session] of sessions) {
      if (session.expiresAt <= time || ['rejected', 'cancelled', 'disconnected'].includes(session.status)) {
        sessions.delete(id);
      }
    }
    for (const [key, entry] of rateLimits) {
      if (entry.resetAt <= time) rateLimits.delete(key);
    }
  }

  function limited(request, bucket, limit) {
    cleanup();
    const address = request.socket.remoteAddress ?? 'unknown';
    const key = `${bucket}:${address}`;
    let entry = rateLimits.get(key);
    if (!entry || entry.resetAt <= now()) {
      if (rateLimits.size >= MAX_RATE_KEYS) {
        const oldestKey = rateLimits.keys().next().value;
        if (oldestKey) rateLimits.delete(oldestKey);
      }
      entry = { count: 0, resetAt: now() + RATE_WINDOW_MS };
      rateLimits.set(key, entry);
    }
    entry.count += 1;
    return entry.count > limit;
  }

  function newCode() {
    for (let attempt = 0; attempt < 100; attempt += 1) {
      const candidate = randomInt(0, 10 ** CODE_DIGITS).toString().padStart(CODE_DIGITS, '0');
      const codeHash = digest(secret, candidate);
      if (![...sessions.values()].some((session) => timingSafeEqual(session.codeHash, codeHash))) return { candidate, codeHash };
    }
    throw new Error('Unable to allocate a unique pairing code');
  }

  function authenticate(request, session, role) {
    const parts = (request.headers.authorization ?? '').trim().split(/\s+/);
    if (parts.length !== 2) return false;
    const [scheme, presented] = parts;
    const credential = session.credentials[role];
    return scheme === 'Bearer' && credential && constantTimeMatches({ secret, hash: credential.hash }, presented);
  }

  async function handler(request, response) {
    if (request.method === 'GET' && request.url === '/healthz') {
      return json(response, 200, { status: 'ok' });
    }

    let url;
    try {
      url = new URL(request.url ?? '/', 'http://localhost');
    } catch {
      return json(response, 400, { error: 'invalid_url' });
    }

    if (request.method === 'POST' && url.pathname === '/v1/sessions') {
      if (limited(request, 'create', 20)) return json(response, 429, { error: 'rate_limited' });
      cleanup();
      if (sessions.size >= MAX_SESSIONS) return json(response, 503, { error: 'capacity_reached' });
      const { candidate, codeHash } = newCode();
      const id = randomUUID();
      const controllerToken = token();
      const createdAt = now();
      const session = {
        id,
        codeHash,
        status: 'waiting',
        createdAt,
        expiresAt: createdAt + ttlMs,
        credentials: {
          controller: { hash: digest(secret, controllerToken) },
          receiver: null,
        },
      };
      sessions.set(id, session);
      return json(response, 201, {
        sessionId: id,
        pairingCode: candidate,
        controllerToken,
        status: session.status,
        expiresAt: new Date(session.expiresAt).toISOString(),
      });
    }

    if (request.method === 'POST' && url.pathname === '/v1/sessions/join') {
      if (limited(request, 'join', 10)) return json(response, 429, { error: 'rate_limited' });
      const body = await readJson(request);
      if (typeof body.code !== 'string' || !/^\d{6}$/.test(body.code)) {
        return json(response, 400, { error: 'invalid_code_format' });
      }
      cleanup();
      const submittedHash = digest(secret, body.code);
      const session = [...sessions.values()].find((candidate) => timingSafeEqual(candidate.codeHash, submittedHash));
      if (!session || session.status !== 'waiting') return json(response, 404, { error: 'pairing_code_unavailable' });
      const receiverToken = token();
      session.credentials.receiver = { hash: digest(secret, receiverToken) };
      session.status = 'approval_pending';
      return json(response, 200, { sessionId: session.id, receiverToken, status: session.status, expiresAt: new Date(session.expiresAt).toISOString() });
    }

    const match = url.pathname.match(/^\/v1\/sessions\/([0-9a-f-]{36})(?:\/(approve|reject|cancel|leave|disconnect))?$/i);
    if (!match) return json(response, 404, { error: 'not_found' });
    const [, id, action] = match;
    cleanup();
    const session = sessions.get(id);
    if (!session) return json(response, 404, { error: 'session_unavailable' });

    const controllerRoute = ['approve', 'reject', 'cancel'].includes(action);
    const role = controllerRoute ? 'controller' : 'receiver';

    if (request.method === 'GET' && !action) {
      if (!authenticate(request, session, 'controller') && !authenticate(request, session, 'receiver')) {
        return json(response, 401, { error: 'unauthorized' });
      }
      return json(response, 200, { sessionId: session.id, status: session.status, createdAt: new Date(session.createdAt).toISOString(), expiresAt: new Date(session.expiresAt).toISOString() });
    }
    if (!authenticate(request, session, role)) return json(response, 401, { error: 'unauthorized' });
    if (request.method !== 'POST' || !action) return json(response, 405, { error: 'method_not_allowed' });

    const allowed = {
      approve: { from: 'approval_pending', to: 'approved', role: 'controller' },
      reject: { from: 'approval_pending', to: 'rejected', role: 'controller' },
      cancel: { from: ['waiting', 'approval_pending', 'approved'], to: 'cancelled', role: 'controller' },
      leave: { from: 'approval_pending', to: 'cancelled', role: 'receiver' },
      disconnect: { from: 'approved', to: 'disconnected', role: 'receiver' },
    }[action];
    if (allowed.role !== role) return json(response, 403, { error: 'forbidden' });
    const currentAllowed = Array.isArray(allowed.from) ? allowed.from.includes(session.status) : allowed.from === session.status;
    if (!currentAllowed) return json(response, 409, { error: 'invalid_state_transition', status: session.status });

    session.status = allowed.to;
    const publicStatus = { sessionId: session.id, status: session.status, expiresAt: new Date(session.expiresAt).toISOString() };
    if (['rejected', 'cancelled', 'disconnected'].includes(session.status)) sessions.delete(session.id);
    return json(response, 200, publicStatus);
  }

  const server = createHttpServer((request, response) => {
    handler(request, response).catch((error) => {
      if (!response.headersSent) json(response, error.status ?? 500, { error: error.status ? 'invalid_request' : 'internal_error' });
      else response.destroy();
    });
  });
  const cleanupTimer = setInterval(cleanup, Math.min(ttlMs, 30_000));
  cleanupTimer.unref();
  server.on('close', () => clearInterval(cleanupTimer));
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const secret = process.env.PAIRING_HMAC_SECRET;
  const port = Number(process.env.PORT ?? 8080);
  const server = createPairingServer({ secret });
  server.listen(port, '0.0.0.0', () => console.log(`FALDY VIEW pairing API listening on port ${port}`));
}

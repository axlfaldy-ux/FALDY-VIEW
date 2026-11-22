import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';
import { createPairingServer } from '../src/server.js';

const secret = 'test-only-secret-that-is-at-least-32-bytes';
let server;
let baseUrl;

beforeEach(async () => {
  server = createPairingServer({ secret });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

afterEach(async () => {
  if (server?.listening) await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

async function post(path, body, bearer) {
  return fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(bearer ? { authorization: `Bearer ${bearer}` } : {}),
    },
    body: JSON.stringify(body ?? {}),
  });
}

async function createSession() {
  const response = await post('/v1/sessions');
  assert.equal(response.status, 201);
  return response.json();
}

test('requires a strong server-side HMAC secret', () => {
  assert.throws(() => createPairingServer({ secret: 'too-short' }), /at least 32 bytes/);
});

test('creates a six-digit one-time code and requires controller approval', async () => {
  const controller = await createSession();
  assert.match(controller.pairingCode, /^\d{6}$/);
  assert.equal(controller.status, 'waiting');

  const unauthorized = await post(`/v1/sessions/${controller.sessionId}/approve`, {}, 'wrong-token');
  assert.equal(unauthorized.status, 401);

  const join = await post('/v1/sessions/join', { code: controller.pairingCode });
  assert.equal(join.status, 200);
  const receiver = await join.json();
  assert.equal(receiver.status, 'approval_pending');

  const receiverStatusResponse = await fetch(`${baseUrl}/v1/sessions/${controller.sessionId}`, {
    headers: { authorization: `Bearer ${receiver.receiverToken}` },
  });
  assert.equal((await receiverStatusResponse.json()).status, 'approval_pending');

  const approved = await post(`/v1/sessions/${controller.sessionId}/approve`, {}, controller.controllerToken);
  assert.equal(approved.status, 200);
  assert.equal((await approved.json()).status, 'approved');

  const disconnected = await post(`/v1/sessions/${controller.sessionId}/disconnect`, {}, receiver.receiverToken);
  assert.equal(disconnected.status, 200);
  assert.equal((await disconnected.json()).status, 'disconnected');
});

test('receiver can leave an approval request and the code cannot be reused', async () => {
  const controller = await createSession();
  const joined = await post('/v1/sessions/join', { code: controller.pairingCode });
  const receiver = await joined.json();
  const left = await post(`/v1/sessions/${controller.sessionId}/leave`, {}, receiver.receiverToken);
  assert.equal(left.status, 200);
  assert.equal((await left.json()).status, 'cancelled');
  assert.equal((await post('/v1/sessions/join', { code: controller.pairingCode })).status, 404);
});

test('rejects malformed codes and expires sessions', async () => {
  const malformed = await post('/v1/sessions/join', { code: '12345' });
  assert.equal(malformed.status, 400);

  let currentTime = 1_000_000;
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  server = createPairingServer({ secret, ttlMs: 1_000, now: () => currentTime });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;

  const controller = await createSession();
  currentTime += 1_001;
  const expired = await post('/v1/sessions/join', { code: controller.pairingCode });
  assert.equal(expired.status, 404);
});

test('rate limits repeated join attempts from one client address', async () => {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    assert.equal((await post('/v1/sessions/join', { code: '000000' })).status, 404);
  }
  assert.equal((await post('/v1/sessions/join', { code: '000000' })).status, 429);
});

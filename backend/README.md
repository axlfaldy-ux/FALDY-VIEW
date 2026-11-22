# FALDY VIEW pairing API (prototype)

Self-hosted Node.js pairing API with no third-party runtime dependencies. It does not relay media, exchange WebRTC signaling messages, authenticate account identities, or connect to the Android app yet.

## Run locally

Requires Node.js 22 or newer. Set a unique server-side secret of at least 32 bytes; never put it in source control or in the Android app.

```sh
export PAIRING_HMAC_SECRET="$(openssl rand -hex 32)"
npm start
```

The server listens on `0.0.0.0:8080` by default. `PORT` changes the port. `/healthz` returns a basic health response. The service does not terminate TLS; any internet deployment must use HTTPS through a correctly configured TLS reverse proxy or platform ingress.

## API

Every response is JSON with `Cache-Control: no-store`. Session credentials are bearer tokens and are returned only once at creation/join. Store them only in app memory or Android Keystore-backed storage; never log them.

| Method and route | Purpose | Authorization |
| --- | --- | --- |
| `POST /v1/sessions` | Create a session and return a random six-digit code, session ID, and Controller token. | None |
| `POST /v1/sessions/join` with `{"code":"123456"}` | Consume an available code once and return the Receiver token. | None; limited by source IP |
| `GET /v1/sessions/{id}` | Read current session status. | Controller or Receiver bearer token |
| `POST /v1/sessions/{id}/approve` | Approve a pending pairing request. | Controller bearer token |
| `POST /v1/sessions/{id}/reject` | Reject a pending pairing request. | Controller bearer token |
| `POST /v1/sessions/{id}/cancel` | Cancel a waiting, pending, or approved session. | Controller bearer token |
| `POST /v1/sessions/{id}/leave` | Cancel the Receiver's pending request. | Receiver bearer token |
| `POST /v1/sessions/{id}/disconnect` | End an approved session. | Receiver bearer token |

Pairing codes expire after five minutes, are single-use, and are stored as keyed digests in the service's memory. Random bearer credentials are also stored as keyed digests. Join requests are limited to 10 per source address per 10 minutes, and session creation to 20 per source address per 10 minutes. State and rate limits are process-local and disappear on restart.

## Deployment limits and security requirements

This is a single-process prototype, not a production-ready public service. Do not run multiple replicas: they will not share sessions or rate limits. Before deployment, add shared ephemeral storage and distributed rate limiting, robust client IP handling for the selected proxy, monitoring, abuse protection, secret rotation, and an operational retention policy. Do not trust arbitrary `X-Forwarded-For` headers.

A six-digit code is only a short-lived pairing locator, not a user identity or a long-term secret. Possession of the random bearer token authenticates a session role. The app must verify the server using HTTPS and protect bearer tokens. Pairing approval does not grant MediaProjection consent. The Receiver must separately approve screen capture through Android's system prompt in the screen-sharing stage.

This API uses HTTP polling for state and does not include WebSocket signaling. WebRTC signaling, STUN/TURN, authenticated TURN credentials, media encryption policy, reconnect behavior, and the Android client are future work. No hosting provider, paid service, domain, or public endpoint has been selected.

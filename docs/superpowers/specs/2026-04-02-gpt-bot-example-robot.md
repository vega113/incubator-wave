# gpt-bot Example Robot Design

Status: implemented
Date: 2026-04-02
Scope: a copyable Java example robot for SupaWave that receives callback events, detects explicit mentions, and posts generated replies through the Wave robot APIs.

## Implemented Shape

The example now lives in `org.waveprotocol.examples.robots.gptbot` and includes:

- `GET /healthz`
- `GET /_wave/capabilities.xml`
- `GET /_wave/robot/profile`
- `POST /_wave/robot/jsonrpc`

The default flow is passive callback replies. Optional configuration enables active API writes and context fetches through SupaWave's JSON-RPC endpoints.

The outbound SupaWave client path now reuses the shared Java robot SDK with endpoint-scoped JWT bearer auth instead of hand-built JSON-RPC request payloads.

Passive callback delivery still follows the current server contract:

- callback discovery through `/_wave/capabilities.xml`
- event delivery to `/_wave/robot/jsonrpc`
- callback protection via the configured callback token in the registered URL

Server-signed callback bearer JWTs are not part of the live runtime contract yet, so the example does not invent a separate callback-auth protocol.

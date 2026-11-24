# ClassDrop Enhancement Plan (C: Harden + Product Quality)

**Status**: COMPLETE  
**Verified**: 77/77 automated tests green; fat JAR rebuilt (`target/classdrop-1.0.0.jar`)

## Phase overview

| Phase | Focus | Outcome |
|---|---|---|
| 0 | Truth & docs | Docs match shipped code; stretch claims corrected |
| 1 | MVP hardening | `/me` + `/stats` APIs, host share UI, `JsonUtil`, Maven notes |
| 2 | Host controls | Remove file, kick device, extension blocklist |
| 3 | Transfer reliability | HTTP Range resume, download-all ZIP |
| 4 | UX polish | Web/Swing polish, image preview, SSE live updates |
| 5 | Packaging | Tests green, JAR, docs final sync |

## Delivered

- Host Swing: **Share Local File**, **Remove Selected**, **Kick Selected Device**, host token display
- HTTP: `/me`, `/stats`, `DELETE /files/{id}`, `/kick`, `/download` Range/206, `/download-all.zip`, `/preview/{id}`, `/events` SSE
- Security: blocked dangerous extensions (`.exe`, `.bat`, `.ps1`, …)
- Client: room stats strip, ZIP button, image thumbnails/modal, SSE with polling fallback
- Tooling: portable Maven under `.tools/` (gitignored), launcher prefers it when rebuilding

## Still out of scope (honest future work)

- UDP / mDNS nearby discovery
- Optional HTTPS / TLS
- Cloud / Internet Share via `NetworkMode.RELAY` (**shipped** — optional tunnel + passphrase; offline Hotspot remains primary). See `docs/FULL_OFFLINE_CONNECTION_PLAN.md`.

## Gate rule

Do not mark a feature complete in docs until code + tests exist.

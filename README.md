# Festo Mobile — Android chat client (`festomobile2`)

> [!IMPORTANT]
> **Canonical name: `festo-private-mobile`.** Part of the `festo-private`
> constellation, wired to `festo-private-backend` (GitHub: `wendy-prototype`)
> — `WendyApi.kt` hardcodes that VPS's address and a live bearer token.
> Same opencode session, same memory, same lock as that backend's Telegram
> bot: this app is a second body for the same assistant, not a separate one.
> **Not connected to `festo-commercial`** (GitHub: `festofire1`) — the
> multi-tenant fleet has no mobile client today. Full ecosystem map: see
> `festo-docs` (GitHub: `festo-handoff`) §0.

## What this is

A native Kotlin/Jetpack Compose Android app — streaming text chat, a model
picker, turn-based voice, cross-thread memory. Built directly in Google AI
Studio, not by an engineering session — package is still `com.example`
(the AI Studio default) even though `applicationId` is `com.festofire.mobile`.

## Connects to

- `festo-private-backend`'s `mobile_api.py` (`http://74.208.155.72:8090`),
  via `app/src/main/java/com/example/data/WendyApi.kt`
- `POST /api/chat` — NDJSON streamed reply
- `GET /api/history` — reads back the shared opencode session

## Known issue, not yet fixed

The bearer token and backend IP in `WendyApi.kt` are hardcoded directly in
committed source, in a **public** repo. Anyone can read it off GitHub and
hit the endpoint as the authorized user. Needs moving to a build-time
config / secrets mechanism and the token needs rotating.

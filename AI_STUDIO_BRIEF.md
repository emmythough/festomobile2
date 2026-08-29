# Brief for Google AI Studio — read this before changing anything

This is `festomobile2` — the real Android client for Wendy (canonical name
`festo-private-mobile`). It's further along than it may look from the repo
alone; read this first so work doesn't collide with what's already real.

## What's real right now (don't rebuild these)

- **Chat** — `WendyApi.kt` calls the real backend at `74.208.155.72:8090`
  (Gen 1's `mobile_api.py`), streaming real replies from the actual Wendy
  — same brain, same memory as Telegram. Not mocked.
- **History** — the app loads real past conversation on launch via
  `GET /api/history`, not placeholder data.
- **Voice** — real mic capture (`VoiceAudioEngine.kt`) → real
  speech-to-text → real chat reply → real text-to-speech → real playback.
  The backend endpoints (`/api/audio/transcribe`, `/api/audio/speak`) are
  live and verified working as of 2026-08-29.

## What to actually work on

1. **Test the real voice pipeline on a physical device end to end** —
   record a real question, confirm it transcribes correctly, confirm the
   reply plays back as audio. This has been verified server-side (the
   endpoints work), but not yet confirmed on-device with a real
   recording. If something breaks, it's almost certainly on the client
   side (mic permission flow, audio format mismatch, playback), not the
   server — the server side is independently verified.
2. **General UI/UX polish** — visual quality, animations, empty states,
   error states. Normal client-side work, no backend implications.
3. **Bug fixes in existing screens** — anything broken in chat, history,
   drawer, settings that doesn't require a new backend capability.

## Do NOT do these without asking first (real, unresolved decisions)

- **Do not add push notifications (FCM/APNs).** Explicitly declined —
  no Firebase project exists or is wanted right now. If a
  `google-services.json` warning appears in the build, that's expected
  and harmless (`missingGoogleServicesStrategy = WARN` in
  `build.gradle.kts`) — do not "fix" it by creating a Firebase project.
- **Do not switch the backend integration to v4's API.** A second,
  parallel backend (`v4/`) exists and is live, with a different
  architecture (async, poll-based replies) than what this app currently
  uses (Gen 1, synchronous streaming). Moving the app over is a real,
  undecided architecture change — flag it, don't just do it.
- **Do not touch anything under a `server/` or backend-looking
  directory** if one appears in context — this repo is client-only.

## If you push changes

Push to `main` directly (this repo's real convention) with a clear,
accurate commit message describing what actually changed — not what you
intended, what the diff actually does. If unsure whether something here
is stale, verify against the real repo state rather than trust this
document blindly; it was written 2026-08-29 and things move fast.

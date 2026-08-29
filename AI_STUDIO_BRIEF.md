# Brief for Google AI Studio — read this before changing anything

This is `festomobile2` — the real Android client for Wendy (canonical name
`festo-private-mobile`). The backend behind this app is genuinely
sophisticated (real streaming, real memory, real voice, real sandboxing).
The frontend is not yet at that bar. This brief exists to close that gap.
Read it fully before touching code — it corrects a stale prior version of
itself in two places and gives you a full, prioritized punch list.

## Build order (read the detail sections before starting any of these)

1. **Model picker is decorative — resolve it.** It presents a choice
   that does nothing. Needs an owner decision first.
2. **Cramped-text sweep** — one instance fixed, find the rest.
3. **Memory browser** (§A) — real live endpoint, biggest capability win.
4. **Floating voice capsule** (§B) — pure client-side, biggest feel win.
5. **Markdown rendering + syntax highlighting** — table stakes.
6. **Haptics** (§D) — near-zero effort.
7. **Remaining 5 animations** — the polish pass.
8. **Live work panel** (§C), settings screen, message actions,
   swipe-to-delete.
9. **Voice → v4** (§E) — blocked on a firewall change.

## What's real right now (don't rebuild these)

- **Chat runs on v4**, not the old Gen 1 backend. `WendyApi.kt` posts to
  `74.208.155.72:8091` (`POST /api/v4/message`) and polls
  `GET /api/v4/replies` for the reply — accept-and-poll, not a single
  streamed HTTP body. The reply stream has no explicit "done" flag; the
  client tells ack / delta / final apart by the real event shape
  (`kind=="ack"` → placeholder, has `"blocks"` → the true final, otherwise
  → a streaming delta). This migration is DONE and live-verified — a
  stale earlier version of this brief said "don't switch to v4," that is
  no longer true, ignore it if you see it anywhere else.
- **History and voice (`/api/history`, `/api/audio/transcribe`,
  `/api/audio/speak`) currently point at Gen 1**, port 8090, with Gen 1's
  own separate bearer token (`GEN1_API_TOKEN` in `WendyApi.kt`). Two
  different tokens for two different hosts is deliberate, not a bug;
  don't "simplify" it to one constant.
  **Correction to an earlier version of this brief:** it claimed v4 has
  no voice endpoints. That was wrong — v4 has a live voice gateway (see
  the gateway map below). Voice staying on Gen 1 is now a *migration
  that hasn't happened yet*, not a capability gap. History genuinely has
  no v4 equivalent.
- **Dark and light mode already exist and work.** `ui/theme/Theme.kt` has
  two complete, correct color schemes (`DarkColorScheme`/
  `LightColorScheme`) and follows the system setting automatically via
  `isSystemInDarkTheme()`. Don't build new themes. What's missing is a
  **manual in-app override** (Settings → Appearance → System / Light /
  Dark) — that's a real, scoped task below, not "add dark mode from
  scratch."
- **The voice pipeline is real end to end** — mic capture
  (`VoiceAudioEngine.kt`) → transcribe → v4 reply → speak → playback, with
  live barge-in.

## The real backend surface (verified live 2026-08-29, not from docs)

The backend is much larger than this app uses. Six v4 services run on
`74.208.155.72`. Only the first two are wired into the app today:

| Port | What | Endpoints | App uses it? |
|---|---|---|---|
| 8090 | Gen 1 (legacy, still live) | `/api/history`, `/api/audio/transcribe`, `/api/audio/speak` | yes |
| 8091 | v4 mobile API | `/api/v4/health`, `/message`, `/replies` | yes |
| 8092 | v4 voice gateway | `/api/v4/voice/health`, `/voice/message`, `/voice/speak` | **no — unused** |
| 8093 | v4 introspection API | `/api/v4/introspection/health`, `/workflows`, `/workflows/{id}/cancel`, `/memory`, `/memory/forget` | **no — unused** |

Two more (diagnostics, webhooks) exist and are not for this app.

**BLOCKER before building against 8092/8093:** those ports are listening
but **firewalled off**. Only 8090 and 8091 are open at both layers (host
UFW *and* the IONOS cloud-panel firewall — it's two separate firewalls,
opening one is not enough). The owner has to open each new port in both
places before any client work against it can be tested. Ask before
assuming it's done; a closed port fails as a connection timeout that
looks exactly like a bug in your code.

**Tokens:** every gateway has its own separate bearer token, stored in
`/home/assistant/secrets/wendy_v4_*.env` on the server. They are NOT
interchangeable — using the wrong one returns `{"error":"unauthorized"}`,
which is exactly the bug that shipped in this app earlier today. Ask the
owner for the specific token for whatever port you're integrating; do not
guess and do not reuse `API_TOKEN`.

## Highest-priority bug: the model picker is decorative

`ModelPickerSheet` and the `ModelBadgeChip` in the top bar let a user
pick between models. **On v4, this does nothing.** Verified in the
backend source: `/api/v4/message` reads only `body.get("message")` and
ignores everything else in the payload; the core then hardcodes
`config.MODEL_ROSTER["voice"]` for its model call. The chip showing
"Gemini 2.5 Flash" is not reporting a real setting.

This is a UI that states something untrue, which is worse than a missing
feature. Two acceptable fixes — **ask the owner which one before
building**, since one needs backend work and the other doesn't:
- **Client-only (no backend change):** stop presenting model choice as
  user-controllable. Show the model as a read-only indicator of what
  the backend actually used, or remove the chip.
- **Full fix (needs backend):** add an optional `model` field to
  `/api/v4/message` and thread it through the core. That's a backend
  task in the `Wendy-Prototype` repo, not this one — flag it, don't
  attempt it from here.

## Two confirmed bugs, fix these first

1. **Cramped drawer text (already partially fixed).**
   `ui/drawer/ConversationDrawer.kt`'s `ConversationDrawerItem` had its
   title/preview `Column` with no vertical gap and no explicit line
   height — read as overlapping text at small sizes. A minimal fix
   (`Arrangement.spacedBy(2.dp)` + explicit `lineHeight` on both `Text`s)
   is already applied. **Sweep the rest of the app for the same pattern**
   — any `Column` stacking two `Text`s with tight custom `fontSize`
   overrides and no `lineHeight`/spacing (check `ChatMessageItem.kt`'s
   footer row, `ModelPickerSheet.kt`, `UsageSheet.kt`) and apply the same
   fix wherever it's real, not everywhere reflexively.
2. **The vertical row of icons (back/home/sync/volume) visible over the
   drawer in a user screenshot is NOT part of this app.** Confirmed by
   grep — no matching composable exists anywhere in `app/src/main/java`.
   It's a phone-level overlay (Android's accessibility shortcut menu or a
   screen-recorder's floating toolbar). Do not build anything to
   "remove" it; it isn't yours to remove.

## Animation pass — 2 of 7 already landed, do the rest

Landed (for reference, so the next 5 match their feel — same
`FastOutSlowInEasing`/spring family, don't invent a different curve):
- New/reordered messages in `ChatScreen.kt`'s `LazyColumn` now use
  `Modifier.animateItem(fadeInSpec = tween(220, easing =
  FastOutSlowInEasing), placementSpec = spring(dampingRatio =
  Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))`.
- Streaming message content in `ChatMessageItem.kt` now crossfades +
  slides through `AnimatedContent` on each v4 edit instead of snapping.

Still to do, same priority order:

3. **Voice state text hard-cuts against the orb's smooth animation**
   (`VoiceOverlay.kt` lines ~164-193). The orb (`VoiceGlowingOrb`)
   animates beautifully across IDLE→RECORDING→THINKING→SPEAKING; the
   label + caption `Text`s next to it just swap instantly. Wrap that
   `Column`'s content in `AnimatedContent(targetState = voiceState)`
   with a slide+fade transition on the same easing family as the orb.
4. **Send button state snaps** (`ChatScreen.kt`'s `ChatComposer`,
   the `Box` around line 447). Background color and icon↔spinner swap
   with zero transition. Use `animateColorAsState` for the background,
   `AnimatedContent` for the icon/spinner swap.
5. **Drawer and model-picker sheet ride stock Material3 defaults** — zero
   custom motion code in `ConversationDrawer.kt` or `ModelPickerSheet.kt`.
   Give the drawer's `AnimatedVisibility` in `AppRoot.kt` (currently plain
   `fadeIn()`/`fadeOut()`) a real slide-in-from-left transition
   (`slideInHorizontally` + fade) instead of a flat crossfade — a
   drawer that only fades instead of sliding reads as unfinished. Add
   `Modifier.animateItem()` to the conversation list inside the drawer
   too.
6. **Empty-state entrance** (`ChatScreen.kt`'s `EmptyChatStarter`) —
   title and 3 starter cards appear all at once. Stagger: title fades in
   first, then each `StarterCard` slides up ~80ms apart.
7. **Copy button has no self-feedback** (`ChatMessageItem.kt` line
   ~216) — currently a bare `Toast`. Morph the icon to a checkmark for
   ~800ms on tap instead (or in addition to — your call, but the icon
   feedback is the important part, the Toast alone isn't enough).

## Flagship features — backed by real endpoints, build these

Each of these was checked against the running backend. Build order is
deliberate. **Everything in this section is real; nothing here is
speculative.**

### A. Memory browser — the single highest-value feature

This is what the backend does better than almost any consumer AI app,
and the app currently exposes none of it. `MemorySheet.kt` exists but
isn't wired to real memory.

`GET /api/v4/introspection/memory?q=<query>&limit=<n>` on port 8093
(bearer token from `wendy_v4_introspection.env`). Live-verified response:

```json
{"results": [{
  "id": "fact:ki5w4n5rm1xolmfj6t3k",
  "text": "user: Spirit?\nassistant: Spirit today: 15 minutes...",
  "subject": "conversation", "predicate": "exchange", "object": "user+assistant",
  "source": "transcript", "confidence": 1.0,
  "created_at": "2026-08-28T21:48:13Z", "valid_from": "...",
  "distance": 0.783, "rrf_score": 0.0163,
  "embedding": [ ...1536 floats... ]
}]}
```

Build: a real search field over this, results as cards showing `text`,
`created_at`, and `source`. `rrf_score` is hybrid vector+keyword
relevance (higher = better); `distance` is raw vector distance (lower =
closer) — display neither raw, use them for ordering only.

**Critical: strip `embedding` on parse and never hold it in memory.**
Three results came back as 94 KB, almost entirely embedding floats.
Parse the fields you need and discard the rest, or you will thrash
memory on a long result list.

Forgetting: `POST /api/v4/introspection/memory/forget` with
`{"id": "fact:..."}`. It *closes* the fact (stops it surfacing) rather
than hard-deleting — so word the UI as "Forget" and don't promise
deletion. Confirm before calling; it's user data.

### B. Floating voice capsule

Voice currently takes over the whole screen (`VoiceOverlay.kt`), so you
can't read the thread, copy a code block, or scroll back while talking.
Make the overlay minimizable: swipe down (or a chevron) collapses it to
a compact glowing pill docked over the chat that keeps recording/playing
and keeps the live state, with tap-to-expand restoring the full orb.

Pure client-side — no backend change, no new endpoint. Reuse the
existing `VoiceGlowingOrb` at small scale rather than drawing a new
thing. This is the highest-impact pure-frontend feature on the list.

### C. Live work panel

`GET /api/v4/introspection/workflows` (port 8093) returns work the
assistant is doing right now:

```json
{"workflows": [{"id": "...", "name": "respond", "status": "running",
                "current_step": "respond", "elapsed_seconds": 4.2}]}
```

`POST /api/v4/introspection/workflows/{id}/cancel` cancels one (returns
404 unknown / 400 if it isn't running or suspended — handle both).

Build it as a **list**, not a graph. v4 workflows are linear steps, not
a branching DAG — a node-graph visualization would misrepresent the
architecture. A small sheet showing what's running, for how long, with a
cancel button, is honest and genuinely useful.

### D. Haptics

`LocalHapticFeedback` on send, mic start/stop, model switch, and
long-press actions. Near-zero effort; its complete absence today is one
of the more noticeable "budget app" tells.

### E. Move voice to v4 (only after 8092 is opened)

Voice currently runs through Gen 1 on 8090 — a different brain and
memory from the chat, which now runs on v4. That split means spoken
turns may not share context with typed ones. v4's voice gateway
(`/api/v4/voice/message`, `/api/v4/voice/speak`, port 8092) is the
correct destination. **Read the real request/response shapes from the
backend source before writing any client code** — an earlier bug in
this exact area came from assuming a request shape instead of checking.
Do not start until the port is confirmed open and you have its token.

## Explicitly NOT backed — do not build these

Proposed elsewhere, checked, and not supported by anything real. Building
any of them produces a UI that renders nothing or lies about capability:

- **Artifact / code canvas viewer.** `Block(type="artifact")` exists in
  the response schema, but **nothing in production ever emits a block** —
  every reply ships `blocks: []`. A viewer would render an empty state
  forever. Needs a backend producer first.
- **Per-message memory attribution** ("this reply used 2 memories"). The
  memory *browser* is real (section A); attribution is not. Nothing on
  the reply event says which facts fed it.
- **Multi-model compare / side-by-side / cross-model branching.**
  Requires model selection, which does not exist (see the model-picker
  section above).
- **Full-duplex / continuous streaming audio.** Both voice gateways are
  request/response HTTP. There is no WebSocket anywhere in v4, and the
  reply path is a polled in-memory queue.
- **Git-style conversation branching/merging.** v4's conversation is a
  linear event stream; there is no branch model to visualize.
- **On-device Python/Kotlin execution or a dynamic Compose runtime.**
  Wrong architecture and a real security regression — the backend
  already has a proper sandbox (Landlock + bubblewrap) for running code.
  Execute server-side, render results on-device.
- **UMAP/PCA vector topology graph.** The embeddings are real, but
  projecting them on-device is expensive and it's a demo feature, not a
  daily-use one. If it's ever wanted, the projection must be computed
  server-side.

## Standard app gaps — still worth doing

Ordinary table-stakes work, below the flagship features above in
priority but above pure animation polish:

- **Message actions.** Long-press (or a persistent hover row) on any
  assistant message should offer: regenerate, and on user messages:
  edit-and-resend. Right now `ChatMessageItem.kt` only has a bare copy
  icon. Regenerate needs a new `sendMessage`-style call in `WendyApi.kt`
  (reuse the exact same v4 contract, just re-post the prior user text);
  edit-and-resend needs the composer to accept a pre-filled draft.
- **Real markdown rendering.** `FormattedMessageContent` currently only
  special-cases triple-backtick code fences by string-splitting — no
  bold/italic, no bullet/numbered lists, no links, no tables, no inline
  code. A model-forward chat app without markdown rendering looks broken
  next to any competitor. Pull in a maintained Compose markdown renderer
  (evaluate `compose-markdown` or `richtext` — check current maintenance
  status before committing to one) rather than hand-rolling a parser.
- **Syntax highlighting in code blocks.** Currently plain monospace text
  with no coloring at all. Even a lightweight regex-based highlighter for
  the top 5-6 languages users will actually paste (Kotlin, Python, JS/TS,
  JSON, bash) reads dramatically more premium than none.
- **A real Settings screen.** Doesn't exist as a dedicated surface right
  now — scattered across the drawer's memory/usage sheets. Add one,
  reachable from the drawer, holding at minimum the light/dark/system
  toggle described above. Do **not** put a "default model" preference in
  it unless the model-picker backend gap is fixed first — that would
  add a second control that silently does nothing.
- **Swipe-to-delete on conversations**, not click-to-select-then-tap-
  trash (`ConversationDrawerItem` currently only shows its delete icon
  when already selected — two taps for what should be one swipe).
- **Visible error/offline states.** `WendyApi.kt`'s `WendyEvent.Error`
  already carries a real message from the network layer, but check
  whether `ChatScreen.kt`/`FestoAppState` actually surfaces it anywhere
  a user would see (a retry banner, an inline error bubble) rather than
  silently dropping it — if it's silently dropped today, that's a real
  bug, not just a polish gap.
- **Model switch confirmation.** Picking a model in `ModelPickerSheet`
  has no settle moment — a brief brand-color glow on `ModelBadgeChip`
  when a new model lands would make the choice feel registered rather
  than incidental.
- **App launch moment.** Nothing brands the cold-open right now — a
  ~1.5s materialize of the existing 4-point star mark (already drawn in
  `NovaAvatar.kt`'s `Canvas` block, don't redraw it, reuse it) before
  landing on chat/auth would set tone in the first second a user ever
  sees this product.

## Constraints — real, not stylistic preferences

- **Do not touch `WendyApi.kt`'s tokens, ports, or the v4 poll contract**
  without flagging it first — that integration was just fixed live
  tonight (wrong bearer token bug, now corrected and round-trip verified
  against the real VPS) and any change here needs the same live
  verification, not just a clean compile.
- **Do not add push notifications (FCM/APNs).** Still explicitly
  declined — no Firebase project exists or is wanted. The
  `google-services.json` warning at build time is expected and harmless
  (`missingGoogleServicesStrategy = WARN`); do not "fix" it by creating a
  Firebase project.
- **Use the existing design tokens** (`ui/theme/Color.kt`'s
  `FestoExtendedColors`, `BrandNova` family) for every new surface. Don't
  introduce new hex values or a second accent color without a real reason
  — the brand rust-orange (`#C96F4A`) is the one accent this app has and
  every new feature should read as an extension of it, not a competing
  palette.
- **Do not touch anything under a `server/` or backend-looking
  directory** if one appears in context — this repo is client-only.
- **Compile before every commit**
  (`./gradlew :app:compileDebugKotlin`) — a clean compile is the floor,
  not the finish line; where you can, actually run the app (emulator or
  device) and look at what you built before calling it done.

## If you push changes

Push to `main` directly (this repo's real convention) with a clear,
accurate commit message describing what actually changed — not what you
intended, what the diff actually does. If unsure whether something here
is stale, verify against the real repo state rather than trust this
document blindly; it was rewritten 2026-08-29 and things move fast.

# Brief for Google AI Studio — read this before changing anything

This is `festomobile2` — the real Android client for Wendy (canonical name
`festo-private-mobile`). The backend behind this app is genuinely
sophisticated (real streaming, real memory, real voice, real sandboxing).
The frontend is not yet at that bar. This brief exists to close that gap.
Read it fully before touching code — it corrects a stale prior version of
itself in two places and gives you a full, prioritized punch list.

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
  `/api/audio/speak`) still point at Gen 1**, port 8090, with Gen 1's own
  separate bearer token (`GEN1_API_TOKEN` in `WendyApi.kt`) — v4 doesn't
  have equivalents yet. Two different tokens for two different hosts is
  deliberate, not a bug; don't "simplify" it to one constant.
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

## Next-level feature gaps — this is the real "billion-dollar frontend" work

Animation polish alone won't close the gap the backend has already
opened. These are the missing pieces that top-tier AI chat apps
(ChatGPT, Claude, DeepSeek) have and this app doesn't yet:

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
  reachable from the drawer, holding at minimum: the light/dark/system
  toggle from the "dark and light mode" item above, default model
  preference, and voice playback speed/voice selection if the backend
  supports it.
- **Swipe-to-delete on conversations**, not click-to-select-then-tap-
  trash (`ConversationDrawerItem` currently only shows its delete icon
  when already selected — two taps for what should be one swipe).
- **Haptic feedback** on send, mic tap, model switch, and message
  regenerate — `HapticFeedbackConstants`/Compose's
  `LocalHapticFeedback`. Costs almost nothing, is expected table stakes
  in 2026, and its total absence right now is one of the more visible
  "budget app" tells.
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

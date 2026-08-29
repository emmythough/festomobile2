# Festo Mobile Audit — 2026-08-29

**⚠️ Read this first: the workspace changed under me mid-audit.** At 13:05 and again at 14:09–14:20 local, OneDrive synced new versions of 10+ files (the "diverged work manually reconciled" from your brief, apparently still landing). Everything below references the **current** disk state — I re-read every affected file. The uncommitted diff is `+213/−18` across 5 files: a **working file-attachment feature** (picker → base64 → `/api/chat` attachment field → badge in chat), which was *not* in the brief I was given.

---

## 1. Answers to your four standing questions

**f) Theme schemes** — ✅ Done. Dark/Light `ColorScheme`s are complete for every field the UI consumes; unset fields fall back to Material3 per-mode defaults (correct behavior, not a gap). Extended palette has proper dark variants everywhere it's used. Only hardcoded colors are deliberate (white-on-brandNova icons, drawer scrim, per-theme `voiceOrbBrush`). Markdown rendering is now **Markwon-based** (tables, LaTeX, task lists, linkify) — this replaced the hand-rolled parser from an earlier version. One real bug survived the rewrite, see §2.

**g) Drawer buttons** — ✅ All wired (search, new, select, delete, logout, memory/usage rows). But several act on **fake state**: see §3.

**Sheets** — Model picker and usage sheet are real; memory sheet is theater; picker has a lying empty state. Details in §2–3.

**RealVoiceEngine** — **Dead code.** Zero references outside its own file (`rg "RealVoiceEngine"` → no hits). The app uses `VoiceAudioEngine` (MediaRecorder→AAC→server STT/TTS proxy). RealVoiceEngine is an on-device SpeechRecognizer+TTS engine that duplicates `speak()`/`startListening()` functionality and is never constructed. Delete it or wire it — right now it's ~240 lines of divergence-bait, exactly the kind of thing that caused tonight's two-backend mess.

---

## 2. Bugs (fix these first)

1. **Voice-mode network failures are 100% silent.** `runVoiceReply` (`FestoAppState.kt:626–630`) writes *"Couldn't reach Wendy: …"* into the streaming placeholder on Error — then line 652 **deletes that message** (`messages.removeAll { it.id == assistantMsgId }`) and returns `null`. Caller (`:537–540`) silently drops to IDLE. User sees their transcribed words, a spinner, then nothing. The error text is written and thrown away — dead code plus a silent failure. Fix: on failure, put the error into `voiceLiveTranscript` (the overlay's only message surface) instead of returning silently.

2. **`VoicePipelineTest.testVoiceRecordingTransitionsAndMessageCreation` is red as written.** `startVoiceRecording()` returns early when `audioEngine == null` (`FestoAppState.kt:453`) and when `micPermissionGranted == false` (`:454–459`). The test constructs `FestoAppState(testScope)` with neither → `voiceState` stays `IDLE` → the `assertEquals(RECORDING, …)` at line 31 fails. (Test 2 passes.) The test predates the engine/permission guards. Also: `init {}` fires real OkHttp calls at the server from unit tests — inject the API layer or gate init in tests.

3. **Model picker's empty state lies.** `fetchModels()` returns `emptyList()` on *any* failure (`WendyApi.kt:212–214`), and `ModelPickerSheet.kt:151–163` then shows **"Loading models from server…"** forever on a network error. It's not loading; it's dead. Needs an error + retry state (the retry is a one-line `loadRealModels()` re-call).

4. **Attachment file read happens on the main thread.** `ChatScreen.kt:434` — `openInputStream(uri).readBytes()` inside the picker callback can block the UI thread for seconds on a large file (12MB limit) → ANR risk. Wrap in `withContext(Dispatchers.IO)`. The code comment at `:438–444` honestly notes read-failure is a silent no-op — fine for now, but the ANR is not.

5. **Fence regex bug in `RichMessageRenderer.kt:24`** — `(?:```|\$)` : `\$` is a *literal dollar sign*, not end-of-input. Effect: while streaming, an unclosed code fence containing a `$` (e.g. "costs $5") splits the code block at the dollar sign mid-stream; it re-parses correctly once the closer arrives, so it's a transient visual glitch, but the regex is simply wrong. Correct fix for streaming: treat unclosed fence as code-to-EOF.

6. **`Message.isError` is dead weight.** Only set in the voice path (`FestoAppState.kt:642`) on a message that's immediately deleted (bug #1); text path never sets it; no composable reads it. Either render an error style for it in `ChatMessageItem` (good — that's the natural fix for #1 in text mode) or delete the field.

---

## 3. Fabrication & honesty issues (the 4c class — each is "UI states something the code doesn't do")

1. **Auth is pure theater.** `isAuthenticated = true` by default (`FestoAppState.kt:86`); `submitAuth` is `delay(800)` then success (`:188–195`); the screen is pre-filled with `demo@festo.app` / `festo1234` (`AuthScreen.kt:74–75`) and the "Quick Demo Sign-In" chip auto-submits. Any 6-char password "creates an account." Nothing checks anything. Either delete the auth screen (the API token is the actual auth) or label it "Demo sign-in (no server check)."

2. **Memory sheet shows fabricated data.** Header claims "Cross-Session Memory — N durable facts distilled across threads" (`MemorySheet.kt:115–123`), but: 3 facts are hardcoded fiction (`MockData.kt:73–95`, e.g. "Backend runs Debian 12 on Hetzner"), "distillation" is a keyword-match on *i prefer / my project / remember* (`FestoAppState.kt:426–444`), add/delete are local-only, and nothing ever sends these facts to Wendy — she never sees them. The brief marks the memory browser "not wired"; the current implementation is worse than absent: it invents facts and a count.

3. **Drawer pretends there are multiple threads.** New/delete/rename operate on local-only state, but the backend has exactly one shared conversation (`wendy-main`). Delete doesn't touch server history (relaunch resurrects it via `GET /api/history`); "New Conversation" posts into the same shared Wendy session. `renameConversation` (`FestoAppState.kt:256`) has **no caller** — dead. Options: reduce drawer to one entry + info, or hide delete/rename until a server endpoint exists.

4. **Usage sheet: "TOTAL SPEND / TOTAL TOKENS" are session-only but unlabeled.** Real numbers (server-reported ✓, mock list now empty ✓), but `usageEvents` starts empty every launch and nothing is persisted — it's this-session spend presented as a total. The brief explicitly said to label session totals as a lower bound; unpriced turns (no `usage.cost_usd`) are also silently excluded. One caption line fixes both. Also no empty state (shows "$0.0000 / 0 events" with no explanation).

5. **Voice event `durationMs` is invented** — `words × 180` (`FestoAppState.kt:574`) — displayed as fact in `UsageEventCard`. Chat's duration is real wall-clock; voice's is not. Also "Spoken Audio (Xs)" duration is estimated (`words × 0.22`), while the user's own recording duration is real.

6. **Voice overlay captions misstate reality** (`VoiceOverlay.kt:179–181`): "Uploading **24kHz** audio stream to **OpenRouter**" — actual: 16kHz AAC/M4A (`VoiceAudioEngine.kt:55`) to *your server proxy* on :8090. Also during SPEAKING the waveform bars freeze at their last recording values (no playback-level feed), which reads as a live meter but isn't.

7. **Starter cards reference models that don't exist in the picker** ("Gemini 2.5 Flash … GLM 5.3", `ChatScreen.kt:336`) — the real list is flash/haiku/sonnet/deepseek/luna/ox-alpha.

8. **Cost-tier `$`/`$$`/`$$$` badges are client-invented** (`ModelOption.kt:30–35` hardcodes the mapping) — the API sends no pricing. The picker's "Cost shown after reply" caption is honest; the badge glyphs read as data. Minor, but same family. Also `ModelOption.inputCostPerMtok/outputCostPerMtok/contextDisplay` are dead fields, and their doc comment claims "the picker UI (which still reads them)" — it doesn't; stale comment.

---

## 4. Security (acknowledged in-code, listed for completeness)

- **Plaintext HTTP + hardcoded bearer token** (`WendyApi.kt:84–85`) — deliberate, documented in the header comment and scoped via `network_security_config.xml` to exactly `74.208.155.72`. The tradeoff note is honest. **But**: the token is now in this chat, in the repo, and will be in git history and every APK build — it's effectively public. The documented TLS plan (Caddy + hostname) is the fix; until then treat the token as rotatable-only.
- **Attachments up to 12MB ride the same cleartext channel** — the new feature widens the existing exposure from "chat text" to "whatever file the user picks." Worth mentioning in the same TLS ticket.
- `.env.example` is placeholder-only; no other secrets found in source (grep for key patterns came back clean apart from the known token).

---

## 5. Dead code inventory (all verified zero-reference)

| Item | Location |
|---|---|
| `RealVoiceEngine` (entire class) | `voice/RealVoiceEngine.kt` |
| `MockData.initialConversations` / `initialMessages` | `MockData.kt:4–71` |
| `renameConversation()` | `FestoAppState.kt:256` |
| `ModelOption.inputCostPerMtok/outputCostPerMtok/contextDisplay` | `ModelOption.kt:28–29,56` |
| `Conversation.isArchived` | `Conversation.kt:11` |
| `Message.isError` (no UI consumer) | `Message.kt:27` |
| Dependencies: `retrofit`, `converter-moshi`, `moshi`, `firebase-ai`, `firebase-appcheck`, `room.*` + KSP — no `@Entity`/`Retrofit`/`Firebase` reference in source | `app/build.gradle.kts:99–113,147–148` |

Markwon (7 artifacts) and Vico (3) **are** used — no concern there.

---

## 6. What's genuinely solid

- **All four brief items (4b) verified real in code**: streaming chat with error surfacing (text mode), live shared model state via `GET/POST /api/model`, server-reported token/cost on every message (`ServerUsage`, no client estimation — the "never substitute an estimate" rule is honored everywhere I traced), and the full voice pipeline through the same `/api/chat` brain.
- The new **attachment feature** is complete end-to-end and defensively written (12MB client cap with real error message, empty-file check, chip UI, badge in bubbles, comment honestly flagging its own read-failure gap).
- Error-message body from non-2xx responses is surfaced (`WendyApi.kt:143–155`) — nice touch.
- `network_security_config` correctly limits cleartext to the one IP.
- No `TODO/FIXME/placeholder` markers anywhere; the code's self-documentation is unusually honest (WendyApi.kt header is a model of "why, not what").

## 7. Suggested order of attack

1. Fix voice silent-failure (#2.1) + wire `isError` (#2.6) — one small change each, closes the worst honesty gap.
2. Fix/land the fence regex + test repair (#2.5, #2.2).
3. Memory sheet: either wire to the real v4 memory API when it ships, or strip the fake seeds/count and label "coming soon" — the current mix is the one thing in the app actively *asserting* falsehoods.
4. Auth: decide delete-vs-label.
5. Dead-code purge (§5) in one commit — prevents the next divergence accident.
6. Model picker error state (#2.3), attachment IO dispatch (#2.4), usage caption (#3.4), voice captions (#3.6).
7. TLS ticket covering token + attachments.

**Caveat:** I could not run a Gradle build (no Android SDK in this session); compile-consistency was verified manually across all call sites — every signature in the post-sync files lines up (`ModelBadgeChip(model, onClick)`, `CodeBlockView(code, language)`, `MessageChartBlock(spec)`, etc.), so I expect it to build. Also note the app syncs history only at launch — Telegram messages sent after the app opens won't appear until relaunch (no polling); fine, but worth knowing when testing the "same chat" promise.

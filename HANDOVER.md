# Festo Mobile / Wendy — Production Handover

Written 2026-08-29, overnight session. Everything below is either (a)
verified live against the real running systems, with the exact command
and result shown, or (b) explicitly marked as unverified/pending, never
blurred together. Where I had to make a call without you awake to ask,
it's under **Decisions made on your behalf** with the reasoning — flag
anything you'd have called differently.

---

## 1. The headline fix: Telegram and the app are one chat again

**What broke:** earlier tonight I moved the mobile app's chat onto v4
(a second, newer backend) while Telegram stayed on Gen 1. That silently
split them into two brains with two separate histories. You caught it
("I thought telegram chat and festo mobile chat are synced??"), and
said plainly: *"what is telegram should be what is on the app -- same
chat."*

**What I did:** reverted the app to Gen 1 (port 8090) — the same
backend, same session, same memory as Telegram — and then made the
integration genuinely better than it was before the split, not just
reverted:

- **Model switching is now real and bidirectional.** `GET`/`POST
  /api/model` on Gen 1 reads and writes bridge.py's own
  `model_for()`/`set_model_for()` — the exact function Telegram's
  `/model` command already used. Verified live: a `POST` from the app's
  side landed in `~/assistant_workspace/models.json` — the same file
  Telegram reads — before I moved on. Switching model in the app changes
  what Telegram uses next message, and vice versa.
- **Real per-turn cost**, not an estimate. `POST /api/chat`'s final
  reply now carries the actual model id, real prompt/completion token
  counts, and real cost, read back from opencode's own finished message.
  Verified live: a real turn returned `cost_usd: 0.036751875` for the
  actual model it used — not computed client-side.
- Removed a fake number I caught in passing: the model picker was about
  to show "$0.000/$0.000" per-token pricing that doesn't exist on Gen 1.
  Showing `$0.000` reads as "free," which is worse than not showing a
  number — replaced with "Cost shown after reply."

**Files changed:** `assistant_bridge/mobile_api.py`,
`assistant_bridge/bridge.py` (backend, deployed to the VPS directly —
not in this repo's git history, see §6), and
`app/src/main/java/com/example/data/{WendyApi,ModelOption,FestoAppState}.kt`
+ `ui/models/ModelPickerSheet.kt` (this repo, commit `f8fe7e1`).

---

## 2. The other real gap you flagged: file attachments

You said plainly: *"like I can't attach files on the app."* Checked
first — confirmed zero attachment code existed anywhere, client or
server. Built end-to-end:

- Investigated opencode's real API first rather than guessing. It has a
  genuine multimodal file-part type and I verified it works (sent a
  real image via a `data:` URI, the model correctly described it). But
  **Telegram's own existing, already-working file handling doesn't use
  that path** — it saves the file to a shared `inbox/` folder and tells
  the agent the file's location as plain text, letting Wendy's own file
  tools do the reading. I mirrored that proven pattern instead of
  introducing a second, untested mechanism.
- Backend: `POST /api/chat` takes an optional `attachment
  {filename, data}` (base64), validates it (real base64, non-empty,
  15MB decoded cap), saves it to the same `inbox/` Telegram uses.
  Verified live: a real upload landed on disk with the correct byte
  count and a sanitized filename; a deliberately malformed request
  correctly got a 400.
- Client: real attach button in the composer using Android's modern
  document picker (no new runtime permission needed). 12MB client-side
  cap with an honest error message on rejection, not a silent drop.

Commit `72129d4`.

---

## 3. Mobile app: fixed, working, and shipped

Three APKs sent to you tonight, each superseding the last:
1. First build after the message-rendering overhaul (Markwon/LaTeX/
   tables, assistant bubble removed, model picker wired to v4).
2. After discovering and fixing the Telegram/app split (§1).
3. After adding file attachments (§2) — **this is the one to actually
   use going forward.**

All three compiled with a full clean rebuild
(`./gradlew :app:compileDebugKotlin --rerun-tasks`), not an incremental
build, before being sent.

### A real bug found and fixed along the way
The AI Studio sandbox's rendering work and my backend work diverged
(it edits in an isolated cloud workspace with no git access — see §6),
and the manual merge left 8 compile errors: `FestoAppState.kt`
referenced a `ServerUsage` type and a `sendMessage(message, model)`
overload that didn't exist yet in `WendyApi.kt`. Fixed and
independently verified with a forced full rebuild (commit `76eab89`).

### Known, real, unverified-without-a-device gap
**LaTeX isn't rendering.** You reported it and sent a screenshot. Root
cause, confirmed by reading the actual reply text: the model wrote
plain unicode math (`x = (-b ± √(b²-4ac)) / (2a)`) with **no `$`
delimiters at all** — there's nothing for the Markwon renderer to
catch. Bold text and lists in that same message rendered correctly,
confirming the renderer itself works.

**This is not a quick fix — it's a real architectural conflict I
chose not to paper over.** Telegram's own operating instructions
(`CLAUDE.md` §9) explicitly say *"Plain text only. No markdown"* —
Telegram renders none of it, so `$` delimiters would show up as ugly
literal text there. Since mobile and Telegram now share one prompt
(§1), instructing the model to emit `$...$` for math would fix mobile
and break Telegram. Gen 1 has no per-surface awareness (v4 does, via
its `surface` field) to tell the two apart. **Left unfixed on
purpose — see Decisions section below.**

---

## 4. The Mechanic: real gaps found, fixes in progress

You said: *"the mechanic is not working, it should be able to work
regardless and tell me all systems normal if I ask... like a mechanic
checking the oil."*

**Two real, confirmed gaps, both because I checked, not assumed:**

1. **It has no ears.** `mechanic/reporter.py` can *send* Telegram
   messages; nothing anywhere polls for *incoming* ones. Messaging
   `@FestoMechbot` (confirmed live via `getMe`: real bot, display name
   "Festo mechanics") reaches nothing. It's a one-way alarm, not
   something you can ask a question.
2. **Every one of its 16 probes checks "is the engine bolted in," not
   "does anything come out."** Confirmed by reproducing tonight's real
   outage: while Telegram was silently returning empty replies, the
   Mechanic polled `/health` every 60 seconds and got a clean `200 OK`
   the entire time.

**Root cause of tonight's actual outage** (separate from the gap
above, and now fixed): a genuine race condition in
`assistant_bridge/opencode_http.py`. opencode-serve emits two SSE
events with no ordering guarantee — one classifying a reply part as
"text," one carrying its content — and the code discarded content that
arrived before its classification. For short replies this content
almost always won the race, so real answers were generated and then
silently thrown away. Fixed with a buffer-and-flush pattern, plus a
backstop: if a turn ever produces zero captured text, it now re-reads
the finished message directly from opencode rather than returning
nothing. Verified live before and after (reproduced the empty reply,
then confirmed `'pong'` came through).

**Status as of the end of this session — genuinely incomplete, said
plainly rather than glossed over:**

- Two background engineers were dispatched for the blank-reply/recency
  probes and the Telegram listener. The first attempt on both **hung
  for 25+ minutes with zero file changes and near-frozen CPU** — a real
  stall, not just a slow complex task (confirmed by comparison against
  every other engineer tonight, all of which showed real CPU movement).
  Killed and relaunched both fresh; **check `git log` in
  `Wendy-Prototype`'s `v4/` and `mechanic/` dirs for whether the second
  attempt actually landed** — I could not wait out a third full cycle
  before this handover was written.
- **The full instrument panel (Redis, all 8 v4 services, endpoint
  reachability, OpenRouter credit balance, swap, load average) was
  never dispatched a second time** — deliberately deferred rather than
  risk a third long hang eating the remaining session. Its complete,
  ready-to-run prompt is saved at
  `dc_fullpanel.txt` in this session's scratchpad
  (`C:\Users\emmya\AppData\Local\Temp\claude\...\scratchpad\`, exact
  path will differ next session — ask me to regenerate it if it's
  gone). Nothing needs re-deriving; it can be handed to a fresh
  `deepcode -x -p` call as-is.
- **Whatever the outcome, the Mechanic's actual biggest gap — the
  OpenRouter balance at ~$7.45 of $10 with zero monitoring — is still
  live and unwatched right now.** When it hits zero, every reply stops
  and every existing probe stays green, because none of them check
  spend. This is the single highest-value gauge in the whole full-panel
  prompt if only one more thing gets built.

---

## 5. Full mobile-app audit — DONE, `AUDIT_REPORT.md` has the full findings

A read-only audit engineer checked every screen, button, and call path
against the real live backend. It found 8 real bugs and 8 "the UI
states something the code doesn't do" honesty issues — genuinely good,
specific work (every finding cites file:line). **Six of the bugs are
fixed, verified, and shipped** (commit `1297897`):

1. A real ANR risk **I introduced an hour earlier** shipping file
   attachments — reading the picked file's bytes on the main thread
   with no size check first. Moved to a background dispatcher.
2. Voice failures were **completely silent** — the error was written
   into a message, then that exact message was deleted two lines later,
   and the caller returned null with nothing shown anywhere. Now
   surfaces to the voice overlay's real transcript field.
3. Model picker said "Loading models..." **forever** on a failed fetch,
   with no way to tell slow from broken and no retry. Added both.
4. `VoicePipelineTest` was genuinely red — it tested a state the app's
   own safety guards correctly prevent. Rewritten to test the guard
   itself; ran it for real: 2/2 passing, not assumed.
5. A regex bug in the code-fence parser used a line-boundary anchor as
   its "reached the end of a streaming reply" fallback, which could
   close an unterminated code block early at its own internal line
   breaks. Replaced with the correct true-end-of-input anchor.
6. Two factually wrong captions (claimed 24kHz-to-OpenRouter audio when
   it's really 16kHz to this app's own proxy; a starter card named
   three models — Gemini 2.5 Flash, Sonnet 4.5, GLM 5.3 — that don't
   exist in the real, live model list).

**Not fixed tonight — real judgment calls, not oversights, see §6:**
the auth screen always succeeds with pre-filled demo credentials
(pure theater); the memory sheet shows three hardcoded fake facts as if
they were real, distilled memory; the drawer presents multiple
conversation threads when the backend has exactly one shared session.
`RealVoiceEngine.kt` is confirmed **zero-reference dead code** — the
on-device SpeechRecognizer/TTS engine from the earlier AI Studio
divergence was never wired to anything. Delete it or wire it; leaving
~240 unreferenced lines around is exactly the kind of thing that caused
tonight's two-backend split in the first place.

A meaningful chunk of `AUDIT_REPORT.md` is genuinely reassuring, not
just a bug list: every "does X show a real number or an estimate"
question came back clean — server-reported cost/tokens are honored
everywhere, no client-side estimation slipped back in.

---

## 6. Decisions made on your behalf (you were asleep — flag any you'd reverse)

1. **Reverted mobile to Gen 1 instead of moving Telegram to v4.** Your
   own words picked this ("what is telegram should be what is on the
   app") — not a guess, but noting it since it means tonight's v4 work
   (model tiers, usage reporting, memory-browser groundwork) is now
   sitting unused by any real client. It's not wasted — it's the
   natural next migration once the app is stable — but it needs a
   deliberate decision to activate, not another silent switch.
2. **Did not touch the LaTeX/system-prompt issue.** Explained in §3.
   The two real options: (a) accept plain unicode math on both surfaces
   (current state), or (b) add surface-awareness to Gen 1 (real backend
   work, a few hours) so mobile gets real LaTeX while Telegram keeps
   plain text. I'd lean toward (b) since it's a contained, well-scoped
   change and the pattern already exists in v4 to copy from — but
   that's a recommendation, not a decision I made unilaterally given
   the risk of touching Telegram's live prompt without you awake to
   confirm.
3. **Mirrored Telegram's existing file-handling pattern (path-as-text)
   instead of opencode's multimodal image API**, even though the
   multimodal path is real and verified working. Reasoning: consistency
   with the one proven, already-in-production pattern beats a second,
   parallel, untested one — especially attaching new capability to a
   shared, single-brain backend right after fixing a sync bug caused by
   exactly this kind of silent divergence.
4. **Chose Gen 1's model list (flash/luna/ox-alpha/sonnet/haiku/
   deepseek) as the real, permanent selectable set**, discarding the
   six different, invented model ids the app previously showed (which
   never existed on any real backend). If you want different/more
   models available, that's a `_MODEL_MAP` addition in `bridge.py` on
   the VPS — say which real OpenRouter models and I'll wire them
   properly rather than inventing ids again.
5. **Left both voice pipelines in place** (`VoiceAudioEngine.kt`,
   server-side proxy; `RealVoiceEngine.kt`, on-device SpeechRecognizer/
   TextToSpeech from the AI Studio sandbox) without merging or choosing
   between them. Both exist; I have not verified whether both are
   reachable from the same UI button (a real bug if so) — that question
   is explicitly in the audit engineer's scope (§5).

---

## 7. Real, live-verified facts (so you don't have to re-check)

- `@FestoMechbot` is real, confirmed via Telegram's own `getMe`:
  `{"id":8802613106,"username":"FestoMechbot","first_name":"Festo mechanics"}`.
- OpenRouter balance as of tonight: `total_credits: 10, total_usage: 2.55`
  → **~$7.45 remaining**, unwatched by anything (see §4).
- Ports 8090–8093 are all open at both firewall layers (host UFW *and*
  the IONOS cloud panel — this box has two independent firewalls,
  confirmed the hard way twice tonight) and externally reachable.
- `~/assistant_workspace/models.json` on the VPS is the real, single
  source of truth for model selection, shared by Telegram and mobile.

## 8. If something looks wrong

Every backend file I touched has a timestamped `.bak-<unix-time>` copy
sitting right next to it on the VPS (`assistant_bridge/`) — a plain
`cp` back is always available, no git needed. Every mobile-app commit
tonight is a normal, revertable git commit on `main` with a message
describing exactly what changed and why (`git log --oneline` in this
repo). Nothing was force-pushed; nothing was squashed.

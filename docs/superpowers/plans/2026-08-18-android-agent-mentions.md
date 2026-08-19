# Android Agent Mentions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a mobile-friendly inline `@` picker that supports multiple Codeg agents and serializes selected tokens into structured `codeg://agent/<agent_type>` prompt references.

**Architecture:** Keep the existing Compose text input, but switch it to caret-aware `TextFieldValue` state. Store visible `@label` text separately from range metadata, use a pure Kotlin codec to update ranges and serialize/restore structured agent references, and render a small Compose popup driven by the existing `acpListAgents()` data. The ViewModel remains responsible for loading agents and sending serialized prompt text; no server API changes are needed.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Kotlin serialization, existing Hilt/ViewModel/repository layers, JVM unit tests with the repository's current test stack.

## Global Constraints

- Preserve Android 12+ support (`minSdk 31`) and the existing single `:app` module.
- Reuse `CodegClient.acpListAgents()` and existing `AcpAgentInfo`/agent visual mappings.
- Send agent mentions as `[@label](codeg://agent/<agent_type>)`; ordinary text and attachments keep their existing wire format.
- Do not add a rich-text editor dependency or change the Codeg server protocol.
- Keep the existing English and Simplified Chinese resource coverage.
- Do not commit changes; leave the implementation on the isolated feature branch for review.

## File Map

- Create: `app/src/main/java/app/codeg/android/core/model/AgentMention.kt` — pure draft/token model, range transforms, and wire codec.
- Create: `app/src/main/java/app/codeg/android/feature/sessiondetail/AgentMentionPopup.kt` — touch-friendly filtered agent suggestion UI.
- Modify: `app/src/main/java/app/codeg/android/feature/sessiondetail/ComposeBar.kt` — caret-aware field state, active `@query` detection, popup and touch trigger wiring.
- Modify: `app/src/main/java/app/codeg/android/feature/sessiondetail/SessionDetailScreen.kt` — mention-aware draft state, agent list collection, send/restoration integration.
- Modify: `app/src/main/java/app/codeg/android/feature/sessiondetail/SessionDetailViewModel.kt` — agent-list loading and mention-aware draft restoration/send helpers.
- Modify: `app/src/main/res/values/strings.xml` — English picker and accessibility strings.
- Modify: `app/src/main/res/values-zh-rCN/strings.xml` — Simplified Chinese picker and accessibility strings.
- Create: `app/src/test/java/app/codeg/android/core/model/AgentMentionTest.kt` — codec and range behavior tests.
- Modify: `docs/superpowers/specs/2026-08-18-android-agent-mentions-design.md` only if implementation discoveries require a documented design correction.

### Task 1: Establish the failing mention codec tests

**Files:**
- Create: `app/src/test/java/app/codeg/android/core/model/AgentMentionTest.kt`
- Create: `app/src/main/java/app/codeg/android/core/model/AgentMention.kt`

**Interfaces:**
- `AgentMention` stores `start`, `end`, `label`, and `agentType`.
- `AgentMentionDraft` stores visible `text` and ordered mentions.
- `AgentMentionDraft.insert(range, agent)` replaces the active `@query` range and returns a caret positioned after the inserted visible token.
- `AgentMentionDraft.applyEdit(oldText, newText, selection)` shifts valid spans, drops spans whose text was edited, and returns the updated draft.
- `AgentMentionDraft.deleteMentionAt(selection)` removes a whole token when the caret is immediately after it.
- `AgentMentionCodec.toWire(draft)` serializes visible text plus structured agent links.
- `AgentMentionCodec.fromWire(text)` parses only exact `codeg://agent/<type>` links and preserves malformed/unknown content visibly.

- [ ] **Step 1: Write failing tests for single insertion and wire serialization**

  Assert that selecting Grok over `@gr` produces visible `@Grok` and sends `[@Grok](codeg://agent/grok)`.

- [ ] **Step 2: Run the focused test and verify it fails for the missing model/codec**

  Run: `./gradlew :app:testDebugUnitTest --tests app.codeg.android.core.model.AgentMentionTest`

  Expected: compilation/test failure because `AgentMentionDraft` and the codec do not exist.

- [ ] **Step 3: Add the minimal model and codec implementation**

  Implement immutable data classes and deterministic range transforms. Escape Markdown-significant label characters and preserve unknown references rather than silently dropping them.

- [ ] **Step 4: Run the focused test and verify it passes**

  Run the same focused Gradle test; expected result is PASS for the first cases.

### Task 2: Cover edit, deletion, restore, and multi-mention edge cases

**Files:**
- Modify: `app/src/test/java/app/codeg/android/core/model/AgentMentionTest.kt`
- Modify: `app/src/main/java/app/codeg/android/core/model/AgentMention.kt`

**Interfaces:** Reuse Task 1's `AgentMentionDraft` and codec APIs without adding UI dependencies.

- [ ] **Step 1: Add failing tests for multiple insertion and range shifting**

  Cover two mentions in one prompt, ordinary text inserted before/after a token, editing inside a token, atomic backspace at a token boundary, and deletion of a selected token.

- [ ] **Step 2: Run the focused tests and confirm the new cases fail**

  Run: `./gradlew :app:testDebugUnitTest --tests app.codeg.android.core.model.AgentMentionTest`

- [ ] **Step 3: Implement the smallest range update and restore behavior**

  Keep ranges ordered, clamp invalid edits, and make parsing/serialization round-trip valid references while preserving malformed input.

- [ ] **Step 4: Run the focused tests and confirm all codec cases pass**

  Run the focused Gradle test again; expected result is PASS with no unrelated test changes.

### Task 3: Add the agent suggestion popup

**Files:**
- Create: `app/src/main/java/app/codeg/android/feature/sessiondetail/AgentMentionPopup.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- `AgentMentionPopup(agents, query, onSelect, onDismiss, modifier)` renders filtered `AcpAgentInfo` rows and loading/empty/error states.
- Rows reuse existing `AgentIcon`/agent visual helpers and expose content descriptions.

- [ ] **Step 1: Add resource strings and compile the empty component shell**

  Add picker title, search hint, no-results, unavailable/error, and accessibility strings in both supported locales.

- [ ] **Step 2: Implement the popup with stable filtering and touch targets**

  Filter by case-insensitive display name and wire id; sort using server order; keep the popup dismissible without changing the current draft.

- [ ] **Step 3: Run the existing unit suite to catch resource/model regressions**

  Run: `./gradlew :app:testDebugUnitTest`

### Task 4: Make the composer caret-aware and wire mention selection

**Files:**
- Modify: `app/src/main/java/app/codeg/android/feature/sessiondetail/ComposeBar.kt`
- Modify: `app/src/main/java/app/codeg/android/feature/sessiondetail/SessionDetailScreen.kt`

**Interfaces:**
- `ComposeBar` accepts `TextFieldValue`, an `onValueChange` callback, filtered agents, and mention insert/dismiss callbacks while retaining existing plus, attach, send, and stop behavior.
- Active query detection returns the text range from the nearest `@` at the current caret only when it is a standalone trigger.

- [ ] **Step 1: Write/extend pure tests for active query detection if the helper is extracted**

  Cover `@`, `@gr`, whitespace termination, cursor-in-middle behavior, and email-like text not opening the picker.

- [ ] **Step 2: Run the focused helper tests and verify they fail before implementation**

  Run the relevant `AgentMentionTest` target; expected result is failure for the new query helper.

- [ ] **Step 3: Switch `BasicTextField` to `TextFieldValue` and add popup state**

  Preserve the current IME behavior, multiline sizing, draft edits, and send/stop transitions. Trigger the popup from typed `@` and from the touch `@` action at the current caret.

- [ ] **Step 4: Insert selected agents through the draft model**

  Replace the active query range, update metadata, dismiss the popup, and restore the caret after the visible token. Support repeated selections in one prompt.

- [ ] **Step 5: Run the focused unit suite and assemble debug to catch Compose/API errors**

  Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

### Task 5: Load agents and serialize/restore drafts through the session flow

**Files:**
- Modify: `app/src/main/java/app/codeg/android/feature/sessiondetail/SessionDetailViewModel.kt`
- Modify: `app/src/main/java/app/codeg/android/feature/sessiondetail/SessionDetailScreen.kt`

**Interfaces:**
- The ViewModel exposes the sorted available `AcpAgentInfo` list and a non-blocking load error for the popup.
- `send` receives the visible draft plus mention metadata and calls the existing prompt path with the codec's wire text.
- Failed-send restoration returns a mention-aware draft rather than a plain string.

- [ ] **Step 1: Add failing ViewModel/model tests for serialized multi-agent send input**

  Verify that two visible tokens become two structured links and that a failed send restores both tokens and their metadata.

- [ ] **Step 2: Run the focused tests and confirm they fail**

  Run: `./gradlew :app:testDebugUnitTest --tests app.codeg.android.core.model.AgentMentionTest`

- [ ] **Step 3: Load agent data through the existing repository client**

  Reuse the new-task/session initialization path; avoid a network request on every keystroke and do not block ordinary text sending if the list fails.

- [ ] **Step 4: Pass mention-aware drafts through send and restoration**

  Keep attachments and the empty-text-with-attachments case unchanged. Serialize only at the network boundary.

- [ ] **Step 5: Run the complete JVM test suite**

  Run: `./gradlew :app:testDebugUnitTest`

### Task 6: Validate the end-to-end Android build and review the diff

**Files:**
- All files from Tasks 1–5.

- [ ] **Step 1: Run formatting/static checks available in the repository**

  Use the existing Gradle verification tasks if present; do not introduce a new formatter or dependency.

- [ ] **Step 2: Build the debug APK**

  Run: `./gradlew :app:assembleDebug`

- [ ] **Step 3: Inspect the final diff for scope and protocol correctness**

  Verify no server files, secrets, generated APKs, or unrelated app settings changed. Confirm that all mention wire strings use the selected agent's wire id, not its display label.

- [ ] **Step 4: Run the final test command and record the output**

  Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`


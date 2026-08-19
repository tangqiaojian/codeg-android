# Android Multi-Agent Mentions Design

**Date:** 2026-08-18

**Status:** Approved direction; implementation pending.

## Goal

Add a mobile-friendly inline `@` experience to Codeg for Android so a user can
search and insert multiple available agents into one prompt, while preserving
the Codeg server's structured `codeg://agent/<agent_type>` routing references.

## Context

The Android client currently uses a plain `BasicTextField` string composer. It
already has access to the server's enabled ACP agent list through
`CodegClient.acpListAgents()` and already renders per-agent visuals. The server
composer represents an agent mention as an inline reference serialized as:

```text
[@Grok](codeg://agent/grok)
```

The mobile implementation must not send a display-only `@Grok` string when a
structured reference is intended, and it must not require a new server API.

## User Experience

1. Typing `@` at the caret opens an agent suggestion popup.
2. The popup filters enabled/available agents by display name and wire id.
3. Selecting an agent replaces the active `@query` with a visible inline token
   such as `@Grok`, retaining its agent type as metadata.
4. The user can continue typing and select additional agents in the same
   prompt.
5. Selecting an existing token is not required for the first version; the
   token behaves as an atomic unit for backspace/delete and range edits.
6. A compact `@` action beside the composer is also available as a touch-first
   fallback. It opens the same picker and inserts at the current caret.
7. Sending converts every token to the server wire form while leaving normal
   text, whitespace, and attachments unchanged.
8. If a send fails, the draft restoration path preserves the structured tokens
   rather than restoring only their display labels.

## Proposed Architecture

### Mention model and codec

Create a small pure Kotlin model for a draft with inline agent spans. Each span
stores the display label, agent type, and its current text range. Keep the
display text human-readable (`@Grok`) while a codec converts spans to and from
the server's Markdown-link reference format.

The codec owns:

- inserting a mention at a `TextRange`;
- replacing the active `@query` range;
- shifting or removing spans after ordinary edits;
- treating a mention span as one deletion unit;
- serializing spans to `[@label](codeg://agent/<type>)` on send;
- parsing the same wire form when restoring a failed draft.

The codec must escape labels when serializing and must tolerate unknown or
malformed references by preserving their visible text instead of dropping the
user's draft.

### Composer state

Change the composer contract from a bare `String` value to a
`TextFieldValue`-based state so the feature can observe the caret and selection.
The screen owns the draft text, selection, and mention metadata; the ViewModel
continues to own network sends and failed-send restoration.

The text field remains a native Compose text input. The first implementation
uses visible `@label` text plus range metadata instead of introducing a custom
rich-text editor dependency. A stable mapping function updates ranges after
each edit and keeps the metadata aligned with the user's actual text.

### Suggestion popup

Add a focused Compose popup component that receives:

- the current query;
- filtered `AcpAgentInfo` rows;
- loading/empty states;
- the currently active row;
- select and dismiss callbacks.

The popup must be keyboard/IME-safe but optimized for touch: rows have a native
touch target, show the existing agent icon, display name, and wire id or
description when available. It is scoped to the session composer and does not
change the settings Agent screen.

### Data loading

Load the agent list once when a session composer becomes active, reuse the
existing `acpListAgents()` API, sort by the server's order, and filter to agents
that are enabled/available. A transient failure should not block normal text
entry or sending; the `@` popup shows an actionable empty/error state.

## Files and Responsibilities

- Create `app/src/main/java/app/codeg/android/core/model/AgentMention.kt` for
  mention spans, draft state helpers, and wire serialization/parsing.
- Create
  `app/src/main/java/app/codeg/android/feature/sessiondetail/AgentMentionPopup.kt`
  for the touch-friendly suggestion UI.
- Modify `ComposeBar.kt` to expose caret-aware `TextFieldValue` state, detect an
  active `@query`, show the popup, and expose the touch `@` action.
- Modify `SessionDetailScreen.kt` to own mention-aware draft state, load the
  agent list from the ViewModel, and pass serialized prompt text to send.
- Modify `SessionDetailViewModel.kt` to expose the available agent list and to
  restore mention-aware drafts without losing metadata.
- Add focused JVM tests under
  `app/src/test/java/app/codeg/android/core/model/AgentMentionTest.kt` for
  insertion, range shifting, atomic deletion, serialization, parsing, and
  malformed-reference preservation.
- Add or extend session-detail tests for failed-send draft restoration and
  multi-mention send serialization.
- Add English and Simplified Chinese strings for the picker title, empty/error
  states, and accessibility labels.

## Wire Contract

For a draft that displays:

```text
请让 @Grok 和 @Codex 一起检查
```

the sent text block must be:

```text
请让 [@Grok](codeg://agent/grok) 和 [@Codex](codeg://agent/codex) 一起检查
```

The existing `PromptInputBlock.Text` path remains unchanged. No new endpoint,
WebSocket event, or server migration is required.

## Error and Edge-Case Rules

- Disabled/unavailable agents are not offered for new mentions.
- Existing tokens remain in a restored draft even if the agent is temporarily
  unavailable; they serialize using their stored wire id.
- A query with no results keeps the literal `@query` text and allows continued
  typing.
- Pasting ordinary text does not create mention metadata.
- Pasting a serialized server reference may be parsed into a token only when it
  exactly matches the supported `codeg://agent/<type>` form.
- Backspace at the end of a mention removes the whole token; deleting inside a
  token falls back to normal text editing and drops that token's metadata.
- Sending an empty display text with only attachments remains valid.

## Non-Goals

- Changing the Codeg server protocol or delegation engine.
- Adding new agent configuration screens.
- Supporting file/session/commit/skill `@` references in this Android change.
- Implementing rich Markdown editing or a third-party editor.
- Automatically starting multiple agent sessions independently of the server's
  existing mention/delegation semantics.

## Acceptance Criteria

- Typing `@` on Android opens a filtered agent picker.
- At least two agents can be inserted into one prompt in separate positions.
- The display remains readable while the sent payload contains structured
  `codeg://agent/<type>` references.
- Token deletion, ordinary edits, failed-send restoration, and attachments do
  not corrupt the draft.
- Unit tests cover codec/range behavior and pass.
- `:app:assembleDebug` completes successfully on a machine with the documented
  Android toolchain.


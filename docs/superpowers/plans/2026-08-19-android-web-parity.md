# Android Web-parity Five Fixes Implementation Plan

> Direct workspace implementation; no git commit.

**Goal:** Make the Android client present workspace/folder hierarchy, user vs assistant messages, real changed files, agent-mention task details, and fully localized Automations.

**Architecture:** Reuse existing Compose/Hilt surfaces and server APIs (`list_all_conversations` + `includeChildren`, `list_child_conversations`, `work_task_*`, `git_status`/`git_diff`). No WebView. Pure grouping/parsing stays unit-tested.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx.serialization, JUnit.

## Global Constraints

- Do not create a Git commit.
- Do not introduce WebView or fake static data.
- Keep existing collapse + session click behavior.
- Do not break tool calls, streaming, markdown, images, or interactive cards.
- New buttons must call real APIs or existing state.
- English `values` stay complete; `values-zh-rCN` is the Chinese surface.

## Root causes

1. Session list treats every folder as a same-weight section and ignores `parentId` worktrees plus conversation `parent_id` children.
2. User and assistant turns share a left-aligned timeline; user is only a faint accent card.
3. Todo detail loads changed files only on tap; session detail never loads git/work-task changes.
4. `@` mentions serialize correctly, but `delegate_to_agent` still renders as a generic tool and child sessions/tasks have no navigation.
5. Automations UI/validation/labels are hardcoded English.

## Files

- Modify: `SessionSections.kt`, `SessionListScreen.kt`, `SectionHeader.kt`, `SessionRow.kt`, `SessionListViewModel.kt`
- Modify: `Conversation.kt`, `CodegClient.kt`, `WireRequests.kt`
- Modify: `TimelineNodeBody.kt`, `TimelineNode.kt`, `SessionDetailScreen.kt`, `SessionDetailViewModel.kt`, `MainShell.kt`
- Modify: `ToolCallCard.kt`, `TodoDetailScreen.kt`, `TodoDetailViewModel.kt`
- Create: `DelegationCard.kt` + `DelegationCardTest.kt`
- Modify: Automations screens/logic + `strings.xml` / `values-zh-rCN/strings.xml`

---

### Task 1: Workspace/folder hierarchy + child sessions

### Task 2: User/assistant bubbles and session header role/status

### Task 3: Real changed files + diff in session and task detail

### Task 4: Delegation card → task detail / session / files / timeline

### Task 5: Automations i18n

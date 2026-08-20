package app.codeg.android.feature.sessiondetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.designsystem.component.AgentAvatar
import app.codeg.android.core.designsystem.component.EmptyState
import app.codeg.android.core.designsystem.component.GlassCard
import app.codeg.android.core.designsystem.component.InlineError
import app.codeg.android.core.designsystem.component.LoadingView
import app.codeg.android.core.designsystem.diff.DiffView
import app.codeg.android.core.designsystem.diff.UnifiedDiff
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.AgentMentionDraft
import app.codeg.android.core.model.AgentMentionTarget
import app.codeg.android.feature.sessiondetail.interactive.AskQuestionCard
import app.codeg.android.feature.sessiondetail.interactive.PermissionRequestCard
import app.codeg.android.feature.sessiondetail.interactive.PlanApprovalCard
import app.codeg.android.feature.sessiondetail.rendering.DelegationActions
import app.codeg.android.feature.sessiondetail.rendering.LocalDelegationActions
import app.codeg.android.feature.sessiondetail.timeline.LocalTimelineScroll
import app.codeg.android.feature.sessiondetail.timeline.NodeBody
import app.codeg.android.feature.sessiondetail.timeline.NodeContent
import app.codeg.android.feature.sessiondetail.timeline.TimelineRow
import app.codeg.android.feature.sessiondetail.timeline.TranscriptTimeline
import app.codeg.android.core.model.ConversationStatus
import app.codeg.android.feature.todos.TaskStatusPill
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

/**
 * The session detail screen: the transcript rendered as a vertical **timeline**
 * (one rail, every event a node) over a pinned composer. Sending resolves/opens an
 * ACP connection, streams the reply, and reconciles against the server transcript
 * on completion. Port of the iOS `SessionDetailView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenSession: (Int) -> Unit = {},
    onOpenConversation: (Int) -> Unit = {},
    onOpenTask: (Int) -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = CodegTheme.colors
    var draft by remember { mutableStateOf(AgentMentionDraft("")) }
    var composerValue by remember { mutableStateOf(TextFieldValue("")) }
    var showInsert by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenVisible()
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenHidden()
        }
    }

    // A failed send returns the user's typed text to the composer so it isn't lost — but only
    // when the composer is empty, so it never clobbers a fresh draft typed during the send.
    LaunchedEffect(ui.restoreDraft) {
        ui.restoreDraft?.let { restored ->
            if (draft.text.isBlank()) {
                draft = AgentMentionDraft.fromWire(restored)
                composerValue = TextFieldValue(draft.text, TextRange(draft.text.length))
            }
            viewModel.consumeRestoredDraft()
        }
    }
    LaunchedEffect(ui.editDraft) {
        ui.editDraft?.let { edited ->
            draft = AgentMentionDraft.fromWire(edited)
            composerValue = TextFieldValue(edited, TextRange(edited.length))
            viewModel.consumeEditDraft()
        }
    }

    if (showOptions) AgentOptionsSheet(
        viewModel = viewModel,
        onOpenSession = onOpenSession,
        onDismiss = { showOptions = false },
    )
    if (showFolderPicker) FolderPickerSheet(
        viewModel = viewModel,
        onDismiss = { showFolderPicker = false },
    )
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AttachmentPrep.MAX_COUNT),
    ) { uris -> if (uris.isNotEmpty()) viewModel.addAttachments(uris, context.contentResolver) }

    if (showInsert) {
        ComposeInsertSheet(
            viewModel = viewModel,
            onInsert = { transform ->
                val updated = draft.applyTextChange(transform(draft.text))
                draft = updated
                composerValue = TextFieldValue(updated.text, TextRange(updated.text.length))
            },
            onDismiss = { showInsert = false },
        )
    }

    // Flatten the transcript into timeline nodes. The persisted half is memoized on its
    // own so the ~50ms live stream only re-runs the cheap live append, not a full re-adapt.
    // On reattach the live turn (rebuilt from the snapshot) owns the in-flight reply, so
    // hide the partial copy the server persists into `turns` to avoid a doubled reply.
    val hideReattachDup = ui.liveFromReattach && ui.live != null
    val persistedNodes = remember(ui.turns, ui.pendingUserTurns, ui.agent, hideReattachDup) {
        TranscriptTimeline.buildPersisted(ui.turns, ui.pendingUserTurns, ui.agent, hideReattachDup)
    }
    val nodes = remember(persistedNodes, ui.live, ui.agent) {
        TranscriptTimeline.withLive(persistedNodes, ui.live, ui.agent)
    }
    val hasContent = nodes.isNotEmpty()

    // Native stick-to-bottom: auto-follow only while the bottom anchor is in view.
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1
        }
    }
    var stick by remember { mutableStateOf(true) }
    // Re-evaluate "follow" only when a *user* scroll settles — programmatic scrolls
    // and streamed growth must not flip it (that would race the auto-follow off).
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress) stick = isAtBottom
        }
    }
    // A freshly sent message always re-pins to the bottom.
    LaunchedEffect(ui.pendingUserTurns.lastOrNull()?.id) {
        if (ui.pendingUserTurns.isNotEmpty()) stick = true
    }
    val timelineBottomIndex = remember(
        nodes.size,
        ui.relatedTask,
        ui.taskChangedFiles,
        ui.gitChanges,
        ui.transcriptWindow?.hasOlder,
    ) {
        var index = 0
        if (ui.relatedTask != null || ui.taskChangedFiles.isNotEmpty() || ui.gitChanges.isNotEmpty()) index++
        if (ui.transcriptWindow?.hasOlder == true) index++
        index + nodes.size // spacer sits at this index
    }
    // Follow new / streamed content while pinned.
    LaunchedEffect(timelineBottomIndex, ui.live, stick) {
        if (stick && nodes.isNotEmpty()) listState.scrollToItem(timelineBottomIndex)
    }
    // Prepending older history must not yank the viewport to the live tail.
    LaunchedEffect(ui.olderTurnsPrependEpoch) {
        if (ui.olderTurnsPrependEpoch > 0) stick = false
    }
    val scrollToId: (String) -> Unit = remember(nodes) {
        { id ->
            val idx = nodes.indexOfFirst { it.id == id }
            if (idx >= 0) {
                stick = false // unpin so the stream doesn't immediately yank us back down
                scope.launch { listState.animateScrollToItem(idx) }
            }
        }
    }
    val findHits = remember(ui.turns, findQuery) { TranscriptSearch.findHits(ui.turns, findQuery) }
    LaunchedEffect(findQuery) { findIndex = 0 }
    LaunchedEffect(findOpen, findHits, findIndex) {
        if (findOpen && findHits.isNotEmpty()) {
            val hit = findHits.getOrNull(findIndex.coerceIn(0, findHits.lastIndex)) ?: return@LaunchedEffect
            scrollToId(hit.turnId)
        }
    }

    val title = when {
        ui.isNew -> stringResource(R.string.session_new_task)
        else -> ui.summary?.trimmedTitle ?: stringResource(R.string.session_title_fallback)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val subtitle = sessionHeaderSubtitle(ui)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (!ui.isNew) {
                        IconButton(onClick = { findOpen = !findOpen }) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.session_find),
                                tint = colors.textSecondary,
                            )
                        }
                    }
                    // The agent avatar is the options button (mode / config / folder / branch).
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClickLabel = stringResource(R.string.session_agent_options),
                                role = Role.Button,
                            ) { showOptions = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        AgentAvatar(ui.agent, size = 28.dp)
                    }
                    if (ui.summary != null) SessionActionsMenu(ui, viewModel, onDeleted = onBack)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                ),
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                MessageQueueBar(
                    queue = ui.queuedPrompts,
                    onRemove = viewModel::removeQueuedPrompt,
                    onEdit = viewModel::editQueuedPrompt,
                )
                ui.pendingPermission?.let { p ->
                    PermissionRequestCard(
                        parsed = p.parsed,
                        options = p.options,
                        onRespond = viewModel::respondPermission,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                ui.pendingQuestion?.let { q ->
                    AskQuestionCard(
                        questions = q.questions,
                        onSubmit = viewModel::answerQuestion,
                        onSkip = viewModel::dismissQuestion,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                ui.pendingPlanApproval?.let { p ->
                    PlanApprovalCard(
                        planMarkdown = p.planMarkdown,
                        onAnswer = viewModel::answerPlanApproval,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (ui.attachments.isNotEmpty()) AttachmentStrip(ui.attachments, onRemove = viewModel::removeAttachment)
                ComposeBar(
                    value = composerValue,
                    onValueChange = { next ->
                        val updated = draft.applyTextChange(next.text)
                        draft = updated
                        composerValue = next.copy(text = updated.text)
                    },
                    onSend = {
                        viewModel.send(draft.toWire())
                        draft = AgentMentionDraft("")
                        composerValue = TextFieldValue("")
                    },
                    onVoiceCommit = { spoken, sendNow ->
                        val updated = draft.applyTextChange(spoken)
                        if (sendNow) {
                            viewModel.send(updated.toWire())
                            draft = AgentMentionDraft("")
                            composerValue = TextFieldValue("")
                        } else {
                            draft = updated
                            composerValue = TextFieldValue(updated.text, TextRange(updated.text.length))
                        }
                    },
                    isInFlight = ui.isInFlight,
                    onStop = viewModel::cancel,
                    onPlus = { showInsert = true },
                    onAttach = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    canSendOverride = ui.attachments.isNotEmpty(),
                    mentionAgents = ui.availableMentionAgents,
                    mentionRanges = draft.mentions,
                    onMentionSelected = { query, selected ->
                        val label = selected.name.trim().ifEmpty { selected.agentType.displayName }
                        val updated = draft.insertMention(
                            query.start,
                            query.end,
                            AgentMentionTarget(selected.agentType, label),
                        )
                        draft = updated
                        composerValue = TextFieldValue(
                            text = updated.text,
                            selection = TextRange(query.start + "@${label.removePrefix("@")}".length),
                        )
                    },
                    onMentionDeleted = { cursor ->
                        val mention = draft.mentions.firstOrNull { it.end == cursor }
                        draft.deleteMentionBeforeCursor(cursor)?.let { updated ->
                            draft = updated
                            composerValue = TextFieldValue(updated.text, TextRange(mention?.start ?: cursor))
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SessionStatusStrip(
                sendStatus = ui.sendStatus,
                failures = ui.sessionFailures,
                notice = ui.notice,
                canOpenNewSession = ui.selectedFolderId != null || ui.summary?.folderId != null,
                canOpenSettings = onOpenSettings != null,
                onFailureAction = { action, _ ->
                    viewModel.onSessionFailureAction(
                        action = action,
                        onNewSession = { folderId -> onOpenSession(folderId) },
                        onLogin = { onOpenSettings?.invoke() },
                    )
                },
                onDismissFailures = viewModel::dismissSessionFailures,
                onDismissNotice = viewModel::dismissNotice,
            )
            if (findOpen) {
                TranscriptFindBar(
                    query = findQuery,
                    onQueryChange = { findQuery = it },
                    matchIndex = findIndex,
                    matchCount = findHits.size,
                    onPrevious = {
                        if (findHits.isNotEmpty()) {
                            findIndex = (findIndex - 1 + findHits.size) % findHits.size
                        }
                    },
                    onNext = {
                        if (findHits.isNotEmpty()) {
                            findIndex = (findIndex + 1) % findHits.size
                        }
                    },
                    onClose = {
                        findOpen = false
                        findQuery = ""
                    },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    ui.loading && !hasContent ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingView(message = stringResource(R.string.common_loading))
                        }

                    ui.error != null && !hasContent ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            InlineError(
                                icon = Icons.Rounded.Forum,
                                title = stringResource(R.string.session_load_failed),
                                message = ui.error!!,
                                onRetry = viewModel::load,
                                retryLabel = stringResource(R.string.common_retry),
                            )
                        }

                    !hasContent ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(horizontal = 24.dp),
                            ) {
                                if (ui.isNew) {
                                    WorkspaceDraftCard(
                                        folderName = ui.folderName,
                                        folderPath = ui.folderPath,
                                        onClick = { showFolderPicker = true },
                                    )
                                }
                                EmptyState(
                                    icon = Icons.Rounded.Forum,
                                    title = if (ui.isNew) {
                                        stringResource(R.string.session_new_task)
                                    } else {
                                        stringResource(R.string.session_empty_title)
                                    },
                                    message = if (ui.isNew) {
                                        stringResource(R.string.session_pick_workspace_hint)
                                    } else {
                                        stringResource(R.string.session_empty_message)
                                    },
                                )
                            }
                        }

                    else -> CompositionLocalProvider(
                        LocalTimelineScroll provides scrollToId,
                        LocalDelegationActions provides DelegationActions(
                            onOpenConversation = onOpenConversation,
                            onOpenTask = onOpenTask,
                            taskIdForConversation = { id -> ui.taskIdByConversation[id] },
                        ),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                        ) {
                            if (ui.relatedTask != null || ui.taskChangedFiles.isNotEmpty() || ui.gitChanges.isNotEmpty()) {
                                item(key = "session-changes", contentType = "changes") {
                                    SessionChangesCard(
                                        ui = ui,
                                        onOpenTask = onOpenTask,
                                        onOpenFile = { path, fromTask -> viewModel.loadFileDiff(path, fromTask) },
                                        modifier = Modifier.padding(bottom = 10.dp),
                                    )
                                }
                            }
                            if (ui.transcriptWindow?.hasOlder == true) {
                                item(key = "load-older", contentType = "load-older") {
                                    TextButton(
                                        onClick = viewModel::loadOlderTurns,
                                        enabled = !ui.loadingOlderTurns,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    ) {
                                        Text(
                                            stringResource(
                                                if (ui.loadingOlderTurns) {
                                                    R.string.session_loading_earlier
                                                } else {
                                                    R.string.session_load_earlier
                                                },
                                            ),
                                            color = colors.accent,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                            items(nodes, key = { it.id }, contentType = { it.contentTypeKey }) { node ->
                                TimelineRow(
                                    marker = node.marker,
                                    connectTop = node.connectTop,
                                    connectBottom = node.connectBottom,
                                    startsGroup = node.startsGroup,
                                    rail = node.rail,
                                    modifier = if (node.content is NodeContent.AssistantBlock) Modifier else Modifier.animateItem(),
                                ) {
                                    NodeBody(node)
                                }
                            }
                            item(key = "__timeline_bottom_anchor__") { Spacer(Modifier.height(1.dp)) }
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hasContent && !isAtBottom,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                    ) {
                        JumpToLatestButton {
                            stick = true
                            scope.launch { listState.animateScrollToItem(timelineBottomIndex) }
                        }
                    }
                }
            }
        }
    }

    if (ui.selectedDiffPath != null) {
        val colors = CodegTheme.colors
        ModalBottomSheet(onDismissRequest = viewModel::dismissDiff, containerColor = colors.bgElevated) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    ui.selectedDiffPath ?: "",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
                when {
                    ui.changesLoading && ui.selectedDiff == null -> LoadingView(message = stringResource(R.string.common_loading))
                    else -> {
                        val parsed = ui.selectedDiff?.let { UnifiedDiff.parse(it) }
                        if (parsed != null) {
                            DiffView(parsed)
                        } else {
                            Text(
                                ui.selectedDiff?.ifBlank { stringResource(R.string.todos_diff_empty) }
                                    ?: stringResource(R.string.todos_diff_empty),
                                color = colors.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceDraftCard(
    folderName: String?,
    folderPath: String?,
    onClick: () -> Unit,
) {
    val colors = CodegTheme.colors
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        padding = 14.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.session_pick_workspace),
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
                Text(
                    folderName ?: stringResource(R.string.agentopts_choose_folder),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (folderPath != null) {
                    Text(
                        folderPath,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = colors.textTertiary)
        }
    }
}

@Composable
private fun TranscriptFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchIndex: Int,
    matchCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = CodegTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgElevated.copy(alpha = 0.96f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.session_find_placeholder)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.codeSurface,
                unfocusedContainerColor = colors.codeSurface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = colors.accent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedPlaceholderColor = colors.textTertiary,
                unfocusedPlaceholderColor = colors.textTertiary,
            ),
        )
        Text(
            if (matchCount == 0) "0/0" else "${matchIndex + 1}/$matchCount",
            color = colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        IconButton(onClick = onPrevious, enabled = matchCount > 0) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.session_find_previous))
        }
        IconButton(onClick = onNext, enabled = matchCount > 0) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.session_find_next))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_dismiss))
        }
    }
}

@Composable
private fun conversationStatusText(status: ConversationStatus): String = stringResource(
    when (status) {
        ConversationStatus.IN_PROGRESS -> R.string.session_status_running
        ConversationStatus.PENDING_REVIEW -> R.string.session_status_review
        ConversationStatus.COMPLETED -> R.string.session_status_done
        ConversationStatus.CANCELLED -> R.string.session_status_cancelled
        ConversationStatus.OTHER -> R.string.session_status_other
    },
)

@Composable
private fun sessionHeaderSubtitle(ui: SessionDetailUiState): String? {
    val parts = buildList {
        ui.folderBreadcrumb?.takeIf { it.isNotBlank() }?.let { add(it) }
        add(ui.agent.shortName)
        ui.summary?.status?.let { add(conversationStatusText(it)) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun SessionChangesCard(
    ui: SessionDetailUiState,
    onOpenTask: (Int) -> Unit,
    onOpenFile: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodegTheme.colors
    GlassCard(modifier.fillMaxWidth(), padding = 12.dp) {
        Text(stringResource(R.string.session_changes_title), color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
        ui.relatedTask?.let { task ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(task.title, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TaskStatusPill(task.status)
            }
            val summary = listOfNotNull(
                task.filesChanged?.let { stringResource(R.string.session_changes_file_count, it) },
                task.additions?.takeIf { it > 0 }?.let { "+$it" },
                task.deletions?.takeIf { it > 0 }?.let { "−$it" },
            ).joinToString("  ")
            if (summary.isNotBlank()) {
                Text(summary, color = colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            TextButton(onClick = { onOpenTask(task.id) }) {
                Text(stringResource(R.string.delegation_open_task))
            }
        }
        if (ui.taskChangedFiles.isNotEmpty()) {
            Text(stringResource(R.string.todos_changed_files), color = colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            ui.taskChangedFiles.forEach { file ->
                TextButton(onClick = { onOpenFile(file.file, true) }) {
                    Text("${file.file} (+${file.additions}/−${file.deletions})", fontSize = 12.sp)
                }
            }
        } else if (ui.gitChanges.isNotEmpty()) {
            Text(stringResource(R.string.todos_changed_files), color = colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            ui.gitChanges.take(12).forEach { entry ->
                TextButton(onClick = { onOpenFile(entry.path, false) }) {
                    Text("${entry.change.badge}  ${entry.path}", fontSize = 12.sp)
                }
            }
        }
    }
}

/** A circular "jump to latest" affordance, floated above the composer when scrolled up. */
@Composable
private fun JumpToLatestButton(onClick: () -> Unit) {
    val colors = CodegTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.bgElevated.copy(alpha = 0.96f))
            .border(0.5.dp, colors.surfaceStroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ArrowDownward,
            contentDescription = stringResource(R.string.session_jump_to_latest),
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A horizontal strip of pending image attachments, each removable. */
@Composable
private fun AttachmentStrip(attachments: List<Attachment>, onRemove: (String) -> Unit) {
    val colors = CodegTheme.colors
    LazyRow(
        Modifier.fillMaxWidth().background(colors.bgElevated.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.id }) { att ->
            val bitmap = remember(att.id) {
                runCatching {
                    val bytes = android.util.Base64.decode(att.base64, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
            Box(Modifier.size(60.dp)) {
                if (bitmap != null) {
                    Image(bitmap, contentDescription = stringResource(R.string.sessiondetail_attachment), contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)))
                } else {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(colors.codeSurface))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).clip(RoundedCornerShape(50)).background(colors.bg.copy(alpha = 0.7f)).clickable { onRemove(att.id) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_remove), tint = colors.textPrimary, modifier = Modifier.size(12.dp)) }
            }
        }
    }
}

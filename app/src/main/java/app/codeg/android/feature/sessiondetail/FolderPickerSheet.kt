package app.codeg.android.feature.sessiondetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.codeg.android.R
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.WorkspaceFolders
import app.codeg.android.feature.projects.DirectoryBrowserSheet

/**
 * Searchable workspace picker for a new session: open folders (worktrees nested
 * under their repo) plus a server-filesystem browse action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    viewModel: SessionDetailViewModel,
    onDismiss: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = CodegTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var showBrowser by remember { mutableStateOf(false) }
    val rows = remember(ui.availableFolders, query) {
        WorkspaceFolders.choices(ui.availableFolders, query)
    }

    if (showBrowser) {
        DirectoryBrowserSheet(
            title = stringResource(R.string.projects_open_folder),
            confirmLabel = stringResource(R.string.projects_open_this_folder),
            loadHome = { viewModel.loadHomeDirectory() },
            loadDirs = { viewModel.listWorkspaceDirs(it) },
            onSelect = { path ->
                showBrowser = false
                viewModel.openWorkspacePath(path) { err -> if (err == null) onDismiss() }
            },
            onDismiss = { showBrowser = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElevated,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.agentopts_change_workspace),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.agentopts_search_folders)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
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
            TextButton(
                onClick = { showBrowser = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.agentopts_browse_folder),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider(color = colors.surfaceStroke)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(rows, key = { it.folder.id }) { row ->
                    val selected = row.folder.id == ui.selectedFolderId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectFolder(row.folder)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            if (row.isWorktree) Icons.Rounded.AccountTree else Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = if (selected) colors.accent else colors.textTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                row.title,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                row.subtitle,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

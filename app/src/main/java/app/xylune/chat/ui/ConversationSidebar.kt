package app.xylune.chat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.xylune.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xylune.chat.data.ConversationListItem
import app.xylune.chat.data.ProjectEntity

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationSidebar(
    conversations: List<ConversationListItem>,
    projects: List<ProjectEntity>,
    selectedId: String?,
    selectedProjectId: String?,
    showArchived: Boolean,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onScreen: (Screen) -> Unit,
    onProjectFilter: (String?) -> Unit,
    onShowArchived: (Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onMove: (String, String?) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreateProject: (String, String?) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var actionTarget by remember { mutableStateOf<ConversationListItem?>(null) }
    var renameTarget by remember { mutableStateOf<ConversationListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationListItem?>(null) }
    var movingTarget by remember { mutableStateOf<ConversationListItem?>(null) }
    var projectTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var renameProjectTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleteProjectTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var createProjectForChat by remember { mutableStateOf<String?>(null) }
    var creatingProject by remember { mutableStateOf(false) }
    val haptics = rememberXyluneHaptics()

    val visible = remember(conversations, selectedProjectId) {
        if (selectedProjectId == null) conversations else conversations.filter { it.conversation.projectId == selectedProjectId }
    }

    Surface(modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                XyluneMark(Modifier.size(34.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            }
            FilledTonalButton(onClick = {
                haptics.confirm()
                onNew()
            }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
                Icon(Icons.Outlined.Add, null)
                Text(uiText("New chat"), Modifier.padding(start = 8.dp))
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .combinedClickable(
                        onClick = { haptics.selection(); onScreen(Screen.SEARCH) },
                        onLongClick = { haptics.longPress(); onScreen(Screen.SEARCH) },
                    ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        uiText("Search chats and messages"),
                        Modifier.padding(start = 11.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 8.dp),
            ) {
                item("all-chats") {
                    NavigationDrawerItem(
                        label = { Text(uiText("All chats")) },
                        icon = { Icon(Icons.Outlined.Inbox, null) },
                        selected = !showArchived && selectedProjectId == null,
                        onClick = { haptics.selection(); onShowArchived(false); onProjectFilter(null) },
                    )
                }
                item("archived") {
                    NavigationDrawerItem(
                        label = { Text(uiText("Archived")) },
                        icon = { Icon(Icons.Outlined.Archive, null) },
                        selected = showArchived,
                        onClick = { haptics.selection(); onShowArchived(true); onProjectFilter(null) },
                    )
                }
                item("projects-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(uiText("PROJECTS"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { haptics.tap(); createProjectForChat = null; creatingProject = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Add, uiText("New project"), Modifier.size(18.dp))
                        }
                    }
                }
                items(projects, key = { "project:${it.id}" }) { project ->
                    NavigationDrawerItem(
                        label = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(if (selectedProjectId == project.id) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, tint = Color(project.colorArgb)) },
                        selected = !showArchived && selectedProjectId == project.id,
                        onClick = { haptics.selection(); onShowArchived(false); onProjectFilter(project.id) },
                        modifier = Modifier.combinedClickable(
                            onClick = { haptics.selection(); onShowArchived(false); onProjectFilter(project.id) },
                            onLongClick = { projectTarget = project },
                        ),
                    )
                }
                item("chats-header") {
                    Text(
                        uiText(if (showArchived) "ARCHIVED CHATS" else if (selectedProjectId == null) "RECENT CHATS" else "PROJECT CHATS"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp, 10.dp, 12.dp, 4.dp),
                    )
                }
                items(visible, key = { "chat:${it.conversation.id}" }) { item ->
                    ConversationRow(
                        item = item,
                        selected = item.conversation.id == selectedId,
                        onClick = { haptics.selection(); onSelect(item.conversation.id) },
                        onLongClick = { haptics.longPress(); actionTarget = item },
                    )
                }
                if (visible.isEmpty()) {
                    item("empty-chats") {
                        Text(
                            uiText(if (showArchived) "Archived chats will appear here." else "No chats in this project yet."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 4.dp))
            NavigationDrawerItem(
                label = { Text(uiText("Settings")) },
                icon = { Icon(Icons.Outlined.Settings, null) },
                selected = false,
                onClick = { haptics.selection(); onScreen(Screen.SETTINGS) },
            )
            Text(uiText("On-device history • BYOK"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp, 4.dp, 12.dp, 0.dp))
        }
    }

    actionTarget?.let { item ->
        val conversation = item.conversation
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            SheetHeader(conversation.title, item.projectName ?: "No project")
            SheetAction(Icons.Filled.Edit, "Rename") { renameTarget = item; actionTarget = null }
            SheetAction(if (conversation.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, if (conversation.pinned) "Unpin" else "Pin") {
                onPin(conversation.id, !conversation.pinned); actionTarget = null
            }
            SheetAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move to project") { movingTarget = item; actionTarget = null }
            SheetAction(Icons.Outlined.Share, "Share portable chat") { onShare(conversation.id); actionTarget = null }
            SheetAction(if (conversation.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, if (conversation.archived) "Unarchive" else "Archive") {
                onArchive(conversation.id, !conversation.archived); actionTarget = null
            }
            SheetAction(Icons.Filled.Delete, "Delete permanently", destructive = true) { deleteTarget = item; actionTarget = null }
            Spacer(Modifier.size(20.dp))
        }
    }

    movingTarget?.let { item ->
        ModalBottomSheet(onDismissRequest = { movingTarget = null }) {
            SheetHeader("Move chat", item.conversation.title)
            SheetAction(Icons.Outlined.Inbox, "No project") { onMove(item.conversation.id, null); movingTarget = null }
            projects.forEach { project ->
                SheetAction(Icons.Filled.Folder, project.name, tint = Color(project.colorArgb)) {
                    onMove(item.conversation.id, project.id); movingTarget = null
                }
            }
            SheetAction(Icons.Outlined.Add, "Create new project") {
                createProjectForChat = item.conversation.id; creatingProject = true; movingTarget = null
            }
            Spacer(Modifier.size(20.dp))
        }
    }

    projectTarget?.let { project ->
        ModalBottomSheet(onDismissRequest = { projectTarget = null }) {
            SheetHeader(project.name, "Project")
            SheetAction(Icons.Filled.Edit, "Rename project") { renameProjectTarget = project; projectTarget = null }
            SheetAction(Icons.Filled.Delete, "Delete project", destructive = true) { deleteProjectTarget = project; projectTarget = null }
            Spacer(Modifier.size(20.dp))
        }
    }

    renameTarget?.let { item ->
        NameDialog("Rename chat", item.conversation.title, "Save", onDismiss = { renameTarget = null }) { name ->
            onRename(item.conversation.id, name); renameTarget = null
        }
    }
    if (creatingProject) NameDialog("New project", "", "Create", onDismiss = { creatingProject = false }) { name ->
        onCreateProject(name, createProjectForChat); creatingProject = false; createProjectForChat = null
    }
    renameProjectTarget?.let { project ->
        NameDialog("Rename project", project.name, "Save", onDismiss = { renameProjectTarget = null }) { name ->
            onRenameProject(project.id, name); renameProjectTarget = null
        }
    }
    deleteTarget?.let { item ->
        ConfirmDeleteDialog("Delete “${item.conversation.title}”?", "Its complete message history, attachments, and local code workspace records will be removed from Xylune.", onDismiss = { deleteTarget = null }) {
            onDelete(item.conversation.id); deleteTarget = null
        }
    }
    deleteProjectTarget?.let { project ->
        ConfirmDeleteDialog("Delete project “${project.name}”?", "Chats are kept and moved back to All chats.", onDismiss = { deleteProjectTarget = null }) {
            onDeleteProject(project.id); deleteProjectTarget = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(item: ConversationListItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val conversation = item.conversation
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.pinned) Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (conversation.title == "New conversation") uiText(conversation.title) else conversation.title,
                        modifier = if (conversation.pinned) Modifier.padding(start = 5.dp) else Modifier,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Text(
                    uiText(buildString {
                        item.projectName?.let { append(it).append(" • ") }
                        append(conversation.totalInputTokens + conversation.totalOutputTokens).append(" tokens")
                        if (conversation.totalCostMicros > 0) append(" • $").append("%.4f".format(conversation.totalCostMicros / 1_000_000.0))
                        if (conversation.hasUnknownCost) append(" • partial cost")
                    }),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.padding(start = 8.dp), contentAlignment = Alignment.Center) {
                when {
                    item.isResponding -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    item.unreadCount > 0 && !selected -> Badge { Text(item.unreadCount.coerceAtMost(99).toString()) }
                    item.needsAttention -> Surface(Modifier.size(10.dp), RoundedCornerShape(50), color = MaterialTheme.colorScheme.error) {}
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label)
        Text(uiText(label), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetAction(icon: ImageVector, label: String, destructive: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(uiText(label), color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified) },
        leadingContent = { Icon(icon, null, tint = tint ?: if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onClick),
    )
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    XyluneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(title)) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
        confirmButton = { Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(uiText(confirm)) } },
    )
}

@Composable
private fun ConfirmDeleteDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    XyluneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(title)) },
        text = { Text(uiText(body)) },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(uiText("Delete")) } },
    )
}

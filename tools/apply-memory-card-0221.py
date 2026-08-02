from pathlib import Path

settings = Path("app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt")
source = settings.read_text()
old = r'''      ListItem(
          headlineContent = { Text(memory.content) },
          supportingContent = {
              Text("${memory.category} · ${if (memory.enabled) "Enabled" else "Disabled"}")
          },
          trailingContent = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = {
                      editingId = memory.id
                      editText = memory.content
                      editCategory = memory.category
                  }) {
                      Icon(Icons.Outlined.Edit, "Edit memory")
                  }
                  Switch(
                      checked = memory.enabled,
                      onCheckedChange = { viewModel.setMemoryEnabled(memory.id, it) },
                  )
                  IconButton(onClick = { viewModel.deleteMemory(memory.id) }) {
                      Icon(Icons.Outlined.DeleteOutline, "Delete memory")
                  }
              }
          },
          colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
      )'''
new = r'''      Column(
          modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
          Text(
              text = memory.content,
              style = MaterialTheme.typography.bodyLarge,
          )
          Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
          ) {
              Column(Modifier.weight(1f)) {
                  Text(
                      text = memory.category,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                  )
                  Text(
                      text = if (memory.enabled) "Enabled" else "Disabled",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (memory.enabled) {
                          MaterialTheme.colorScheme.primary
                      } else {
                          MaterialTheme.colorScheme.onSurfaceVariant
                      },
                  )
              }
              IconButton(onClick = {
                  editingId = memory.id
                  editText = memory.content
                  editCategory = memory.category
              }) {
                  Icon(Icons.Outlined.Edit, "Edit memory")
              }
              Switch(
                  checked = memory.enabled,
                  onCheckedChange = { viewModel.setMemoryEnabled(memory.id, it) },
              )
              IconButton(onClick = { viewModel.deleteMemory(memory.id) }) {
                  Icon(Icons.Outlined.DeleteOutline, "Delete memory")
              }
          }
      }'''

matches = source.count(old)
if matches != 1:
    raise SystemExit(f"Expected one memory ListItem block, found {matches}")
settings.write_text(source.replace(old, new))

build = Path("app/build.gradle.kts")
build_source = build.read_text()
old_version = '        versionCode = 164\n        versionName = "0.22.0"'
new_version = '        versionCode = 165\n        versionName = "0.22.1"'
if build_source.count(old_version) != 1:
    raise SystemExit("Could not find the 0.22.0 version block")
build.write_text(build_source.replace(old_version, new_version))

notes = Path("docs/releases/RELEASE_NOTES_0.22.1.md")
notes.write_text(
    """# Arbor 0.22.1

This hotfix repairs the Saved memories card layout on phones.

## Memory UI

- Gives memory text the full card width instead of placing it beside edit, enable, and delete controls.
- Moves metadata and controls into a separate secondary row below the memory text.
- Preserves editing, enable/disable, deletion, search, and bulk-management behavior.

## Included

- Includes the adaptive Home-widget and managed-memory changes from Arbor 0.22.0.
"""
)

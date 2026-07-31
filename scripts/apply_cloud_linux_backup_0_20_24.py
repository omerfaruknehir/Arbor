from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor in {path}, found {count}")
    file.write_text(text.replace(old, new, 1))


def replace_range(path: str, start: str, end: str, replacement: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    if replacement in text:
        return
    start_index = text.find(start)
    end_index = text.find(end, start_index + len(start))
    if start_index < 0 or end_index < 0:
        raise RuntimeError(f"{label}: range anchors not found in {path}")
    file.write_text(text[:start_index] + replacement + text[end_index:])


# Python tar helpers preserve Linux rootfs permissions, symlinks, and hardlinks.
replace_once(
    "app/src/main/java/app/arbor/chat/sandbox/PythonSandbox.kt",
    '''@Serializable
data class RootfsExtractionResult(
    val extracted: Int = 0,
    val skipped: List<String> = emptyList(),
    val elapsedMs: Long = 0,
)
''',
    '''@Serializable
data class RootfsExtractionResult(
    val extracted: Int = 0,
    val skipped: List<String> = emptyList(),
    val elapsedMs: Long = 0,
)

@Serializable
data class PortableArchiveResult(
    val fileCount: Long = 0,
    val sizeBytes: Long = 0,
)
''',
    "portable archive result",
)
replace_once(
    "app/src/main/java/app/arbor/chat/sandbox/PythonSandbox.kt",
    '''    suspend fun extractRootfs(archive: File, destination: File, stripComponents: Int = 0): RootfsExtractionResult = withContext(Dispatchers.IO) { mutex.withLock {
        startPython()
        val raw = Python.getInstance().getModule("sandbox_runner")
            .callAttr("extract_rootfs", archive.absolutePath, destination.absolutePath, stripComponents)
            .toString()
        json.decodeFromString<RootfsExtractionResult>(raw)
    } }

    suspend fun environment(conversationId: String): PythonEnvironmentInfo = withContext(Dispatchers.IO) { mutex.withLock {
''',
    '''    suspend fun extractRootfs(archive: File, destination: File, stripComponents: Int = 0): RootfsExtractionResult = withContext(Dispatchers.IO) { mutex.withLock {
        startPython()
        val raw = Python.getInstance().getModule("sandbox_runner")
            .callAttr("extract_rootfs", archive.absolutePath, destination.absolutePath, stripComponents)
            .toString()
        json.decodeFromString<RootfsExtractionResult>(raw)
    } }

    suspend fun createPortableTar(source: File, destination: File): PortableArchiveResult =
        withContext(Dispatchers.IO) { mutex.withLock {
            require(source.isDirectory) { "Linux environment directory is missing" }
            destination.parentFile?.mkdirs()
            startPython()
            val raw = Python.getInstance().getModule("sandbox_runner")
                .callAttr("create_portable_tar", source.absolutePath, destination.absolutePath)
                .toString()
            json.decodeFromString<PortableArchiveResult>(raw)
        } }

    suspend fun extractPortableTar(archive: File, destination: File): PortableArchiveResult =
        withContext(Dispatchers.IO) { mutex.withLock {
            require(archive.isFile) { "Linux environment archive is missing" }
            destination.mkdirs()
            startPython()
            val raw = Python.getInstance().getModule("sandbox_runner")
                .callAttr("extract_portable_tar", archive.absolutePath, destination.absolutePath)
                .toString()
            json.decodeFromString<PortableArchiveResult>(raw)
        } }

    suspend fun environment(conversationId: String): PythonEnvironmentInfo = withContext(Dispatchers.IO) { mutex.withLock {
''',
    "python portable tar methods",
)

runner = Path("app/src/main/python/sandbox_runner.py")
runner_text = runner.read_text()
if "def create_portable_tar(" not in runner_text:
    runner.write_text(runner_text + r'''


def _portable_archive_stats(root):
    file_count = 0
    size_bytes = 0
    for directory, _, files in os.walk(root, followlinks=False):
        for name in files:
            path = os.path.join(directory, name)
            file_count += 1
            if not os.path.islink(path):
                try:
                    size_bytes += os.path.getsize(path)
                except OSError:
                    pass
    return {"fileCount": file_count, "sizeBytes": size_bytes}


def create_portable_tar(source, destination):
    source = os.path.realpath(source)
    destination = os.path.realpath(destination)
    if not os.path.isdir(source):
        raise ValueError("Linux environment directory is missing")
    os.makedirs(os.path.dirname(destination), exist_ok=True)
    temporary = destination + ".part"
    try:
        os.unlink(temporary)
    except FileNotFoundError:
        pass
    try:
        with tarfile.open(
            temporary,
            "w:gz",
            format=tarfile.PAX_FORMAT,
            dereference=False,
            compresslevel=6,
        ) as archive:
            archive.add(source, arcname=".", recursive=True)
        os.replace(temporary, destination)
    finally:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass
    result = _portable_archive_stats(source)
    result["sizeBytes"] = os.path.getsize(destination)
    return json.dumps(result)


def _safe_portable_tar_member(member, destination):
    name = member.name.replace("\\", "/")
    normalized = os.path.normpath(name)
    if name.startswith("/") or normalized in ("..", ".") or normalized.startswith("../"):
        if normalized == "." and member.isdir():
            return member
        raise ValueError("Linux environment archive contains an unsafe path")
    if member.isdev() or member.isfifo():
        raise ValueError("Linux environment archive contains an unsupported device node")
    if member.issym() or member.islnk():
        link = member.linkname.replace("\\", "/")
        if link.startswith("/"):
            raise ValueError("Linux environment archive contains an absolute link")
        resolved = os.path.normpath(os.path.join(os.path.dirname(normalized), link))
        if resolved == ".." or resolved.startswith("../"):
            raise ValueError("Linux environment archive contains a link outside its root")
    target = os.path.realpath(os.path.join(destination, normalized))
    root = os.path.realpath(destination)
    if target != root and not target.startswith(root + os.sep):
        raise ValueError("Linux environment archive escapes its destination")
    return member


def extract_portable_tar(archive_path, destination):
    archive_path = os.path.realpath(archive_path)
    destination = os.path.realpath(destination)
    if not os.path.isfile(archive_path):
        raise ValueError("Linux environment archive is missing")
    os.makedirs(destination, exist_ok=True)
    with tarfile.open(archive_path, "r:*") as archive:
        archive.extractall(
            destination,
            filter=lambda member, path: _safe_portable_tar_member(member, destination),
        )
    return json.dumps(_portable_archive_stats(destination))
''')

# Wire archive/cloud services into the application container.
replace_once(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    "import app.arbor.chat.transfer.ArborArchiveManager\n",
    '''import app.arbor.chat.transfer.ArborArchiveManager
import app.arbor.chat.transfer.GoogleDriveAppDataClient
import app.arbor.chat.transfer.LinuxEnvironmentArchiveStore
import app.arbor.chat.transfer.ScopedCloudFolderStore
''',
    "application transfer imports",
)
replace_once(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    '''    val attachmentStore = AttachmentStore(application, database.attachmentDao())
    val archiveManager = ArborArchiveManager(application, database)
    val ocrEngine = OcrEngine(application, database.attachmentDao())
    val pythonSandbox = PythonSandbox(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
''',
    '''    val attachmentStore = AttachmentStore(application, database.attachmentDao())
    val ocrEngine = OcrEngine(application, database.attachmentDao())
    val pythonSandbox = PythonSandbox(application)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
''',
    "application transfer services",
)

archive_path = "app/src/main/java/app/arbor/chat/transfer/ArborArchiveManager.kt"
replace_once(
    archive_path,
    '''data class ArchiveOptions(
    val includeAttachments: Boolean = true,
    val includeReasoning: Boolean = false,
    val includeToolData: Boolean = false,
    val includeSystemPrompt: Boolean = false,
    val includeRequestMetadata: Boolean = false,
)
''',
    '''data class ArchiveOptions(
    val includeAttachments: Boolean = true,
    val includeReasoning: Boolean = false,
    val includeToolData: Boolean = false,
    val includeSystemPrompt: Boolean = false,
    val includeRequestMetadata: Boolean = false,
    val includeLinuxEnvironments: Boolean = false,
)
''',
    "archive Linux option",
)
replace_once(
    archive_path,
    '''    val options: ArchiveOptions,
    val conversations: List<PortableConversationBundle>,
)
''',
    '''    val options: ArchiveOptions,
    val conversations: List<PortableConversationBundle>,
    val linuxEnvironments: List<PortableLinuxEnvironment> = emptyList(),
)
''',
    "archive Linux manifest",
)
replace_once(
    archive_path,
    '''    val attachmentCount: Int,
    val encrypted: Boolean,
''',
    '''    val attachmentCount: Int,
    val linuxEnvironmentCount: Int,
    val linuxEnvironmentBytes: Long,
    val encrypted: Boolean,
''',
    "archive Linux preview",
)
replace_once(
    archive_path,
    '''data class IncomingArchiveState(
    val uri: Uri,
    val preview: ArchivePreview? = null,
    val passwordRequired: Boolean = false,
    val importing: Boolean = false,
    val error: String? = null,
)

class ArchivePasswordRequiredException''',
    '''data class IncomingArchiveState(
    val uri: Uri,
    val preview: ArchivePreview? = null,
    val passwordRequired: Boolean = false,
    val importing: Boolean = false,
    val error: String? = null,
)

data class ArchiveImportResult(
    val conversationIds: List<String>,
    val linuxEnvironmentCount: Int,
)

class ArchivePasswordRequiredException''',
    "archive import result",
)
replace_once(
    archive_path,
    '''class ArborArchiveManager(
    private val context: Context,
    private val database: ArborDatabase,
) {''',
    '''class ArborArchiveManager(
    private val context: Context,
    private val database: ArborDatabase,
    private val linuxEnvironments: LinuxEnvironmentArchiveStore,
) {''',
    "archive manager Linux dependency",
)
replace_once(
    archive_path,
    '''        val conversationIds = database.conversationDao().all().map(ConversationEntity::id)
        require(conversationIds.isNotEmpty()) { "There are no chats to back up" }
        val output = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
''',
    '''        val conversationIds = database.conversationDao().all().map(ConversationEntity::id)
        val output = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
''',
    "allow Linux-only backup",
)
replace_once(
    archive_path,
    '''    suspend fun inspect(uri: Uri, password: String = ""): ArchivePreview = withContext(Dispatchers.IO) {
''',
    '''    suspend fun writeBackupToCache(
        options: ArchiveOptions,
        password: String,
    ): File = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "backup-exports").apply { mkdirs() }
        root.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > SHARE_CACHE_MAX_AGE_MS }
            ?.forEach(File::delete)
        val file = File(root, "Arbor-backup-${System.currentTimeMillis()}$ARBOR_BACKUP_EXTENSION")
        try {
            file.outputStream().buffered().use { output ->
                writeArchive(
                    output = output,
                    kind = ArchiveKind.BACKUP,
                    conversationIds = database.conversationDao().all().map(ConversationEntity::id),
                    options = options,
                    password = password,
                )
            }
            file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    suspend fun inspect(uri: Uri, password: String = ""): ArchivePreview = withContext(Dispatchers.IO) {
''',
    "cache backup export",
)
replace_once(
    archive_path,
    '''                attachmentCount = manifest.conversations.sumOf { it.attachments.size },
                encrypted = decoded.header.encrypted,
''',
    '''                attachmentCount = manifest.conversations.sumOf { it.attachments.size },
                linuxEnvironmentCount = manifest.linuxEnvironments.size,
                linuxEnvironmentBytes = manifest.linuxEnvironments.sumOf { it.sizeBytes },
                encrypted = decoded.header.encrypted,
''',
    "Linux preview values",
)
replace_once(
    archive_path,
    '''    suspend fun importArchive(uri: Uri, password: String = ""): List<String> = withContext(Dispatchers.IO) {
        val decoded = decodePayloadToTemp(uri, password)
        try {
            val manifest = readManifest(decoded.file)
            ZipFile(decoded.file).use { zip ->
                manifest.conversations.map { bundle ->
                    importConversation(zip, bundle, preserveArchiveState = manifest.kind == ArchiveKind.BACKUP)
                }
            }
        } finally {
            decoded.file.delete()
        }
    }
''',
    '''    suspend fun importArchive(uri: Uri, password: String = ""): ArchiveImportResult = withContext(Dispatchers.IO) {
        val decoded = decodePayloadToTemp(uri, password)
        try {
            val manifest = readManifest(decoded.file)
            ZipFile(decoded.file).use { zip ->
                val conversationIds = manifest.conversations.map { bundle ->
                    importConversation(zip, bundle, preserveArchiveState = manifest.kind == ArchiveKind.BACKUP)
                }
                val restoredLinux = linuxEnvironments.restore(zip, manifest.linuxEnvironments)
                ArchiveImportResult(conversationIds, restoredLinux)
            }
        } finally {
            decoded.file.delete()
        }
    }
''',
    "Linux archive import",
)
write_archive = '''    private suspend fun writeArchive(
        output: OutputStream,
        kind: ArchiveKind,
        conversationIds: List<String>,
        options: ArchiveOptions,
        password: String,
    ) {
        val bundles = conversationIds.mapNotNull { id -> snapshotConversation(id, options) }
        val preparedLinux = if (kind == ArchiveKind.BACKUP && options.includeLinuxEnvironments) {
            linuxEnvironments.prepareSnapshots()
        } else emptyList()
        try {
            require(bundles.isNotEmpty() || preparedLinux.isNotEmpty()) {
                if (options.includeLinuxEnvironments) "There are no chats or installed Linux environments to back up"
                else "There are no chats to back up"
            }
            val now = System.currentTimeMillis()
            val manifest = ArchiveManifest(
                kind = kind,
                createdAt = now,
                appVersion = BuildConfig.VERSION_NAME,
                title = when {
                    kind == ArchiveKind.CHAT -> bundles.single().conversation.title
                    bundles.isEmpty() -> "Arbor Linux backup"
                    else -> "Arbor backup"
                },
                options = options,
                conversations = bundles,
                linuxEnvironments = preparedLinux.map(PreparedLinuxEnvironment::metadata),
            )
            val encrypted = password.isNotEmpty()
            val salt = if (encrypted) ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes) else null
            val iv = if (encrypted) ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes) else null
            val header = EnvelopeHeader(
                kind = kind,
                encrypted = encrypted,
                createdAt = now,
                saltBase64 = salt?.let(::encodeBase64),
                ivBase64 = iv?.let(::encodeBase64),
            )
            output.write(MAGIC)
            output.write(json.encodeToString(header).toByteArray(Charsets.UTF_8))
            output.write('\\n'.code)
            val payloadOutput: OutputStream = if (encrypted) {
                CipherOutputStream(output, encryptionCipher(password, requireNotNull(salt), requireNotNull(iv), header.iterations))
            } else output
            ZipOutputStream(BufferedOutputStream(payloadOutput)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                if (options.includeAttachments) {
                    bundles.forEach { bundle ->
                        val entities = database.attachmentDao().forConversation(bundle.conversation.id).associateBy(AttachmentEntity::id)
                        bundle.attachments.forEach { portable ->
                            val source = entities[portable.id]?.localPath?.let(::File)?.takeIf(File::isFile) ?: return@forEach
                            zip.putNextEntry(ZipEntry(portable.entryName))
                            source.inputStream().buffered().use { input -> copyWithLimit(input, zip, MAX_ATTACHMENT_BYTES) }
                            zip.closeEntry()
                        }
                    }
                }
                linuxEnvironments.writePrepared(zip, preparedLinux)
            }
        } finally {
            preparedLinux.forEach(PreparedLinuxEnvironment::delete)
        }
    }

'''
replace_range(
    archive_path,
    "    private suspend fun writeArchive(\n",
    "    private suspend fun snapshotConversation(\n",
    write_archive,
    "replace archive writer",
)
replace_once(
    archive_path,
    "        const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024\n",
    "        const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024 * 1024\n",
    "larger portable backup limit",
)

# Expose cloud targets through ChatViewModel and report Linux restore results.
replace_once(
    "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt",
    "import app.arbor.chat.transfer.ArchivePasswordRequiredException\n",
    '''import app.arbor.chat.transfer.ArchivePasswordRequiredException
import app.arbor.chat.transfer.CloudBackupEntry
''',
    "cloud backup viewmodel import",
)
replace_once(
    "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt",
    '''    suspend fun writePortableBackup(uri: Uri, options: ArchiveOptions, password: String) {
        container.archiveManager.writeBackup(uri, options, password)
    }

    fun receivePortableArchive(uri: Uri) {
''',
    '''    suspend fun writePortableBackup(uri: Uri, options: ArchiveOptions, password: String) {
        container.archiveManager.writeBackup(uri, options, password)
    }

    fun connectedCloudFolderUri(): Uri? = container.scopedCloudFolder.connectedUri()

    fun connectedCloudFolderLabel(): String? = container.scopedCloudFolder.connectedLabel()

    fun connectCloudFolder(uri: Uri) = container.scopedCloudFolder.connect(uri)

    fun disconnectCloudFolder() = container.scopedCloudFolder.disconnect()

    suspend fun writeConnectedFolderBackup(options: ArchiveOptions, password: String): Uri {
        val file = container.archiveManager.writeBackupToCache(options, password)
        return try {
            container.scopedCloudFolder.saveBackup(file, file.name)
        } finally {
            file.delete()
        }
    }

    suspend fun listConnectedFolderBackups(): List<CloudBackupEntry> =
        container.scopedCloudFolder.listBackups()

    fun openConnectedFolderBackup(entry: CloudBackupEntry): Uri =
        container.scopedCloudFolder.open(entry)

    suspend fun writeGoogleDriveBackup(
        accessToken: String,
        options: ArchiveOptions,
        password: String,
    ): CloudBackupEntry {
        val file = container.archiveManager.writeBackupToCache(options, password)
        return try {
            container.googleDriveAppData.uploadBackup(accessToken, file, file.name)
        } finally {
            file.delete()
        }
    }

    suspend fun listGoogleDriveBackups(accessToken: String): List<CloudBackupEntry> =
        container.googleDriveAppData.listBackups(accessToken)

    suspend fun downloadGoogleDriveBackup(accessToken: String, entry: CloudBackupEntry): Uri =
        container.googleDriveAppData.downloadBackup(accessToken, entry)

    fun receivePortableArchive(uri: Uri) {
''',
    "cloud backup viewmodel methods",
)
replace_once(
    "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt",
    '''                .onSuccess { conversationIds ->
                    incomingArchive.value = null
                    conversationIds.firstOrNull()?.let(::selectConversation)
                    screen.value = Screen.CHAT
                    notices.tryEmit("Imported ${conversationIds.size} chat${if (conversationIds.size == 1) "" else "s"}")
                }
''',
    '''                .onSuccess { result ->
                    incomingArchive.value = null
                    result.conversationIds.firstOrNull()?.let(::selectConversation)
                    if (result.conversationIds.isNotEmpty()) screen.value = Screen.CHAT
                    val parts = buildList {
                        if (result.conversationIds.isNotEmpty()) {
                            add("${result.conversationIds.size} chat${if (result.conversationIds.size == 1) "" else "s"}")
                        }
                        if (result.linuxEnvironmentCount > 0) {
                            add("${result.linuxEnvironmentCount} Linux environment${if (result.linuxEnvironmentCount == 1) "" else "s"}")
                        }
                    }
                    notices.tryEmit("Imported ${parts.joinToString(" and ")}")
                    if (result.linuxEnvironmentCount > 0) container.ubuntuRuntime.refresh()
                }
''',
    "Linux import notice",
)

# Backup UI: optional Linux rootfs plus direct/scoped cloud targets.
transfer_path = "app/src/main/java/app/arbor/chat/ui/TransferUi.kt"
replace_once(
    transfer_path,
    '''    var includeSystemPrompt by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val passwordsMatch = password == confirmPassword
''',
    '''    var includeSystemPrompt by remember { mutableStateOf(true) }
    var includeLinuxEnvironments by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val passwordsMatch = password == confirmPassword
    val backupOptions = ArchiveOptions(
        includeAttachments = includeAttachments,
        includeReasoning = includePrivateData,
        includeToolData = includePrivateData,
        includeSystemPrompt = includeSystemPrompt,
        includeRequestMetadata = includePrivateData,
        includeLinuxEnvironments = includeLinuxEnvironments,
    )
''',
    "backup UI Linux state",
)
replace_once(
    transfer_path,
    '''                    options = ArchiveOptions(
                        includeAttachments = includeAttachments,
                        includeReasoning = includePrivateData,
                        includeToolData = includePrivateData,
                        includeSystemPrompt = includeSystemPrompt,
                        includeRequestMetadata = includePrivateData,
                    ),
''',
    '''                    options = backupOptions,
''',
    "backup UI options reuse",
)
replace_once(
    transfer_path,
    '''            TransferSwitch("Include custom system prompts", includeSystemPrompt) { includeSystemPrompt = it }
        }
    }

    PasswordSection(
''',
    '''            TransferSwitch("Include custom system prompts", includeSystemPrompt) { includeSystemPrompt = it }
            TransferSwitch("Include installed Linux environments", includeLinuxEnvironments) { includeLinuxEnvironments = it }
            if (includeLinuxEnvironments) {
                Text(
                    "Arbor includes each installed root filesystem, packages, and configuration. Permissions, symbolic links, and hard links are preserved. This can make the backup several gigabytes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    PasswordSection(
''',
    "backup UI Linux switch",
)
replace_once(
    transfer_path,
    '''    Button(
        onClick = {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
''',
    '''    CloudBackupTargets(
        viewModel = viewModel,
        options = backupOptions,
        password = password,
        enabled = !busy && passwordsMatch,
    )

    Button(
        onClick = {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
''',
    "cloud target UI",
)
replace_once(
    transfer_path,
    '''                            Text("${value.conversationCount} chat${if (value.conversationCount == 1) "" else "s"} • ${value.messageCount} messages • ${value.attachmentCount} attachments")
''',
    '''                            Text(
                                buildString {
                                    append("${value.conversationCount} chat${if (value.conversationCount == 1) "" else "s"} • ${value.messageCount} messages • ${value.attachmentCount} attachments")
                                    if (value.linuxEnvironmentCount > 0) {
                                        append(" • ${value.linuxEnvironmentCount} Linux environment${if (value.linuxEnvironmentCount == 1) "" else "s"}")
                                    }
                                },
                            )
''',
    "Linux archive preview summary",
)
replace_once(
    transfer_path,
    '''                    IncludedRow("Request metadata", value.options.includeRequestMetadata)
                    Surface(
''',
    '''                    IncludedRow("Request metadata", value.options.includeRequestMetadata)
                    IncludedRow("Installed Linux environments", value.options.includeLinuxEnvironments)
                    Surface(
''',
    "Linux archive included row",
)

# Version, dependency, Android app backup, and FileProvider paths.
replace_once(
    "app/build.gradle.kts",
    '''        versionCode = 149
        versionName = "0.20.23"
''',
    '''        versionCode = 150
        versionName = "0.20.24"
''',
    "version 0.20.24",
)
replace_once(
    "app/build.gradle.kts",
    '''    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
''',
    '''    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
''',
    "Google authorization dependency",
)
replace_once(
    "app/src/main/AndroidManifest.xml",
    '''        android:allowBackup="false"
''',
    '''        android:allowBackup="true"
''',
    "enable Android app backup",
)
replace_once(
    "app/src/main/AndroidManifest.xml",
    '''        android:fullBackupContent="false"
''',
    '''        android:fullBackupContent="@xml/backup_rules"
''',
    "Android backup rules",
)
Path("app/src/main/res/xml/data_extraction_rules.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <include domain="sharedpref" path="arbor_app_settings.xml" />
        <include domain="sharedpref" path="arbor_ui_session.xml" />
        <include domain="sharedpref" path="arbor_linux_runtime.xml" />
        <exclude domain="sharedpref" path="arbor_secrets.xml" />
        <exclude domain="database" path="arbor.db" />
        <exclude domain="database" path="arbor.db-shm" />
        <exclude domain="database" path="arbor.db-wal" />
        <exclude domain="file" path="attachments/" />
        <exclude domain="file" path="ubuntu/" />
        <exclude domain="file" path="linux-runtimes/" />
        <exclude domain="file" path="workspaces/" />
    </cloud-backup>
    <device-transfer>
        <include domain="sharedpref" path="arbor_app_settings.xml" />
        <include domain="sharedpref" path="arbor_ui_session.xml" />
        <include domain="sharedpref" path="arbor_linux_runtime.xml" />
        <exclude domain="sharedpref" path="arbor_secrets.xml" />
        <exclude domain="database" path="arbor.db" />
        <exclude domain="database" path="arbor.db-shm" />
        <exclude domain="database" path="arbor.db-wal" />
        <exclude domain="file" path="attachments/" />
        <exclude domain="file" path="ubuntu/" />
        <exclude domain="file" path="linux-runtimes/" />
        <exclude domain="file" path="workspaces/" />
    </device-transfer>
</data-extraction-rules>
''')
replace_once(
    "app/src/main/res/xml/file_paths.xml",
    '''    <cache-path name="shares" path="shares/" />
    <cache-path name="camera" path="camera/" />
''',
    '''    <cache-path name="shares" path="shares/" />
    <cache-path name="drive_app_data" path="drive-app-data/" />
    <cache-path name="camera" path="camera/" />
''',
    "Drive download FileProvider path",
)

# Changelog, release notes, and focused regression tests.
changelog = Path("CHANGELOG.md")
changelog_text = changelog.read_text()
entry = '''## 0.20.24 — 2026-07-31

- Let portable backups include installed Ubuntu, Debian, and Alpine environments, preserving rootfs permissions, symbolic links, hard links, packages, and configuration.
- Add a persistent Android document-provider folder target for Google Drive, OneDrive, Dropbox, Nextcloud, USB, and local storage; Arbor receives access only to the folder the user explicitly selects.
- Add direct Google Drive app-data backup using only the non-sensitive `drive.appdata` scope and Drive's hidden Arbor-only `appDataFolder`.
- Enable Android/Google One app backup only for small non-secret preferences. Chats, attachments, encrypted database material, credentials, workspaces, and Linux root filesystems remain excluded.
- Keep passwords optional for every local and cloud portable backup target.

'''
if entry not in changelog_text:
    anchor = "# Changelog\n\n"
    if anchor not in changelog_text:
        raise RuntimeError("Changelog heading is missing")
    changelog.write_text(changelog_text.replace(anchor, anchor + entry, 1))

Path("docs/releases/RELEASE_NOTES_0.20.24.md").write_text('''# Arbor 0.20.24

## Linux environment backup

Portable backups can now include installed Ubuntu, Debian, and Alpine root filesystems. Arbor stores each environment as a permission-preserving nested tar archive and restores it through a staging directory before activation.

## Least-privilege cloud backup

- **App-only cloud folder:** choose one folder through Android's system document picker. Arbor receives persistent access only to that folder. This works with document providers exposed by Google Drive, OneDrive, Dropbox, Nextcloud, USB storage, and local storage.
- **Google Drive app storage:** authorize the non-sensitive `drive.appdata` scope only. Arbor writes backups to Drive's hidden appDataFolder and never requests My Drive access.
- **Android/Google One app backup:** enabled only for small, non-secret preferences. The encrypted database, its device-bound key, credentials, attachments, workspaces, and Linux files remain excluded.

Direct Google Drive app-data backup requires the Drive API and an Android OAuth client configured for Arbor's package name and signing certificate in Google Cloud Console.
''')

Path("app/src/test/java/app/arbor/chat/ui/CloudLinuxBackupFeatureTest.kt").write_text('''package app.arbor.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLinuxBackupFeatureTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun driveAuthorizationRequestsOnlyAppDataScope() {
        val source = source("src/main/java/app/arbor/chat/ui/CloudBackupUi.kt")
        assertTrue(source.contains("Scope(Scopes.DRIVE_APPFOLDER)"))
        assertFalse(source.contains("Scopes.DRIVE_FILE"))
        assertFalse(source.contains("https://www.googleapis.com/auth/drive\""))
    }

    @Test
    fun portableBackupCanIncludeLinuxEnvironments() {
        val archive = source("src/main/java/app/arbor/chat/transfer/ArborArchiveManager.kt")
        val linux = source("src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt")
        assertTrue(archive.contains("includeLinuxEnvironments: Boolean = false"))
        assertTrue(archive.contains("linuxEnvironments.prepareSnapshots()"))
        assertTrue(linux.contains(".restore-"))
        assertTrue(linux.contains("runtime.properties"))
    }

    @Test
    fun AndroidBackupExcludesSecretsAndLargePrivateData() {
        val manifest = source("src/main/AndroidManifest.xml")
        val rules = source("src/main/res/xml/data_extraction_rules.xml")
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
        assertTrue(rules.contains("arbor_secrets.xml"))
        assertTrue(rules.contains("path=\"ubuntu/\""))
        assertTrue(rules.contains("path=\"linux-runtimes/\""))
        assertTrue(rules.contains("path=\"attachments/\""))
    }
}
''')

print("Applied Arbor 0.20.24 Linux and least-privilege cloud backup changes.")

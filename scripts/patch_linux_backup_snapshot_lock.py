from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor in {path}, found {count}")
    file.write_text(text.replace(old, new, 1))


replace_once(
    "app/src/main/java/app/arbor/chat/sandbox/UbuntuRuntime.kt",
    '''    suspend fun refresh(): UbuntuRuntimeStatus = withContext(Dispatchers.IO) {
        inspect().also { _status.value = it }
    }

    suspend fun install(): UbuntuRuntimeStatus''',
    '''    suspend fun refresh(): UbuntuRuntimeStatus = withContext(Dispatchers.IO) {
        inspect().also { _status.value = it }
    }

    suspend fun <T> withFilesystemSnapshot(block: suspend () -> T): T =
        lifecycleMutex.withLock { processMutex.withLock { block() } }

    suspend fun install(): UbuntuRuntimeStatus''',
    "Ubuntu filesystem snapshot lock",
)

replace_once(
    "app/src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt",
    '''import app.arbor.chat.sandbox.PythonSandbox
''',
    '''import app.arbor.chat.sandbox.PythonSandbox
import app.arbor.chat.sandbox.UbuntuRuntime
''',
    "Ubuntu runtime import",
)
replace_once(
    "app/src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt",
    '''class LinuxEnvironmentArchiveStore(
    private val context: Context,
    private val python: PythonSandbox,
) {
    suspend fun prepareSnapshots(): List<PreparedLinuxEnvironment> = withContext(Dispatchers.IO) {
        val selected''',
    '''class LinuxEnvironmentArchiveStore(
    private val context: Context,
    private val python: PythonSandbox,
    private val runtime: UbuntuRuntime,
) {
    suspend fun prepareSnapshots(): List<PreparedLinuxEnvironment> = withContext(Dispatchers.IO) {
        runtime.withFilesystemSnapshot {
        val selected''',
    "Linux snapshot lock entry",
)
replace_once(
    "app/src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt",
    '''            )
        }
    }

    fun writePrepared''',
    '''            )
        }
    } }

    fun writePrepared''',
    "Linux snapshot lock exit",
)
replace_once(
    "app/src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt",
    '''    ): Int = withContext(Dispatchers.IO) {
        var restored = 0''',
    '''    ): Int = withContext(Dispatchers.IO) {
        runtime.withFilesystemSnapshot {
        var restored = 0''',
    "Linux restore lock entry",
)
replace_once(
    "app/src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt",
    '''        restored
    }

    private fun isInstalledEnvironment''',
    '''        restored
    } }

    private fun isInstalledEnvironment''',
    "Linux restore lock exit",
)

replace_once(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    '''    val pythonSandbox = PythonSandbox(application)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
''',
    '''    val pythonSandbox = PythonSandbox(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox, ubuntuRuntime)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
''',
    "Linux runtime service order",
)

replace_once(
    "app/src/test/java/app/arbor/chat/ui/CloudLinuxBackupFeatureTest.kt",
    '''        assertTrue(linux.contains("runtime.properties"))
    }
''',
    '''        val runtime = source("src/main/java/app/arbor/chat/sandbox/UbuntuRuntime.kt")
        assertTrue(linux.contains("runtime.properties"))
        assertTrue(linux.contains("runtime.withFilesystemSnapshot"))
        assertTrue(runtime.contains("withFilesystemSnapshot"))
    }
''',
    "Linux snapshot regression assertions",
)

print("Applied consistent Linux environment snapshot locking.")

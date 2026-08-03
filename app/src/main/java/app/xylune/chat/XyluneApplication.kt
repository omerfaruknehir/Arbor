package app.xylune.chat

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import app.xylune.chat.chat.ChatRepository
import app.xylune.chat.chat.AuxiliaryModelService
import app.xylune.chat.agent.AgentTools
import app.xylune.chat.data.XyluneDatabase
import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.files.AttachmentStore
import app.xylune.chat.files.OcrEngine
import app.xylune.chat.generation.GenerationScheduler
import app.xylune.chat.provider.ProviderRegistry
import app.xylune.chat.provider.ModelDiscoveryService
import app.xylune.chat.provider.ModelRequestPolicy
import app.xylune.chat.provider.HybridTokenCounter
import app.xylune.chat.provider.OpenAiOAuthManager
import app.xylune.chat.sandbox.PythonSandbox
import app.xylune.chat.sandbox.UbuntuRuntime
import app.xylune.chat.sandbox.PackageApprovalService
import app.xylune.chat.sandbox.RunRecordStore
import app.xylune.chat.security.SecureStore
import app.xylune.chat.security.CrashReporter
import app.xylune.chat.settings.AppPreferences
import app.xylune.chat.settings.ComposerDraftStore
import app.xylune.chat.settings.PersistentUiStateStore
import app.xylune.chat.transfer.AppSettingsArchiveStore
import app.xylune.chat.transfer.XyluneArchiveManager
import app.xylune.chat.transfer.GoogleDriveAppDataClient
import app.xylune.chat.transfer.CloudOAuthManager
import app.xylune.chat.transfer.DirectCloudConfigStore
import app.xylune.chat.transfer.DirectCloudBackupCoordinator
import app.xylune.chat.transfer.LinuxEnvironmentArchiveStore
import app.xylune.chat.transfer.ScopedCloudFolderStore
import app.xylune.chat.update.RepositoryUpdateManager
import app.xylune.chat.generated.GeneratedBlockCompiler
import app.xylune.chat.generated.GeneratedBlockRepairCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class XyluneApplication : Application() {
    private var launcherIconProcess = false

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        launcherIconProcess = isLauncherIconProcess()
        if (launcherIconProcess) return
        val crashReporter = CrashReporter(this).also(CrashReporter::install)
        container = AppContainer(this, crashReporter)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Generated/returned assistant images are already known to the agent which created them.
            // Keep old installs visually clean instead of retaining an unnecessary OCR overlay.
            container.database.attachmentDao().clearAssistantImageAnalysis()
            // Builds before 0.11 materialized every tap on New chat. Remove
            // only rows that never acquired a message or attachment.
            container.database.catalogDao().insertProvidersIfMissing(DefaultCatalog.providers)
            container.database.catalogDao().insertModelsIfMissing(DefaultCatalog.models)
            // 0.19.4 could leave existing official image rows absent or classified as chat.
            // Repair only Xylune-owned OpenAI image presets; user-defined models remain untouched.
            container.database.catalogDao().upsertModels(ModelRequestPolicy.officialOpenAiImageModels())
            container.repository.observeProviders().first()
                .filter { it.kind == ProviderKind.OPENAI_OAUTH }
                .forEach { oauthProvider ->
                    if (container.openAiOAuth.signedInAccountId(oauthProvider.id) != null) {
                        container.secureStore.setApiKey(oauthProvider.id, "oauth-session")
                        if (!oauthProvider.registered || oauthProvider.apiKeyRequired) {
                            container.repository.saveProvider(oauthProvider.copy(registered = true, apiKeyRequired = false))
                        }
                    } else if (container.secureStore.apiKey(oauthProvider.id).isNotBlank()) {
                        // Keep the provider entry so it can be reconnected; only discard the stale marker.
                        container.secureStore.setApiKey(oauthProvider.id, "")
                    }
                }
            container.database.automationSettingsDao().upsert(
                container.database.automationSettingsDao().get() ?: app.xylune.chat.data.AutomationSettingsEntity(),
            )
        }
    }


    private fun isLauncherIconProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = Process.myPid()
            getSystemService(Context.ACTIVITY_SERVICE)
                .let { it as? ActivityManager }
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
                .orEmpty()
        }
        return processName.endsWith(":launcher_icon")
    }
}

class AppContainer(val application: Application, val crashReporter: CrashReporter) {
    val appPreferences = AppPreferences(application)
    val composerDrafts = ComposerDraftStore(application)
    val persistentUiState = PersistentUiStateStore(application)
    val secureStore = SecureStore(application)
    val database = XyluneDatabase.create(application, secureStore.databasePassphrase())
    val repository = ChatRepository(database)
    val openAiOAuth = OpenAiOAuthManager(application, secureStore)
    val providers = ProviderRegistry(openAiOAuth)
    val modelDiscovery = ModelDiscoveryService(openAiOAuth)
    val tokenCounter = HybridTokenCounter()
    val auxiliaryModels = AuxiliaryModelService(repository, providers, secureStore)
    val attachmentStore = AttachmentStore(application, database.attachmentDao())
    val ocrEngine = OcrEngine(application, database.attachmentDao())
    val pythonSandbox = PythonSandbox(application)
    val ubuntuRuntime = UbuntuRuntime(application, pythonSandbox)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox, ubuntuRuntime)
    val appSettingsArchives = AppSettingsArchiveStore(application, appPreferences, database)
    val archiveManager = XyluneArchiveManager(application, database, linuxEnvironmentArchives, appSettingsArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
    val cloudOAuth = CloudOAuthManager(application, secureStore)
    val directCloudConfigs = DirectCloudConfigStore(secureStore)
    val directCloud = DirectCloudBackupCoordinator(application, cloudOAuth, directCloudConfigs)
    val repositoryUpdates = RepositoryUpdateManager(application)
    val runRecords = RunRecordStore(pythonSandbox::workspace)
    val generatedBlockCompiler = GeneratedBlockCompiler(application)
    val generatedBlockRepairs = GeneratedBlockRepairCoordinator(
        workspace = pythonSandbox::workspace,
        compileCandidate = generatedBlockCompiler::compile,
        requestRepair = auxiliaryModels::repairGeneratedBlock,
    )
    val packageApprovals = PackageApprovalService(repository, auxiliaryModels)
    val agentTools = AgentTools(pythonSandbox, ubuntuRuntime, repository, generatedBlockCompiler, runRecords)
    val scheduler = GenerationScheduler(application, repository)
}

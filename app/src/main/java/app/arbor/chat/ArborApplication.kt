package app.arbor.chat

import android.app.Application
import app.arbor.chat.chat.ChatRepository
import app.arbor.chat.chat.AuxiliaryModelService
import app.arbor.chat.agent.AgentTools
import app.arbor.chat.data.ArborDatabase
import app.arbor.chat.data.DefaultCatalog
import app.arbor.chat.files.AttachmentStore
import app.arbor.chat.files.OcrEngine
import app.arbor.chat.generation.GenerationScheduler
import app.arbor.chat.provider.ProviderRegistry
import app.arbor.chat.provider.ModelDiscoveryService
import app.arbor.chat.provider.HybridTokenCounter
import app.arbor.chat.provider.OpenAiOAuthManager
import app.arbor.chat.sandbox.PythonSandbox
import app.arbor.chat.sandbox.UbuntuRuntime
import app.arbor.chat.sandbox.PackageApprovalService
import app.arbor.chat.sandbox.RunRecordStore
import app.arbor.chat.security.SecureStore
import app.arbor.chat.security.CrashReporter
import app.arbor.chat.settings.AppPreferences
import app.arbor.chat.generated.GeneratedBlockRepairCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ArborApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val crashReporter = CrashReporter(this).also(CrashReporter::install)
        container = AppContainer(this, crashReporter)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Generated/returned assistant images are already known to the agent which created them.
            // Keep old installs visually clean instead of retaining an unnecessary OCR overlay.
            container.database.attachmentDao().clearAssistantImageAnalysis()
            // Builds before 0.11 materialized every tap on New chat. Remove
            // only rows that never acquired a message or attachment.
            container.database.conversationDao().deleteTrulyEmpty()
            container.database.catalogDao().insertProvidersIfMissing(DefaultCatalog.providers)
            container.database.catalogDao().insertModelsIfMissing(DefaultCatalog.models)
            container.repository.provider(OpenAiOAuthManager.PROVIDER_ID)?.let { oauthProvider ->
                if (container.openAiOAuth.signedInAccountId() != null) {
                    container.secureStore.setApiKey(OpenAiOAuthManager.PROVIDER_ID, "oauth-session")
                    if (!oauthProvider.registered) container.repository.saveProvider(oauthProvider.copy(registered = true, apiKeyRequired = false))
                } else if (container.secureStore.apiKey(OpenAiOAuthManager.PROVIDER_ID).isNotBlank()) {
                    container.secureStore.setApiKey(OpenAiOAuthManager.PROVIDER_ID, "")
                    if (oauthProvider.registered) container.repository.saveProvider(oauthProvider.copy(registered = false))
                }
            }
            container.database.automationSettingsDao().upsert(
                container.database.automationSettingsDao().get() ?: app.arbor.chat.data.AutomationSettingsEntity(),
            )
        }
    }
}

class AppContainer(application: Application, val crashReporter: CrashReporter) {
    val appPreferences = AppPreferences(application)
    val secureStore = SecureStore(application)
    val database = ArborDatabase.create(application, secureStore.databasePassphrase())
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
    val runRecords = RunRecordStore(pythonSandbox::workspace)
    val generatedBlockRepairs = GeneratedBlockRepairCoordinator(pythonSandbox::workspace, auxiliaryModels::repairGeneratedBlock)
    val packageApprovals = PackageApprovalService(repository, auxiliaryModels)
    val agentTools = AgentTools(pythonSandbox, ubuntuRuntime, repository, runRecords)
    val scheduler = GenerationScheduler(application, repository)
}

package app.xylune.chat.sandbox

import app.xylune.chat.chat.AuxiliaryModelService
import app.xylune.chat.chat.ChatRepository
import app.xylune.chat.data.PackageApprovalMode
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
enum class PackageEcosystem { PIP, APT, APK }

@Serializable
enum class PackageAction { ALREADY_INSTALLED, INSTALL, UPDATE, INVALID }

@Serializable
data class PackagePlanItem(
    val request: String,
    val name: String,
    val installedVersion: String? = null,
    val candidateVersion: String? = null,
    val action: PackageAction,
    val detail: String = "",
)

@Serializable
data class PackagePlan(
    val ecosystem: PackageEcosystem,
    val items: List<PackagePlanItem> = emptyList(),
    val downloadSummary: String = "",
    val diskSummary: String = "",
    val rawPreview: String = "",
    val error: String? = null,
) {
    val hasChanges: Boolean get() = items.any { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
    val isValid: Boolean get() = error == null && items.isNotEmpty() && items.none { it.action == PackageAction.INVALID }
}

fun PackagePlan.fingerprint(): String {
    val canonical = buildString {
        append(ecosystem.name).append('\n')
        items.forEach { item ->
            append(item.request).append('\u0000').append(item.name).append('\u0000')
            append(item.installedVersion.orEmpty()).append('\u0000').append(item.candidateVersion.orEmpty()).append('\u0000')
            append(item.action.name).append('\u0000').append(item.detail).append('\n')
        }
        append(downloadSummary).append('\n').append(diskSummary).append('\n').append(error.orEmpty())
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

fun requireApprovedPlan(expected: PackagePlan?, actual: PackagePlan) {
    if (expected != null) require(expected.fingerprint() == actual.fingerprint()) {
        "The package plan changed after approval. Review the updated versions, dependencies, download, and disk impact before installing."
    }
}

enum class PackageApprovalState { REQUIRED, APPROVED, DENIED, NOT_NEEDED }

data class PackageReview(
    val plan: PackagePlan,
    val state: PackageApprovalState,
    val reason: String,
    val decidedBy: String,
)

class PackageApprovalService(
    private val repository: ChatRepository,
    private val auxiliaryModels: AuxiliaryModelService,
) {
    suspend fun review(conversationId: String, plan: PackagePlan): PackageReview {
        if (!plan.isValid) return PackageReview(plan, PackageApprovalState.DENIED, plan.error ?: "Invalid package request", "preflight")
        if (!plan.hasChanges) return PackageReview(plan, PackageApprovalState.NOT_NEEDED, "Every requested package already satisfies the request.", "preflight")
        val settings = repository.automationSettingsNow()
        return when (settings.packageApprovalMode) {
            PackageApprovalMode.ALWAYS_ASK -> PackageReview(plan, PackageApprovalState.REQUIRED, "Your confirmation is required.", "user")
            PackageApprovalMode.AUTO_APPROVE -> PackageReview(plan, PackageApprovalState.APPROVED, "Auto-approve is enabled in Settings.", "policy")
            PackageApprovalMode.TRUSTED_ONLY -> {
                val trusted = trustedNames(
                    if (plan.ecosystem == PackageEcosystem.PIP) settings.trustedPythonPackages else settings.trustedUbuntuPackages,
                )
                val untrusted = plan.items.filter { it.action != PackageAction.ALREADY_INSTALLED }.map { normalize(it.name) }.filterNot(trusted::contains)
                if (untrusted.isEmpty()) PackageReview(plan, PackageApprovalState.APPROVED, "All changed packages are on your trusted list.", "trusted list")
                else PackageReview(plan, PackageApprovalState.REQUIRED, "Not on your trusted list: ${untrusted.joinToString()}", "user")
            }
            PackageApprovalMode.MODEL_REVIEW -> {
                val decision = auxiliaryModels.reviewPackagePlan(conversationId, plan)
                PackageReview(
                    plan,
                    if (decision.first) PackageApprovalState.APPROVED else PackageApprovalState.DENIED,
                    decision.second,
                    "${settings.approvalProviderId}/${settings.approvalModelId}",
                )
            }
        }
    }

    private fun trustedNames(raw: String): Set<String> = raw.lineSequence()
        .flatMap { it.split(',', ' ', '\t').asSequence() }
        .map(::normalize)
        .filter(String::isNotBlank)
        .toSet()

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("[-_.]+"), "-")
}

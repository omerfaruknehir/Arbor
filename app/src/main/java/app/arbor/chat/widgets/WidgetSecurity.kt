package app.arbor.chat.widgets

import java.net.URI

enum class WidgetRiskLevel { LOW, MODERATE, ELEVATED }

data class WidgetCapability(
    val title: String,
    val detail: String,
    val caution: Boolean = false,
)

data class WidgetSecurityReport(
    val risk: WidgetRiskLevel,
    val capabilities: List<WidgetCapability>,
    val benefits: List<String>,
    val cautions: List<String>,
)

object WidgetSecurityAnalyzer {
    fun analyze(definition: ArborWidgetDefinition, allowHomePinning: Boolean): WidgetSecurityReport {
        val source = definition.dataSource
        val host = source?.url?.let { runCatching { URI(it).host }.getOrNull() }
        val home = allowHomePinning && definition.homeEnabled
        val capabilities = buildList {
            add(WidgetCapability("Generated code", "None. Arbor renders a bounded native definition; scripts, WebViews, reflection, shell commands, and downloaded code are unavailable."))
            add(WidgetCapability("Device access", "No contacts, location, camera, microphone, files, clipboard, notifications, or arbitrary Android intents."))
            add(WidgetCapability("Network", if (host == null) "No network access." else "Read-only HTTPS JSON from $host after you explicitly enable it. Your IP address and Arbor user-agent are visible to that server.", host != null))
            add(WidgetCapability("Conversation", "Values enter the chat only after you tap a Submit/Use action; ordinary edits stay inside the widget."))
            add(WidgetCapability("Local state", if (home) "Pinned state is stored in Arbor's private app storage until the widget is removed." else "In-chat state is local and may reset when the message leaves memory."))
            add(WidgetCapability("Background activity", if (home && source != null) "Android may refresh public data approximately every ${source.refreshMinutes} minutes." else "No widget background work.", home && source != null))
            if (home) add(WidgetCapability("Home-screen visibility", "The title and displayed values may be visible to anyone who can see the unlocked launcher.", true))
        }
        val cautions = buildList {
            if (host != null) add("The data-source operator can observe requests and may return inaccurate or changing data.")
            if (home) add("Home-screen content is less private than content kept inside Arbor.")
            if (definition.type in setOf("stock", "converter")) add("Generated financial values are informational and must not be treated as verified financial advice.")
            if (definition.type == "prayer_times" && source == null) add("Prayer times are static values supplied in the answer, not a locally verified calculation.")
            if (isEmpty()) add("No elevated capabilities were detected in this definition.")
        }
        val risk = when {
            home && source != null -> WidgetRiskLevel.ELEVATED
            home || source != null -> WidgetRiskLevel.MODERATE
            else -> WidgetRiskLevel.LOW
        }
        return WidgetSecurityReport(
            risk = risk,
            capabilities = capabilities,
            benefits = listOf(
                "Native Android rendering with a fixed, validated component set.",
                "No arbitrary generated application code or hidden permissions.",
                "Network access is read-only, HTTPS-only, size-limited, redirect-blocked, and private-address-blocked.",
            ),
            cautions = cautions,
        )
    }
}

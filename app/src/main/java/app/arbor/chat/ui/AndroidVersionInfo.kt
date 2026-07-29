package app.arbor.chat.ui

internal fun androidVersionSummary(apiLevel: Int, isMinimum: Boolean = false): String {
    val suffix = if (isMinimum) "+" else ""
    val version = when (apiLevel) {
        26 -> "8.0"
        27 -> "8.1"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
        else -> null
    }
    return if (version == null) {
        "API $apiLevel$suffix"
    } else {
        "Android $version$suffix · API $apiLevel$suffix"
    }
}

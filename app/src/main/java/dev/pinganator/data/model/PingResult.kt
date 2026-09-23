package dev.pinganator.data.model

data class PingResult(
    val targetId: String,
    val label: String,
    val subtitle: String = "",
    val isReachable: Boolean,
    val latencyMs: Long,
    val checkedAt: Long
) {
    fun displayLabel(): String = if (subtitle.isNotEmpty()) "$label ($subtitle)" else label
}

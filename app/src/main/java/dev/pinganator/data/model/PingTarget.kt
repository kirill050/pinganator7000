package dev.pinganator.data.model

/**
 * Describes a single connectivity check target.
 *
 * CheckMode.TCP  – open a TCP socket to [address]:[port] (used for raw DNS-server reachability).
 * CheckMode.HTTP / HTTPS – send an HTTP HEAD to [address] (must be a full URL).
 */
data class PingTarget(
    val id: String,
    val label: String,
    val address: String,
    val checkMode: CheckMode,
    val port: Int = 53,
    /** Shown in parentheses after label, e.g. "77.88.8.8" or "api.telegram.org". */
    val subtitle: String = "",
    val isBuiltIn: Boolean = true
) {
    enum class CheckMode { TCP, HTTPS_HEAD, HTTP_HEAD }

    fun displayLabel(): String = if (subtitle.isNotEmpty()) "$label ($subtitle)" else label
}

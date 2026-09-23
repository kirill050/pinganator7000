package dev.pinganator.ping

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** Abstraction over raw network calls – allows test substitution without mocking statics. */
interface NetworkChecker {
    fun tcpCheck(host: String, port: Int, timeoutMs: Int): Boolean
    fun httpCheck(url: String, timeoutMs: Int): Boolean
}

class DefaultNetworkChecker : NetworkChecker {
    override fun tcpCheck(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (_: Exception) {
        false
    }

    override fun httpCheck(url: String, timeoutMs: Int): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = false
            conn.connect()
            conn.responseCode in 100..599
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}

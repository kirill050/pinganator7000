package dev.pinganator.ping

import dev.pinganator.data.model.PingResult
import dev.pinganator.data.model.PingTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TCP_TIMEOUT_MS = 3_000
private const val HTTP_TIMEOUT_MS = 5_000

class Pinger(private val checker: NetworkChecker = DefaultNetworkChecker()) {

    suspend fun ping(target: PingTarget): PingResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val reachable = when (target.checkMode) {
            PingTarget.CheckMode.TCP ->
                checker.tcpCheck(target.address, target.port, TCP_TIMEOUT_MS)
            PingTarget.CheckMode.HTTPS_HEAD, PingTarget.CheckMode.HTTP_HEAD ->
                checker.httpCheck(target.address, HTTP_TIMEOUT_MS)
        }
        val latency = if (reachable) System.currentTimeMillis() - start else -1L
        PingResult(target.id, target.label, target.subtitle, reachable, latency, System.currentTimeMillis())
    }
}

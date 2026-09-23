package dev.pinganator.ping

import dev.pinganator.data.model.PingTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PingerTest {

    // Controllable fake – no actual network calls
    private val fake = object : NetworkChecker {
        var succeed = true
        override fun tcpCheck(host: String, port: Int, timeoutMs: Int) = succeed
        override fun httpCheck(url: String, timeoutMs: Int) = succeed
    }

    private val pinger = Pinger(fake)

    @Test
    fun `reachable result has non-negative latency`() = runTest {
        fake.succeed = true
        val result = pinger.ping(DefaultTargets.ALL.first())
        assertTrue(result.isReachable)
        assertTrue(result.latencyMs >= 0)
    }

    @Test
    fun `unreachable result has latency of minus one`() = runTest {
        fake.succeed = false
        val result = pinger.ping(DefaultTargets.ALL.first())
        assertFalse(result.isReachable)
        assertEquals(-1L, result.latencyMs)
    }

    @Test
    fun `result carries the correct target id`() = runTest {
        fake.succeed = true
        val target = DefaultTargets.ALL[1]
        assertEquals(target.id, pinger.ping(target).targetId)
    }

    @Test
    fun `result carries the correct label`() = runTest {
        fake.succeed = false
        val target = DefaultTargets.ALL[2]
        assertEquals(target.label, pinger.ping(target).label)
    }

    @Test
    fun `tcp target invokes tcpCheck`() = runTest {
        var tcpCalled = false
        val checker = object : NetworkChecker {
            override fun tcpCheck(host: String, port: Int, timeoutMs: Int): Boolean {
                tcpCalled = true; return true
            }
            override fun httpCheck(url: String, timeoutMs: Int) = false
        }
        Pinger(checker).ping(
            PingTarget("t", "T", "1.1.1.1", PingTarget.CheckMode.TCP, port = 53)
        )
        assertTrue(tcpCalled)
    }

    @Test
    fun `https target invokes httpCheck`() = runTest {
        var httpCalled = false
        val checker = object : NetworkChecker {
            override fun tcpCheck(host: String, port: Int, timeoutMs: Int) = false
            override fun httpCheck(url: String, timeoutMs: Int): Boolean {
                httpCalled = true; return true
            }
        }
        Pinger(checker).ping(
            PingTarget("h", "H", "https://example.com", PingTarget.CheckMode.HTTPS_HEAD)
        )
        assertTrue(httpCalled)
    }

    @Test
    fun `checkedAt is set to a recent timestamp`() = runTest {
        fake.succeed = true
        val before = System.currentTimeMillis()
        val result = pinger.ping(DefaultTargets.ALL.first())
        val after = System.currentTimeMillis()
        assertTrue(result.checkedAt in before..after)
    }
}

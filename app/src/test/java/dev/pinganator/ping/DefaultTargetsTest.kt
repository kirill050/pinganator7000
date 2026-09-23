package dev.pinganator.ping

import dev.pinganator.data.model.PingTarget
import org.junit.Assert.*
import org.junit.Test

class DefaultTargetsTest {

    @Test
    fun `exactly four built-in targets`() {
        assertEquals(4, DefaultTargets.ALL.size)
    }

    @Test
    fun `all ids are unique`() {
        val ids = DefaultTargets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all targets are marked as built-in`() {
        assertTrue(DefaultTargets.ALL.all { it.isBuiltIn })
    }

    @Test
    fun `yandex and google dns use TCP mode on port 53`() {
        val tcpTargets = DefaultTargets.ALL.filter { it.checkMode == PingTarget.CheckMode.TCP }
        assertEquals(2, tcpTargets.size)
        assertTrue(tcpTargets.all { it.port == 53 })
    }

    @Test
    fun `ya_ru and telegram use HTTPS HEAD`() {
        val httpTargets = DefaultTargets.ALL.filter { it.checkMode == PingTarget.CheckMode.HTTPS_HEAD }
        assertEquals(2, httpTargets.size)
    }

    @Test
    fun `https targets have address starting with https`() {
        DefaultTargets.ALL
            .filter { it.checkMode == PingTarget.CheckMode.HTTPS_HEAD }
            .forEach { assertTrue(it.address.startsWith("https://")) }
    }

    @Test
    fun `known target ids are present`() {
        val ids = DefaultTargets.ALL.map { it.id }.toSet()
        assertTrue("yandex_dns" in ids)
        assertTrue("ya_ru" in ids)
        assertTrue("google_dns" in ids)
        assertTrue("telegram" in ids)
    }
}

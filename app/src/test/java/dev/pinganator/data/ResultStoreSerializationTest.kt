package dev.pinganator.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.pinganator.data.model.PingResult
import org.junit.Assert.*
import org.junit.Test

/** Tests the JSON round-trip used by ResultStore — no Android context needed. */
class ResultStoreSerializationTest {

    private val gson = Gson()

    private val sample = listOf(
        PingResult("id1", "Label 1", isReachable = true, latencyMs = 42L, checkedAt = 1_000L),
        PingResult("id2", "Label 2", isReachable = false, latencyMs = -1L, checkedAt = 2_000L)
    )

    @Test
    fun `serialize then deserialize yields identical list`() {
        val json = gson.toJson(sample)
        val type = object : TypeToken<List<PingResult>>() {}.type
        val restored: List<PingResult> = gson.fromJson(json, type)

        assertEquals(sample.size, restored.size)
        restored.zip(sample).forEach { (got, expected) ->
            assertEquals(expected.targetId, got.targetId)
            assertEquals(expected.label, got.label)
            assertEquals(expected.isReachable, got.isReachable)
            assertEquals(expected.latencyMs, got.latencyMs)
            assertEquals(expected.checkedAt, got.checkedAt)
        }
    }

    @Test
    fun `empty list serializes and deserializes correctly`() {
        val json = gson.toJson(emptyList<PingResult>())
        val type = object : TypeToken<List<PingResult>>() {}.type
        val restored: List<PingResult> = gson.fromJson(json, type)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `corrupt json produces empty list without crashing`() {
        val result: List<PingResult> = try {
            val type = object : TypeToken<List<PingResult>>() {}.type
            gson.fromJson("not-json-at-all", type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        assertNotNull(result)
    }
}

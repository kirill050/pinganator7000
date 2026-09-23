package dev.pinganator.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.pinganator.data.model.PingResult

class ResultStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(results: List<PingResult>) {
        prefs.edit()
            .putString(KEY_RESULTS, gson.toJson(results))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun load(): List<PingResult> {
        val json = prefs.getString(KEY_RESULTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PingResult>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLastUpdated(): Long = prefs.getLong(KEY_UPDATED_AT, 0L)

    fun isPaused(): Boolean = prefs.getBoolean(KEY_PAUSED, false)

    fun setPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSED, paused).apply()
    }

    fun getDisabledDefaultIds(): Set<String> =
        prefs.getStringSet(KEY_DISABLED_DEFAULTS, emptySet()) ?: emptySet()

    fun setDisabledDefaultIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_DISABLED_DEFAULTS, ids).apply()
    }

    fun getIntervalMs(): Long = prefs.getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MS)

    fun setIntervalMs(ms: Long) {
        prefs.edit().putLong(KEY_INTERVAL, ms).apply()
    }

    companion object {
        const val PREFS_NAME = "ping_results"
        private const val KEY_RESULTS = "results"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_PAUSED = "is_paused"
        private const val KEY_DISABLED_DEFAULTS = "disabled_default_ids"
        private const val KEY_INTERVAL = "interval_ms"
        const val DEFAULT_INTERVAL_MS = 5 * 60 * 1_000L
    }
}

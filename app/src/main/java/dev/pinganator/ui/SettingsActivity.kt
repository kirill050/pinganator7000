package dev.pinganator.ui

import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.pinganator.R
import dev.pinganator.data.ResultStore
import dev.pinganator.data.db.AppDatabase
import dev.pinganator.data.db.CustomHost
import dev.pinganator.data.model.PingTarget
import dev.pinganator.ping.DefaultTargets
import dev.pinganator.widget.PingWidgetProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var adapter: SettingsAdapter
    private val dao by lazy { AppDatabase.getInstance(this).customHostDao() }
    private val store by lazy { ResultStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        adapter = SettingsAdapter(
            onPauseToggle = { togglePause() },
            onBuiltinToggle = { target -> toggleBuiltin(target) },
            onCustomDelete = { host ->
                lifecycleScope.launch {
                    dao.delete(host)
                    PingWidgetProvider.triggerPing(this@SettingsActivity)
                }
            }
        )

        findViewById<RecyclerView>(R.id.hosts_recycler).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = this@SettingsActivity.adapter
        }

        lifecycleScope.launch {
            dao.observeAll().collectLatest { customs -> rebuildList(customs) }
        }

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val customs = dao.getAll()
            rebuildList(customs)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_ping_interval -> { showIntervalDialog(); true }
        R.id.action_restore_defaults -> {
            store.setDisabledDefaultIds(emptySet())
            lifecycleScope.launch {
                PingWidgetProvider.triggerPing(this@SettingsActivity)
                rebuildList(dao.getAll())
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showIntervalDialog() {
        val options = listOf(
            "15 сек"   to 15_000L,
            "30 сек"   to 30_000L,
            "1 мин"    to 60_000L,
            "2 мин"    to 2 * 60_000L,
            "5 мин"    to 5 * 60_000L,
            "15 мин"   to 15 * 60_000L,
            "30 мин"   to 30 * 60_000L,
            "1 час"    to 60 * 60_000L,
            "6 часов"  to 6 * 60 * 60_000L,
            "Выключено" to 0L
        )
        val current = store.getIntervalMs()
        val checked = options.indexOfFirst { it.second == current }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.ping_interval)
            .setSingleChoiceItems(
                options.map { it.first }.toTypedArray(), checked
            ) { dialog, which ->
                val ms = options[which].second
                store.setIntervalMs(ms)
                PingWidgetProvider.cancelSchedule(this)
                if (!store.isPaused()) {
                    PingWidgetProvider.schedulePings(this)
                    if (ms > 0L) PingWidgetProvider.triggerPing(this)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun togglePause() {
        val nowPaused = !store.isPaused()
        store.setPaused(nowPaused)
        if (nowPaused) {
            PingWidgetProvider.cancelSchedule(this)
        } else {
            PingWidgetProvider.schedulePings(this)
            PingWidgetProvider.triggerPing(this)
        }
        PingWidgetProvider.refreshAllWidgets(this)
        lifecycleScope.launch { rebuildList(dao.getAll()) }
    }

    private fun toggleBuiltin(target: PingTarget) {
        val disabled = store.getDisabledDefaultIds().toMutableSet()
        if (target.id in disabled) disabled.remove(target.id) else disabled.add(target.id)
        store.setDisabledDefaultIds(disabled)
        PingWidgetProvider.triggerPing(this)
        lifecycleScope.launch { rebuildList(dao.getAll()) }
    }

    private fun rebuildList(customs: List<CustomHost>) {
        val disabled = store.getDisabledDefaultIds()
        val isPaused = store.isPaused()
        val items = mutableListOf<SettingsItem>()
        items += SettingsItem.PauseToggle(isPaused)
        items += SettingsItem.SectionHeader(getString(R.string.section_builtin))
        DefaultTargets.ALL.forEach { items += SettingsItem.BuiltinRow(it, it.id !in disabled) }
        items += SettingsItem.SectionHeader(getString(R.string.section_custom))
        customs.forEach { items += SettingsItem.CustomRow(it) }
        adapter.setItems(items)
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_host, null)
        val labelEdit = view.findViewById<EditText>(R.id.edit_label)
        val urlEdit = view.findViewById<EditText>(R.id.edit_url)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_host)
            .setView(view)
            .setPositiveButton(R.string.add) { _, _ ->
                val label = labelEdit.text.toString().trim()
                val url = urlEdit.text.toString().trim()
                if (label.isNotEmpty() && url.isNotEmpty()) {
                    lifecycleScope.launch {
                        dao.insert(CustomHost(label = label, url = url))
                        PingWidgetProvider.triggerPing(this@SettingsActivity)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

// ── sealed item model ──────────────────────────────────────────────────────

sealed class SettingsItem {
    data class PauseToggle(val isPaused: Boolean) : SettingsItem()
    data class SectionHeader(val title: String) : SettingsItem()
    data class BuiltinRow(val target: PingTarget, val enabled: Boolean) : SettingsItem()
    data class CustomRow(val host: CustomHost) : SettingsItem()
}

// ── adapter ───────────────────────────────────────────────────────────────

private class SettingsAdapter(
    private val onPauseToggle: () -> Unit,
    private val onBuiltinToggle: (PingTarget) -> Unit,
    private val onCustomDelete: (CustomHost) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<SettingsItem> = emptyList()

    fun setItems(newItems: List<SettingsItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is SettingsItem.PauseToggle -> 0
        is SettingsItem.SectionHeader -> 1
        is SettingsItem.BuiltinRow -> 2
        is SettingsItem.CustomRow -> 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> PauseVH(inflater.inflate(R.layout.item_pause_toggle, parent, false))
            1 -> HeaderVH(inflater.inflate(R.layout.item_section_header, parent, false))
            2 -> BuiltinVH(inflater.inflate(R.layout.item_builtin_host, parent, false))
            else -> CustomVH(inflater.inflate(R.layout.item_host, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SettingsItem.PauseToggle -> (holder as PauseVH).bind(item)
            is SettingsItem.SectionHeader -> (holder as HeaderVH).bind(item)
            is SettingsItem.BuiltinRow -> (holder as BuiltinVH).bind(item)
            is SettingsItem.CustomRow -> (holder as CustomVH).bind(item)
        }
    }

    inner class PauseVH(view: View) : RecyclerView.ViewHolder(view) {
        private val statusText: TextView = view.findViewById(R.id.pause_status_text)
        private val toggleBtn: com.google.android.material.button.MaterialButton =
            view.findViewById(R.id.pause_toggle_btn)

        fun bind(item: SettingsItem.PauseToggle) {
            if (item.isPaused) {
                statusText.text = itemView.context.getString(R.string.pinging_paused)
                statusText.setTextColor(
                    ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark))
                toggleBtn.text = itemView.context.getString(R.string.resume)
            } else {
                statusText.text = itemView.context.getString(R.string.pinging_active)
                statusText.setTextColor(
                    ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
                toggleBtn.text = itemView.context.getString(R.string.pause)
            }
            toggleBtn.setOnClickListener { onPauseToggle() }
        }
    }

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.section_title)
        fun bind(item: SettingsItem.SectionHeader) { title.text = item.title }
    }

    inner class BuiltinVH(view: View) : RecyclerView.ViewHolder(view) {
        private val labelTv: TextView = view.findViewById(R.id.builtin_label)
        private val addressTv: TextView = view.findViewById(R.id.builtin_address)
        private val disabledBadge: TextView = view.findViewById(R.id.builtin_disabled_badge)
        private val toggleBtn: ImageButton = view.findViewById(R.id.builtin_toggle_btn)

        fun bind(item: SettingsItem.BuiltinRow) {
            val t = item.target
            labelTv.text = t.label
            addressTv.text = t.displayLabel()
            val alpha = if (item.enabled) 1f else 0.4f
            labelTv.alpha = alpha
            addressTv.alpha = alpha
            disabledBadge.visibility = if (item.enabled) View.GONE else View.VISIBLE

            if (item.enabled) {
                toggleBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                toggleBtn.contentDescription = itemView.context.getString(R.string.delete)
            } else {
                toggleBtn.setImageResource(android.R.drawable.ic_menu_rotate)
                toggleBtn.contentDescription = itemView.context.getString(R.string.restore_defaults)
            }
            toggleBtn.setOnClickListener { onBuiltinToggle(t) }
        }
    }

    inner class CustomVH(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.host_label)
        private val url: TextView = view.findViewById(R.id.host_url)
        private val deleteBtn: ImageButton = view.findViewById(R.id.host_delete)

        fun bind(item: SettingsItem.CustomRow) {
            label.text = item.host.label
            url.text = item.host.url
            deleteBtn.setOnClickListener { onCustomDelete(item.host) }
        }
    }
}

package com.danpatten.politicalblocker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Date

class MainActivity : AppCompatActivity() {

    private val app get() = BlockerApp.get(this)
    private val adapter = SuppressedAdapter()

    private lateinit var statusCard: MaterialCardView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var blockedCount: TextView
    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var spamSwitch: MaterialSwitch
    private lateinit var setupCard: View
    private lateinit var notificationRow: View
    private lateinit var contactsRow: View
    private lateinit var archiveRow: View
    private lateinit var restrictedHelp: View
    private lateinit var quietTitle: TextView
    private lateinit var quietStatus: TextView
    private lateinit var clearAll: Button
    private lateinit var emptyView: View
    private lateinit var activityLog: TextView
    private lateinit var activityToggle: Button

    private val changedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshPermissions()
            refreshList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Also picks dark status-bar icons in light theme, so the clock stays readable.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android 15+ draws edge-to-edge; keep content clear of the status and navigation bars.
        val padding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left + padding, bars.top + padding, bars.right + padding, bars.bottom + padding)
            insets
        }

        statusCard = findViewById(R.id.statusCard)
        statusTitle = findViewById(R.id.statusTitle)
        statusDetail = findViewById(R.id.statusDetail)
        blockedCount = findViewById(R.id.blockedCount)
        setupCard = findViewById(R.id.setupCard)
        notificationRow = findViewById(R.id.notificationRow)
        contactsRow = findViewById(R.id.contactsRow)
        archiveRow = findViewById(R.id.archiveRow)
        restrictedHelp = findViewById(R.id.restrictedHelp)
        quietTitle = findViewById(R.id.quietTitle)
        quietStatus = findViewById(R.id.quietStatus)
        clearAll = findViewById(R.id.clearAll)
        emptyView = findViewById(R.id.emptyView)
        activityLog = findViewById(R.id.activityLog)
        activityToggle = findViewById(R.id.activityToggle)

        findViewById<Button>(R.id.openAppInfo).setOnClickListener { openAppInfo() }
        findViewById<Button>(R.id.grantNotification).setOnClickListener {
            if (BlockerApp.hasSeenRestrictedIntro(this)) {
                openListenerSettings()
            } else {
                // First time only: sideloaded apps get "App was denied access" here, so explain the
                // unlock steps before sending the user into Settings.
                BlockerApp.setSeenRestrictedIntro(this)
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.restricted_intro_title)
                    .setMessage(R.string.restricted_intro_message)
                    .setPositiveButton(R.string.restricted_intro_continue) { _, _ -> openListenerSettings() }
                    .setNeutralButton(R.string.app_info) { _, _ -> openAppInfo() }
                    .show()
            }
        }
        findViewById<Button>(R.id.grantContacts).setOnClickListener {
            requestPermissions(RUNTIME_PERMISSIONS, REQUEST_CONTACTS)
        }
        findViewById<Button>(R.id.grantArchive).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        enabledSwitch = findViewById<MaterialSwitch>(R.id.enabledSwitch).apply {
            isChecked = BlockerApp.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                BlockerApp.setEnabled(this@MainActivity, checked)
                refreshPermissions()
            }
        }
        spamSwitch = findViewById<MaterialSwitch>(R.id.spamSwitch).apply {
            isChecked = BlockerApp.isSpamBlockingOn(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                BlockerApp.setSpamBlockingOn(this@MainActivity, checked)
                refreshPermissions()
            }
        }
        findViewById<MaterialSwitch>(R.id.autoArchiveSwitch).apply {
            isChecked = BlockerApp.isAutoArchiveOn(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                BlockerApp.setAutoArchiveOn(this@MainActivity, checked)
                // Don't leave queued archives to fire later once the user has switched it off.
                if (!checked) app.store.pendingArchive().forEach(app.store::removePendingArchive)
                refreshPermissions()
            }
        }

        clearAll.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_all_title)
                .setMessage(R.string.clear_all_message)
                .setPositiveButton(R.string.clear_all) { _, _ ->
                    app.store.clearSuppressed()
                    refreshList()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        activityToggle.setOnClickListener {
            val show = !activityLog.isVisible
            activityLog.isVisible = show
            activityToggle.setText(if (show) R.string.hide else R.string.show)
        }

        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            // Android sometimes leaves the listener unbound after an app update.
            NotificationListenerService.requestRebind(ComponentName(this, SmsNotificationListener::class.java))
        }
        refreshPermissions()
        refreshList()
        ContextCompat.registerReceiver(
            this, changedReceiver, IntentFilter(BlockerApp.ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(changedReceiver)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissions()
    }

    private fun openListenerSettings() = startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun openAppInfo() =
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    /** Shows only what still needs doing: granted permissions drop off the screen. */
    private fun refreshPermissions() {
        val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val contactsOn = RUNTIME_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        // The accessibility service only matters when the auto-archive toggle is on.
        val archiveNeeded = BlockerApp.isAutoArchiveOn(this) && !ArchiveService.isEnabled(this)

        notificationRow.isVisible = !listenerOn
        contactsRow.isVisible = !contactsOn
        archiveRow.isVisible = archiveNeeded
        // Sideloaded apps can't get notification or accessibility access until the user allows
        // restricted settings, so explain how while either is still missing.
        restrictedHelp.isVisible = !listenerOn || archiveNeeded
        setupCard.isVisible = !listenerOn || !contactsOn || archiveNeeded

        val status = when {
            !listenerOn -> Status(
                R.string.status_setup, R.string.status_setup_detail,
                com.google.android.material.R.attr.colorErrorContainer,
                com.google.android.material.R.attr.colorOnErrorContainer,
            )
            !enabledSwitch.isChecked && !spamSwitch.isChecked -> Status(
                R.string.status_paused, R.string.status_paused_detail,
                com.google.android.material.R.attr.colorSurfaceContainerHighest,
                com.google.android.material.R.attr.colorOnSurface,
            )
            else -> Status(
                R.string.status_protected,
                when {
                    !spamSwitch.isChecked -> R.string.status_political_only_detail
                    !enabledSwitch.isChecked -> R.string.status_spam_only_detail
                    else -> R.string.status_protected_detail
                },
                com.google.android.material.R.attr.colorPrimaryContainer,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
            )
        }
        statusCard.setCardBackgroundColor(MaterialColors.getColor(statusCard, status.background))
        val foreground = MaterialColors.getColor(statusCard, status.foreground)
        statusTitle.setText(status.title)
        statusDetail.setText(status.detail)
        listOf(statusTitle, statusDetail, blockedCount, enabledSwitch, spamSwitch).forEach { it.setTextColor(foreground) }

        val quiet = BlockerApp.isQuietMode(this)
        quietTitle.setText(if (quiet) R.string.quiet_title_on else R.string.quiet_title_off)
        quietStatus.setText(if (quiet) R.string.quiet_detail_on else R.string.quiet_detail_off)
    }

    private fun refreshList() {
        val items = app.store.suppressed()
        adapter.submit(items)
        blockedCount.text = resources.getQuantityString(R.plurals.blocked_count, items.size, items.size)
        emptyView.isVisible = items.isEmpty()
        clearAll.isVisible = items.isNotEmpty()

        val activity = app.store.activity()
        activityLog.text = if (activity.isEmpty()) {
            getString(R.string.no_activity)
        } else {
            val timeFormat = DateFormat.getTimeFormat(this)
            activity.joinToString("\n") { (time, line) -> "${timeFormat.format(Date(time))}  $line" }
        }
    }

    private fun keywordSuffix(keywords: String) = if (keywords.isEmpty()) "" else " · $keywords"

    private fun markNotPolitical(item: Store.Suppressed) {
        app.store.allowSender(item.sender)
        app.store.addFeedback(political = false, body = item.body)
        app.store.deleteSuppressed(item.id)
        app.invalidateClassifier()
        Toast.makeText(this, R.string.marked_not_political, Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private class Status(val title: Int, val detail: Int, val background: Int, val foreground: Int)

    private inner class SuppressedAdapter : RecyclerView.Adapter<SuppressedAdapter.Holder>() {
        private var items: List<Store.Suppressed> = emptyList()

        @Suppress("NotifyDataSetChanged")
        fun submit(newItems: List<Store.Suppressed>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_suppressed, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            // Named senders get their initial; plain numbers get a neutral "#".
            holder.avatar.text = item.sender.firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"
            holder.sender.text = item.sender
            holder.time.text = DateUtils.getRelativeTimeSpanString(item.timestamp)
            holder.body.text = item.body
            holder.reason.text = getString(
                if (item.isSpam) R.string.reason_spam else R.string.reason,
                (item.probability * 100).toInt(),
                keywordSuffix(item.keywords),
            )
            // Either way the text is recorded as ordinary and the sender allowed.
            holder.notPolitical.setText(if (item.isSpam) R.string.not_spam else R.string.not_political)
            holder.notPolitical.setOnClickListener { markNotPolitical(item) }
            holder.delete.setOnClickListener {
                app.store.deleteSuppressed(item.id)
                refreshList()
            }
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val avatar: TextView = view.findViewById(R.id.avatar)
            val sender: TextView = view.findViewById(R.id.sender)
            val time: TextView = view.findViewById(R.id.time)
            val body: TextView = view.findViewById(R.id.body)
            val reason: TextView = view.findViewById(R.id.reason)
            val notPolitical: Button = view.findViewById(R.id.notPolitical)
            val delete: Button = view.findViewById(R.id.delete)
        }
    }

    companion object {
        private const val REQUEST_CONTACTS = 1
        private val RUNTIME_PERMISSIONS = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
}

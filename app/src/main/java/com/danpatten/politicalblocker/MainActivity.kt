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
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Date

class MainActivity : AppCompatActivity() {

    private val app get() = BlockerApp.get(this)
    private val adapter = SuppressedAdapter()

    private lateinit var notificationStatus: TextView
    private lateinit var grantNotification: Button
    private lateinit var contactsStatus: TextView
    private lateinit var grantContacts: Button
    private lateinit var archiveStatus: TextView
    private lateinit var grantArchive: Button
    private lateinit var suppressedHeader: TextView
    private lateinit var emptyView: TextView
    private lateinit var ownNumber: EditText
    private lateinit var activityLog: TextView
    private lateinit var quietStatus: TextView

    private val changedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshPermissions()
            refreshList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android 15+ draws edge-to-edge; keep content clear of the status and navigation bars.
        val padding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left + padding, bars.top + padding, bars.right + padding, bars.bottom + padding)
            insets
        }

        notificationStatus = findViewById(R.id.notificationStatus)
        grantNotification = findViewById(R.id.grantNotification)
        contactsStatus = findViewById(R.id.contactsStatus)
        grantContacts = findViewById(R.id.grantContacts)
        archiveStatus = findViewById(R.id.archiveStatus)
        grantArchive = findViewById(R.id.grantArchive)
        suppressedHeader = findViewById(R.id.suppressedHeader)
        emptyView = findViewById(R.id.emptyView)
        activityLog = findViewById<TextView>(R.id.activityLog).apply { movementMethod = ScrollingMovementMethod() }

        findViewById<Button>(R.id.openAppInfo).setOnClickListener { openAppInfo() }
        grantNotification.setOnClickListener {
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
        grantContacts.setOnClickListener {
            requestPermissions(RUNTIME_PERMISSIONS, REQUEST_CONTACTS)
        }
        grantArchive.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        quietStatus = findViewById(R.id.quietStatus)

        findViewById<MaterialSwitch>(R.id.enabledSwitch).apply {
            isChecked = BlockerApp.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> BlockerApp.setEnabled(this@MainActivity, checked) }
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

        val testInput = findViewById<EditText>(R.id.testInput)
        val testResult = findViewById<TextView>(R.id.testResult)
        findViewById<Button>(R.id.testButton).setOnClickListener {
            val result = app.classifier.classify(testInput.text.toString())
            testResult.visibility = View.VISIBLE
            testResult.text = getString(
                R.string.test_result,
                getString(if (result.isPolitical) R.string.would_block else R.string.would_allow),
                (result.probability * 100).toInt(),
                keywordSuffix(result.matchedKeywords.joinToString(", ")),
            )
        }

        ownNumber = findViewById<EditText>(R.id.ownNumber).apply { setText(BlockerApp.ownNumber(this@MainActivity)) }
        findViewById<Button>(R.id.sendTest).setOnClickListener {
            if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                scheduleTestText()
            } else {
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS), REQUEST_SEND_SMS)
            }
        }

        findViewById<Button>(R.id.clearAll).setOnClickListener {
            app.store.clearSuppressed()
            refreshList()
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
        if (requestCode == REQUEST_SEND_SMS) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                scheduleTestText()
            } else {
                Toast.makeText(this, R.string.test_needs_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Texts a random political sample to the user's own number in 10s, so they can close the app first. */
    private fun scheduleTestText() {
        val number = ownNumber.text.toString().trim()
        if (!ContactChecker.looksLikeNumber(number)) {
            Toast.makeText(this, R.string.test_needs_number, Toast.LENGTH_SHORT).show()
            return
        }
        BlockerApp.setOwnNumber(this, number)
        app.sendTestTextLater(number, TEST_DELAY_MS)
        Toast.makeText(this, getString(R.string.test_scheduled, number), Toast.LENGTH_LONG).show()
    }

    private fun openListenerSettings() = startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun openAppInfo() =
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    private fun refreshPermissions() {
        val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        notificationStatus.setText(if (listenerOn) R.string.notification_access_on else R.string.notification_access_off)
        grantNotification.visibility = if (listenerOn) View.GONE else View.VISIBLE

        val contactsOn = RUNTIME_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        contactsStatus.setText(if (contactsOn) R.string.contacts_on else R.string.contacts_off)
        grantContacts.visibility = if (contactsOn) View.GONE else View.VISIBLE

        quietStatus.setText(if (BlockerApp.isQuietMode(this)) R.string.quiet_on else R.string.quiet_off)

        // The accessibility service only matters when the auto-archive toggle is on.
        (archiveStatus.parent as View).visibility = if (BlockerApp.isAutoArchiveOn(this)) View.VISIBLE else View.GONE
        val archiveOn = ArchiveService.isEnabled(this)
        archiveStatus.setText(if (archiveOn) R.string.archive_on else R.string.archive_off)
        grantArchive.visibility = if (archiveOn) View.GONE else View.VISIBLE

        // Sideloaded apps can't get notification or accessibility access until the user allows
        // restricted settings, so explain how while either is still missing.
        val restricted = !listenerOn || (BlockerApp.isAutoArchiveOn(this) && !archiveOn)
        findViewById<View>(R.id.restrictedHelp).visibility = if (restricted) View.VISIBLE else View.GONE
    }

    private fun refreshList() {
        val items = app.store.suppressed()
        adapter.submit(items)
        suppressedHeader.text = getString(R.string.suppressed_header, items.size)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

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
            holder.sender.text = item.sender
            holder.time.text = DateUtils.getRelativeTimeSpanString(item.timestamp)
            holder.body.text = item.body
            holder.reason.text = getString(R.string.reason, (item.probability * 100).toInt(), keywordSuffix(item.keywords))
            holder.notPolitical.setOnClickListener { markNotPolitical(item) }
            holder.delete.setOnClickListener {
                app.store.deleteSuppressed(item.id)
                refreshList()
            }
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
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
        private const val REQUEST_SEND_SMS = 2
        private const val TEST_DELAY_MS = 10_000L
        private val RUNTIME_PERMISSIONS = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
}

package com.danpatten.politicalblocker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private val app get() = BlockerApp.get(this)
    private val adapter = SuppressedAdapter()

    private lateinit var notificationStatus: TextView
    private lateinit var grantNotification: Button
    private lateinit var contactsStatus: TextView
    private lateinit var grantContacts: Button
    private lateinit var suppressedHeader: TextView
    private lateinit var emptyView: TextView

    private val changedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationStatus = findViewById(R.id.notificationStatus)
        grantNotification = findViewById(R.id.grantNotification)
        contactsStatus = findViewById(R.id.contactsStatus)
        grantContacts = findViewById(R.id.grantContacts)
        suppressedHeader = findViewById(R.id.suppressedHeader)
        emptyView = findViewById(R.id.emptyView)

        grantNotification.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        grantContacts.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
        }

        findViewById<MaterialSwitch>(R.id.enabledSwitch).apply {
            isChecked = BlockerApp.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> BlockerApp.setEnabled(this@MainActivity, checked) }
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
        refreshPermissions()
        refreshList()
        ContextCompat.registerReceiver(
            this, changedReceiver, IntentFilter(BlockerApp.ACTION_SUPPRESSED_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
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

    private fun refreshPermissions() {
        val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        notificationStatus.setText(if (listenerOn) R.string.notification_access_on else R.string.notification_access_off)
        grantNotification.visibility = if (listenerOn) View.GONE else View.VISIBLE

        val contactsOn = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        contactsStatus.setText(if (contactsOn) R.string.contacts_on else R.string.contacts_off)
        grantContacts.visibility = if (contactsOn) View.GONE else View.VISIBLE
    }

    private fun refreshList() {
        val items = app.store.suppressed()
        adapter.submit(items)
        suppressedHeader.text = getString(R.string.suppressed_header, items.size)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
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
    }
}

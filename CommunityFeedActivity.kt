package com.amogelang.safeconnect.app

import android.os.Bundle
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.amogelang.safeconnect.app.firebase.FirebaseAuthRepository
import com.amogelang.safeconnect.app.firebase.FirebaseResult
import com.amogelang.safeconnect.app.offline.OfflineCommunityRepository
import com.amogelang.safeconnect.app.util.LocationHelper
import com.amogelang.safeconnect.app.settings.UserSafetyPreferencesStore
import kotlinx.coroutines.launch

/**
 * Matches the "Community Feed" wireframe from the Part 1 design doc: a
 * scrollable list of nearby safety reports plus a floating action button
 * to post a new one.
 */
class CommunityFeedActivity : AppCompatActivity() {

    private val authRepository = FirebaseAuthRepository()
    private lateinit var communityRepository: OfflineCommunityRepository

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private var feedListener: ListenerRegistration? = null
    private lateinit var provinceFilter: Spinner
    private lateinit var networkStatus: TextView
    private var latestReports: List<com.amogelang.safeconnect.app.firebase.CommunityReport> = emptyList()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_feed)
        communityRepository = OfflineCommunityRepository(this)

        recyclerView = findViewById(R.id.recyclerReports)
        recyclerView.layoutManager = LinearLayoutManager(this)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        provinceFilter = findViewById(R.id.spinnerProvinceFilter)
        networkStatus = findViewById(R.id.tvNetworkStatus)
        provinceFilter.adapter = ArrayAdapter.createFromResource(this, R.array.south_african_provinces, android.R.layout.simple_spinner_dropdown_item)
        provinceFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = renderReports()
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddReport).setOnClickListener { showAddReportDialog() }

        startLiveFeed()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { runOnUiThread { startLiveFeed() } }
            override fun onLost(network: Network) { runOnUiThread { stopLiveFeed("You are offline. Reconnect to view live reports.") } }
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun stopLiveFeed(message: String) {
        feedListener?.remove(); feedListener = null
        latestReports = emptyList(); recyclerView.adapter = CommunityReportAdapter(emptyList())
        progressBar.visibility = ProgressBar.GONE; tvEmptyState.visibility = TextView.VISIBLE
        tvEmptyState.text = message; networkStatus.text = message
        findViewById<Button>(R.id.btnAddReport).isEnabled = false
    }

    private fun startLiveFeed() {
        if (!isOnline()) { stopLiveFeed(getString(R.string.community_offline)); return }
        if (feedListener != null) return
        findViewById<Button>(R.id.btnAddReport).isEnabled = true
        networkStatus.text = getString(R.string.community_online_only)
        progressBar.visibility = ProgressBar.VISIBLE
        tvEmptyState.visibility = TextView.GONE
        feedListener = communityRepository.observeRecentReports(
            onChange = { reports ->
                val selectedCategories = UserSafetyPreferencesStore(this).read().showCommunityCategories
                runOnUiThread {
                    latestReports = reports.filter { it.category in selectedCategories }
                    progressBar.visibility = ProgressBar.GONE
                    renderReports()
                }
            },
            onError = { error ->
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    tvEmptyState.visibility = TextView.VISIBLE
                    networkStatus.text = "Live feed unavailable. Check your connection."
                    Toast.makeText(this, error.message ?: "Could not load live reports", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun renderReports() {
        if (!isOnline()) { stopLiveFeed(getString(R.string.community_offline)); return }
        val selected = provinceFilter.selectedItem?.toString() ?: "All provinces"
        val visible = if (selected == "All provinces") latestReports else latestReports.filter { it.province.equals(selected, ignoreCase = true) }
        tvEmptyState.text = getString(R.string.no_reports_found)
        tvEmptyState.visibility = if (visible.isEmpty()) TextView.VISIBLE else TextView.GONE
        recyclerView.adapter = CommunityReportAdapter(visible)
    }

    override fun onDestroy() {
        feedListener?.remove()
        networkCallback?.let { runCatching { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } }
        super.onDestroy()
    }

    private fun loadReports() {
        progressBar.visibility = ProgressBar.VISIBLE
        tvEmptyState.visibility = TextView.GONE
        lifecycleScope.launch {
            when (val result = communityRepository.getRecentReports()) {
                is FirebaseResult.Success -> {
                    progressBar.visibility = ProgressBar.GONE
                    if (result.data.isEmpty()) {
                        tvEmptyState.visibility = TextView.VISIBLE
                    } else {
                        recyclerView.adapter = CommunityReportAdapter(result.data)
                    }
                }
                is FirebaseResult.Failure -> {
                    progressBar.visibility = ProgressBar.GONE
                    tvEmptyState.visibility = TextView.VISIBLE
                    Toast.makeText(this@CommunityFeedActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddReportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_report, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescription)

        spinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.report_categories, android.R.layout.simple_spinner_dropdown_item
        )
        val provinceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@CommunityFeedActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Select province") + resources.getStringArray(R.array.south_african_provinces).drop(1))
        }
        (dialogView as? android.view.ViewGroup)?.addView(TextView(this).apply { text = "Province"; setPadding(0, 12, 0, 4) })
        (dialogView as? android.view.ViewGroup)?.addView(provinceSpinner)

        AlertDialog.Builder(this)
            .setTitle(R.string.title_community)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_post_report) { _, _ ->
                val category = spinner.selectedItem.toString()
                val description = etDescription.text.toString().trim()
                val selectedProvince = provinceSpinner.selectedItem.toString()
                if (!isOnline()) { Toast.makeText(this, R.string.community_offline, Toast.LENGTH_LONG).show(); return@setPositiveButton }
                if (selectedProvince == "Select province") { Toast.makeText(this, "Select the province for this report.", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (description.isNotEmpty()) {
                    postReport(category, description, selectedProvince)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun postReport(category: String, description: String, province: String) {
        val uid = authRepository.currentUserId() ?: return
        lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(this@CommunityFeedActivity)
            val lat = location?.first ?: 0.0
            val lng = location?.second ?: 0.0

            when (val result = communityRepository.postReport(uid, category, description, lat, lng, province)) {
                is FirebaseResult.Success -> {
                    Toast.makeText(this@CommunityFeedActivity, "Community report submitted.", Toast.LENGTH_LONG).show()
                }
                is FirebaseResult.Failure -> {
                    Toast.makeText(this@CommunityFeedActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

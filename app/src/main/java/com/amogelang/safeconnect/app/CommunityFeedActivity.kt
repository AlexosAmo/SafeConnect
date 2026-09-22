package com.amogelang.safeconnect.app

import android.os.Bundle
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
import com.amogelang.safeconnect.app.firebase.FirebaseAuthRepository
import com.amogelang.safeconnect.app.firebase.FirebaseResult
import com.amogelang.safeconnect.app.firebase.FirestoreCommunityRepository
import com.amogelang.safeconnect.app.util.LocationHelper
import kotlinx.coroutines.launch

/**
 * Matches the "Community Feed" wireframe from the Part 1 design doc: a
 * scrollable list of nearby safety reports plus a floating action button
 * to post a new one.
 */
class CommunityFeedActivity : AppCompatActivity() {

    private val authRepository = FirebaseAuthRepository()
    private val communityRepository = FirestoreCommunityRepository()

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_feed)

        recyclerView = findViewById(R.id.recyclerReports)
        recyclerView.layoutManager = LinearLayoutManager(this)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddReport).setOnClickListener { showAddReportDialog() }

        loadReports()
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

        AlertDialog.Builder(this)
            .setTitle(R.string.title_community)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_post_report) { _, _ ->
                val category = spinner.selectedItem.toString()
                val description = etDescription.text.toString().trim()
                if (description.isNotEmpty()) {
                    postReport(category, description)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun postReport(category: String, description: String) {
        val uid = authRepository.currentUserId() ?: return
        lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(this@CommunityFeedActivity)
            val lat = location?.first ?: 0.0
            val lng = location?.second ?: 0.0

            when (val result = communityRepository.postReport(uid, category, description, lat, lng)) {
                is FirebaseResult.Success -> {
                    Toast.makeText(this@CommunityFeedActivity, getString(R.string.report_posted), Toast.LENGTH_SHORT).show()
                    loadReports()
                }
                is FirebaseResult.Failure -> {
                    Toast.makeText(this@CommunityFeedActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

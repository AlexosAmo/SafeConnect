package com.amogelang.safeconnect.app

import android.Manifest
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import com.amogelang.safeconnect.app.safety.JourneyTrackingService
import com.amogelang.safeconnect.app.firebase.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

class TrackJourneyActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var destinationInput: EditText
    private lateinit var etaInput: EditText
    private var journeyId: String = ""

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, "Journey tracking can continue, but Android may limit visible notification alerts.", Toast.LENGTH_LONG).show()
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (allowed) startJourney() else status.text = "Location permission is required to share a journey."
    }

    private lateinit var liveIndicator: TextView
    private lateinit var metricDestination: TextView
    private lateinit var metricEta: TextView
    private lateinit var metricContacts: TextView

    private lateinit var endKeepSharingButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_journey)
        status = findViewById(R.id.tvJourneyStatus)
        startButton = findViewById(R.id.btnStartJourney)
        stopButton = findViewById(R.id.btnStopJourney)
        endKeepSharingButton = findViewById(R.id.btnEndKeepSharing)
        destinationInput = findViewById(R.id.etJourneyDestination)
        etaInput = findViewById(R.id.etJourneyEta)

        liveIndicator = findViewById(R.id.tvLiveIndicator)
        metricDestination = findViewById(R.id.tvMetricDestination)
        metricEta = findViewById(R.id.tvMetricEta)
        metricContacts = findViewById(R.id.tvMetricContacts)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        startButton.setOnClickListener {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Toast.makeText(this, "Sign in before starting a journey.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else startJourney()
        }
        endKeepSharingButton.setOnClickListener {
            WorkManager.getInstance(this).cancelUniqueWork("safeconnect-journey-arrival-$journeyId")
            lifecycleScope.launch { runCatching {
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("journeys").document(journeyId)
                    .update(mapOf("status" to "COMPLETED_SHARING_ACTIVE", "journeyEndedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "sharingEnabled" to true))
            } }
            getSharedPreferences("safeconnect_active_journey", MODE_PRIVATE).edit().putString("journeyStatus", "COMPLETED_SHARING_ACTIVE").apply()
            status.text = "Journey ended. Location sharing remains ACTIVE with emergency contacts."
            liveIndicator.text = "● JOURNEY COMPLETED • LIVE LOCATION SHARING ACTIVE"
            startButton.isEnabled = false
            endKeepSharingButton.isEnabled = false
            stopButton.isEnabled = true
            Toast.makeText(this, "Journey ended. Live location sharing continues.", Toast.LENGTH_LONG).show()
        }
        stopButton.setOnClickListener {
            startService(Intent(this, JourneyTrackingService::class.java).setAction(JourneyTrackingService.ACTION_STOP))
            WorkManager.getInstance(this).cancelUniqueWork("safeconnect-journey-arrival-$journeyId")
            status.text = "Journey ended. Location sharing has been stopped."
            liveIndicator.text = "● JOURNEY ENDED"
            startButton.isEnabled = true
            endKeepSharingButton.isEnabled = false
            stopButton.isEnabled = false
        }
        val activePrefs = getSharedPreferences("safeconnect_active_journey", MODE_PRIVATE)
        val savedJourneyId = activePrefs.getString("journeyId", null)
        if (!savedJourneyId.isNullOrBlank()) {
            journeyId = savedJourneyId
            val journeyStatus = activePrefs.getString("journeyStatus", "ACTIVE")
            if (journeyStatus == "COMPLETED_SHARING_ACTIVE") {
                status.text = "Journey completed. Live location sharing is still active."
                liveIndicator.text = "● JOURNEY COMPLETED • LIVE LOCATION SHARING ACTIVE"
                startButton.isEnabled = false
                endKeepSharingButton.isEnabled = false
                stopButton.isEnabled = true
            } else {
                status.text = "A journey is currently active. Location sharing is active."
                liveIndicator.text = "● LIVE GPS BROADCAST ACTIVE"
                startButton.isEnabled = false
                endKeepSharingButton.isEnabled = true
                stopButton.isEnabled = true
            }
        } else {
            endKeepSharingButton.isEnabled = false
            stopButton.isEnabled = false
        }
        val cleanup = PeriodicWorkRequestBuilder<com.amogelang.safeconnect.app.safety.JourneyPointCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "safeconnect-journey-point-retention", ExistingPeriodicWorkPolicy.KEEP, cleanup
        )
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startJourney() {
        val destText = destinationInput.text.toString().trim()
        val etaMinutes = etaInput.text.toString().toLongOrNull()?.coerceIn(1, 24 * 60) ?: 60L
        lifecycleScope.launch {
            try {
                journeyId = UUID.randomUUID().toString()
                getSharedPreferences("safeconnect_active_journey", MODE_PRIVATE).edit().remove("journeyStatus").apply()
                val linkedContactIds = if (findViewById<CheckBox>(R.id.cbShareJourney).isChecked) runCatching {
                    FirebaseRepository.getEmergencyContacts().mapNotNull { it.contactUserId?.takeIf(String::isNotBlank) }.distinct()
                }.getOrDefault(emptyList()) else emptyList()
                val intent = Intent(this@TrackJourneyActivity, JourneyTrackingService::class.java).apply {
                    action = JourneyTrackingService.ACTION_START
                    putExtra(JourneyTrackingService.EXTRA_JOURNEY_ID, journeyId)
                    putExtra(JourneyTrackingService.EXTRA_DESTINATION, destText)
                    putExtra(JourneyTrackingService.EXTRA_ARRIVAL, System.currentTimeMillis() + etaMinutes * 60_000L)
                    putStringArrayListExtra(JourneyTrackingService.EXTRA_SHARED_WITH, ArrayList(linkedContactIds))
                }
                ContextCompat.startForegroundService(this@TrackJourneyActivity, intent)
                val arrivalConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val arrivalWork = OneTimeWorkRequestBuilder<com.amogelang.safeconnect.app.safety.JourneyArrivalCheckWorker>()
                    .setInitialDelay(etaMinutes, TimeUnit.MINUTES)
                    .setConstraints(arrivalConstraints)
                    .setInputData(workDataOf(com.amogelang.safeconnect.app.safety.JourneyArrivalCheckWorker.INPUT_JOURNEY_ID to journeyId))
                    .build()
                WorkManager.getInstance(this@TrackJourneyActivity).enqueueUniqueWork(
                    "safeconnect-journey-arrival-$journeyId", androidx.work.ExistingWorkPolicy.REPLACE, arrivalWork
                )

                liveIndicator.text = "● LIVE GPS BROADCAST ACTIVE"
                metricDestination.text = "📍 Destination: ${if (destText.isBlank()) "Destination not specified" else destText}"
                metricEta.text = "⏱ Expected Arrival: $etaMinutes minutes"
                metricContacts.text = "👥 Sharing: ${if (linkedContactIds.isEmpty()) "Local track (no contacts linked)" else "${linkedContactIds.size} Linked Emergency Contact(s)"}"

                status.text = if (linkedContactIds.isEmpty()) {
                    "Journey tracking started. Live location active."
                } else {
                    "Journey tracking started. Shared with ${linkedContactIds.size} linked SafeConnect contact account(s)."
                }
                startButton.isEnabled = false
                endKeepSharingButton.isEnabled = true
                stopButton.isEnabled = true
            } catch (error: Exception) {
                status.text = "Could not start journey: ${error.localizedMessage ?: "Please try again."}"
            }
        }
    }
}

package com.amogelang.safeconnect.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class SharedJourneysActivity : AppCompatActivity() {
    private var registration: ListenerRegistration? = null
    private lateinit var container: LinearLayout
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_journeys)
        container = findViewById(R.id.sharedJourneyContainer)
        empty = findViewById(R.id.tvSharedJourneyEmpty)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) { empty.text = "Sign in to view shared journeys."; return }

        registration = FirebaseFirestore.getInstance().collection("journeys")
            .whereArrayContains("sharedWith", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { empty.text = "Could not load shared journeys: ${error.localizedMessage}"; return@addSnapshotListener }
                val active = snapshot?.documents.orEmpty().filter { it.getString("status") == "ACTIVE" }
                container.removeAllViews()
                empty.visibility = if (active.isEmpty()) View.VISIBLE else View.GONE
                active.forEach { document ->
                    val destination = document.getString("destination").orEmpty().ifBlank { "Destination not provided" }
                    val lat = document.getDouble("latestLatitude")
                    val lon = document.getDouble("latestLongitude")
                    val owner = document.getString("userId").orEmpty()
                    val arrivalStatus = document.getString("arrivalStatus").orEmpty()
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(18, 18, 18, 18)
                        setBackgroundResource(R.drawable.bg_card)
                    }
                    card.addView(TextView(this).apply { text = "Active journey • $destination"; textSize = 17f; setTextColor(getColor(R.color.text_ink)) })
                    card.addView(TextView(this).apply {
                        text = if (lat != null && lon != null) "Latest location: $lat, $lon" else "Waiting for the first GPS update"
                        setPadding(0, 8, 0, 8)
                    })
                    if (arrivalStatus == "OVERDUE") {
                        card.addView(TextView(this).apply { text = "Expected arrival time has passed. Please check in with this contact."; setTextColor(getColor(R.color.safeconnect_red)); setPadding(0, 6, 0, 6) })
                    }
                    card.addView(TextView(this).apply { text = "Shared by account: ${owner.take(8)}…"; textSize = 12f })
                    if (lat != null && lon != null) {
                        card.addView(Button(this).apply {
                            text = "Open location in Maps"
                            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))) }
                        })
                    }
                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    params.bottomMargin = 14
                    container.addView(card, params)
                }
            }
    }

    override fun onDestroy() { registration?.remove(); super.onDestroy() }
}

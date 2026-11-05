package com.amogelang.safeconnect.app

import android.os.Bundle
import android.content.Intent
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amogelang.safeconnect.app.repository.Phase5Repository
import com.amogelang.safeconnect.app.firebase.FirebaseRepository
import com.amogelang.safeconnect.app.safety.SafetyScoreManager
import kotlinx.coroutines.launch

class SafetyScoreActivity : AppCompatActivity() {
    private lateinit var points: TextView
    private lateinit var level: TextView
    private lateinit var status: TextView
    private lateinit var assessmentSummary: TextView
    private lateinit var progress: ProgressBar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 28); setBackgroundColor(getColor(R.color.panel_grey)) }
        val scroll = ScrollView(this).apply { addView(root) }; setContentView(scroll)
        root.addView(TextView(this).apply { text = "Your Safety Score"; textSize = 27f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(getColor(R.color.text_ink)) })
        val scoreCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 22, 20, 22); setBackgroundResource(R.drawable.bg_card) }
        val initialScore = SafetyScoreManager.getScore(this)
        val initialLevel = SafetyScoreManager.getLevel(this)
        val hasAssessment = SafetyScoreManager.hasAssessment(this)
        points = TextView(this).apply { text = "$initialScore points"; textSize = 36f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(getColor(R.color.safeconnect_blue)) }
        level = TextView(this).apply { text = initialLevel; textSize = 18f; setTextColor(getColor(R.color.text_ink)); setPadding(0, 8, 0, 10) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; this.progress = initialScore }
        scoreCard.addView(points); scoreCard.addView(level); scoreCard.addView(progress); root.addView(scoreCard)
        root.addView(TextView(this).apply { text = "Complete positive safety activities and your readiness assessment to maintain a high safety score."; textSize = 14f; setTextColor(getColor(R.color.text_muted)); setPadding(0, 18, 0, 16) })
        root.addView(TextView(this).apply { text = "Personal Safety Assessment"; textSize = 20f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(getColor(R.color.text_ink)) })
        assessmentSummary = TextView(this).apply {
            text = if (hasAssessment) "Latest readiness score: $initialScore/100\nLevel: $initialLevel" else "No assessment completed yet."
            textSize = 16f; setTextColor(getColor(R.color.text_ink)); setPadding(0, 8, 0, 12)
        }
        root.addView(assessmentSummary)
        root.addView(TextView(this).apply { text = "Take a short readiness test to review your emergency preparation, journey habits and account settings."; textSize = 14f; setTextColor(getColor(R.color.text_muted)); setPadding(0, 8, 0, 14) })
        root.addView(Button(this).apply { text = getString(R.string.take_assessment); setOnClickListener { startActivity(Intent(this@SafetyScoreActivity, SafetyAssessmentActivity::class.java)) } })
        status = TextView(this).apply { textSize = 14f; setPadding(0, 12, 0, 12); setTextColor(getColor(R.color.text_muted)) }; root.addView(status)
        root.addView(Button(this).apply { text = "Refresh score"; setOnClickListener { loadLatestAssessment() } })
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        loadLatestAssessment()
    }
    override fun onResume() {
        super.onResume()
        if (::points.isInitialized) {
            applyScore(SafetyScoreManager.getScore(this), SafetyScoreManager.getLevel(this), SafetyScoreManager.hasAssessment(this))
            loadLatestAssessment()
        }
    }
    private fun applyScore(score: Int, lvl: String, hasAssessment: Boolean) {
        points.text = "$score points"
        level.text = lvl
        progress.progress = score.coerceIn(0, 100)
        assessmentSummary.text = if (hasAssessment) {
            "Latest readiness score: $score/100\nLevel: $lvl"
        } else {
            "No assessment completed yet. Take the test to calculate your readiness score."
        }
    }
    private fun loadLatestAssessment() { lifecycleScope.launch {
        try {
            val assessment = FirebaseRepository.getLatestSafetyAssessment()
            if (assessment != null) {
                val (score, lvl) = assessment
                SafetyScoreManager.saveScore(this@SafetyScoreActivity, score, lvl)
                applyScore(score, lvl, true)
                status.text = "Safety score refreshed from your latest assessment."
            } else if (!SafetyScoreManager.hasAssessment(this@SafetyScoreActivity)) {
                applyScore(SafetyScoreManager.DEFAULT_SCORE, SafetyScoreManager.DEFAULT_LEVEL, false)
                status.text = "Initial safety score active."
            }
        } catch (_: Exception) {
            val has = SafetyScoreManager.hasAssessment(this@SafetyScoreActivity)
            applyScore(SafetyScoreManager.getScore(this@SafetyScoreActivity), SafetyScoreManager.getLevel(this@SafetyScoreActivity), has)
            status.text = "Showing cached safety score."
        }
    } }
}

class AiAssistantActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 24, 22, 22); setBackgroundColor(getColor(R.color.panel_grey)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 20, 18, 20); setBackgroundResource(R.drawable.bg_soft_blue) }
        header.addView(TextView(this).apply { text = "🛡 SafeConnect AI"; textSize = 25f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(getColor(R.color.text_ink)) })
        header.addView(TextView(this).apply { text = "Your personal safety information assistant"; textSize = 14f; setTextColor(getColor(R.color.text_muted)); setPadding(0, 6, 0, 0) })
        root.addView(header)
        root.addView(TextView(this).apply { text = "Ask about journey preparation, home security, digital privacy or general safety awareness. Do not enter names, phone numbers, passwords or exact locations. This assistant does not contact emergency services."; textSize = 14f; setTextColor(getColor(R.color.text_muted)); setPadding(0, 16, 0, 14) })
        val question = EditText(this).apply { hint = "How can I prepare for a safer journey?"; minLines = 3; gravity = Gravity.TOP; setPadding(14, 14, 14, 14); background = getDrawable(R.drawable.bg_input) }; root.addView(question, LinearLayout.LayoutParams(-1, -2))
        val answerCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundResource(R.drawable.bg_card) }
        answerCard.addView(TextView(this).apply { text = "ASSISTANT RESPONSE"; textSize = 12f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(getColor(R.color.safeconnect_blue)) })
        val answer = TextView(this).apply { text = "Your safety guidance will appear here."; textSize = 16f; setTextColor(getColor(R.color.text_ink)); setPadding(0, 10, 0, 4) }; answerCard.addView(answer)
        val answerParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 16; bottomMargin = 16 }; root.addView(answerCard, answerParams)
        val ask = Button(this).apply { text = "Ask SafeConnect AI" }
        ask.setOnClickListener { val q = question.text.toString().trim(); if (q.isBlank()) { question.error = "Enter a question"; return@setOnClickListener }; ask.isEnabled = false; ask.text = "Preparing guidance…"; answer.text = "Preparing general safety guidance…"; lifecycleScope.launch { try { answer.text = Phase5Repository.ask(q) } catch (e: Exception) { answer.text = "The assistant is unavailable. Please try again or consult a trusted source." } finally { ask.isEnabled = true; ask.text = "Ask SafeConnect AI" } } }
        root.addView(ask)
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(root)
    }
}

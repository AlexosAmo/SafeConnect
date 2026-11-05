package com.amogelang.safeconnect.app

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amogelang.safeconnect.app.firebase.FirebaseRepository
import com.amogelang.safeconnect.app.safety.SafetyAssessmentScoring
import com.amogelang.safeconnect.app.safety.SafetyScoreManager
import kotlinx.coroutines.launch

class SafetyAssessmentActivity : AppCompatActivity() {
    private val questions = listOf(
        "Do you have at least one trusted emergency contact saved?",
        "Do your emergency contacts know they are listed in SafeConnect?",
        "Do you share your journey and expected arrival time when travelling?",
        "Do you usually choose well-lit, familiar routes when possible?",
        "Do you know how to activate and cancel an SOS in SafeConnect?",
        "Do you keep your phone charged or carry a power bank when travelling?",
        "Do you check local safety information before visiting unfamiliar places?",
        "Do you review your privacy, location-sharing and emergency-contact settings?"
    )
    private val selected = MutableList(8) { -1 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 24, 22, 24); setBackgroundColor(getColor(R.color.panel_grey)) }
        val scroll = ScrollView(this).apply { addView(root) }; setContentView(scroll)
        root.addView(TextView(this).apply { text = getString(R.string.assessment_title); textSize = 25f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(getColor(R.color.text_ink)) })
        root.addView(TextView(this).apply { text = getString(R.string.assessment_intro); textSize = 14f; setPadding(0, 10, 0, 18); setTextColor(getColor(R.color.text_muted)) })
        questions.forEachIndexed { index, question ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14); setBackgroundResource(R.drawable.bg_card) }
            card.addView(TextView(this).apply { text = "${index + 1}. $question"; textSize = 16f; setTextColor(getColor(R.color.text_ink)); setTypeface(null, android.graphics.Typeface.BOLD) })
            val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
            listOf("Not yet / No" to 0, "Sometimes / Partly" to 5, "Yes / Consistently" to 10).forEach { (label, points) ->
                group.addView(RadioButton(this).apply { text = label; tag = points })
            }
            group.setOnCheckedChangeListener { g, checkedId -> selected[index] = g.findViewById<RadioButton>(checkedId).tag as Int }
            card.addView(group)
            val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 }; root.addView(card, params)
        }
        val result = TextView(this).apply { textSize = 18f; setPadding(0, 14, 0, 14); setTextColor(getColor(R.color.text_ink)) }; root.addView(result)
        val submitButton = Button(this).apply { text = "Calculate my score" }
        submitButton.setOnClickListener {
                if (selected.any { it < 0 }) { result.text = getString(R.string.assessment_complete); return@setOnClickListener }
                val score = SafetyAssessmentScoring.calculate(selected)
                val level = SafetyAssessmentScoring.level(score)
                result.text = getString(R.string.assessment_score, score) + "\n$level"
                SafetyScoreManager.saveScore(this@SafetyAssessmentActivity, score, level)
                submitButton.isEnabled = false
                lifecycleScope.launch {
                    try { FirebaseRepository.saveSafetyAssessment(score, level, selected.toList()); Toast.makeText(this@SafetyAssessmentActivity, R.string.assessment_saved, Toast.LENGTH_LONG).show() }
                    catch (e: Exception) { Toast.makeText(this@SafetyAssessmentActivity, e.message ?: "Assessment could not be saved.", Toast.LENGTH_LONG).show() }
                    finally { submitButton.isEnabled = true }
                }
        }
        root.addView(submitButton)
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
    }
}

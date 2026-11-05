package com.amogelang.safeconnect.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.amogelang.safeconnect.app.firebase.FirebaseRepository
import com.amogelang.safeconnect.app.model.EmergencyContact
import com.amogelang.safeconnect.app.model.Incident
import com.amogelang.safeconnect.app.model.UserProfile
import com.amogelang.safeconnect.app.offline.OfflineIncidentRepository
import com.amogelang.safeconnect.app.offline.OfflineIncidentEntity
import com.amogelang.safeconnect.app.safety.JourneyTrackingService
import com.amogelang.safeconnect.app.safety.SafetyScoreManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.amogelang.safeconnect.app.settings.UserSafetyPreferencesStore
import com.amogelang.safeconnect.app.util.InputValidator
import com.amogelang.safeconnect.app.util.LanguageHelper
import com.amogelang.safeconnect.app.util.LocationHelper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.text.DateFormat
import java.util.Date

class LoginActivity : AppCompatActivity() {
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) { setAuthBusy(false); return@registerForActivityResult }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            lifecycleScope.launch {
                setAuthBusy(true)
                try {
                    if (idToken.isNullOrBlank()) {
                        throw IllegalStateException(
                            "Google did not return an ID token. Check the Web OAuth client ID and SHA-1/SHA-256 fingerprints in Firebase Console, then download the updated google-services.json."
                        )
                    }
                    FirebaseRepository.signInWithCredential(
                        GoogleAuthProvider.getCredential(idToken, null)
                    )
                    completeSignIn()
                } catch (e: Exception) {
                    showAuthError(firebaseError(e))
                } finally {
                    setAuthBusy(false)
                }
            }
        } catch (e: Exception) {
            showAuthError(e.message ?: getString(R.string.error_unknown))
        }
    }

    private var authErrorView: TextView? = null
    private var authProgress: ProgressBar? = null
    private var loginButton: Button? = null
    private var googleButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser != null) { routeExistingSession(); return }
        setContentView(R.layout.activity_login)
        authErrorView = findViewById(R.id.tvError)
        authProgress = findViewById(R.id.progressBar)
        loginButton = findViewById(R.id.btnLogin)
        googleButton = findViewById(R.id.btnGoogle)
        val email = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)

        loginButton?.setOnClickListener {
            val cleanEmail = email.text.toString().trim()
            if (!InputValidator.isValidEmail(cleanEmail) || password.text.isNullOrBlank()) {
                showAuthError(getString(R.string.error_valid_email_password)); return@setOnClickListener
            }
            lifecycleScope.launch {
                setAuthBusy(true)
                try { FirebaseRepository.login(cleanEmail, password.text.toString()); completeSignIn() }
                catch (e: Exception) { showAuthError(firebaseError(e)) }
                finally { setAuthBusy(false) }
            }
        }
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener { startActivity(Intent(this, ForgotPasswordActivity::class.java)) }
        findViewById<TextView>(R.id.tvGoRegister).setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        googleButton?.setOnClickListener { startGoogleSignIn() }
    }

    private fun startGoogleSignIn() {
        val clientIdResource = resources.getIdentifier("default_web_client_id", "string", packageName)
        val clientId = if (clientIdResource != 0) getString(clientIdResource) else ""
        if (clientId.isBlank() || clientId.contains("YOUR_")) {
            showAuthError("Google Sign-In is not configured. Enable Google in Firebase Authentication, register your SHA-1/SHA-256 fingerprints, and download the updated google-services.json.")
            return
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(clientId)
            .build()
        setAuthBusy(true)
        googleSignInLauncher.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    private fun completeSignIn() {
        if (BiometricSessionManager.isAvailable(this) && !BiometricSessionManager.isEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.enable_biometric_title)
                .setMessage(R.string.enable_biometric_message)
                .setPositiveButton(R.string.enable) { _, _ ->
                    showBiometricEnrollmentPrompt()
                }
                .setNegativeButton(R.string.not_now) { _, _ -> goHome() }
                .setOnCancelListener { goHome() }
                .show()
        } else goHome()
    }

    private fun showBiometricEnrollmentPrompt() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                BiometricSessionManager.setEnabled(this@LoginActivity, true)
                Toast.makeText(this@LoginActivity, R.string.biometric_enabled, Toast.LENGTH_SHORT).show()
                goHome()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Toast.makeText(this@LoginActivity, R.string.biometric_not_enabled, Toast.LENGTH_LONG).show()
                goHome()
            }
        })
        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(getString(R.string.not_now))
        }
        prompt.authenticate(promptBuilder.build())
    }

    private fun routeExistingSession() {
        val destination = if (BiometricSessionManager.isEnabled(this)) BiometricUnlockActivity::class.java else HomeActivity::class.java
        startActivity(Intent(this, destination)); finish()
    }
    private fun setAuthBusy(busy: Boolean) {
        authProgress?.visibility = if (busy) ProgressBar.VISIBLE else ProgressBar.GONE
        loginButton?.isEnabled = !busy; googleButton?.isEnabled = !busy
    }
    private fun showAuthError(message: String) { authErrorView?.show(message); setAuthBusy(false) }
    private fun goHome() { startActivity(Intent(this, HomeActivity::class.java)); finishAffinity() }
}

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_register)
        val name=findViewById<EditText>(R.id.etFullName); val email=findViewById<EditText>(R.id.etEmail); val phone=findViewById<EditText>(R.id.etPhone)
        val password=findViewById<EditText>(R.id.etPassword); val confirm=findViewById<EditText>(R.id.etConfirmPassword)
        val terms=findViewById<CheckBox>(R.id.cbTerms); val error=findViewById<TextView>(R.id.tvError); val progress=findViewById<ProgressBar>(R.id.progressBar)
        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            when { !InputValidator.isNotBlank(name.text.toString()) -> error.show(getString(R.string.error_full_name_required))
                !InputValidator.isValidEmail(email.text.toString().trim()) -> error.show(getString(R.string.error_valid_email))
                !InputValidator.isValidPhoneNumber(phone.text.toString()) -> error.show(getString(R.string.error_valid_phone))
                !InputValidator.isStrongPassword(password.text.toString()) -> error.show(getString(R.string.error_strong_password))
                password.text.toString()!=confirm.text.toString() -> error.show(getString(R.string.error_passwords_mismatch))
                !terms.isChecked -> error.show(getString(R.string.error_accept_terms))
                else -> {
                    val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "en" }
                    progress.visible(true); lifecycleScope.launch { try { FirebaseRepository.register(name.text.toString().trim(),email.text.toString().trim(),phone.text.toString().trim(),password.text.toString(), currentLang); startActivity(Intent(this@RegisterActivity,HomeActivity::class.java)); finishAffinity() } catch(e:Exception){error.show(firebaseError(e))} finally{progress.visible(false)} }
                }
            }
        }
        findViewById<TextView>(R.id.tvGoLogin).setOnClickListener{finish()}
    }
}

class ForgotPasswordActivity: AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_forgot_password)
        val email=findViewById<EditText>(R.id.etEmail); val status=findViewById<TextView>(R.id.tvStatus)
        findViewById<Button>(R.id.btnReset).setOnClickListener{ if(!InputValidator.isValidEmail(email.text.toString().trim())){status.show(getString(R.string.error_valid_email));return@setOnClickListener}; lifecycleScope.launch{try{FirebaseRepository.sendPasswordReset(email.text.toString().trim());status.show(getString(R.string.password_reset_sent))}catch(e:Exception){status.show(firebaseError(e))}}}
        findViewById<TextView>(R.id.tvBackLogin).setOnClickListener{finish()}
    }
}

class HomeActivity: AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_home);loadProfile();updateSafetyScoreDisplay()
        findViewById<LinearLayout>(R.id.cardSafetyScore)?.setOnClickListener{startActivity(Intent(this,SafetyScoreActivity::class.java))}
        findViewById<Button>(R.id.btnSos).setOnClickListener{confirmSos()}
        findViewById<Button>(R.id.btnSos).setOnLongClickListener {
            // Discreet in-app trigger: only works while the dashboard is visible and touch is available.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(45)
            }
            triggerSos(discreet = true)
            true
        }
        findViewById<Button>(R.id.btnTrack).setOnClickListener{startActivity(Intent(this,TrackJourneyActivity::class.java))}
        findViewById<Button>(R.id.btnReport).setOnClickListener{startActivity(Intent(this,ReportIncidentActivity::class.java))}
        findViewById<Button>(R.id.btnContacts).setOnClickListener{startActivity(Intent(this,EmergencyContactsActivity::class.java))}
        findViewById<Button>(R.id.btnCommunity).setOnClickListener{startActivity(Intent(this,CommunityActivity::class.java))}
        findViewById<Button>(R.id.btnAlerts).setOnClickListener{startActivity(Intent(this,AlertsActivity::class.java))}
        findViewById<Button>(R.id.btnSharedJourneys).setOnClickListener{startActivity(Intent(this,SharedJourneysActivity::class.java))}
        findViewById<Button>(R.id.btnSafetyScore).setOnClickListener{startActivity(Intent(this,SafetyScoreActivity::class.java))}
        findViewById<Button>(R.id.btnAiAssistant).setOnClickListener{startActivity(Intent(this,AiAssistantActivity::class.java))}
        findViewById<Button>(R.id.btnSettings).setOnClickListener{startActivity(Intent(this,SettingsActivity::class.java))}
        findViewById<ImageView>(R.id.ivProfileIcon).setOnClickListener{startActivity(Intent(this,SettingsActivity::class.java))}
    }
    override fun onResume() {
        super.onResume()
        updateSafetyScoreDisplay()
    }
    private fun updateSafetyScoreDisplay() {
        val cachedScore = SafetyScoreManager.getScore(this)
        val cachedLevel = SafetyScoreManager.getLevel(this)
        val hasAssessment = SafetyScoreManager.hasAssessment(this)
        findViewById<TextView>(R.id.tvSafetyScore)?.text = "$cachedScore/100"
        findViewById<TextView>(R.id.tvSafetyLevel)?.text = if (hasAssessment) "Result: $cachedLevel" else cachedLevel
        lifecycleScope.launch {
            try {
                val assessment = FirebaseRepository.getLatestSafetyAssessment()
                if (assessment != null) {
                    val (score, level) = assessment
                    SafetyScoreManager.saveScore(this@HomeActivity, score, level)
                    findViewById<TextView>(R.id.tvSafetyScore)?.text = "$score/100"
                    findViewById<TextView>(R.id.tvSafetyLevel)?.text = "Result: $level"
                }
            } catch (_: Exception) {}
        }
    }
    private fun loadProfile(){lifecycleScope.launch{try{val p=FirebaseRepository.getProfile(); p?.let { profile ->
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != profile.preferredLanguage) LanguageHelper.applyLocale(profile.preferredLanguage)
        if (profile.profilePhotoUrl.isNotBlank()) Glide.with(this@HomeActivity).load(profile.profilePhotoUrl).placeholder(R.drawable.ic_user_placeholder).into(findViewById(R.id.ivProfileIcon))
    }; findViewById<TextView>(R.id.tvGreeting).text=if(!p?.fullName.isNullOrBlank()) getString(R.string.welcome_user_format, p?.fullName) else getString(R.string.greeting_default); updateSafetyScoreDisplay()}catch(_:Exception){}}}
    private fun confirmSos(){AlertDialog.Builder(this).setTitle(R.string.sos_confirm_title).setMessage(R.string.sos_confirm_message).setPositiveButton(R.string.btn_activate){_,_->triggerSos()}.setNegativeButton(R.string.btn_cancel,null).show()}
    private fun triggerSos(discreet: Boolean = false) {
        lifecycleScope.launch {
            try {
                val hasLocationPermission = ContextCompat.checkSelfPermission(this@HomeActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this@HomeActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val loc = if (hasLocationPermission) runCatching { LocationHelper.getCurrentLocation(this@HomeActivity) }.getOrNull() else null
                val uid = FirebaseRepository.currentUserId() ?: error("Please sign in first")
                val desc = if (discreet) "Discreet SOS activated from the SafeConnect dashboard." else "Emergency SOS activated by user."

                val initialIncident = Incident(
                    type = if (discreet) "silent_sos" else "sos",
                    category = "Emergency",
                    description = desc,
                    latitude = loc?.first,
                    longitude = loc?.second,
                    status = "SUBMITTED",
                    responderName = "",
                    responderStatus = "Awaiting responder acknowledgement",
                    notificationStatus = "NOT_DISPATCHED"
                )

                val id = OfflineIncidentRepository(this@HomeActivity).saveIncident(uid, initialIncident)

                val autoSmsEnabled = getSharedPreferences("safeconnect_sos", MODE_PRIVATE).getBoolean("auto_sms", false)
                if (autoSmsEnabled && ContextCompat.checkSelfPermission(this@HomeActivity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    runCatching {
                        val phones = FirebaseRepository.getEmergencyContacts().map { it.phone }.filter { it.isNotBlank() }
                        val sentCount = com.amogelang.safeconnect.app.safety.SosSmsSender.send(this@HomeActivity, id, phones, loc?.first, loc?.second)
                        Toast.makeText(this@HomeActivity, if (sentCount > 0) getString(R.string.sms_sent_submitted) else getString(R.string.sms_no_contacts), Toast.LENGTH_LONG).show()
                    }.onFailure { error -> Toast.makeText(this@HomeActivity, "Automatic SMS could not be sent: ${error.message}", Toast.LENGTH_LONG).show() }
                }

                if (!discreet) {
                    Toast.makeText(this@HomeActivity, "🚨 SOS submitted. SMS is sent automatically only when enabled in Settings.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@HomeActivity, SosActiveActivity::class.java).putExtra("incidentId", id))
                }
            } catch (e: Exception) {
                if (!discreet) Toast.makeText(this@HomeActivity, firebaseError(e), Toast.LENGTH_LONG).show()
            }
        }
    }
}

class SosActiveActivity : AppCompatActivity() {
    private var incidentListener: ListenerRegistration? = null
    private var smsListener: ListenerRegistration? = null
    private var incidentTrackingText: String = ""
    private var smsTrackingText: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_active)
        val id = intent.getStringExtra("incidentId")
        val tvStatus = findViewById<TextView>(R.id.tvIncidentStatus)
        val tvResponder = findViewById<TextView>(R.id.tvResponder)
        val tvTracking = findViewById<TextView>(R.id.tvTracking)
        val btnNotifySms = findViewById<Button>(R.id.btnNotifySms)

        if (id != null) {
            incidentListener = FirebaseRepository.observeIncident(id, { incident ->
                runOnUiThread {
                    val inc = incident ?: Incident(
                        id = id,
                        userId = "",
                        status = "SUBMITTED",
                        responderName = "",
                        responderStatus = "Awaiting responder acknowledgement",
                        notificationStatus = "NOT_DISPATCHED"
                    )
                    tvStatus.text = "Incident status: ${if (inc.status.isBlank() || inc.status == "Active") "SUBMITTED" else inc.status}"
                    tvResponder.text = "Responder: ${if (inc.responderName.isBlank()) "Awaiting responder acknowledgement" else inc.responderName}"
                    val notificationInfo = when (inc.notificationStatus) {
                        "FCM_ACCEPTED" -> "Firebase accepted the alert for linked contact(s). Recipient delivery is not confirmed."
                        "FCM_NOT_ACCEPTED" -> "FCM did not accept the alert. Direct SMS is attempted only if enabled in Settings."
                        "NOT_DISPATCHED" -> "SOS saved. Notification dispatch has not yet been confirmed."
                        "DISPATCHED" -> "Notification dispatch request submitted; recipient delivery is not confirmed."
                        else -> "Notification status: ${inc.notificationStatus}"
                    }
                    val responderLoc = if (inc.responderLatitude != null && inc.responderLongitude != null)
                        "\nResponder GPS Location: ${inc.responderLatitude}, ${inc.responderLongitude}" else ""
                    val responderStat = if (inc.responderStatus.isNotBlank()) "\nStatus: ${inc.responderStatus}" else "\nStatus: Awaiting responder acknowledgement"

                    incidentTrackingText = "$notificationInfo$responderStat$responderLoc"
                    tvTracking.text = incidentTrackingText + smsTrackingText
                }
            }, { error ->
                runOnUiThread {
                    tvStatus.text = "Incident status: SUBMITTED"
                    tvResponder.text = "Responder: Awaiting acknowledgement"
                    incidentTrackingText = "✓ Emergency alert saved locally & queued. ${error.message ?: ""}"
                    tvTracking.text = incidentTrackingText + smsTrackingText
                }
            })
            smsListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("incidents").document(id).collection("smsDelivery")
                .addSnapshotListener { snapshot, _ ->
                    val statuses = snapshot?.documents.orEmpty().mapNotNull { doc -> doc.getString("status") }.distinct()
                    smsTrackingText = if (statuses.isEmpty()) "" else "\nSMS status: ${statuses.joinToString(", ") { it.replace('_', ' ') }}"
                    runOnUiThread { tvTracking.text = incidentTrackingText + smsTrackingText }
                }
        }

        btnNotifySms.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val contacts = FirebaseRepository.getEmergencyContacts()
                    val phones = contacts.map { it.phone }.filter { it.isNotBlank() }
                    val loc = if (ContextCompat.checkSelfPermission(this@SosActiveActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        LocationHelper.getCurrentLocation(this@SosActiveActivity) else null
                    val mapsLink = if (loc != null) "https://maps.google.com/?q=${loc.first},${loc.second}" else "Current Location"
                    val message = "🚨 EMERGENCY SOS ALERT! I have activated Emergency SOS on SafeConnect. My location: $mapsLink. Please check on me or send help immediately!"

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:${phones.joinToString(";")}")
                        putExtra("sms_body", message)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@SosActiveActivity, "Could not open SMS: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        findViewById<Button>(R.id.btnCancelSos).setOnClickListener {
            if (id != null) lifecycleScope.launch {
                try {
                    OfflineIncidentRepository(this@SosActiveActivity).cancelIncident(id)
                    Toast.makeText(this@SosActiveActivity, "SOS cancellation saved.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@SosActiveActivity, firebaseError(e), Toast.LENGTH_LONG).show()
                }
            }
            finish()
        }
    }

    override fun onDestroy() {
        incidentListener?.remove()
        smsListener?.remove()
        super.onDestroy()
    }
}

class ReportIncidentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_incident)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val category = findViewById<Spinner>(R.id.spinnerCategory)
        val description = findViewById<EditText>(R.id.etDescription)
        val status = findViewById<TextView>(R.id.tvStatus)
        val bannerPanel = findViewById<LinearLayout>(R.id.reportBannerPanel)
        val bannerRef = findViewById<TextView>(R.id.tvBannerRef)
        val bannerMessage = findViewById<TextView>(R.id.tvBannerMessage)

        category.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.incident_categories)
        )

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            val descText = description.text.toString().trim()
            if (descText.isBlank()) {
                description.error = "Please describe the incident"
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val loc = if (ContextCompat.checkSelfPermission(
                            this@ReportIncidentActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) LocationHelper.getCurrentLocation(this@ReportIncidentActivity) else null

                    val uid = FirebaseRepository.currentUserId() ?: error("Please sign in first")
                    val selectedCategory = category.selectedItem.toString()
                    val id = OfflineIncidentRepository(this@ReportIncidentActivity).saveIncident(
                        uid,
                        Incident(
                            type = "report",
                            category = selectedCategory,
                            description = descText,
                            latitude = loc?.first,
                            longitude = loc?.second,
                            status = "Submitted"
                        )
                    )

                    bannerRef.text = "Reference ID: #${id.take(8).uppercase()}"
                    bannerMessage.text = "Status: Submitted & Dispatched to Community Network\nYour report for '$selectedCategory' has been recorded and submitted to local community safety channels."
                    bannerPanel.visibility = android.view.View.VISIBLE
                    status.show("Incident report submitted successfully!")
                    description.setText("")
                    Toast.makeText(this@ReportIncidentActivity, "Incident Report Submitted Successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    status.show(firebaseError(e))
                }
            }
        }
    }
}

class EmergencyContactsActivity:AppCompatActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_emergency_contacts);findViewById<ImageButton>(R.id.btnBack).setOnClickListener{finish()};val list=findViewById<LinearLayout>(R.id.contactsContainer);val empty=findViewById<TextView>(R.id.tvEmpty);fun refresh(){lifecycleScope.launch{try{list.removeAllViews();val contacts=FirebaseRepository.getEmergencyContacts();empty.visibility=if(contacts.isEmpty())TextView.VISIBLE else TextView.GONE;contacts.forEach{c->val row=TextView(this@EmergencyContactsActivity);row.text="${c.name} • ${c.relationship}\n${c.phone}\n${if(c.contactUserId.isNullOrBlank()) "Phone contact only" else "SafeConnect account linked"}";row.textSize=16f;row.setPadding(18,18,18,18);row.setOnClickListener{startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:${c.phone}")))};row.setOnLongClickListener{AlertDialog.Builder(this@EmergencyContactsActivity).setTitle("Delete emergency contact?").setMessage("Remove ${c.name} from your emergency contacts?").setNegativeButton(R.string.btn_cancel,null).setPositiveButton("Delete"){_,_->lifecycleScope.launch{try{FirebaseRepository.deleteEmergencyContact(c.id);Toast.makeText(this@EmergencyContactsActivity,"Emergency contact deleted.",Toast.LENGTH_SHORT).show();refresh()}catch(e:Exception){Toast.makeText(this@EmergencyContactsActivity,firebaseError(e),Toast.LENGTH_LONG).show()}}}.show();true};list.addView(row)}}catch(e:Exception){empty.show(firebaseError(e))}}};findViewById<Button>(R.id.btnAddContact).setOnClickListener{val v=layoutInflater.inflate(R.layout.dialog_add_contact,null);val dialog=AlertDialog.Builder(this).setTitle(R.string.dialog_add_contact_title).setView(v).setNegativeButton(R.string.btn_cancel,null).setPositiveButton(R.string.btn_add,null).create();dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val n=v.findViewById<EditText>(R.id.etContactName).text.toString();val p=v.findViewById<EditText>(R.id.etContactPhone).text.toString();val r=v.findViewById<EditText>(R.id.etRelationship).text.toString();if(n.isBlank()||p.isBlank()){Toast.makeText(this@EmergencyContactsActivity,R.string.error_name_phone_required,Toast.LENGTH_SHORT).show();return@setOnClickListener};lifecycleScope.launch{try{val contactId=FirebaseRepository.addEmergencyContact(EmergencyContact(name=n,phone=p,relationship=r.ifBlank{getString(R.string.relationship_default)},contactUserId=v.findViewById<EditText>(R.id.etContactUserId).text.toString().trim().ifBlank { null }));runCatching { com.amogelang.safeconnect.app.repository.Phase5Repository.award("EMERGENCY_CONTACT_ADDED",contactId) };dialog.dismiss();refresh()}catch(e:Exception){Toast.makeText(this@EmergencyContactsActivity,firebaseError(e),Toast.LENGTH_LONG).show()}}}};dialog.show()};refresh()}}

class CommunityActivity : AppCompatActivity() {
    private val safetyTipsPool = listOf(
        Pair("Stay Aware in Public", "Keep your head up and stay aware of your surroundings. Avoid looking down at your phone while walking in quiet or unfamiliar areas."),
        Pair("Share Your Live Route", "Always share your trip destination and ETA with emergency contacts using SafeConnect Live Track before embarking on your journey."),
        Pair("Emergency SOS Shortcut", "Long-press the SOS button on your SafeConnect dashboard to trigger a discreet emergency alert without drawing attention."),
        Pair("Trust Your Instincts", "If a street, vehicle, or person makes you feel uneasy, cross the road or enter a well-lit public shop immediately."),
        Pair("Vehicle Security First", "Lock your vehicle doors as soon as you enter, and keep valuable items out of sight from windows while parked or driving."),
        Pair("Home Entry Readiness", "Have your house or gate keys in your hand before approaching your front entrance or vehicle."),
        Pair("Night Travel Precautions", "Stick to well-lit main roads and avoid shortcuts through unlit alleys, parks, or vacant lots at night."),
        Pair("Public Transport Caution", "Wait for buses or taxis in designated, brightly lit areas close to other commuters and official transport nodes."),
        Pair("Digital Location Privacy", "Do not post your real-time exact check-in locations on social media until after you have safely left the venue."),
        Pair("Emergency Contacts Check", "Ensure your emergency contacts list contains at least 2 active, reliable phone numbers linked for instant SOS alerts."),
        Pair("Community Alert Awareness", "Check community safety reports before visiting unfamiliar areas or travelling late at night."),
        Pair("Safety Score Maintenance", "Maintain your safety preferences and verify your contacts regularly to keep your safety score high.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val tipsContainer = findViewById<LinearLayout>(R.id.tipsContainer)
        val btnRefresh = findViewById<Button>(R.id.btnRefreshTips)

        fun renderTips() {
            tipsContainer.removeAllViews()
            val selectedTips = safetyTipsPool.shuffled().take(3)
            selectedTips.forEachIndexed { index, tip ->
                val card = LinearLayout(this@CommunityActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(18, 18, 18, 18)
                    setBackgroundResource(R.drawable.bg_card)
                }
                val title = TextView(this@CommunityActivity).apply {
                    text = "💡 Tip #${index + 1}: ${tip.first}"
                    textSize = 17f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(getColor(R.color.text_ink))
                }
                val content = TextView(this@CommunityActivity).apply {
                    text = tip.second
                    textSize = 14f
                    setTextColor(getColor(R.color.text_muted))
                    setPadding(0, 6, 0, 0)
                }
                card.addView(title)
                card.addView(content)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 14 }
                tipsContainer.addView(card, params)
            }
        }

        renderTips()

        btnRefresh.setOnClickListener {
            renderTips()
            Toast.makeText(this, "Safety tips refreshed!", Toast.LENGTH_SHORT).show()
        }
    }
}
class AlertsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        val output = findViewById<TextView>(R.id.tvAlerts)
        val uid = FirebaseRepository.currentUserId()
        if (uid == null) { output.show(getString(R.string.error_no_account)); return }

        lifecycleScope.launch {
            val cloudIncidents = runCatching { FirebaseRepository.getMyIncidents() }.getOrDefault(emptyList())
            OfflineIncidentRepository(this@AlertsActivity).observeIncidents(uid).collect { localIncidents ->
                val localIds = localIncidents.map { it.id }.toSet()
                val cloudLines = cloudIncidents.filterNot { it.id in localIds }.map {
                    val sharedPrefix = if (it.userId != uid) "Shared alert from account ${it.userId.take(8)}…\n" else ""
                    val location = if (it.latitude != null && it.longitude != null) "\nLocation: ${it.latitude}, ${it.longitude}" else ""
                    "$sharedPrefix🚨 ${it.category.uppercase()} • ${it.status.ifBlank { "Submitted" }}\nDate: ${DateFormat.getDateTimeInstance().format(Date(it.createdAt))}$location"
                }
                val localLines = localIncidents.map {
                    val statusBadge = when (it.syncStatus) {
                        OfflineIncidentEntity.STATUS_PENDING -> "Saved on device • awaiting connection"
                        OfflineIncidentEntity.STATUS_SYNCING -> "Submitting to SafeConnect server"
                        OfflineIncidentEntity.STATUS_SYNCED -> "Submitted to SafeConnect server"
                        OfflineIncidentEntity.STATUS_FAILED -> "Submission failed • retry required"
                        else -> "Submitted"
                    }
                    "🚨 ${it.category.uppercase()} (${it.type.uppercase()})\nStatus: $statusBadge\nDate: ${DateFormat.getDateTimeInstance().format(Date(it.createdAt))}"
                }
                val lines = localLines + cloudLines
                output.text = if (lines.isEmpty()) getString(R.string.no_incidents) else lines.joinToString("\n\n─────────────────────────\n\n")
            }
        }
    }
}

class SettingsActivity : AppCompatActivity() {
    private val smsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val toggle = findViewById<Switch>(R.id.switchAutoSosSms)
        toggle.isChecked = granted
        getSharedPreferences("safeconnect_sos", MODE_PRIVATE).edit().putBoolean("auto_sms", granted).apply()
        if (!granted) Toast.makeText(this, R.string.sms_permission_required, Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_settings)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnEditProfile).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        findViewById<Button>(R.id.btnSafetyPreferences).setOnClickListener { startActivity(Intent(this, SafetyPreferencesActivity::class.java)) }
        val language = findViewById<Spinner>(R.id.spinnerLanguage)
        val theme = findViewById<Spinner>(R.id.spinnerTheme)
        theme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Light", "Dark"))
        val themePrefs = getSharedPreferences("safeconnect_appearance", MODE_PRIVATE)
        theme.setSelection(if (themePrefs.getBoolean("dark_mode", false)) 1 else 0)
        val notifications = findViewById<Switch>(R.id.switchNotifications)
        val autoSms = findViewById<Switch>(R.id.switchAutoSosSms)
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        autoSms.isChecked = getSharedPreferences("safeconnect_sos", MODE_PRIVATE).getBoolean("auto_sms", false) && smsGranted
        autoSms.setOnCheckedChangeListener { _, checked ->
            if (checked && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                autoSms.isChecked = false; smsPermission.launch(Manifest.permission.SEND_SMS)
            } else getSharedPreferences("safeconnect_sos", MODE_PRIVATE).edit().putBoolean("auto_sms", checked).apply()
        }
        val biometric = findViewById<Switch>(R.id.switchBiometric)
        biometric.isChecked = BiometricSessionManager.isEnabled(this)
        biometric.setOnCheckedChangeListener { _, checked ->
            if (!checked) BiometricSessionManager.setEnabled(this, false)
            else if (!BiometricSessionManager.isAvailable(this)) { biometric.isChecked = false; Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_LONG).show() }
            else { biometric.isChecked = false; Toast.makeText(this, R.string.biometric_enable_from_next_login, Toast.LENGTH_LONG).show() }
        }
        language.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.languages))
        lifecycleScope.launch {
            try { FirebaseRepository.getProfile()?.let { p ->
                findViewById<TextView>(R.id.tvProfileName).text = p.fullName
                findViewById<TextView>(R.id.tvSafeConnectId).text = "SafeConnect ID: ${p.safeConnectId}"
                findViewById<TextView>(R.id.tvProfileEmail).text = p.email
                findViewById<TextView>(R.id.tvProfilePhone).text = p.phoneNumber
                if (p.profilePhotoUrl.isNotBlank()) Glide.with(this@SettingsActivity).load(p.profilePhotoUrl).placeholder(R.drawable.ic_user_placeholder).into(findViewById(R.id.ivProfileLarge))
                notifications.isChecked = p.notificationsEnabled
                val langDisplayName = LanguageHelper.getDisplayName(p.preferredLanguage)
                language.setSelection(resources.getStringArray(R.array.languages).indexOf(langDisplayName).coerceAtLeast(0))
            } } catch (_: Exception) { }
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            lifecycleScope.launch { try {
                val old = FirebaseRepository.getProfile() ?: return@launch
                val newLangCode = LanguageHelper.getLanguageCode(language.selectedItem.toString())
                FirebaseRepository.updateProfile(old.copy(preferredLanguage = newLangCode, notificationsEnabled = notifications.isChecked))
                LanguageHelper.applyLocale(newLangCode)
                val dark = theme.selectedItemPosition == 1
                themePrefs.edit().putBoolean("dark_mode", dark).apply()
                AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@SettingsActivity, firebaseError(e), Toast.LENGTH_LONG).show() } }
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            FirebaseRepository.signOut(); GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            BiometricSessionManager.setEnabled(this, false); startActivity(Intent(this, LoginActivity::class.java)); finishAffinity()
        }
    }
}

class SafetyPreferencesActivity : AppCompatActivity() {
    private val store by lazy { UserSafetyPreferencesStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = store.read()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 28) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply { text = getString(R.string.customize_safety_preferences); textSize = 24f; setTypeface(null, android.graphics.Typeface.BOLD) })
        root.addView(TextView(this).apply { text = getString(R.string.safety_preferences_explanation); textSize = 14f; setPadding(0, 8, 0, 20) })
        root.addView(Button(this).apply { text = getString(R.string.back); setOnClickListener { finish() } })

        root.addView(TextView(this).apply { text = getString(R.string.nearby_alert_radius); textSize = 17f })
        val radiusValue = TextView(this).apply { text = getString(R.string.radius_km_format, current.nearbyAlertRadiusKm) }
        root.addView(radiusValue)
        val radius = SeekBar(this).apply { max = 49; progress = current.nearbyAlertRadiusKm - 1 }
        radius.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { radiusValue.text = getString(R.string.radius_km_format, progress + 1) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(radius)

        val autoShare = Switch(this).apply { text = getString(R.string.auto_share_journey); isChecked = current.autoShareJourneyWithContacts }
        root.addView(autoShare)

        root.addView(TextView(this).apply { text = getString(R.string.journey_check_in_interval); textSize = 17f; setPadding(0, 16, 0, 4) })
        val intervalValues = listOf(5, 10, 15, 30, 60, 120)
        val intervalSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SafetyPreferencesActivity, android.R.layout.simple_spinner_dropdown_item, intervalValues.map { getString(R.string.minutes_format, it) })
            setSelection(intervalValues.indexOf(current.journeyCheckInMinutes).takeIf { it >= 0 } ?: 2)
        }
        root.addView(intervalSpinner)

        root.addView(TextView(this).apply { text = getString(R.string.community_categories); textSize = 17f; setPadding(0, 16, 0, 4) })
        val categoryNames = listOf("Suspicious activity", "Theft", "Road incident", "Harassment", "Medical emergency", "Other")
        val categoryChecks = categoryNames.map { category ->
            CheckBox(this).apply { text = category; isChecked = category in current.showCommunityCategories; root.addView(this) }
        }

        val silentSos = Switch(this).apply { text = getString(R.string.discreet_sos_shortcut); isChecked = current.enableDiscreetSosShortcut }
        root.addView(silentSos)
        root.addView(TextView(this).apply { text = getString(R.string.discreet_sos_warning); textSize = 12f; setPadding(0, 4, 0, 12) })

        root.addView(Button(this).apply {
            text = getString(R.string.btn_save_settings)
            setOnClickListener {
                store.save(current.copy(
                    nearbyAlertRadiusKm = radius.progress + 1,
                    autoShareJourneyWithContacts = autoShare.isChecked,
                    journeyCheckInMinutes = intervalValues[intervalSpinner.selectedItemPosition],
                    showCommunityCategories = categoryChecks.filter { it.isChecked }.map { it.text.toString() }.toSet(),
                    enableDiscreetSosShortcut = silentSos.isChecked
                ))
                Toast.makeText(this@SafetyPreferencesActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        })
    }
}

private fun TextView.show(message:String){text=message;visibility=TextView.VISIBLE}
private fun ProgressBar.visible(value:Boolean){visibility=if(value)ProgressBar.VISIBLE else ProgressBar.GONE}
private fun AppCompatActivity.firebaseError(e:Exception):String=when(e){is FirebaseAuthInvalidUserException->getString(R.string.error_no_account);is FirebaseAuthInvalidCredentialsException->getString(R.string.error_invalid_credentials);is FirebaseNetworkException->getString(R.string.error_network);else->e.message?:getString(R.string.error_unknown)}

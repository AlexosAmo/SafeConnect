package com.amogelang.safeconnect.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.amogelang.safeconnect.app.firebase.FirebaseRepository
import com.amogelang.safeconnect.app.model.EmergencyContact
import com.amogelang.safeconnect.app.model.Incident
import com.amogelang.safeconnect.app.model.UserProfile
import com.amogelang.safeconnect.app.util.InputValidator
import com.amogelang.safeconnect.app.util.LanguageHelper
import com.amogelang.safeconnect.app.util.LocationHelper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser != null) { goHome(); return }
        setContentView(R.layout.activity_login)
        val email = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val error = findViewById<TextView>(R.id.tvError)
        val progress = findViewById<ProgressBar>(R.id.progressBar)
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            if (!InputValidator.isValidEmail(email.text.toString().trim()) || password.text.isNullOrBlank()) { error.show(getString(R.string.error_valid_email_password)); return@setOnClickListener }
            progress.visible(true)
            lifecycleScope.launch { try { FirebaseRepository.login(email.text.toString().trim(), password.text.toString()); goHome() } catch (e: Exception) { error.show(firebaseError(e)) } finally { progress.visible(false) } }
        }
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener { startActivity(Intent(this, ForgotPasswordActivity::class.java)) }
        findViewById<TextView>(R.id.tvGoRegister).setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        findViewById<Button>(R.id.btnGoogle).setOnClickListener { Toast.makeText(this, "Social login with Google initiated", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnFacebook).setOnClickListener { Toast.makeText(this, "Social login with Facebook initiated", Toast.LENGTH_SHORT).show() }
    }
    private fun goHome() { startActivity(Intent(this, HomeActivity::class.java)); finish() }
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
    private val locationCode=1001
    override fun onCreate(savedInstanceState: Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_home);loadProfile()
        findViewById<Button>(R.id.btnSos).setOnClickListener{confirmSos()}
        findViewById<Button>(R.id.btnTrack).setOnClickListener{startActivity(Intent(this,TrackJourneyActivity::class.java))}
        findViewById<Button>(R.id.btnReport).setOnClickListener{startActivity(Intent(this,ReportIncidentActivity::class.java))}
        findViewById<Button>(R.id.btnContacts).setOnClickListener{startActivity(Intent(this,EmergencyContactsActivity::class.java))}
        findViewById<Button>(R.id.btnCommunity).setOnClickListener{startActivity(Intent(this,CommunityActivity::class.java))}
        findViewById<Button>(R.id.btnAlerts).setOnClickListener{startActivity(Intent(this,AlertsActivity::class.java))}
        findViewById<Button>(R.id.btnSettings).setOnClickListener{startActivity(Intent(this,SettingsActivity::class.java))}
        findViewById<ImageView>(R.id.ivProfileIcon).setOnClickListener{startActivity(Intent(this,SettingsActivity::class.java))}
    }
    private fun loadProfile(){lifecycleScope.launch{try{val p=FirebaseRepository.getProfile(); p?.let { if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != it.preferredLanguage) LanguageHelper.applyLocale(it.preferredLanguage) }; findViewById<TextView>(R.id.tvGreeting).text=if(p?.fullName!=null) getString(R.string.welcome_user_format, p.fullName) else getString(R.string.greeting_default);findViewById<TextView>(R.id.tvSafetyScore).text="${p?.safetyScore?:100}/100"}catch(_:Exception){}}}
    private fun confirmSos(){AlertDialog.Builder(this).setTitle(R.string.sos_confirm_title).setMessage(R.string.sos_confirm_message).setPositiveButton(R.string.btn_activate){_,_->triggerSos()}.setNegativeButton(R.string.btn_cancel,null).show()}
    private fun triggerSos(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),locationCode);return}; lifecycleScope.launch{try{val loc=LocationHelper.getCurrentLocation(this@HomeActivity); val contacts = FirebaseRepository.getEmergencyContacts(); val desc = if (contacts.isNotEmpty()) "SOS activated. Notifying contacts: ${contacts.joinToString { it.name }}" else "SOS activated"; val id=FirebaseRepository.createIncident(Incident(type="sos",category="Emergency",description=desc,latitude=loc?.first,longitude=loc?.second)); Toast.makeText(this@HomeActivity, getString(R.string.sos_contacts_notified, contacts.size), Toast.LENGTH_LONG).show(); startActivity(Intent(this@HomeActivity,SosActiveActivity::class.java).putExtra("incidentId",id))}catch(e:Exception){Toast.makeText(this@HomeActivity,firebaseError(e),Toast.LENGTH_LONG).show()}}}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==locationCode&&grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)triggerSos()}
}

class SosActiveActivity: AppCompatActivity(){
    private var incidentListener: ListenerRegistration? = null
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_active)
        val id=intent.getStringExtra("incidentId")
        val tvStatus=findViewById<TextView>(R.id.tvIncidentStatus)
        val tvResponder=findViewById<TextView>(R.id.tvResponder)
        val tvTracking=findViewById<TextView>(R.id.tvTracking)
        if(id!=null){
            incidentListener=FirebaseRepository.observeIncident(id, { incident ->
                runOnUiThread {
                    if(incident == null) return@runOnUiThread
                    tvStatus.text="Incident status: ${incident.status}"
                    tvResponder.text=if(incident.responderName.isBlank()) "Responder: Awaiting assignment" else "Responder: ${incident.responderName}"
                    tvTracking.text=when {
                        incident.responderLatitude != null && incident.responderLongitude != null -> "Responder location received\n${incident.responderLatitude}, ${incident.responderLongitude}"
                        incident.responderStatus.isNotBlank() -> "Responder status: ${incident.responderStatus}"
                        else -> "Waiting for responder updates from Firebase."
                    }
                }
            }, { error ->
                runOnUiThread { tvTracking.text="Live tracking unavailable: ${error.message ?: "Firebase error"}" }
            })
        }
        findViewById<Button>(R.id.btnCancelSos).setOnClickListener{
            if(id!=null) lifecycleScope.launch{try{FirebaseRepository.updateIncidentStatus(id,"Cancelled")}catch(e:Exception){Toast.makeText(this@SosActiveActivity,firebaseError(e),Toast.LENGTH_LONG).show()} }
            finish()
        }
    }
    override fun onDestroy(){ incidentListener?.remove(); super.onDestroy() }
}

class ReportIncidentActivity:AppCompatActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_report_incident);findViewById<ImageButton>(R.id.btnBack).setOnClickListener{finish()};val category=findViewById<Spinner>(R.id.spinnerCategory);val description=findViewById<EditText>(R.id.etDescription);val status=findViewById<TextView>(R.id.tvStatus);category.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.incident_categories));findViewById<Button>(R.id.btnSubmit).setOnClickListener{lifecycleScope.launch{try{val loc=if(ContextCompat.checkSelfPermission(this@ReportIncidentActivity,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)LocationHelper.getCurrentLocation(this@ReportIncidentActivity)else null;val id=FirebaseRepository.createIncident(Incident(type="report",category=category.selectedItem.toString(),description=description.text.toString(),latitude=loc?.first,longitude=loc?.second,status="Submitted"));status.show(getString(R.string.report_submitted, id.take(8)));description.setText("")}catch(e:Exception){status.show(firebaseError(e))}}}}}

class TrackJourneyActivity:AppCompatActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_track_journey);findViewById<ImageButton>(R.id.btnBack).setOnClickListener{finish()};findViewById<Button>(R.id.btnStartJourney).setOnClickListener{findViewById<TextView>(R.id.tvJourneyStatus).show(getString(R.string.journey_active_status));findViewById<Button>(R.id.btnStartJourney).text=getString(R.string.journey_active_btn)}}}

class EmergencyContactsActivity:AppCompatActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_emergency_contacts);findViewById<ImageButton>(R.id.btnBack).setOnClickListener{finish()};val list=findViewById<LinearLayout>(R.id.contactsContainer);val empty=findViewById<TextView>(R.id.tvEmpty);fun refresh(){lifecycleScope.launch{try{list.removeAllViews();val contacts=FirebaseRepository.getEmergencyContacts();empty.visibility=if(contacts.isEmpty())TextView.VISIBLE else TextView.GONE;contacts.forEach{c->val row=TextView(this@EmergencyContactsActivity);row.text="${c.name} • ${c.relationship}\n${c.phone}";row.textSize=16f;row.setPadding(18,18,18,18);row.setOnClickListener{startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:${c.phone}")))};list.addView(row)}}catch(e:Exception){empty.show(firebaseError(e))}}};findViewById<Button>(R.id.btnAddContact).setOnClickListener{val v=layoutInflater.inflate(R.layout.dialog_add_contact,null);val dialog=AlertDialog.Builder(this).setTitle(R.string.dialog_add_contact_title).setView(v).setNegativeButton(R.string.btn_cancel,null).setPositiveButton(R.string.btn_add,null).create();dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val n=v.findViewById<EditText>(R.id.etContactName).text.toString();val p=v.findViewById<EditText>(R.id.etContactPhone).text.toString();val r=v.findViewById<EditText>(R.id.etRelationship).text.toString();if(n.isBlank()||p.isBlank()){Toast.makeText(this@EmergencyContactsActivity,R.string.error_name_phone_required,Toast.LENGTH_SHORT).show();return@setOnClickListener};lifecycleScope.launch{try{FirebaseRepository.addEmergencyContact(EmergencyContact(name=n,phone=p,relationship=r.ifBlank{getString(R.string.relationship_default)}));dialog.dismiss();refresh()}catch(e:Exception){Toast.makeText(this@EmergencyContactsActivity,firebaseError(e),Toast.LENGTH_LONG).show()}}}};dialog.show()};refresh()}}

class CommunityActivity:AppCompatActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_community);findViewById<ImageButton>(R.id.btnBack).setOnClickListener{finish()}}}
class AlertsActivity:AppCompatActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_alerts);findViewById<ImageButton>(R.id.btnBack).setOnClickListener{finish()};lifecycleScope.launch{try{val incidents=FirebaseRepository.getMyIncidents();findViewById<TextView>(R.id.tvAlerts).text=if(incidents.isEmpty()) getString(R.string.no_incidents) else incidents.joinToString("\n\n"){"${it.category} • ${it.status}\n${DateFormat.getDateTimeInstance().format(Date(it.createdAt))}"} }catch(e:Exception){findViewById<TextView>(R.id.tvAlerts).show(firebaseError(e))}}}}

class SettingsActivity:AppCompatActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_settings);findViewById<ImageButton>(R.id.btnBack).setOnClickListener{finish()};val language=findViewById<Spinner>(R.id.spinnerLanguage);val notifications=findViewById<Switch>(R.id.switchNotifications);language.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,resources.getStringArray(R.array.languages));lifecycleScope.launch{try{FirebaseRepository.getProfile()?.let{p->
    findViewById<TextView>(R.id.tvProfileName).text = p.fullName
    findViewById<TextView>(R.id.tvProfileEmail).text = p.email
    findViewById<TextView>(R.id.tvProfilePhone).text = p.phoneNumber
    notifications.isChecked=p.notificationsEnabled
    val langDisplayName = LanguageHelper.getDisplayName(p.preferredLanguage)
    language.setSelection(resources.getStringArray(R.array.languages).indexOf(langDisplayName).coerceAtLeast(0))}}catch(_:Exception){}};findViewById<Button>(R.id.btnSave).setOnClickListener{lifecycleScope.launch{try{val old=FirebaseRepository.getProfile()?:return@launch; val newLangCode = LanguageHelper.getLanguageCode(language.selectedItem.toString()); FirebaseRepository.updateProfile(old.copy(preferredLanguage=newLangCode,notificationsEnabled=notifications.isChecked)); LanguageHelper.applyLocale(newLangCode); Toast.makeText(this@SettingsActivity,R.string.settings_saved,Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this@SettingsActivity,firebaseError(e),Toast.LENGTH_LONG).show()}}};findViewById<Button>(R.id.btnLogout).setOnClickListener{FirebaseRepository.signOut();startActivity(Intent(this,LoginActivity::class.java));finishAffinity()}}}

private fun TextView.show(message:String){text=message;visibility=TextView.VISIBLE}
private fun ProgressBar.visible(value:Boolean){visibility=if(value)ProgressBar.VISIBLE else ProgressBar.GONE}
private fun AppCompatActivity.firebaseError(e:Exception):String=when(e){is FirebaseAuthInvalidUserException->getString(R.string.error_no_account);is FirebaseAuthInvalidCredentialsException->getString(R.string.error_invalid_credentials);is FirebaseNetworkException->getString(R.string.error_network);else->e.message?:getString(R.string.error_unknown)}

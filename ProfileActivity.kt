package com.amogelang.safeconnect.app

import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amogelang.safeconnect.app.firebase.FirebaseRepository
import com.amogelang.safeconnect.app.model.UserProfile
import com.amogelang.safeconnect.app.util.InputValidator
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private var selectedPhoto: Uri? = null
    private var originalProfile: UserProfile? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { selectedPhoto = uri; findViewById<ImageView>(R.id.ivProfilePhoto).setImageURI(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val name = findViewById<EditText>(R.id.etProfileName)
        val email = findViewById<EditText>(R.id.etProfileEmail)
        val phone = findViewById<EditText>(R.id.etProfilePhone)
        val province = findViewById<Spinner>(R.id.spinnerProfileProvince)
        val status = findViewById<TextView>(R.id.tvProfileStatus)
        val photo = findViewById<ImageView>(R.id.ivProfilePhoto)
        val provinceOptions = listOf("Select province") + resources.getStringArray(R.array.south_african_provinces).drop(1)
        province.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, provinceOptions)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnChoosePhoto).setOnClickListener { photoPicker.launch("image/*") }

        lifecycleScope.launch {
            try {
                val profile = FirebaseRepository.getProfile() ?: error("Profile not found")
                originalProfile = profile
                name.setText(profile.fullName); email.setText(profile.email); phone.setText(profile.phoneNumber)
                findViewById<TextView>(R.id.tvSafeConnectId).text = "SafeConnect ID: ${profile.safeConnectId}"
                province.setSelection(if (profile.province.isBlank()) 0 else provinceOptions.indexOf(profile.province).coerceAtLeast(1))
                if (profile.profilePhotoUrl.isNotBlank()) Glide.with(this@ProfileActivity).load(profile.profilePhotoUrl).placeholder(R.drawable.ic_user_placeholder).into(photo)
            } catch (e: Exception) { status.text = e.message ?: "Could not load profile." }
        }

        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            val current = originalProfile ?: run { status.text = "Profile is still loading."; return@setOnClickListener }
            val newName = name.text.toString().trim(); val newEmail = email.text.toString().trim(); val newPhone = phone.text.toString().trim()
            if (newName.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() || !InputValidator.isValidPhoneNumber(newPhone)) {
                status.text = "Enter a valid name, email address and phone number."; return@setOnClickListener
            }
            lifecycleScope.launch {
                val button = findViewById<Button>(R.id.btnSaveProfile); button.isEnabled = false
                try {
                    var photoUrl = current.profilePhotoUrl
                    selectedPhoto?.let { photoUrl = FirebaseRepository.uploadProfilePhoto(it) }
                    if (newEmail != current.email) FirebaseRepository.updateAccountEmail(newEmail)
                    val updated = current.copy(fullName = newName, email = newEmail, phoneNumber = newPhone, province = province.selectedItem.toString().takeUnless { it == "Select province" }.orEmpty(), profilePhotoUrl = photoUrl)
                    FirebaseRepository.updateProfile(updated)
                    originalProfile = updated
                    status.setText(R.string.profile_saved)
                    findViewById<TextView>(R.id.tvSafeConnectId).text = "SafeConnect ID: ${updated.safeConnectId}"
                    Toast.makeText(this@ProfileActivity, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    status.text = if (e.javaClass.simpleName.contains("RecentLogin")) getString(R.string.email_recent_login) else (e.message ?: getString(R.string.profile_photo_error))
                } finally { button.isEnabled = true }
            }
        }
    }
}

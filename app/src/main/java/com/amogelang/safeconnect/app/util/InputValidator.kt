package com.amogelang.safeconnect.app.util

import android.util.Patterns

object InputValidator {
    fun isValidEmail(value: String) = Patterns.EMAIL_ADDRESS.matcher(value).matches()
    fun isNotBlank(value: String) = value.isNotBlank()
    fun isValidPhoneNumber(value: String) = value.replace(" ", "").length >= 9
    fun isStrongPassword(value: String) = value.length >= 8 && value.any(Char::isLetter) && value.any(Char::isDigit)
    fun passwordsMatch(a: String, b: String) = a == b
}

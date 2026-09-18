package com.vibe.browser

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText

object IncognitoKeyboardHelper {
    fun apply(editText: EditText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            editText.imeOptions = editText.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        
        val existingPrivate = editText.privateImeOptions ?: ""
        val incognitoFlags = "nm,incognito=true,org.chromium.chrome.browser.incognito=true"
        editText.privateImeOptions = if (existingPrivate.isEmpty()) {
            incognitoFlags
        } else {
            "$existingPrivate,$incognitoFlags"
        }

        editText.inputType = editText.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
}

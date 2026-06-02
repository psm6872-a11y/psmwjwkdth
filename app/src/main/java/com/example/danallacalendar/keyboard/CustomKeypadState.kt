package com.example.danallacalendar.keyboard

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class KeypadActiveField {
    NONE,
    LOCATION_1B, // 동/호수 1 (위치 1 하단)
    LOCATION_2B, // 동/호수 2 (위치 2 하단)
    PHONE       // 전화번호
}

class CustomKeypadState(
    context: Context
) {
    var activeField by mutableStateOf(KeypadActiveField.NONE)
    var activePhoneIndex by mutableStateOf(-1)
    
    // 현재 입력창 값 및 업데이트 콜백
    var currentValue by mutableStateOf("")
    private var onValueChangeCallback: ((String) -> Unit)? = null

    // "가" 버튼을 눌러 시스템 키보드로 명시적 전환했는지 여부
    var forceSystemKeyboard by mutableStateOf(false)

    private val prefs = context.getSharedPreferences("custom_keypad_prefs", Context.MODE_PRIVATE)

    // 저장된 키보드 높이 (기본값 280.dp)
    var savedKeyboardHeightDp by mutableStateOf(
        prefs.getFloat("keyboard_height_dp", 280f).dp
    )

    fun updateKeyboardHeight(height: Dp) {
        if (height > 0.dp && height != savedKeyboardHeightDp) {
            savedKeyboardHeightDp = height
            prefs.edit().putFloat("keyboard_height_dp", height.value).apply()
        }
    }

    fun focusField(
        field: KeypadActiveField, 
        phoneIndex: Int = -1, 
        value: String, 
        onValueChange: (String) -> Unit
    ) {
        // 다른 필드로 포커스가 이동하면 시스템 키보드 강제 모드는 리셋
        if (this.activeField != field || (field == KeypadActiveField.PHONE && this.activePhoneIndex != phoneIndex)) {
            this.forceSystemKeyboard = false
        }
        this.activeField = field
        this.activePhoneIndex = phoneIndex
        this.currentValue = value
        this.onValueChangeCallback = onValueChange
    }

    fun isCustomKeypadVisible(): Boolean {
        return activeField != KeypadActiveField.NONE && !forceSystemKeyboard
    }

    fun hideKeypad() {
        activeField = KeypadActiveField.NONE
        activePhoneIndex = -1
        onValueChangeCallback = null
    }

    fun handleKeyPress(key: String) {
        when (key) {
            "BACKSPACE" -> {
                if (currentValue.isNotEmpty()) {
                    val updated = currentValue.dropLast(1)
                    currentValue = updated
                    onValueChangeCallback?.invoke(updated)
                }
            }
            "ENTER" -> {
                val updated = currentValue + "\n"
                currentValue = updated
                onValueChangeCallback?.invoke(updated)
            }
            "SPACE" -> {
                val updated = currentValue + " "
                currentValue = updated
                onValueChangeCallback?.invoke(updated)
            }
            else -> {
                val updated = currentValue + key
                currentValue = updated
                onValueChangeCallback?.invoke(updated)
            }
        }
    }
}

@Composable
fun rememberCustomKeypadState(context: Context = androidx.compose.ui.platform.LocalContext.current): CustomKeypadState {
    return remember { CustomKeypadState(context) }
}

package com.example.danallacalendar.keyboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

fun hideSystemKeyboard(context: Context) {
    val activity = context as? android.app.Activity ?: return
    val window = activity.window
    WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
}

fun showSystemKeyboard(context: Context) {
    val activity = context as? android.app.Activity ?: return
    val window = activity.window
    WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.ime())
}

@Composable
fun CustomKeypad(
    state: CustomKeypadState,
    keyboardHeight: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 버튼 높이 계산 (4행 기준, 행간 간격 5.dp씩 총 5개 Spacing 고려)
    val totalSpacing = 5.dp * 5
    val buttonHeight = (keyboardHeight - totalSpacing) / 4

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFF0F0F0),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1행: [1] [2] [3] [⌫]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            KeypadButton("1", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("1") }
            KeypadButton("2", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("2") }
            KeypadButton("3", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("3") }
            KeypadButton("⌫", buttonHeight, Color(0xFFD0D0D0), Color.Black, Modifier.weight(1f)) { state.handleKeyPress("BACKSPACE") }
        }

        // 2행: [4] [5] [6] [↵]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            KeypadButton("4", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("4") }
            KeypadButton("5", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("5") }
            KeypadButton("6", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("6") }
            KeypadButton("↵", buttonHeight, Color(0xFFD0D0D0), Color.Black, Modifier.weight(1f)) { state.handleKeyPress("ENTER") }
        }

        // 3행: [7] [8] [9] [,]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            KeypadButton("7", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("7") }
            KeypadButton("8", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("8") }
            KeypadButton("9", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("9") }
            KeypadButton(",", buttonHeight, Color(0xFFD0D0D0), Color.Black, Modifier.weight(1f)) { state.handleKeyPress(",") }
        }

        // 4행: [/] [가] [0] [␣] [완료]
        // [/] 와 [가] 는 합쳐서 1칸 (weight 0.5f 씩 총 1f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [/] 버튼
            KeypadButton("/", buttonHeight, Color(0xFF4A90E2), Color.White, Modifier.weight(0.5f)) { state.handleKeyPress("/") }
            
            // [가] 버튼
            KeypadButton("가", buttonHeight, Color(0xFFD0D0D0), Color.Black, Modifier.weight(0.5f)) {
                state.forceSystemKeyboard = true
                showSystemKeyboard(context)
            }
            
            // [0] 버튼
            KeypadButton("0", buttonHeight, Color.White, Color.Black, Modifier.weight(1f)) { state.handleKeyPress("0") }
            
            // [␣] 버튼
            KeypadButton("␣", buttonHeight, Color(0xFFD0D0D0), Color.Black, Modifier.weight(1f)) { state.handleKeyPress("SPACE") }
            
            // [완료] 버튼
            KeypadButton("완료", buttonHeight, Color(0xFF34C759), Color.White, Modifier.weight(1f)) {
                state.hideKeypad()
                focusManager.clearFocus()
                hideSystemKeyboard(context)
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    height: Dp,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

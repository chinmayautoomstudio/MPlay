package com.autoomstudio.mp3studio.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.autoomstudio.mp3studio.ui.theme.NeonBrush
import com.autoomstudio.mp3studio.ui.theme.WordmarkStyle

@Composable
fun MPlayWordmark(
    modifier: Modifier = Modifier,
    style: TextStyle = WordmarkStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val text = remember {
        buildAnnotatedString {
            withStyle(SpanStyle(brush = NeonBrush)) { append("MP3") }
            append(" Studio")
        }
    }
    Text(text = text, style = style, color = color, modifier = modifier)
}

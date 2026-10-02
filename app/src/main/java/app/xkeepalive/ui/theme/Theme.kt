package app.xkeepalive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF05070D)
val Panel = Color(0xFF0C1422)
val PanelHigh = Color(0xFF132033)
val Accent = Color(0xFF2EC5FF)
val Mist = Color(0xFF8EA4C0)
val Paper = Color(0xFFE7F2FF)
val Line = Color(0xFF1E3A55)
val Danger = Color(0xFFFF5D73)
val Warning = Color(0xFFF0B45A)
val Ok = Color(0xFF3EE0A0)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF041018),
    secondary = Color(0xFF7EB6FF),
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Mist,
    error = Danger,
    onError = Ink,
    outline = Line,
)

private val Type = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        letterSpacing = 0.6.sp,
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = Mist),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
)

@Composable
fun XBackgroundTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        content = content,
    )
}

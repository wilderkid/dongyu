package app.dongyu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Teal = Color(0xFF0FA3A3)
    val TealDeep = Color(0xFF0B7C8C)
    val Ocean = Color(0xFF1565C0)
    val Coral = Color(0xFFFF6B4A)
    val Sun = Color(0xFFFFB020)
    val Up = Color(0xFF19B36B)
    val Down = Color(0xFFE5484D)
}

@Immutable
data class ExtraColors(
    val card: Color,
    val cardAlt: Color,
    val subtle: Color,
    val grid: Color,
    val heat0: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Color.White, Color(0xFFF1F4F8), Color(0xFF8A94A6), Color(0x14000000), Color(0xFFE9EEF3), Brand.Teal, Brand.Ocean)
}

private val LightColors = lightColorScheme(
    primary = Brand.Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDF3EF),
    onPrimaryContainer = Color(0xFF00363A),
    secondary = Brand.Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1D8),
    tertiary = Brand.Ocean,
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF151A23),
    surface = Color(0xFFF4F6FA),
    onSurface = Color(0xFF151A23),
    surfaceVariant = Color(0xFFE7EBF1),
    onSurfaceVariant = Color(0xFF5B6475),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFD5DAE2),
    outlineVariant = Color(0xFFE5E9EF),
    error = Brand.Down,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3FD0C6),
    onPrimary = Color(0xFF00201F),
    primaryContainer = Color(0xFF0E4B4B),
    onPrimaryContainer = Color(0xFFBDF4EE),
    secondary = Color(0xFFFF8A6E),
    onSecondary = Color(0xFF3A0B00),
    secondaryContainer = Color(0xFF5A2416),
    tertiary = Color(0xFF7FB2FF),
    background = Color(0xFF0D1015),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF0D1015),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF1D232D),
    onSurfaceVariant = Color(0xFF9AA4B5),
    surfaceContainer = Color(0xFF161B22),
    surfaceContainerLow = Color(0xFF12161C),
    surfaceContainerHigh = Color(0xFF1B2129),
    outline = Color(0xFF2E3542),
    outlineVariant = Color(0xFF242A35),
    error = Color(0xFFFF6B6E),
)

private val LightExtra = ExtraColors(
    card = Color.White, cardAlt = Color(0xFFF1F4F8), subtle = Color(0xFF8A94A6), grid = Color(0x14000000),
    heat0 = Color(0xFFE9EEF3), heroStart = Color(0xFF12B5A6), heroEnd = Color(0xFF1E6FD9),
)
private val DarkExtra = ExtraColors(
    card = Color(0xFF161B22), cardAlt = Color(0xFF1D232D), subtle = Color(0xFF7D8799), grid = Color(0x1FFFFFFF),
    heat0 = Color(0xFF1F2630), heroStart = Color(0xFF0C7F78), heroEnd = Color(0xFF1B4C9C),
)

private val DongyuTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Big tabular numbers for metrics. */
object NumberStyle {
    val Huge = TextStyle(fontSize = 76.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", letterSpacing = (-2).sp, lineHeight = 80.sp)
    val Large = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", letterSpacing = (-0.5).sp)
    val Medium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val Small = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
}

private val DongyuShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun DongyuTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = DongyuTypography,
            shapes = DongyuShapes,
        ) {
            // Default text/icon colour for screens drawn outside a Surface/Scaffold.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}

val extraColors: ExtraColors @Composable get() = LocalExtraColors.current

package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

import androidx.compose.material3.ColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF25232A),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF313033),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

fun getLightColorSchemeForOption(option: String): ColorScheme {
    return when (option) {
        "Blue" -> lightColorScheme(
            primary = Color(0xFF00639B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFC7E6FF),
            onPrimaryContainer = Color(0xFF001D35),
            secondary = Color(0xFF516072),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD6E4F7),
            onSecondaryContainer = Color(0xFF0E1C2C),
            background = Color(0xFFF8F9FF),
            onBackground = Color(0xFF191C20),
            surface = Color(0xFFF8F9FF),
            onSurface = Color(0xFF191C20),
            surfaceVariant = Color(0xFFDEE3EB),
            onSurfaceVariant = Color(0xFF43474E),
            outline = Color(0xFF73777F)
        )
        "Green" -> lightColorScheme(
            primary = Color(0xFF006D2F),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFA6FFBD),
            onPrimaryContainer = Color(0xFF00210A),
            secondary = Color(0xFF4F6352),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD1E7D3),
            onSecondaryContainer = Color(0xFF0C1F12),
            background = Color(0xFFF7FBF3),
            onBackground = Color(0xFF191D19),
            surface = Color(0xFFF7FBF3),
            onSurface = Color(0xFF191D19),
            surfaceVariant = Color(0xFFDCE5DC),
            onSurfaceVariant = Color(0xFF414942),
            outline = Color(0xFF717972)
        )
        "Orange" -> lightColorScheme(
            primary = Color(0xFF904D00),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDCC0),
            onPrimaryContainer = Color(0xFF2E1400),
            secondary = Color(0xFF725A47),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFDE0C1),
            onSecondaryContainer = Color(0xFF291809),
            background = Color(0xFFFFF8F5),
            onBackground = Color(0xFF201A16),
            surface = Color(0xFFFFF8F5),
            onSurface = Color(0xFF201A16),
            surfaceVariant = Color(0xFFF2DFD1),
            onSurfaceVariant = Color(0xFF51443B),
            outline = Color(0xFF83746A)
        )
        "Teal" -> lightColorScheme(
            primary = Color(0xFF006A5A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF9DFFE7),
            onPrimaryContainer = Color(0xFF00201A),
            secondary = Color(0xFF4A635D),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCCE8E1),
            onSecondaryContainer = Color(0xFF05201A),
            background = Color(0xFFFAFDFB),
            onBackground = Color(0xFF191C1B),
            surface = Color(0xFFFAFDFB),
            onSurface = Color(0xFF191C1B),
            surfaceVariant = Color(0xFFDAE5E1),
            onSurfaceVariant = Color(0xFF3F4946),
            outline = Color(0xFF6F7976)
        )
        "Pink" -> lightColorScheme(
            primary = Color(0xFFA9364C),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD9DF),
            onPrimaryContainer = Color(0xFF3F0010),
            secondary = Color(0xFF77565A),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDADF),
            onSecondaryContainer = Color(0xFF2C1518),
            background = Color(0xFFFFF8F8),
            onBackground = Color(0xFF201A1B),
            surface = Color(0xFFFFF8F8),
            onSurface = Color(0xFF201A1B),
            surfaceVariant = Color(0xFFF4DDDF),
            onSurfaceVariant = Color(0xFF524344),
            outline = Color(0xFF857375)
        )
        "Grey" -> lightColorScheme(
            primary = Color(0xFF5D5E61),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE1E2E6),
            onPrimaryContainer = Color(0xFF1A1C1E),
            secondary = Color(0xFF5D5E61),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE1E2E6),
            onSecondaryContainer = Color(0xFF1A1C1E),
            background = Color(0xFFF9F9FC),
            onBackground = Color(0xFF1B1B1F),
            surface = Color(0xFFF9F9FC),
            onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFE1E2E6),
            onSurfaceVariant = Color(0xFF44474B),
            outline = Color(0xFF75777F)
        )
        else -> LightColorScheme // Purple (default)
    }
}

fun getDarkColorSchemeForOption(option: String): ColorScheme {
    return when (option) {
        "Blue" -> darkColorScheme(
            primary = Color(0xFF82CFFF),
            onPrimary = Color(0xFF003452),
            primaryContainer = Color(0xFF004B73),
            onPrimaryContainer = Color(0xFFC7E6FF),
            secondary = Color(0xFFBAC8DB),
            onSecondary = Color(0xFF243140),
            secondaryContainer = Color(0xFF3A4857),
            onSecondaryContainer = Color(0xFFD6E4F7),
            background = Color(0xFF1A1C1E),
            onBackground = Color(0xFFE2E2E6),
            surface = Color(0xFF222427),
            onSurface = Color(0xFFE2E2E6),
            surfaceVariant = Color(0xFF43474E),
            onSurfaceVariant = Color(0xFFC3C7CF),
            outline = Color(0xFF8D9199)
        )
        "Green" -> darkColorScheme(
            primary = Color(0xFF88F3A2),
            onPrimary = Color(0xFF003914),
            primaryContainer = Color(0xFF005322),
            onPrimaryContainer = Color(0xFFA6FFBD),
            secondary = Color(0xFFB4CBB7),
            onSecondary = Color(0xFF203526),
            secondaryContainer = Color(0xFF364C3B),
            onSecondaryContainer = Color(0xFFD0E7D2),
            background = Color(0xFF191C19),
            onBackground = Color(0xFFE1E3DE),
            surface = Color(0xFF212521),
            onSurface = Color(0xFFE1E3DE),
            surfaceVariant = Color(0xFF414942),
            onSurfaceVariant = Color(0xFFC1C9BF),
            outline = Color(0xFF8B938A)
        )
        "Orange" -> darkColorScheme(
            primary = Color(0xFFFFB77C),
            onPrimary = Color(0xFF4E2600),
            primaryContainer = Color(0xFF6F3B00),
            onPrimaryContainer = Color(0xFFFFDCC0),
            secondary = Color(0xFFE1BEA5),
            onSecondary = Color(0xFF412A19),
            secondaryContainer = Color(0xFF59402D),
            onSecondaryContainer = Color(0xFFFDE0C1),
            background = Color(0xFF201A16),
            onBackground = Color(0xFFECE0DA),
            surface = Color(0xFF29221D),
            onSurface = Color(0xFFECE0DA),
            surfaceVariant = Color(0xFF51443B),
            onSurfaceVariant = Color(0xFFD5C3B5),
            outline = Color(0xFF9E8E81)
        )
        "Teal" -> darkColorScheme(
            primary = Color(0xFF80F2DB),
            onPrimary = Color(0xFF00372E),
            primaryContainer = Color(0xFF005044),
            onPrimaryContainer = Color(0xFF9DFFE7),
            secondary = Color(0xFFB0CCC5),
            onSecondary = Color(0xFF1B3530),
            secondaryContainer = Color(0xFF324B46),
            onSecondaryContainer = Color(0xFFCCE8E1),
            background = Color(0xFF191C1B),
            onBackground = Color(0xFFE0E3E1),
            surface = Color(0xFF212523),
            onSurface = Color(0xFFE0E3E1),
            surfaceVariant = Color(0xFF3F4946),
            onSurfaceVariant = Color(0xFFBEC9C5),
            outline = Color(0xFF899390)
        )
        "Pink" -> darkColorScheme(
            primary = Color(0xFFFFAEBA),
            onPrimary = Color(0xFF530018),
            primaryContainer = Color(0xFF7E2A3C),
            onPrimaryContainer = Color(0xFFFFD9DF),
            secondary = Color(0xFFE4BDC1),
            onSecondary = Color(0xFF43292C),
            secondaryContainer = Color(0xFF5C3F42),
            onSecondaryContainer = Color(0xFFFFDADF),
            background = Color(0xFF201A1B),
            onBackground = Color(0xFFECE0E1),
            surface = Color(0xFF292223),
            onSurface = Color(0xFFECE0E1),
            surfaceVariant = Color(0xFF524344),
            onSurfaceVariant = Color(0xFFD6C2C4),
            outline = Color(0xFF9F8D8F)
        )
        "Grey" -> darkColorScheme(
            primary = Color(0xFFC7C6CA),
            onPrimary = Color(0xFF303033),
            primaryContainer = Color(0xFF47474A),
            onPrimaryContainer = Color(0xFFE3E2E6),
            secondary = Color(0xFF8F9099),
            onSecondary = Color(0xFF2A2A2E),
            secondaryContainer = Color(0xFF404145),
            onSecondaryContainer = Color(0xFFC4C6D0),
            background = Color(0xFF1B1B1F),
            onBackground = Color(0xFFE3E2E6),
            surface = Color(0xFF242428),
            onSurface = Color(0xFFE3E2E6),
            surfaceVariant = Color(0xFF44474B),
            onSurfaceVariant = Color(0xFFC4C6D0),
            outline = Color(0xFF8F9099)
        )
        else -> DarkColorScheme // Purple (default)
    }
}

@Composable
fun MyApplicationTheme(
  themeColorOption: String = "Purple",
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) {
      getDarkColorSchemeForOption(themeColorOption)
  } else {
      getLightColorSchemeForOption(themeColorOption)
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

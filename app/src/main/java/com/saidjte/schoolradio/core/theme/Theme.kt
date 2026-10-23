package com.saidjte.schoolradio.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ثيم التطبيق — Material 3 بألوان مدرسية (أخضر مزرق أساسي + أحمر تكميلي).
 * يدعم الوضع الفاتح والداكن وحساب النظام.
 */
private val TealPrimary = Color(0xFF00695C)
private val TealPrimaryDark = Color(0xFF004D40)
private val TealLight = Color(0xFF4DB6AC)
private val RedAccent = Color(0xFFC62828)
private val LightBg = Color(0xFFF6F9F8)
private val LightSurface = Color(0xFFFFFFFF)
private val DarkBg = Color(0xFF10201E)
private val DarkSurface = Color(0xFF16302C)

private val LightScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2F1),
    onPrimaryContainer = TealPrimaryDark,
    secondary = RedAccent,
    secondaryContainer = Color(0xFFFFEBEE),
    onSecondaryContainer = Color(0xFF7F1616),
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = Color(0xFFEDF4F2)
)

private val DarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00332C),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFEF9A9A),
    secondaryContainer = Color(0xFF5C1A1A),
    onSecondaryContainer = Color(0xFFFFDAD6),
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF1F3A36)
)

/** ثيم التطبيق: mode 0=حسب النظام، 1=فاتح، 2=داكن */
@Composable
fun SchoolRadioTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}

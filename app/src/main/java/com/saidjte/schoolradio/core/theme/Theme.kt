package com.saidjte.schoolradio.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saidjte.schoolradio.R

/**
 * نظام التصميم الكامل (v0.7.0 — إعادة صياغة عصرية أنيقة):
 *  - لوحة «زمرد مدرسي» أعمق وأرقى: primary فيروزي عميق + ذهبي دافئ كثانوي
 *  - أسطح صافية تطفو فوق خلفية نعناعية ناعمة (عمق بثلاث طبقات حاويات)
 *  - تدرجات رسمية (AppGradients) للبطاقة البطلة والبث الحي والأزرار الرئيسية
 *    تتكيّف مع الوضع الداكن عبر CompositionLocal
 *  - ألوان دلالية theme-aware للحالات (نجاح/تشغيل/فشل/تحذير/بث حي)
 *  - خط Tajawal موحّد بأرقام مغربية لاتينية
 *  - مقياس طباعة مضبوط للعربية + أشكال أكبر أنعم (14→32dp)
 */

// ===== البذرة اللونية — زمرد مدرسي =====
private val Teal10 = Color(0xFF00201B)
private val Teal20 = Color(0xFF07372F)
private val Teal30 = Color(0xFF0B4F44)
private val Teal40 = Color(0xFF0B6F60)   // primary فاتح — أعمق وأرتب من سابقه
private val Teal50 = Color(0xFF0E8A78)
private val Teal80 = Color(0xFF5CDAC4)   // primary داكن
private val Teal90 = Color(0xFF8BF3DE)
private val Teal95 = Color(0xFFC5F5E9)

private val TealContainer = Color(0xFFB8F0E2)      // primaryContainer فاتح
private val TealContainerDark = Color(0xFF0B4F44)

private val Red40 = Color(0xFFB3261E)
private val Red80 = Color(0xFFFFB4AB)
private val Red20 = Color(0xFF410002)
private val RedContainer = Color(0xFFFFDAD6)
private val RedContainerDark = Color(0xFF93000A)

private val Gold40 = Color(0xFF7A5900)   // secondary ذهبي دافئ
private val Gold80 = Color(0xFFF3BE49)
private val Gold90 = Color(0xFFFFE08D)
private val GoldContainer = Color(0xFFFFE3AE)
private val GoldContainerDark = Color(0xFF5A4300)

// ===== أسطح أنيقة — أبيض صافٍ يطفو فوق نعناعي ناعم =====
private val BgLight = Color(0xFFF3F8F5)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceDimLight = Color(0xFFEDF3EF)
private val SurfaceVariantLight = Color(0xFFDDE7E2)
private val OnSurfaceLight = Color(0xFF161D1B)

private val BgDark = Color(0xFF0C1512)
private val SurfaceDark = Color(0xFF111B18)
private val SurfaceVariantDark = Color(0xFF22302B)
private val OnSurfaceDark = Color(0xFFDEE6E2)

// ===== ألوان دلالية للحالات (تتكيّف مع الوضع الداكن) =====
@Immutable
data class AppStatusColors(
    val success: Color,      // مكتملة
    val running: Color,      // قيد التشغيل
    val failed: Color,       // فشلت / أخطاء
    val warning: Color,      // فائتة / تنبيه
    val live: Color,         // البث المباشر (أحمر معتم)
    val liveContainer: Color // خلفية شارات البث
)

private val LightStatusColors = AppStatusColors(
    success = Color(0xFF1E7B3C),
    running = Color(0xFF1A6AC4),
    failed = Color(0xFFB3261E),
    warning = Color(0xFF9A6A00),
    live = Color(0xFFD32F2F),
    liveContainer = Color(0xFFFFEBEE)
)

private val DarkStatusColors = AppStatusColors(
    success = Color(0xFF85D996),
    running = Color(0xFF9BC4FF),
    failed = Color(0xFFFFB4AB),
    warning = Color(0xFFFFD66E),
    live = Color(0xFFFF7069),
    liveContainer = Color(0xFF4A1515)
)

val LocalAppStatusColors = staticCompositionLocalOf { LightStatusColors }

/** وصول مختصر: MaterialTheme.status.success — داخل نطاق SchoolRadioTheme */
val MaterialTheme.status: AppStatusColors
    @Composable get() = LocalAppStatusColors.current

// ===== التدرجات الرسمية (v0.7.0) =====
@Immutable
data class AppGradients(
    val hero: List<Color>,        // البطاقة البطلة والوقت المميز
    val heroSoft: List<Color>,    // خلفية ناعمة للبطاقات الثانوية
    val live: List<Color>,        // البث المباشر
    val gold: List<Color>         // الأزرار واللمسات الذهبية
)

private val LightGradients = AppGradients(
    hero = listOf(Color(0xFF12A08C), Color(0xFF0B6F60), Color(0xFF0A4A46)),
    heroSoft = listOf(Color(0xFFDCF4EE), Color(0xFFC8EDE2)),
    live = listOf(Color(0xFFFF6459), Color(0xFFD32F2F)),
    gold = listOf(Color(0xFFFFC65C), Color(0xFFE8960C))
)

private val DarkGradients = AppGradients(
    hero = listOf(Color(0xFF116B5E), Color(0xFF0A4740), Color(0xFF072E2B)),
    heroSoft = listOf(Color(0xFF10352F), Color(0xFF0C2A26)),
    live = listOf(Color(0xFFB3423C), Color(0xFF7E2723)),
    gold = listOf(Color(0xFFA87A22), Color(0xFF7E5A10))
)

val LocalAppGradients = staticCompositionLocalOf { LightGradients }

/** وصول مختصر: MaterialTheme.gradients.hero — داخل نطاق SchoolRadioTheme */
val MaterialTheme.gradients: AppGradients
    @Composable get() = LocalAppGradients.current

/** تدرج قطري موحّد للاستخدام مع Brush.linearGradient */
fun List<Color>.diagonal(): Brush = Brush.linearGradient(this)

// ===== المخططات الكاملة =====
private val LightScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Teal20,
    inversePrimary = Teal80,
    secondary = Gold40,
    onSecondary = Color.White,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Teal30,
    onTertiary = Color.White,
    tertiaryContainer = Teal95,
    onTertiaryContainer = Teal10,
    background = BgLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceDim = SurfaceDimLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF404946),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6FAF7),
    surfaceContainer = Color(0xFFEFF5F1),
    surfaceContainerHigh = Color(0xFFE9F0EB),
    surfaceContainerHighest = Color(0xFFE3EBE6),
    surfaceTint = Teal40,
    inverseSurface = Color(0xFF2B302D),
    inverseOnSurface = Color(0xFFEDF2EF),
    error = Red40,
    onError = Color.White,
    errorContainer = RedContainer,
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFC2CCC7),
    scrim = Color.Black
)

private val DarkScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal10,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Teal95,
    inversePrimary = Teal40,
    secondary = Gold80,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = Gold90,
    tertiary = Teal90,
    onTertiary = Teal20,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal95,
    background = BgDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceDim = Color(0xFF0C1512),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFB7C2BD),
    surfaceContainerLowest = Color(0xFF0A1210),
    surfaceContainerLow = Color(0xFF161F1C),
    surfaceContainer = Color(0xFF1A2421),
    surfaceContainerHigh = Color(0xFF24302B),
    surfaceContainerHighest = Color(0xFF2F3B36),
    surfaceTint = Teal80,
    inverseSurface = Color(0xFFDEE6E2),
    inverseOnSurface = Color(0xFF2B302D),
    error = Red80,
    onError = Color(0xFF690005),
    errorContainer = RedContainerDark,
    onErrorContainer = RedContainer,
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3D4844),
    scrim = Color.Black
)

// ===== خط التطبيق الموحّد =====
// Tajawal — خط عربي حديث واضح (Boutros، رخصة OFL) بأرقام مغربية لاتينية 1234،
// يُطبّق على كل أنماط التطبيق بدل خط النظام الافتراضي.
val AppFontFamily = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold)
)

// ===== مقياس الطباعة المضبوط للعربية =====
// ارتفاعات أسطر أوسع من الافتراضي لتصقف الحروف العربية (ا ل ك) دون تقطيع،
// وأرقام جدولية (tnum) في الساعات والعدّادات حتى لا ترتعد مع كل ثانية.
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 34.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp, fontFamily = AppFontFamily
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
        fontFamily = AppFontFamily
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold,
        fontFamily = AppFontFamily
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
        fontFamily = AppFontFamily
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp, fontFamily = AppFontFamily
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
        fontFamily = AppFontFamily
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp, lineHeight = 28.sp, letterSpacing = 0.2.sp,
        fontFamily = AppFontFamily
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp,
        fontFamily = AppFontFamily
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, lineHeight = 19.sp, letterSpacing = 0.2.sp,
        fontFamily = AppFontFamily
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp, fontFamily = AppFontFamily
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium,
        fontFamily = AppFontFamily
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
        fontFamily = AppFontFamily
    )
)

// ===== نظام الأشكال الموحّد — أنعم وأكثر عصرية (v0.7.0) =====
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** ثيم التطبيق: mode 0=حسب النظام، 1=فاتح، 2=داكن */
@Composable
fun SchoolRadioTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(
        LocalAppStatusColors provides if (dark) DarkStatusColors else LightStatusColors,
        LocalAppGradients provides if (dark) DarkGradients else LightGradients
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

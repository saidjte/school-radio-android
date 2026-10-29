package com.saidjte.schoolradio.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * أيقونات Material مخصصة (مسارات SVG الرسمية لـ Material Icons — ترخيص Apache 2.0)
 * لتجنب اعتماد مكتبة material-icons-extended الثقيلة (توفر ~9MB من حجم APK).
 */
object AppIcons {

    private fun builder(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply(block).build()

    /** إيقاف مؤقت (شريطان) */
    val Pause: ImageVector by lazy {
        builder("Pause") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6f, 19f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineToRelative(14f)
                close()
                moveTo(14f, 5f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                close()
            }
        }
    }

    /** التالي (سهم + شريط) */
    val SkipNext: ImageVector by lazy {
        builder("SkipNext") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6f, 18f)
                lineToRelative(8.5f, -6f)
                lineTo(6f, 6f)
                verticalLineToRelative(12f)
                close()
                moveTo(16f, 6f)
                verticalLineToRelative(12f)
                horizontalLineToRelative(2f)
                verticalLineTo(6f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }

    /** السابق */
    val SkipPrevious: ImageVector by lazy {
        builder("SkipPrevious") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6f, 6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                close()
                moveTo(9.5f, 12f)
                lineToRelative(8.5f, 6f)
                verticalLineTo(6f)
                close()
            }
        }
    }

    /** تكرار */
    val Repeat: ImageVector by lazy {
        builder("Repeat") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7f, 7f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(3f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
                verticalLineToRelative(3f)
                horizontalLineTo(5f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                close()
                moveTo(17f, 17f)
                horizontalLineTo(7f)
                verticalLineToRelative(-3f)
                lineToRelative(-4f, 4f)
                lineToRelative(4f, 4f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(4f)
                close()
            }
        }
    }

    /** برق — طاقة/حماية الأتمتة (v0.13.0) */
    val Bolt: ImageVector by lazy {
        builder("Bolt") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // مسار Material flash_on (Apache 2.0): M7,2v11h3v9l7,-12h-4l4,-8z
                moveTo(7f, 2f)
                verticalLineTo(13f)
                horizontalLineTo(10f)
                verticalLineTo(22f)
                lineTo(17f, 10f)
                horizontalLineTo(13f)
                lineTo(17f, 2f)
                close()
            }
        }
    }

    /** منبه */
    val Alarm: ImageVector by lazy {
        builder("Alarm") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(22f, 5.72f)
                lineTo(17.4f, 1.86f)
                lineTo(16.11f, 3.39f)
                lineToRelative(4.6f, 3.86f)
                lineTo(22f, 5.72f)
                close()
                moveTo(7.88f, 3.39f)
                lineTo(6.6f, 1.86f)
                lineTo(2f, 5.71f)
                lineToRelative(1.29f, 1.53f)
                lineToRelative(4.59f, -3.85f)
                close()
                moveTo(12.5f, 8f)
                horizontalLineTo(11f)
                verticalLineToRelative(6f)
                lineToRelative(4.75f, 2.85f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4f, -2.37f)
                verticalLineTo(8f)
                close()
                moveTo(12f, 4f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.02f, 9f, 9f, 9f)
                curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
                reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
                reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                close()
            }
        }
    }

    /** ميكروفون */
    val Mic: ImageVector by lazy {
        builder("Mic") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 14f)
                curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
                reflectiveCurveTo(9f, 3.34f, 9f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                close()
                moveTo(17f, 11f)
                curveToRelative(0f, 2.76f, -2.24f, 5f, -5f, 5f)
                reflectiveCurveToRelative(-5f, -2.24f, -5f, -5f)
                horizontalLineTo(5f)
                curveToRelative(0f, 3.53f, 2.61f, 6.43f, 6f, 6.92f)
                verticalLineTo(21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-3.08f)
                curveToRelative(3.39f, -0.49f, 6f, -3.39f, 6f, -6.92f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }

    /** نقطة تسجيل (دائرة ممتلئة) — زر REC */
    val Record: ImageVector by lazy {
        builder("Record") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — FiberManualRecord (Apache 2.0)
                moveTo(12f, 6f)
                curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
                reflectiveCurveToRelative(2.69f, 6f, 6f, 6f)
                reflectiveCurveToRelative(6f, -2.69f, 6f, -6f)
                reflectiveCurveToRelative(-2.69f, -6f, -6f, -6f)
                close()
            }
        }
    }

    /** إيقاف (مربع) — إنهاء التسجيل */
    val Stop: ImageVector by lazy {
        builder("Stop") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Stop
                moveTo(6f, 6f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    /** موسيقى المكتبة — تبويب التسجيلات */
    val LibraryMusic: ImageVector by lazy {
        builder("LibraryMusic") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — LibraryMusic
                moveTo(20f, 2f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(18f, 7f)
                horizontalLineToRelative(-3f)
                verticalLineToRelative(5.5f)
                curveToRelative(0f, 1.38f, -1.12f, 2.5f, -2.5f, 2.5f)
                reflectiveCurveTo(10f, 13.88f, 10f, 12.5f)
                reflectiveCurveToRelative(1.12f, -2.5f, 2.5f, -2.5f)
                curveToRelative(0.57f, 0f, 1.08f, 0.19f, 1.5f, 0.51f)
                verticalLineTo(5f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                close()
                moveTo(4f, 6f)
                horizontalLineTo(2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
            }
        }
    }

    /** لوحة ألوان — قسم المظهر في الإعدادات */
    val Palette: ImageVector by lazy {
        builder("Palette") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Palette (Apache 2.0)
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
                curveToRelative(0f, -0.39f, -0.15f, -0.74f, -0.39f, -1.01f)
                curveToRelative(-0.23f, -0.26f, -0.38f, -0.61f, -0.38f, -0.99f)
                curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(16f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -4.42f, -4.03f, -8f, -9f, -8f)
                close()
                moveTo(6.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(5.67f, 9f, 6.5f, 9f)
                reflectiveCurveTo(8f, 9.67f, 8f, 10.5f)
                reflectiveCurveTo(7.33f, 12f, 6.5f, 12f)
                close()
                moveTo(9.5f, 8f)
                curveTo(8.67f, 8f, 8f, 7.33f, 8f, 6.5f)
                reflectiveCurveTo(8.67f, 5f, 9.5f, 5f)
                reflectiveCurveTo(11f, 5.67f, 11f, 6.5f)
                reflectiveCurveTo(10.33f, 8f, 9.5f, 8f)
                close()
                moveTo(14.5f, 8f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(13.67f, 5f, 14.5f, 5f)
                reflectiveCurveTo(16f, 5.67f, 16f, 6.5f)
                reflectiveCurveTo(15.33f, 8f, 14.5f, 8f)
                close()
                moveTo(17.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(16.67f, 9f, 17.5f, 9f)
                reflectiveCurveTo(19f, 9.67f, 19f, 10.5f)
                reflectiveCurveTo(18.33f, 12f, 17.5f, 12f)
                close()
            }
        }
    }

    /** مكبر صوت — قسم الصوت في الإعدادات */
    val VolumeUp: ImageVector by lazy {
        builder("VolumeUp") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — VolumeUp (Apache 2.0)
                moveTo(3f, 9f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(4f)
                lineToRelative(5f, 5f)
                verticalLineTo(4f)
                lineTo(7f, 9f)
                horizontalLineTo(3f)
                close()
                moveTo(16.5f, 12f)
                curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14f, 3.23f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
                reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
                verticalLineToRelative(2.06f)
                curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
                reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
                close()
            }
        }
    }

    /** ساعة — عدّاد الفقرة التالية وأقسام الجدولة */
    val ScheduleClock: ImageVector by lazy {
        builder("ScheduleClock") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Schedule (Apache 2.0)
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                close()
                moveTo(12.5f, 7f)
                horizontalLineTo(11f)
                verticalLineToRelative(6f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.5f, -2.67f)
                close()
            }
        }
    }

    /** رفع للسحابة — النسخ الاحتياطي */
    val CloudUpload: ImageVector by lazy {
        builder("CloudUpload") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Backup (Apache 2.0)
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
                horizontalLineToRelative(13f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
                moveTo(14f, 13f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(7f)
                lineToRelative(5f, -5f)
                lineToRelative(5f, 5f)
                horizontalLineToRelative(-3f)
                close()
            }
        }
    }

    /** معادل صوتي — بطاقة البث المباشر */
    val GraphicEq: ImageVector by lazy {
        builder("GraphicEq") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — GraphicEq (Apache 2.0)
                moveTo(7f, 18f)
                horizontalLineToRelative(2f)
                verticalLineTo(6f)
                horizontalLineTo(7f)
                verticalLineToRelative(12f)
                close()
                moveTo(11f, 22f)
                horizontalLineToRelative(2f)
                verticalLineTo(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(20f)
                close()
                moveTo(3f, 14f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4f)
                horizontalLineTo(3f)
                verticalLineToRelative(4f)
                close()
                moveTo(15f, 18f)
                horizontalLineToRelative(2f)
                verticalLineTo(6f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(12f)
                close()
                moveTo(19f, 10f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }

    /** نوتة موسيقية — صفوف المكتبة والتسجيلات */
    val MusicNote: ImageVector by lazy {
        builder("MusicNote") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — MusicNote (Apache 2.0)
                moveTo(12f, 3f)
                verticalLineToRelative(10.55f)
                curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2f, -0.55f)
                curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
                verticalLineTo(7f)
                horizontalLineToRelative(4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-6f)
                close()
            }
        }
    }

    /** قائمة موسيقية — بطاقات قوائم التشغيل */
    val QueueMusic: ImageVector by lazy {
        builder("QueueMusic") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — QueueMusic (Apache 2.0)
                moveTo(15f, 6f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(12f)
                verticalLineTo(6f)
                close()
                moveTo(15f, 10f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-2f)
                close()
                moveTo(3f, 16f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveTo(17f, 6f)
                verticalLineToRelative(8.18f)
                curveToRelative(-0.31f, -0.11f, -0.65f, -0.18f, -1f, -0.18f)
                curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
                reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
                reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
                verticalLineTo(8f)
                horizontalLineToRelative(3f)
                verticalLineTo(6f)
                horizontalLineToRelative(-5f)
                close()
            }
        }
    }

    /** تقويم يوم — بطاقة فقرات اليوم */
    val Event: ImageVector by lazy {
        builder("Event") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Event (Apache 2.0)
                moveTo(17f, 12f)
                horizontalLineToRelative(-5f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(-5f)
                close()
                moveTo(16f, 1f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                verticalLineTo(1f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(3f, 19f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-1f)
                verticalLineTo(1f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(19f, 19f)
                horizontalLineTo(5f)
                verticalLineTo(8f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(11f)
                close()
            }
        }
    }

    /** شخص — قسم المستخدمين والأدوار */
    val Person: ImageVector by lazy {
        builder("Person") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Person
                moveTo(12f, 12f)
                curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
                reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
                reflectiveCurveToRelative(-4f, 1.79f, -4f, 4f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                close()
                moveTo(12f, 14f)
                curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
                close()
            }
        }
    }

    /** علامة صح — الفقرات المكتملة */
    val CheckCircle: ImageVector by lazy {
        builder("CheckCircle") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — CheckCircle (Apache 2.0)
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveToRelative(-2f, 15f)
                lineToRelative(-5f, -5f)
                lineToRelative(1.41f, -1.41f)
                lineTo(10f, 14.17f)
                lineToRelative(7.59f, -7.59f)
                lineTo(19f, 8f)
                lineToRelative(-9f, 9f)
                close()
            }
        }
    }

    /** بلوتوث — مسار إخراج الصوت */
    val Bluetooth: ImageVector by lazy {
        builder("Bluetooth") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Bluetooth (Apache 2.0)
                moveTo(17.71f, 7.71f)
                lineTo(12f, 2f)
                horizontalLineToRelative(-1f)
                verticalLineToRelative(7.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(11f, 14.41f)
                verticalLineTo(22f)
                horizontalLineToRelative(1f)
                lineToRelative(5.71f, -5.71f)
                lineToRelative(-4.3f, -4.29f)
                lineToRelative(4.3f, -4.29f)
                close()
                moveTo(13f, 5.83f)
                lineToRelative(1.88f, 1.88f)
                lineTo(13f, 9.59f)
                verticalLineTo(5.83f)
                close()
                moveToRelative(1.88f, 10.46f)
                lineTo(13f, 18.17f)
                verticalLineToRelative(-3.76f)
                lineToRelative(1.88f, 1.88f)
                close()
            }
        }
    }

    /** سماعة سلكية — مسار إخراج الصوت */
    val Headphones: ImageVector by lazy {
        builder("Headphones") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Headphones (Apache 2.0)
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                verticalLineToRelative(7f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-8f)
                horizontalLineTo(5f)
                verticalLineToRelative(-1f)
                curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(4f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-7f)
                curveToRelative(0f, -4.97f, -4.03f, -9f, -9f, -9f)
                close()
            }
        }
    }

    /** تلقائي — المسار الافتراضي للوسائط */
    val Autorenew: ImageVector by lazy {
        builder("Autorenew") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Autorenew (Apache 2.0)
                moveTo(12f, 6f)
                verticalLineToRelative(3f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
                verticalLineToRelative(3f)
                curveToRelative(-4.42f, 0f, -8f, 3.58f, -8f, 8f)
                curveToRelative(0f, 1.57f, 0.46f, 3.03f, 1.24f, 4.26f)
                lineTo(6.7f, 14.8f)
                curveToRelative(-0.45f, -0.83f, -0.7f, -1.79f, -0.7f, -2.8f)
                curveToRelative(0f, -3.31f, 2.69f, -6f, 6f, -6f)
                close()
                moveToRelative(6.76f, 1.74f)
                lineTo(17.3f, 9.2f)
                curveToRelative(0.44f, 0.84f, 0.7f, 1.79f, 0.7f, 2.8f)
                curveToRelative(0f, 3.31f, -2.69f, 6f, -6f, 6f)
                verticalLineToRelative(-3f)
                lineToRelative(-4f, 4f)
                lineToRelative(4f, 4f)
                verticalLineToRelative(-3f)
                curveToRelative(4.42f, 0f, 8f, -3.58f, 8f, -8f)
                curveToRelative(0f, -1.57f, -0.46f, -3.03f, -1.24f, -4.26f)
                close()
            }
        }
    }

    /** شاشة عرض — فتح شاشة «على الهواء» (v0.5.0) */
    val Display: ImageVector by lazy {
        builder("Display") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                // Material Icons — Tv (Apache 2.0)
                moveTo(21f, 3f)
                horizontalLineTo(3f)
                curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f)
                verticalLineTo(17f)
                curveTo(1f, 18.1f, 1.9f, 19f, 3f, 19f)
                horizontalLineTo(8f)
                verticalLineTo(21f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                horizontalLineTo(21f)
                curveTo(22.1f, 19f, 23f, 18.1f, 23f, 17f)
                verticalLineTo(5f)
                curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f)
                close()
                moveTo(21f, 17f)
                horizontalLineTo(3f)
                verticalLineTo(5f)
                horizontalLineTo(21f)
                verticalLineTo(17f)
                close()
            }
        }
    }

    /** خلط الترتيب (v0.8.0) — مسار Material Icons الرسمي (Apache 2.0) */
    val Shuffle: ImageVector by lazy {
        builder("Shuffle") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(10.59f, 9.17f)
                lineTo(5.41f, 4f)
                lineTo(4f, 5.41f)
                lineToRelative(5.17f, 5.17f)
                lineToRelative(1.42f, -1.41f)
                close()
                moveTo(14.5f, 4f)
                lineToRelative(2.04f, 2.04f)
                lineTo(4f, 18.59f)
                lineTo(5.41f, 20f)
                lineTo(17.96f, 7.46f)
                lineTo(20f, 9.5f)
                verticalLineTo(4f)
                horizontalLineTo(14.5f)
                close()
                moveTo(13.42f, 12.83f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(3.13f, 3.13f)
                lineTo(14.5f, 18.5f)
                horizontalLineTo(20f)
                verticalLineTo(13f)
                lineToRelative(-2.04f, 2.04f)
                lineToRelative(-3.13f, -3.13f)
                close()
            }
        }
    }

    /**
     * أيقونة «ضبط/ميكسر» (Material Icons: tune — Apache 2.0):
     * صفوف منزلقات أفقية — رمز شاشة الاستوديو.
     */
    val Tune: ImageVector by lazy {
        builder("Tune") {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(3f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                close()
                moveTo(13f, 21f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                horizontalLineTo(13f)
                close()
                moveTo(7f, 9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                close()
                moveTo(21f, 13f)
                verticalLineToRelative(-2f)
                horizontalLineTo(11f)
                verticalLineToRelative(2f)
                horizontalLineTo(21f)
                close()
                moveTo(15f, 9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-2f)
                verticalLineTo(9f)
                close()
            }
        }
    }

    /**
     * أيقونة «سكربت/مستند نصي» (Material Icons: article — Apache 2.0):
     * ورقة بأسطر نص — رمز شاشة السكربتات.
     */
    val Article: ImageVector by lazy {
        builder("Article") {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(19f, 3f)
                horizontalLineTo(5f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                close()
                moveTo(14f, 17f)
                horizontalLineTo(7f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(7f)
                verticalLineTo(17f)
                close()
                moveTo(17f, 13f)
                horizontalLineTo(7f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(10f)
                verticalLineTo(13f)
                close()
                moveTo(17f, 9f)
                horizontalLineTo(7f)
                verticalLineTo(7f)
                horizontalLineToRelative(10f)
                verticalLineTo(9f)
                close()
            }
        }
    }

    /** مشاركة (عقدة بثقابت ثلاثية — Material share) — v0.14.0 مشاركة التسجيلات */
    val Share: ImageVector by lazy {
        builder("Share") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(18f, 16.08f)
                curveTo(17.24f, 16.08f, 16.56f, 16.38f, 16.04f, 16.85f)
                lineTo(8.91f, 12.7f)
                curveTo(8.96f, 12.47f, 9f, 12.24f, 9f, 12f)
                curveTo(9f, 11.76f, 8.96f, 11.53f, 8.91f, 11.3f)
                lineTo(15.96f, 7.19f)
                curveTo(16.5f, 7.69f, 17.21f, 8f, 18f, 8f)
                curveTo(19.66f, 8f, 21f, 6.66f, 21f, 5f)
                curveTo(21f, 3.34f, 19.66f, 2f, 18f, 2f)
                curveTo(16.34f, 2f, 15f, 3.34f, 15f, 5f)
                curveTo(15f, 5.24f, 15.04f, 5.47f, 15.09f, 5.7f)
                lineTo(8.04f, 9.81f)
                curveTo(7.5f, 9.31f, 6.79f, 9f, 6f, 9f)
                curveTo(4.34f, 9f, 3f, 10.34f, 3f, 12f)
                curveTo(3f, 13.66f, 4.34f, 15f, 6f, 15f)
                curveTo(6.79f, 15f, 7.5f, 14.69f, 8.04f, 14.19f)
                lineTo(15.16f, 18.35f)
                curveTo(15.11f, 18.56f, 15.08f, 18.78f, 15.08f, 19f)
                curveTo(15.08f, 20.61f, 16.39f, 21.92f, 18f, 21.92f)
                curveTo(19.61f, 21.92f, 20.92f, 20.61f, 20.92f, 19f)
                curveTo(20.92f, 17.39f, 19.61f, 16.08f, 18f, 16.08f)
                close()
            }
        }
    }

    /** تنزيل/تصدير (سهم لأسفل + قاعدة — Material download) — v0.14.0 تصدير التسجيلات */
    val Download: ImageVector by lazy {
        builder("Download") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(19f, 9f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                lineToRelative(7f, 7f)
                lineToRelative(7f, -7f)
                close()
                moveTo(5f, 18f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    /** قائمة خيارات عمودية (⋮ — Material more-vert) — v0.17.0 قوائم الأدوات الموحّدة */
    val MoreVert: ImageVector by lazy {
        builder("MoreVert") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                close()
                moveTo(12f, 10f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 16f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
            }
        }
    }

    /** مقص القص (Material content-cut) — v0.18.0 قص الفراغ الأول */
    val Cut: ImageVector by lazy {
        builder("Cut") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(9.64f, 7.64f)
                curveToRelative(0.23f, -0.5f, 0.36f, -1.05f, 0.36f, -1.64f)
                curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f)
                reflectiveCurveTo(2f, 3.79f, 2f, 6f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                curveToRelative(0.59f, 0f, 1.14f, -0.13f, 1.64f, -0.36f)
                lineTo(10f, 12f)
                lineToRelative(-2.36f, 2.36f)
                curveTo(7.14f, 14.13f, 6.59f, 14f, 6f, 14f)
                curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
                curveToRelative(0f, -0.59f, -0.13f, -1.14f, -0.36f, -1.64f)
                lineTo(12f, 14f)
                lineToRelative(7f, 7f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(-1f)
                lineTo(9.64f, 7.64f)
                close()
                moveTo(6f, 8f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                close()
                moveTo(12f, 10.5f)
                curveToRelative(-0.28f, 0f, -0.5f, 0.22f, -0.5f, 0.5f)
                reflectiveCurveToRelative(0.22f, 0.5f, 0.5f, 0.5f)
                reflectiveCurveToRelative(0.5f, -0.22f, 0.5f, -0.5f)
                reflectiveCurveToRelative(-0.22f, -0.5f, -0.5f, -0.5f)
                close()
                moveTo(19f, 3f)
                lineToRelative(-6f, 6f)
                lineToRelative(2f, 2f)
                lineToRelative(7f, -7f)
                verticalLineTo(3f)
                close()
            }
        }
    }

    /**
     * مُعلِن (مايكروفون إذاعي) — v0.49.0 لزر «إعلان بدء البث».
     * مسار Material Icons الرسمي "campaign" (Apache 2.0).
     */
    val Campaign: ImageVector by lazy {
        builder("Campaign") {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(18f, 11f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-2f)
                horizontalLineTo(18f)
                close()
                moveTo(16f, 17.61f)
                curveToRelative(0.96f, 0.71f, 2.21f, 1.65f, 3.2f, 2.39f)
                curveToRelative(0.4f, -0.53f, 0.8f, -1.07f, 1.2f, -1.6f)
                curveToRelative(-0.99f, -0.74f, -2.24f, -1.68f, -3.2f, -2.4f)
                curveTo(16.64f, 16.57f, 16.32f, 17.09f, 16f, 17.61f)
                close()
                moveTo(20.4f, 5.6f)
                curveTo(20f, 5.07f, 19.6f, 4.53f, 19.2f, 4f)
                curveToRelative(-0.99f, 0.74f, -2.24f, 1.68f, -3.2f, 2.4f)
                curveToRelative(0.32f, 0.53f, 0.64f, 1.05f, 0.96f, 1.58f)
                curveTo(17.92f, 7.26f, 19.43f, 6.34f, 20.4f, 5.6f)
                close()
                moveTo(8f, 9f)
                horizontalLineTo(2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(3f)
                lineToRelative(4f, 4f)
                verticalLineTo(9f)
                close()
                moveTo(14.5f, 12f)
                curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveTo(13.48f, 15.29f, 14.5f, 13.77f, 14.5f, 12f)
                close()
                moveTo(12f, 3.92f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.72f)
                reflectiveCurveToRelative(-2.11f, 5.86f, -5f, 6.72f)
                verticalLineToRelative(2.06f)
                curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.78f)
                reflectiveCurveTo(16.01f, 4.83f, 12f, 3.92f)
                close()
            }
        }
    }
}

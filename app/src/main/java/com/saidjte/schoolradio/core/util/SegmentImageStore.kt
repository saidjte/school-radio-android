package com.saidjte.schoolradio.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * مخزن صور الفقرات (v0.5.0) — نسخ مضغوطة داخل ملفات التطبيق الخاص.
 *
 * لماذا نسخة وليس URI الأصلي؟
 *  - صور الـ Photo Picker تمنح صلاحية مؤقتة قد تنتهي؛ النسخة الداخلية تبقى دائمة.
 *  - الضغط إلى حد أقصى 1440px بجودة JPEG 85 يمنع تضخم التخزين (فقرة ≈ 300KB).
 *  - لا حاجة لأي صلاحية تخزين: filesDir خاص بالتطبيق.
 */
object SegmentImageStore {

    private const val MAX_DIMENSION = 1440
    private const val JPEG_QUALITY = 85

    /** نسخ الصورة المختارة إلى filesDir/event_images مع تصغير — يُعيد المسار المطلق أو null */
    fun saveFrom(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver

        // 1) قراءة الأبعاد فقط لاختيار عامل التصغير
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }

        // 2) فك الترميز مصغراً ثم ضغطه JPEG
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val dir = File(context.filesDir, "event_images").apply { mkdirs() }
        val out = File(dir, "seg_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos) }
        bmp.recycle()
        out.absolutePath
    }.getOrNull()

    /** حذف ملف صورة فقرة (أفضل جهد — صمت عند أي فشل) */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}

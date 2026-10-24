package com.saidjte.schoolradio.feature.recordings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.saidjte.schoolradio.core.util.RecordingExportLogic
import com.saidjte.schoolradio.data.local.entity.RecordingEntity
import java.io.File

/**
 * v0.14.0 — مشاركة/تصدير التسجيلات.
 *
 * مساران منفصلان وفق نموذج أمان أندرويد:
 *  - المشاركة: FileProvider (content://) بمنح قراءة مؤقتة للتطبيق المستهدف فقط —
 *    واتساب/تيليغرام/البريد/مكبرات الجماعة المدرسية… بلا أي صلاحيات تخزين.
 *  - التصدير: نسخة دائمة في Downloads/الإذاعة المدرسية:
 *      • أندرويد 10+ عبر MediaStore.Downloads — بلا صلاحيات إطلاقاً.
 *      • أندرويد 9 وما قبله عبر المسار العام المباشر — يتطلب WRITE_EXTERNAL_STORAGE
 *        (مُعلنة بحد أقصى SDK 28 وتُطلب وقتياً من الشاشة قبل الاستدعاء).
 */
object RecordingShareHelper {

    /** نتائج التصدير — تعرض رسائلها الشاشة مباشرة عبر Snackbar */
    sealed interface ExportResult {
        /** نجح: [fileName] داخل مجلد التصدير */
        data class Done(val fileName: String) : ExportResult
        /** ملف المصدر حُذف أو لم يعد متاحاً */
        data object SourceMissing : ExportResult
        /** تعذّر إنشاء وجهة النسخ (ذاكرة ممتلئة/مرفوض) */
        data class Failed(val reason: String?) : ExportResult
    }

    private fun sourceFile(rec: RecordingEntity): File? =
        File(rec.filePath).takeIf { it.isFile && it.length() > 0 }

    // ================== المشاركة ==================

    /**
     * فتح لوحة المشاركة النظامية للتسجيل. ترجع رسالة خطأ عربية أو null عند النجاح.
     * لا تتطلب أي صلاحية — FileProvider يمنح قراءة مؤقتة للمستقبل وحده.
     */
    fun share(context: Context, rec: RecordingEntity): String? {
        val file = sourceFile(rec)
            ?: return "ملف التسجيل غير متاح — ربما حُذف من الذاكرة"

        val uri = try {
            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        } catch (_: IllegalArgumentException) {
            return "تعذر مشاركة هذا الملف (خارج مسارات المشاركة)"
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = rec.mimeType.ifBlank { "audio/mp4" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, rec.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "مشاركة «${rec.title}»").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            null
        } catch (e: Exception) {
            "لا يوجد تطبيق يستقبل المشاركة على هذا الجهاز"
        }
    }

    // ================== التصدير ==================

    /**
     * تصدير دائم إلى Downloads/الإذاعة المدرسية.
     * على أندرويد <10 يجب أن تكون WRITE_EXTERNAL_STORAGE ممنوحة مسبقاً
     * (الشاشة تتكفل بطلبها) وإلا يعود [ExportResult.Failed] بسبب الصلاحية.
     */
    fun export(context: Context, rec: RecordingEntity): ExportResult {
        val src = sourceFile(rec) ?: return ExportResult.SourceMissing
        val fileName = RecordingExportLogic.safeFileName(rec.title, rec.createdAt, rec.mimeType)

        return try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(context, src, fileName, rec.mimeType)
            } else {
                @Suppress("DEPRECATION")
                exportViaPublicDownloads(src, fileName)
            }
            if (result is ExportResult.Done) {
                // تسجيل الملف الجديد في مكتبة الوسائط فوراً (أندرويد 9-)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    MediaScannerConnection.scanFile(
                        context, arrayOf(result.pathForScan()), arrayOf(rec.mimeType), null
                    )
                }
            }
            result
        } catch (e: Exception) {
            ExportResult.Failed(e.message)
        }
    }

    private fun exportViaMediaStore(
        context: Context,
        src: File,
        fileName: String,
        mimeType: String
    ): ExportResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { "audio/mp4" })
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + RecordingExportLogic.EXPORT_SUBDIR
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return ExportResult.Failed("تعذر إنشاء الملف في Downloads")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: return ExportResult.Failed("تعذر فتح مجرى الكتابة")
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) } // نظافة: لا نصف ملفات
            return ExportResult.Failed(e.message)
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return ExportResult.Done(fileName)
    }

    @Suppress("DEPRECATION")
    private fun exportViaPublicDownloads(src: File, fileName: String): ExportResult {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        if (!Environment.getExternalStorageState(downloads).startsWith("mounted")) {
            return ExportResult.Failed("الذاكرة الخارجية غير متاحة")
        }
        val dir = File(downloads, RecordingExportLogic.EXPORT_SUBDIR).apply { mkdirs() }
        if (!dir.isDirectory) return ExportResult.Failed("تعذر إنشاء مجلد التصدير")
        val outFile = File(dir, fileName)
        src.copyTo(outFile, overwrite = true)
        return ExportResult.Done(fileName)
    }

    /** مسار كامل للفحص/المسح الضوئي (يُستخدم في النداء الداخلي فقط) */
    private fun ExportResult.pathForScan(): String {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val name = (this as? ExportResult.Done)?.fileName ?: ""
        return File(File(downloads, RecordingExportLogic.EXPORT_SUBDIR), name).absolutePath
    }
}

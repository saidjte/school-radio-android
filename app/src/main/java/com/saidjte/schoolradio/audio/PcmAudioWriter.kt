package com.saidjte.schoolradio.audio

/**
 * v0.29.0 — عقد موحّد لكاتبي الصوت من خط PCM (الميكروفون) إلى ملف مضغوط:
 * [AacFileWriter] (‎.m4a متوافق) و[OpusFileWriter] (‎.ogg عالي الدقة).
 *
 * وُجد ليستقبل RecordingManager أياً من الكاتبين بنفس الحالة والسلوك:
 * feed() من حلقة البث/خيط الفقرة، finish() عند الإيقاع، وfailed للفحص الدائم —
 * أي ترميز مستقبلي (FLAC مثلاً) يضاف بتنفيذ هذا العقد دون مساس بالمدير.
 */
interface PcmAudioWriter {
    /** رسالة فشل إن حدث — تفحصها الإدارة قبل الاعتماد على الناتج */
    val failed: String?

    /** مجموع العينات المشفَّرة فعلاً — أساس حساب المدة */
    val totalSamples: Long

    /** المسار النهائي للملف (يُستخدم عند إدراج سجل الأرشيف) */
    val filePath: String

    /** تهيئة المُشفّر والحاوية — يعيد null عند النجاح أو رسالة خطأ */
    fun start(): String?

    /** تغذية دفعة PCM — من خيط الصوت فقط، بلا استثناءات ولا حجب */
    fun feed(pcm: ShortArray, n: Int)

    /** إغلاق الملف: EOS + تفريغ — يعيد (المدة_ms، الحجم_بايت) أو null عند الفشل */
    fun finish(): Pair<Long, Long>?
}

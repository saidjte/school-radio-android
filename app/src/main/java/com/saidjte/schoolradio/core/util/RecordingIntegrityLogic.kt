package com.saidjte.schoolradio.core.util

/**
 * v0.39.0 — منطق سلامة تسجيلات الأرشيف (صافٍ بلا تبعيات أندرويد — قابل للاختبار وحدوياً).
 *
 * ─── لماذا وُجد؟ الخاتمة الجذرية لعطل «التسجيلات لا تعمل» ───
 * حتى v0.37.0 كانت حاوية OGG تُكتب عبر MediaMuxer وكانت تخرج تالفة على أجهزة كثيرة
 * (رؤوس/granule مكسورة يرفضها ExoPlayer) — وأصبح كاتب v0.38.0 الصافي يضمن صلاحية
 * التسجيلات الجديدة بايتاً بايتاً. لكن التسجيلات التالفة التي أُنشئت في العصر القديم
 * ما زالت جاثمة في أراشيف الأجهزة، ولا تميّزها الواجهة عن السليمة: نفس السطر،
 * نفس الشارة، والضغط عليها ينتهي برسالة «فشل فك الترميز» العامة — فيبدو للأستاذ
 * أن «التسجيلات لا تعمل» بينما الحقيقة أضيق: ملفات محددة تالفة بياناتها من إصدار قديم.
 *
 * لا كود يستطيع إصلاح بيانات تالفة — الحل الجذري المتبقي هو الصدق البنيوي:
 * نفس فحوص [OpusFileWriter] عند الإغلاق تُطبَّق على ملفات الأرشيف لتُصنَّف
 * صراحة: سليم / مفقود / تالف — فتعرف الواجهة ماذا تعرض، والمشغل ماذا يرفض
 * وبأي سبب محدد، والفقرات المجدولة ماذا تُبلّغ عند فشل انطلاقها.
 *
 * التوقيعات البنيوية (نفس عتبات الكاتب):
 * - audio/ogg: «OggS» عند البايت 0 + «OpusHead» عند البايت 28 (RFC 7845) وحجم ≥ 200
 * - audio/mp4 (‎.m4a): صندوق «ftyp» عند الإزاحة 4 (ISO BMFF) وحجم ≥ 200
 * - نوع مجهول/قديم بلا mime: فحص وجود وحجم فقط — لا نحكم على ما لا نعرف
 */
object RecordingIntegrityLogic {

    /** العتبة الدنيا لحجم ملف تسجيل صالح — نفس عتبة تحقق OpusFileWriter عند الإغلاق */
    const val MIN_FILE_BYTES = 200

    /** حجم رأس القراءة للتحقق البنيوي — يكفي لتوقيعي الحاويتين معاً */
    const val HEAD_BYTES = 64

    /** حالة سلامة ملف التسجيل */
    enum class State {
        /** الملف موجود وبنيته تطابق نوع حاويته */
        OK,
        /** الملف غير موجود على القرص (حُذف خارجياً أو فشل حفظ قديم) */
        MISSING,
        /** الملف موجود لكن بنيته لا تطابق حاويته — تالف من كاتب إصدار قديم */
        CORRUPT
    }

    /**
     * التصنيف من معطيات القراءة المجردة (بلا أي وصول لقرص هنا):
     * @param exists هل الملف موجود على القرص؟
     * @param fileSize حجمه بالبايت (0 إن غاب)
     * @param head أول [HEAD_BYTES] بايت منه (null إن تعذر القراءة/الملف أصغر)
     * @param mimeType نوع الحاوية المخزن في سجل الأرشيف
     */
    fun classify(
        exists: Boolean,
        fileSize: Long,
        head: ByteArray?,
        mimeType: String
    ): State {
        if (!exists) return State.MISSING
        if (fileSize < MIN_FILE_BYTES) return State.CORRUPT
        return when {
            isOgg(mimeType) -> {
                // OggS عند 0 + OpusHead عند 28 — صفحة الرأس الأولى كما يكتبها كاتبنا والمواصفة
                head != null && head.size >= 36 &&
                    head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() &&
                    head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte() &&
                    head[28] == 'O'.code.toByte() && head[29] == 'p'.code.toByte() &&
                    head[30] == 'u'.code.toByte() && head[31] == 's'.code.toByte() &&
                    head[32] == 'H'.code.toByte() && head[33] == 'e'.code.toByte() &&
                    head[34] == 'a'.code.toByte() && head[35] == 'd'.code.toByte()
            }
            isMp4(mimeType) -> {
                // صندوق ftyp عند الإزاحة 4: حجم الصندوق (4 بايت) ثم التوقيع
                head != null && head.size >= 8 &&
                    head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
                    head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()
            }
            else -> true // نوع غير معروف (سجلات أقدم من v0.29.0) — وجود وحجم فقط
        }.let { ok -> if (ok) State.OK else State.CORRUPT }
    }

    /** هل هذا النوع حاوية Ogg Opus؟ — نفس قاعدة RecordingCodecLogic.isOpusRecord */
    fun isOgg(mimeType: String): Boolean =
        mimeType.equals("audio/ogg", ignoreCase = true)

    /** هل هذا النوع حاوية MPEG-4 (‎.m4a)؟ */
    fun isMp4(mimeType: String): Boolean =
        mimeType.equals("audio/mp4", ignoreCase = true) ||
            mimeType.equals("audio/mpeg4", ignoreCase = true) ||
            mimeType.equals("audio/aac", ignoreCase = true)

    /** رسالة الضغط على تسجيل تالف — سبب محدد بدل «فشل فك الترميز» العام */
    fun corruptMessage(): String =
        "ملف هذا التسجيل تالف (أُنشئ بإصدار قديم قبل إصلاح كاتب الحاويات) — لا يمكن تشغيله؛ يمكنك حذفه من قائمة السطر"

    /** رسالة فقرة مجدولة مرتبطة بتسجيل تالف — تُوثَّق في حالة الفقرة */
    fun corruptScheduledNote(title: String): String =
        "ملف التسجيل «$title» تالف من إصدار قديم ولا يمكن تشغيله — سجّله من جديد ثم اربط الفقرة بالتسجيل الجديد"

    /** قراءة رأس ملف للتحقق — يعيد أول [HEAD_BYTES] بايت أو null عند أي فشل */
    fun readFileHead(file: java.io.File): ByteArray? = runCatching {
        if (!file.exists()) return null
        file.inputStream().use { ins ->
            val buf = ByteArray(HEAD_BYTES)
            var read = 0
            while (read < buf.size) {
                val r = ins.read(buf, read, buf.size - read)
                if (r < 0) break
                read += r
            }
            if (read == HEAD_BYTES) buf else buf.copyOf(read)
        }
    }.getOrNull()
}

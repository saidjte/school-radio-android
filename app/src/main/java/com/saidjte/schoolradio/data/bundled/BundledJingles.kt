package com.saidjte.schoolradio.data.bundled

/**
 * v0.14.0 — الجينلات المدمجة للفواصل المجدولة (Breaks).
 *
 * مصدرها: مؤثرات وموسيقى مجانية الترخيص (Mixkit License — استعمال مجاني بلا إسناد؛
 * OrangeFreeSounds — استعمال مجاني) عولجت محلياً: تطبيع مستوى البث ‎-16 LUFS‎،
 * قص الصمت الطرفي، وفويدات ضد النقرات. تُزرع في المكتبة عند أول تشغيل داخل
 * تصنيف «فواصل وجينلات» لتنتقيها محررات الفواصل والحائط الصوتي مباشرة —
 * ثم يمكن للمدرسة تعديلها أو حذفها كأي مقطع آخر.
 *
 * الملفات الفعلية في app/src/main/assets/jingles/ — محلية 100% بلا شبكة.
 */
object BundledJingles {

    /** اسم التصنيف الذي تُزرع تحته الجينلات في المكتبة */
    const val CATEGORY = "فواصل وجينلات"

    /** بادئة documentId لتمييز المقاطع المزروعة من التطبيق (لا يُعاد زرعها مرتين) */
    const val DOC_PREFIX = "bundled:jingles/"

    data class Jingle(
        /** مسار الأصل داخل assets (بعد مجلد jingles) */
        val file: String,
        val title: String,
        val purpose: String
    )

    val ALL: List<Jingle> = listOf(
        Jingle("school_bell.mp3", "جرس المدرسة", "جرس الفسحة الكلاسيكي — أقوى إشارة انتقال يفهمها التلاميذ فوراً"),
        Jingle("opening_jingle.mp3", "افتتاحية مبهجة", "افتتاح موسيقي إذاعي مرح لطابور الصباح"),
        Jingle("intro_sting.mp3", "مقدمة فقرة", "نغمة قصيرة تفتتح كل فقرة بلمسة إذاعية"),
        Jingle("magic_sweep.mp3", "انتقال ساحر", "انتقال انسيابي بين الفقرات بجو حديث"),
        Jingle("quick_marker.mp3", "علامة سريعة", "نقطة انتقال خاطفة (أقل من ثانية) بين الإعلانات القصيرة"),
        Jingle("whoosh.mp3", "مرور هوائي", "whoosh إذاعي كلاسيكي للانتقالات السريعة"),
        Jingle("closing_sting.mp3", "ختام هادئ", "نغمة ختام لطيفة لإنهاء الإذاعة بنعومة")
    )

    /** معرف المستند المقابل لملف جينل */
    fun docId(file: String): String = "$DOC_PREFIX$file"
}

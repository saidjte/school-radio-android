package com.saidjte.schoolradio.core.util

/**
 * منطق إعادة ربط مسارات المقاطع بعد استعادة النسخة الاحتياطية — v0.55.0.
 *
 * **العلة**: النسخة الاحتياطية كانت تنقل uri المقاطع فقط وتترك [com.saidjte.schoolradio.data.local.entity.AudioTrackEntity.documentId]
 * — الحقل الذي وُلد حصراً «لدعم إعادة الربط عند فقدان الصلاحية» — خارج النسخة،
 * وبعد الاستعادة كان كل مقطع غير مقروء يُترك لإعادة ربط *فردي* (منتقي لكل
 * مقطع) رغم أن ملفاته لم تتحرك من مساراتها. الصلاحيات الدائمة لـ SAF تموت
 * مع إزالة التطبيق أو تغيير الجهاز — وهذان تحديداً سببا الاستعادة أصلاً —
 * فالقراءة الحرفية لـ uri القديم تفشل دوماً في سيناريو الاستعادة الحقيقي.
 *
 * **العلاج شبه الآلي القانوني**: منح شجرة واحدة (ACTION_OPEN_DOCUMENT_TREE —
 * مجلد واحد يختاره الأستاذ مرة واحدة) ثم بناء uri مستند جديد لكل مقطع من
 * معرّف مستنده القديم تحت تلك الشجرة — فتُستعاد المكتبة كلها دفعة واحدة.
 *
 * [isUnderTree] هي قاعدة القرار الصافية: هل يقع معرّف مستند تحت مظلة شجرة
 * معينة؟ مطابقة الجذر (ما قبل ':') حرفياً ثم بادئة مسار **واعية بحدود
 * المقاطع** — «primary:Music» لا تغطي «primary:Music2/x.mp3» رغم أن السلسلة
 * تبدأ بها حرفياً، لأن «Music2» مقطع مسار مختلف.
 */
object RestoreRelinkLogic {

    /**
     * هل يقع [documentId] تحت مظلة الشجرة [treeDocId]؟
     *
     * القواعد:
     *  - الجذر (ما قبل أول ':') يجب أن يتطابق حرفياً — «primary:» لا تغطي
     *    مستندات جذر آخر (تخزين خارجي مقابل بطاقة SD مثلاً).
     *  - مظلة جذر بلا مسار («primary:») تغطي كل مستندات جذرها.
     *  - المسار يجب أن يطابق مسار الشجرة أو يبدأ به متبوعاً بـ'/' — بادئة
     *    سلسلة مجردة تكذب عند المقاطع المتشابهة الحروف (Music/Music2).
     *  - معرفات مشوهة بلا ':' تعيد false (ليست مستندات قابلة للبناء).
     */
    fun isUnderTree(treeDocId: String, documentId: String): Boolean {
        val tree = split(treeDocId) ?: return false
        val doc = split(documentId) ?: return false
        if (tree.root != doc.root) return false
        if (tree.path.isEmpty()) return true // مظلة الجذر تغطي الكل
        return doc.path == tree.path || doc.path.startsWith(tree.path + "/")
    }

    /** تفكيك معرف المستند إلى (الجذر، المسار) — null إن كان مشوهاً بلا جذر */
    private fun split(docId: String): Parts? {
        val i = docId.indexOf(':')
        if (i <= 0) return null
        return Parts(root = docId.substring(0, i), path = docId.substring(i + 1).trimEnd('/'))
    }

    private data class Parts(val root: String, val path: String)
}

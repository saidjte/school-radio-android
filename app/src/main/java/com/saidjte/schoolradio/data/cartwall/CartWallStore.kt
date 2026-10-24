package com.saidjte.schoolradio.data.cartwall

/**
 * نموذج رقاقة حائط الجينغل (v0.11.0).
 *
 * [index] موضع الرقاقة داخل الصفحة (0..7 — شبكة 4×2)،
 * [page] الصفحة (0 أو 1)،
 * [trackId] معرف مقطع المكتبة المرتبط — null يعني رقاقة فارغة،
 * [colorIndex] لون الرقاقة (0..7 — لوحة ثابتة معرفة في الواجهة)،
 * [loop] تكرار لا نهائي للموسيقى الخلفية (Music bed).
 */
data class CartPad(
    val index: Int,
    val page: Int,
    val trackId: Long? = null,
    val colorIndex: Int = 0,
    val loop: Boolean = false
) {
    /** مفتاح الرقاقة الفريد — يستخدمه CartPlayer لحالة التشغيل */
    val key: String get() = "$page:$index"
    val isEmpty: Boolean get() = trackId == null || trackId <= 0L
}

/**
 * تخزين وتحميل تخطيط حائط الجينغل.
 *
 * الصيغة: نص CSV خفيف في DataStore — كل سطر رقاقة:
 *   صفحة:موضع:trackId:لون:تكرار
 * مثال: "0:0:12:2:1;0:1:-:0:0"
 * (trackId = "-" يعني فارغاً). صيغة يدوية بلا مكتبات لأنها:
 *  1) قابلة للاختبار وحدوياً على JVM الخالص (لا android.jar)،
 *  2) مقاومة للتلف: أي سطر معطوب يُتجاهل ولا يُسقط البقية.
 */
object CartWallStore {

    const val PAGES = 2
    const val PADS_PER_PAGE = 8

    /** التخطيط الافتراضي: 16 رقاقة فارغة بألوان متتابعة */
    fun defaults(): List<CartPad> =
        (0 until PAGES).flatMap { page ->
            (0 until PADS_PER_PAGE).map { index ->
                CartPad(
                    index = index,
                    page = page,
                    trackId = null,
                    colorIndex = (index + page * PADS_PER_PAGE) % 8
                )
            }
        }

    /** تحميل آمن من النص المحفوظ — أي خلل يعيد الافتراضي بلا استثناء */
    fun parse(raw: String?): List<CartPad> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return defaults()
        val byKey = mutableMapOf<String, CartPad>()
        for (token in text.split(";")) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val parts = t.split(":")
            if (parts.size != 5) continue
            val page = parts[0].toIntOrNull() ?: continue
            val index = parts[1].toIntOrNull() ?: continue
            if (page !in 0 until PAGES || index !in 0 until PADS_PER_PAGE) continue
            val trackId = if (parts[2] == "-" || parts[2].isEmpty()) null else parts[2].toLongOrNull()
            val color = (parts[3].toIntOrNull() ?: 0).coerceIn(0, 7)
            val loop = parts[4] == "1"
            byKey["$page:$index"] = CartPad(index, page, trackId, color, loop)
        }
        // املأ المواضع الناقصة بالافتراضي — التخطيط دائماً 16 رقاقة كاملة
        return defaults().map { d -> byKey[d.key] ?: d }
    }

    /** ترميز التخطيط إلى الصيغة النصية (يُخزن في DataStore) */
    fun serialize(pads: List<CartPad>): String =
        pads.sortedWith(compareBy({ it.page }, { it.index })).joinToString(";") { p ->
            "${p.page}:${p.index}:${p.trackId ?: "-"}:${p.colorIndex}:${if (p.loop) 1 else 0}"
        }
}

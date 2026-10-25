package com.saidjte.schoolradio.core.audio

import kotlin.math.ln

/**
 * v0.22.0 — إعدادات إذاعية مخصصة للمعادل الصوتي.
 *
 * إعدادات الجهاز المسبقة (Presets) تختلف بين الأجهزة وقد لا توجد أصلاً، وهذه
 * خمسة أنماط ضبط صممت خصيصاً للاستخدام الإذاعي المدرسي، معرفة كنقاط تغليف
 * (تردد → ديسيبل) تُستوفى لوغاريتمياً على شرائح أي جهاز مهما كان عددها
 * ونطاقها — فلا تعتمد على دعم الجهاز إطلاقاً.
 *
 * المعرفات 1000+ لتمييزها عن إعدادات الجهاز (Short الموجبة).
 */
object RadioPresets {

    /** نمط ضبط إذاعي واحد */
    data class RadioPreset(
        val id: Int,
        val name: String,
        val description: String,
        /** نقاط المغلف: التردد بالهرتز → الكسب بالديسيبل، مرتبة تصاعدياً */
        val points: List<Pair<Int, Double>>
    )

    val ALL: List<RadioPreset> = listOf(
        RadioPreset(
            id = 1000, name = "صوت المذيع",
            description = "بروز الحديث ووضوحه فوق الموسيقى",
            points = listOf(
                80 to -3.0, 250 to -1.0, 800 to 1.0, 2000 to 3.0,
                3500 to 4.0, 6000 to 2.5, 12000 to 1.0
            )
        ),
        RadioPreset(
            id = 1001, name = "وضوح السرد",
            description = "قصص وإعلانات — وضوح أقصى بلا غبشة",
            points = listOf(
                80 to -4.0, 250 to -2.0, 800 to 1.5, 2500 to 4.0,
                5000 to 3.5, 12000 to 1.5
            )
        ),
        RadioPreset(
            id = 1002, name = "موسيقى هادئة",
            description = "أناشيد وخلفيات — توازن ناعم للقاعات",
            points = listOf(
                60 to 2.0, 250 to 1.0, 1500 to 0.0, 5000 to 1.5, 12000 to 2.5
            )
        ),
        RadioPreset(
            id = 1003, name = "باص دافئ",
            description = "تعويض السماعات الصغيرة في القاعات الواسعة",
            points = listOf(
                60 to 4.5, 150 to 3.5, 500 to 0.0, 2000 to -0.5, 8000 to 1.0
            )
        ),
        RadioPreset(
            id = 1004, name = "صباحي منعش",
            description = "حيوية الصباح المدرسي — إيقاع وحضور",
            points = listOf(
                100 to 2.5, 400 to 0.5, 1200 to 1.5, 3000 to 2.0,
                8000 to 2.5, 14000 to 2.0
            )
        )
    )

    fun byId(id: Int): RadioPreset? = ALL.firstOrNull { it.id == id }

    /**
     * استيفاء لوغاريتمي للمغلف عند تردد [hz]:
     *  - قبل أول نقطة أو بعد آخر نقطة = كسب النقطة الطرفية (تثبيت)
     *  - بين نقطتين = استيفاء خطي على المحور اللوغاريتمي (الإحساس السمعي الصحيح)
     *  - نقاط غير مرتبة/قيم غير طبيعية → 0 (دفاعي)
     */
    fun interpolate(points: List<Pair<Int, Double>>, hz: Double): Double {
        if (points.isEmpty() || hz <= 0.0) return 0.0
        val sorted = points.sortedBy { it.first }
        if (hz <= sorted.first().first) return sorted.first().second
        if (hz >= sorted.last().first) return sorted.last().second
        for (i in 0 until sorted.size - 1) {
            val (f1, g1) = sorted[i]
            val (f2, g2) = sorted[i + 1]
            if (hz >= f1 && hz <= f2) {
                if (f2 == f1) return g2
                val t = (ln(hz) - ln(f1.toDouble())) / (ln(f2.toDouble()) - ln(f1.toDouble()))
                return g1 + t * (g2 - g1)
            }
        }
        return 0.0
    }

    /**
     * حساب كسب كل شريحة ترددات جهاز حقيقي من نمط إذاعي:
     *  - [bandCentersHz] مراكز الشرائح بالهرتز (من الجهاز)
     *  - التقييد داخل نطاق الجهاز [minDb, maxDb]
     */
    fun gainsForBands(
        preset: RadioPreset,
        bandCentersHz: List<Double>,
        minDb: Float,
        maxDb: Float
    ): List<Float> = bandCentersHz.map { hz ->
        interpolate(preset.points, hz).toFloat().coerceIn(minDb, maxDb)
    }
}

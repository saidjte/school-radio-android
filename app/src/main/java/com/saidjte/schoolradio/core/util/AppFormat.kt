package com.saidjte.schoolradio.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * منسّق مركزي للتواريخ والأرقام (v0.6.0).
 *
 * القاعدة: نص عربي (أسماء الأيام والشهور) + أرقام مغربية لاتينية (1234567890)
 * حصراً — لا أرقام مشرقية (٠١٢٣) في أي شاشة.
 * SimpleDateFormat بـ Locale("ar") ينتج أرقاماً مشرقية على أغلب الأجهزة،
 * لذلك نطبّع كل مخرجاته عبر westernDigits() — مضمون على كل إصدارات أندرويد.
 */
object AppFormat {

    private val AR_NAMES = Locale("ar")

    /** تحويل أي أرقام مشرقية (٠-٩ / ۰-۹) إلى أرقام مغربية لاتينية */
    fun westernDigits(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                }
            )
        }
    }

    /** «الأربعاء 24 أكتوبر 2026 — 14:05:09» بأرقام مغربية */
    fun dateLong(ms: Long): String =
        westernDigits(SimpleDateFormat("EEEE d MMMM yyyy — HH:mm:ss", AR_NAMES).format(Date(ms)))

    /** «الأربعاء 24/10/2026 — 14:05» بأرقام مغربية */
    fun dateShort(ms: Long): String =
        westernDigits(SimpleDateFormat("EEEE d/M/yyyy — HH:mm", AR_NAMES).format(Date(ms)))

    /** «14:05» — أرقام فقط، لا يتأثر بلغة الجهاز */
    fun clock(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    /** «الأربعاء 24 أكتوبر 2026» — بلا وقت، لشاشات العرض */
    fun dateDay(ms: Long): String =
        westernDigits(SimpleDateFormat("EEEE d MMMM yyyy", AR_NAMES).format(Date(ms)))

    /** v0.18.0: عدد صحيح بأرقام مغربية مضمونة (مثل 1250 → "1250") */
    fun number(v: Long): String = Locale.US.let { String.format(it, "%d", v) }

    /** v0.18.0: عدد عشري بمنزلة واحدة بأرقام مغربية (مثل 1.8 → "1.8") */
    fun numberOneDecimal(v: Double): String =
        String.format(Locale.US, "%.1f", v).trimEnd('0', '.').ifEmpty { "0" }
}

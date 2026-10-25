package com.saidjte.schoolradio.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import com.saidjte.schoolradio.core.audio.RadioPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * محرك المؤثرات الصوتية الاحترافي (v0.9.0).
 *
 * معمارياً: مشغّلا التطبيق (خدمة الوسائط + مشغل معاينة التسجيلات) يُنشآن بمعرّف
 * جلسة صوتية، وكل منشأ يُبلّغ هنا عبر [attach] — فيُنشئ هذا المحرك سلسلة مؤثرات
 * (Equalizer + BassBoost + Virtualizer) مربوطة بكل جلسة، وتُطبَّق أي تغيير
 * من الواجهة على كل الجلسات المرتبطة دفعة واحدة.
 *
 * - شرائح الترددات تُقرأ من الجهاز نفسه (العدد والنطاق يختلفان بين الأجهزة).
 * - الإعدادات المسبقة (Presets) تُترجم عربياً عبر خريطة الأسماء الشائعة،
 *   وما لا يُترجم يُعرض باسمه الأصلي.
 * - [Visualizer] مرفق بجلسة المشغل الرئيسي لقياس مستوى الموسيقى الحي (VU)
 *   ويعمل بتردد 10 قراءات/ثانية فقط — كلفة طاقة مهملة.
 *
 * الخيطية: كل الدوال آمنة للاستدعاء من الخيط الرئيسي؛ الحالة تُنشر عبر StateFlow.
 */
object AudioEffects {

    /** شريحة تردد واحدة في الواجهة */
    data class EqBandUi(
        val index: Short,
        /** تسمية التردد المركزي مثل "60" أو "1.0k" */
        val freqLabel: String,
        /** v0.22.0: التردد المركزي بالهرتز — لتحويل الأنماط الإذاعية إلى كسب الشرائح */
        val centerHz: Int,
        val minDb: Float,
        val maxDb: Float,
        val gainDb: Float
    )

    /** إعداد مسبق مترجم */
    data class EqPresetUi(val id: Short, val name: String)

    data class AudioEffectsState(
        /** هل يوجد معادل مربوط بجلسة صوتية على هذا الجهاز؟ */
        val available: Boolean = false,
        val enabled: Boolean = false,
        val bands: List<EqBandUi> = emptyList(),
        val presets: List<EqPresetUi> = emptyList(),
        /** المُعد المسبق المختار حالياً — null يعني ضبط مخصص بالشرائح */
        val selectedPreset: Short? = null,
        val bassSupported: Boolean = false,
        /** 0..1 */
        val bassStrength: Float = 0f,
        val virtSupported: Boolean = false,
        /** 0..1 */
        val virtStrength: Float = 0f
    )

    /** ربط المؤثرات بجلسة واحدة (معادل + باص + مؤثر محيطي) */
    private class AttachedFx(
        val sessionId: Int,
        val eq: Equalizer,
        val bass: BassBoost?,
        val virt: Virtualizer?
    ) {
        fun release() {
            runCatching { eq.release() }
            bass?.let { runCatching { it.release() } }
            virt?.let { runCatching { it.release() } }
        }
    }

    private val attached = mutableListOf<AttachedFx>()
    private var visualizer: Visualizer? = null

    /** مضخّة مستوى الموسيقى الحي — يوصلها ServiceLocator بحالة الواجهة */
    @Volatile var onMediaLevel: ((Float) -> Unit)? = null

    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state.asStateFlow()

    /**
     * ربط جلسة صوتية جديدة بالمؤثرات — يُستدعى من كل مشغل فور إنشائه.
     * إن أُنشئت المؤثرات للمرة الأولى تُطبَّق حالة الاسترجاع المحفوظة إن وجدت.
     */
    @Synchronized
    fun attach(sessionId: Int, initial: RestoreData? = null) {
        if (attached.any { it.sessionId == sessionId }) return
        try {
            val eq = Equalizer(0, sessionId)
            if (initial != null) {
                runCatching { eq.enabled = initial.enabled }
            }
            // الإعدادات المسبقة والشرائح من أول معادل (كل الأجهزة تعطي نفس النظام عادة)
            val presets = buildPresets(eq)
            applyBands(eq)

            val bass = runCatching {
                val b = BassBoost(0, sessionId)
                if (b.getStrengthSupported()) {
                    b.apply {
                        setStrength((_state.value.bassStrength * 1000).toInt().toShort())
                        if (_state.value.enabled) enabled = true
                    }
                } else {
                    runCatching { b.release() }
                    null
                }
            }.getOrNull()

            val virt = runCatching {
                val v = Virtualizer(0, sessionId)
                if (v.getStrengthSupported()) {
                    v.apply {
                        setStrength((_state.value.virtStrength * 1000).toInt().toShort())
                        if (_state.value.enabled) enabled = true
                    }
                } else {
                    runCatching { v.release() }
                    null
                }
            }.getOrNull()

            attached += AttachedFx(sessionId, eq, bass, virt)

            _state.value = _state.value.copy(
                available = true,
                enabled = eq.enabled,
                bassSupported = bass != null,
                virtSupported = virt != null,
                presets = presets
            )

            attachVisualizer(sessionId)
            // أول ربط في عمر العملية؟ طبّق الاسترجاع المحفوظ (إعدادات مستمرة)
            initial?.let { restore(initial) }
        } catch (_: Exception) {
            // أجهزة قليلة لا تدعم audiofx إطلاقاً — الواجهة تعرض «غير متاح»
            _state.value = _state.value.copy(available = attached.isNotEmpty())
        }
    }

    /** فك جلسة انتهى مشغلها (تحرير موارد نظيف) */
    @Synchronized
    fun detach(sessionId: Int) {
        val it = attached.firstOrNull { it.sessionId == sessionId } ?: return
        it.release()
        attached.remove(it)
        if (attached.isEmpty()) {
            _state.value = _state.value.copy(available = false)
            runCatching { visualizer?.enabled = false }
        }
    }

    // ============ أوامر الواجهة ============

    /** تفعيل/تعطيل سلسلة المؤثرات كلها */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        attached.forEach { fx ->
            runCatching { fx.eq.enabled = enabled }
            fx.bass?.let { runCatching { it.enabled = enabled } }
            fx.virt?.let { runCatching { it.enabled = enabled } }
        }
        _state.value = _state.value.copy(enabled = enabled)
    }

    /** ضبط كسب شريحة تردد — يُطبق على كل الجلسات */
    @Synchronized
    fun setBandGainDb(index: Short, gainDb: Float) {
        attached.forEach { fx ->
            runCatching { fx.eq.setBandLevel(index, (gainDb * 100).toInt().toShort()) }
        }
        _state.value = _state.value.copy(
            bands = _state.value.bands.map {
                if (it.index == index) it.copy(gainDb = gainDb) else it
            },
            selectedPreset = null
        )
    }

    /** اختيار إعداد مسبق بالمعرف (يتوافق مع قائمة presets في الحالة) */
    @Synchronized
    fun setPreset(id: Short) {
        attached.forEach { fx -> runCatching { fx.eq.usePreset(id) } }
        // اقرأ الشرائح الناتجة فعلياً بعد تطبيق الإعداد
        attached.firstOrNull()?.let { applyBands(it.eq) }
        _state.value = _state.value.copy(selectedPreset = id)
    }

    /** v0.22.0: مسح اختيار الإعداد المسبق — بعد التصفير اليدوي للشرائح */
    @Synchronized
    fun setPresetClear() {
        _state.value = _state.value.copy(selectedPreset = null)
    }

    /**
     * v0.22.0: تطبيق نمط إذاعي مخصص (RadioPresets) — يُحسب كسب كل شريحة
     * من مغلف النمط على مراكز ترددات الجهاز الفعلية ثم يُطبق على كل الجلسات.
     * لا يعتمد على دعم الجهاز للإعدادات المسبقة إطلاقاً.
     */
    @Synchronized
    fun applyRadioPreset(preset: RadioPresets.RadioPreset) {
        val st = _state.value
        if (!st.available || st.bands.isEmpty()) return
        val first = st.bands.first()
        val gains = RadioPresets.gainsForBands(
            preset, st.bands.map { it.centerHz.toDouble() }, first.minDb, first.maxDb
        )
        attached.forEach { fx ->
            gains.forEachIndexed { i, g ->
                runCatching { fx.eq.setBandLevel(i.toShort(), (g * 100).toInt().toShort()) }
            }
        }
        _state.value = st.copy(
            bands = st.bands.mapIndexed { i, b -> b.copy(gainDb = gains[i]) },
            selectedPreset = preset.id.toShort()
        )
    }

    /**
     * v0.22.0: لقطة قابلة للحفظ من الحالة الفعلية للحالة لحظة النداء —
     * تُصلح تسرب حفظ الإعدادات المسبقة (الواجهة كانت تحفظ من حالة كومبوزيشن
     * قد تكون إطاراً متأخرة عن آخر أمر). النمط الإذاعي يُحفظ عبر معرفه والكسب معه.
     */
    @Synchronized
    fun snapshot(): RestoreData {
        val s = _state.value
        return RestoreData(
            enabled = s.enabled,
            presetId = s.selectedPreset?.toInt() ?: -1,
            gains = s.bands.joinToString("|") { String.format(java.util.Locale.US, "%.2f", it.gainDb) },
            bass = s.bassStrength,
            virt = s.virtStrength
        )
    }

    /** تعزيز الباص 0..1 */
    @Synchronized
    fun setBassStrength(v: Float) {
        val s = v.coerceIn(0f, 1f)
        attached.forEach { fx ->
            fx.bass?.let { runCatching { it.setStrength((s * 1000).toInt().toShort()) } }
        }
        _state.value = _state.value.copy(bassStrength = s)
    }

    /** المؤثر المحيطي 0..1 */
    @Synchronized
    fun setVirtualizerStrength(v: Float) {
        val s = v.coerceIn(0f, 1f)
        attached.forEach { fx ->
            fx.virt?.let { runCatching { it.setStrength((s * 1000).toInt().toShort()) } }
        }
        _state.value = _state.value.copy(virtStrength = s)
    }

    // ============ الاسترجاع المستمر بين الجلسات ============

    /** بيانات الاسترجاع المحفوظة في الإعدادات */
    data class RestoreData(
        val enabled: Boolean,
        val presetId: Int,
        /** كسب الشرائح بالديسيبل مفصولاً بـ | بالترتيب */
        val gains: String,
        val bass: Float,
        val virt: Float
    )

    /** تطبيق بيانات مسترجعة من الإعدادات — يُستدعى بعد أول ربط ناجح */
    @Synchronized
    fun restore(data: RestoreData) {
        setEnabled(data.enabled)
        setBassStrength(data.bass)
        setVirtualizerStrength(data.virt)
        if (data.presetId >= 0 && _state.value.presets.any { it.id.toInt() == data.presetId }) {
            setPreset(data.presetId.toShort())
        } else {
            val gains = data.gains.split("|").mapNotNull { it.toFloatOrNull() }
            _state.value.bands.forEachIndexed { i, band ->
                gains.getOrNull(i)?.let { setBandGainDb(band.index, it.coerceIn(band.minDb, band.maxDb)) }
            }
        }
    }

    // ============ داخلية ============

    private fun applyBands(eq: Equalizer) {
        val bands = (0 until eq.numberOfBands).map { i ->
            val idx = i.toShort()
            val centerMilliHz = eq.getCenterFreq(idx) // milli-Hz
            val hz = centerMilliHz / 1000
            EqBandUi(
                index = idx,
                freqLabel = if (hz >= 1000) {
                    val khz = hz / 1000f
                    if (khz % 1f == 0f) "${khz.toInt()}k" else String.format("%.1fk", khz)
                } else "$hz",
                centerHz = hz,
                minDb = eq.bandLevelRange[0] / 100f,
                maxDb = eq.bandLevelRange[1] / 100f,
                gainDb = eq.getBandLevel(idx) / 100f
            )
        }
        _state.value = _state.value.copy(bands = bands)
    }

    private fun buildPresets(eq: Equalizer): List<EqPresetUi> =
        (0 until eq.numberOfPresets).map { i ->
            val id = i.toShort()
            val raw = runCatching { eq.getPresetName(id) }.getOrNull() ?: ""
            EqPresetUi(id, AR_PRESET_NAMES[raw] ?: raw)
        }

    private fun attachVisualizer(sessionId: Int) {
        if (visualizer != null) return
        runCatching {
            val v = Visualizer(sessionId)
            v.captureSize = 256
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        val w = waveform ?: return
                        if (w.isEmpty()) return
                        // RMS للعينات الموجية (0..255 مركزة عند 128) → 0..1
                        var acc = 0.0
                        for (b in w) {
                            val d = (b.toInt() - 128) / 128.0
                            acc += d * d
                        }
                        val rms = kotlin.math.sqrt(acc / w.size)
                        onMediaLevel?.invoke(rms.toFloat().coerceIn(0f, 1f))
                    }
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                },
                minOf(Visualizer.getMaxCaptureRate(), 10000),
                true, false
            )
            v.enabled = true
            visualizer = v
        }
    }

    /** أسماء الإعدادات المسبقة الشائعة مترجمة — الباقي يظهر كما هو */
    private val AR_PRESET_NAMES = mapOf(
        "Normal" to "عادي",
        "Classical" to "كلاسيكي",
        "Dance" to "رقص",
        "Flat" to "مسطح",
        "Folk" to "شعبي",
        "Heavy Metal" to "هيفي ميتال",
        "Hip Hop" to "هيب هوب",
        "Jazz" to "جاز",
        "Pop" to "بوب",
        "Rock" to "روك",
        "Latin" to "لاتيني",
        "Acoustic" to "أكوستيكي",
        "Electronic" to "إلكتروني",
        "Live" to "مباشر",
        "Bass Booster" to "تعزيز الباص",
        "Bass Reducer" to "تخفيض الباص",
        "Treble Booster" to "تعزيز الحدة",
        "Treble Reducer" to "تخفيض الحدة",
        "Vocal Booster" to "بروز الصوت البشري",
        "Small Speakers" to "مكبرات صغيرة"
    )
}

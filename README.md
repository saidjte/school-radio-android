# الإذاعة المدرسية (School Radio)

تطبيق أندرويد **احترافي يعمل دون إنترنت بالكامل** لإدارة الإذاعة المدرسية:
لوحة تحكم، مكتبة صوتية مصنفة، قوائم تشغيل، جدولة أسبوعية دقيقة للفقرات،
بث مباشر عبر الميكروفون إلى المكبرات مع خفض تلقائي للموسيقى، ونسخ احتياطي —
مبنية بالكامل بـ **Kotlin + Jetpack Compose** (Material 3، Media3، Room، عربية RTL).

## التحميل

| الإصدار | APK (اسم موحّد) | الحزمة المصدرية |
|---------|----------------|-----------------|
| **v0.2.0** (الحالية) | [SchoolRadio_v0.2.0.apk](https://github.com/saidjte/school-radio-android/releases/download/v0.2.0/SchoolRadio_v0.2.0.apk) | [SchoolRadio_Source_v0.2.0.zip](https://github.com/saidjte/school-radio-android/releases/download/v0.2.0/SchoolRadio_Source_v0.2.0.zip) |
| v0.1.0 | [SchoolRadio_v0.1.0.apk](https://github.com/saidjte/school-radio-android/releases/download/v0.1.0/SchoolRadio_v0.1.0.apk) | [SchoolRadio_Source_v0.1.0.zip](https://github.com/saidjte/school-radio-android/releases/download/v0.1.0/SchoolRadio_Source_v0.1.0.zip) |

- صفحة الإصدارات: https://github.com/saidjte/school-radio-android/releases
- المعلماريات: APK عالمي يعمل على **arm64-v8a** و **armeabi-v7a**
  (تطبيق Kotlin خالص دون مكتبات native، وفلتر `abiFilters` مثبّت في إعداد البناء)
- التوقيع: نفس المفتاح في كل الإصدارات — بصمة SHA-256 في [docs/SIGNING.md](docs/SIGNING.md) —
  لذلك يُثبَّت التحديث الجديد فوق النسخة القديمة مباشرة دون حذف.
- داخل التطبيق: تبويب «حول التطبيق» ← **تحقق من التحديثات** يسأل GitHub API
  عن أحدث إصدار ويقترح التحميل مباشرة.

## المزايا (v0.2.0 — النسخة الاحترافية دون اتصال)

- **لوحة تحكم**: اسم المدرسة، ساعة حية، قيد التشغيل الآن، الفقرة التالية،
  جدول اليوم مع حالة كل فقرة (مجدولة/قيد التشغيل/مكتملة/فائتة/فشلت)
- **مكتبة صوتية**: استيراد MP3/WAV عبر SAF (متعدد الملفات، بلا صلاحيات شامل)،
  7 تصنيفات افتراضية + مخصصة، بحث، اكتشاف الملفات المفقودة وإعادة ربطها
- **قوائم تشغيل**: إعادة ترتيب، تكرار، ربط بفقرات مجدولة
- **جدولة أسبوعية دقيقة**: AlarmManager دقيق (+ تراجع مرن)، إعادة تهيئة بعد
  إعادة التشغيل/تغيّر الوقت، منع التكرار اليومي (قيد تفرد event+date)، سجل تنفيذ
- **بث مباشر PTT**: «اضغط للتحدث» — AudioRecord→AudioTrack 48kHz بتأخير منخفض
  مع AEC ضد الصفير، اختيار مسار الإخراج (مكبر/AUX/بلوتوث)، حلقة مستوى حية
- **خفض تلقائي للموسيقى** أثناء الإعلان وعودتها بعده
- **مشغل خلفي** Media3 MediaSessionService + إشعار تحكم + تخطي الملفات المعطوبة
- **نسخ احتياطي JSON عبر SAF** مع فحص سلامة + نسخة أمان قبل الاستبدال
- **12 اختبار وحدة** لمنطق الجدولة

## البناء من المصدر

```bash
git clone https://github.com/saidjte/school-radio-android.git
cd school-radio-android
./gradlew :app:assembleRelease   # يتطلب متغيرات KS_* للتوقيع (انظر docs/SIGNING.md)
# أو للتجربة فقط:
./gradlew :app:assembleDebug
```

| | |
|---|---|
| اللغة | Kotlin 2.0.21 + Jetpack Compose (BOM 2024.10) |
| AGP / Gradle | 8.7.3 / 8.10.2 (wrapper مرفق) |
| compileSdk / targetSdk | 35 |
| minSdk | 24 (أندرويد 7.0) |
| التخزين | Room 2.6.1 (7 كيانات، مخطط مُصدَّر) + DataStore |
| الوسائط | Media3 1.4.1 (ExoPlayer + MediaSessionService) |
| الإصدار | `version.properties` هو مصدر الحقيقة الوحيد (versionName + versionCode) |

## CI

سير عمل GitHub Actions (`.github/workflows/build-android.yml`) يبني عند كل
push إلى `main` APK موقّعاً بـ arm64-v8a + armeabi-v7a، يشغّل سكربت التحقق
(`tools/verify_release.sh`)، ويرفعه **كـ Artifact فقط** — الـ Releases تُنشأ
يدوياً عبر REST API (انظر worklog.md: «CI لا ينشئ Release»).

## البنية

```
app/src/main/java/com/saidjte/schoolradio/
├── SchoolRadioApp.kt          # تهيئة الحاقن + زرع التصنيفات + فحص الملفات + إعادة ضبط المنبهات
├── MainActivity.kt            # NavHost + RTL + شريط المشغل + صلاحية الإشعارات
├── core/{theme,ui,util}/      # الثيم، أيقونات مخصصة، ScheduleLogic الصرف
├── data/local/{entity,dao}/   # Room: 7 كيانات + DAOs + مخطط مُصدَّر (app/schemas)
├── data/repository/           # المكتبة/القوائم/الجدولة
├── data/backup/               # تصدير واستعادة JSON عبر SAF
├── data/settings/             # DataStore (المدرسة، المظهر، الخفض)
├── audio/                     # PlaybackService + PlaybackConnection + MicrophoneController + AudioRoutingManager
├── scheduler/                 # SchedulerManager + AlarmReceiver + BootReceiver
├── feature/{dashboard,library,playlists,scheduling,settings,player}/
└── di/ServiceLocator.kt       # حاقن اعتمادي يدوي
tools/verify_release.sh        # سكربت التحقق قبل رفع أي إصدار
docs/SIGNING.md                # بصمة التوقيع الثابتة + طريقة التحقق
worklog.md                     # سجل النشر والدروس المستفادة
```

## الترخيص

MIT — انظر [LICENSE](LICENSE).

# الإذاعة المدرسية (School Radio)

تطبيق أندرويد **احترافي يعمل دون إنترنت بالكامل** لإدارة الإذاعة المدرسية:
لوحة تحكم، مكتبة صوتية مصنفة، قوائم تشغيل، جدولة أسبوعية دقيقة للفقرات،
بث مباشر عبر الميكروفون إلى المكبرات مع خفض تلقائي للموسيقى،
**أرشيف تسجيلات (بث حي + فقرات تحضيرية)** و**أدوار مستخدمين (مشرف/معلم/طالب)** ونسخ احتياطي —
مبنية بالكامل بـ **Kotlin + Jetpack Compose** (Material 3، Media3، Room، عربية RTL).

## التحميل

| الإصدار | APK (اسم موحّد) | الحزمة المصدرية |
|---------|----------------|-----------------|
| **v0.4.0** (الحالية) | [SchoolRadio_v0.4.0.apk](https://github.com/saidjte/school-radio-android/releases/download/v0.4.0/SchoolRadio_v0.4.0.apk) | [SchoolRadio_Source_v0.4.0.zip](https://github.com/saidjte/school-radio-android/releases/download/v0.4.0/SchoolRadio_Source_v0.4.0.zip) |
| v0.3.0 | [SchoolRadio_v0.3.0.apk](https://github.com/saidjte/school-radio-android/releases/download/v0.3.0/SchoolRadio_v0.3.0.apk) | [SchoolRadio_Source_v0.3.0.zip](https://github.com/saidjte/school-radio-android/releases/download/v0.3.0/SchoolRadio_Source_v0.3.0.zip) |
| v0.2.0 | [SchoolRadio_v0.2.0.apk](https://github.com/saidjte/school-radio-android/releases/download/v0.2.0/SchoolRadio_v0.2.0.apk) | [SchoolRadio_Source_v0.2.0.zip](https://github.com/saidjte/school-radio-android/releases/download/v0.2.0/SchoolRadio_Source_v0.2.0.zip) |
| v0.1.0 | [SchoolRadio_v0.1.0.apk](https://github.com/saidjte/school-radio-android/releases/download/v0.1.0/SchoolRadio_v0.1.0.apk) | [SchoolRadio_Source_v0.1.0.zip](https://github.com/saidjte/school-radio-android/releases/download/v0.1.0/SchoolRadio_Source_v0.1.0.zip) |

- صفحة الإصدارات: https://github.com/saidjte/school-radio-android/releases
- المعماريات: APK عالمي يعمل على **arm64-v8a** و **armeabi-v7a**
  (تطبيق Kotlin خالص دون مكتبات native، وفلتر `abiFilters` مثبّت في إعداد البناء)
- التوقيع: نفس المفتاح في كل الإصدارات — بصمة SHA-256 في [docs/SIGNING.md](docs/SIGNING.md) —
  لذلك يُثبَّت التحديث الجديد فوق النسخة القديمة مباشرة دون حذف ودون فقدان البيانات.

## المزايا (v0.4.0 — تحسين تجربة المستخدم UI/UX)

- **نظام تصميم كامل**: مخططات ألوان M3 مكتملة الفتحات (فاتح/داكن) بعمق بطاقات
  `surfaceContainer`، مقياس طباعة مضبوط للعربية (ارتفاعات أسطر مريحة، أرقام جدولية
  `tnum` في الساعات والعدّادات حتى لا ترتعد)، نظام أشكال موحّد 8→28dp
- **ألوان حالة دلالية تتكيف مع الوضع الداكن** (`AppStatusColors`): نجاح/تشغيل/فشل/تحذير/بث حي —
  بدل الألوان الصلبة التي كانت تنكسر في الداكن
- **مكونات مشتركة**: `ScreenHeader` لترويسات موحّدة، `EmptyStateCard` لحالات فارغة
  بأيقونة وزر إجراء، `StatusBadge`، و`MirroredIcon` يعكس أسهم التالي/السابق
  لاتجاه القراءة العربي (كما في يوتيوب العربي)
- **لوحة التحكم**: تحية زمنية باسم المستخدم ودوره، بطاقة بطل متدرجة اللون بساعة حية،
  شريط إحصاءات اليوم (فقرات/مُنجزة/متبقية)، بطاقة «الفقرة التالية» بعدّاد تنازلي حي بالثانية،
  شارة «● مباشر» نابضة، نبضات لمس (haptics) عند الضغط للتحدث وبدء/إيقاف التسجيل،
  حلقة مستوى ميكروفون انسيابية، وحالات فارغة تنقل مباشرة إلى الجدولة/المكتبة
- **شريط مشغل عائم**: كارت دائري الزوايا بارتفاع نغمي، عنوان مقصوص بسطر واحد،
  وانتقال متحرك بين أيقونتي التشغيل/الإيقاف
- **انتقالات شاشات ناعمة** (تلاشٍ + تكبير خفيف محايد الاتجاه) وشارة عدد التسجيلات
  الحية على تبويب الأرشيف
- **أرشيف التسجيلات**: بحث فوري + مرشّح مصدر (الكل/بث مباشر/تحضيرية) +
  ملخص العدد والحجم الكلي + أزرار تشغيل دائرية ملوّنة وشريط تقدم يظهر بحركة
- **المكتبة والقوائم والجدولة والإعدادات**: حالات فارغة بإجراءات مباشرة (زر استيراد
  داخل الحالة الفارغة)، مسح البحث بضغطة، أزرار تشغيل دائرية معطلة للمقاطع المفقودة،
  أزرار مقسّمة (Segmented) لاختيار المظهر، وعناوين بطاقات بأيقونات حاوية

## المزايا الأساسية (منذ v0.1.0 → v0.3.0)

- **لوحة تحكم**: اسم المدرسة، ساعة حية، قيد التشغيل الآن، جدول اليوم مع حالة كل فقرة
- **مكتبة صوتية**: استيراد MP3/WAV عبر SAF (متعدد الملفات، بلا صلاحيات شامل)،
  7 تصنيفات افتراضية + مخصصة، بحث، اكتشاف الملفات المفقودة وإعادة ربطها
- **قوائم تشغيل**: إعادة ترتيب، تكرار، ربط بفقرات مجدولة
- **جدولة أسبوعية دقيقة**: AlarmManager دقيق (+ تراجع مرن)، إعادة تهيئة بعد
  إعادة التشغيل/تغيّر الوقت، منع التكرار اليومي (قيد تفرد event+date)، سجل تنفيذ
- **بث مباشر PTT**: «اضغط للتحدث» — AudioRecord→AudioTrack 48kHz بتأخير منخفض
  مع AEC ضد الصفير، اختيار مسار الإخراج (مكبر/AUX/بلوتوث)، حلقة مستوى حية
- **أرشيف التسجيلات (v0.3.0)**: تسجيل البث الحي أثناءه عبر خط أنابيب AudioRecord واحد
  (حل تنازع MediaRecorder/AudioRecord) + فقرات تحضيرية مستقلة — AAC-LC 128kbps في .m4a،
  حفظ تلقائي عند انتهاء البث، تشغيل فوري وتقدم ومؤقّت وتسمية وحذف وتوقيع المسجّل
- **المستخدمون والأدوار (v0.3.0)**: مشرف / معلم / طالب — الطالب: بث وتسجيل وتحضير •
  المعلم: + الجدولة وإدارة التسجيلات • المشرف: + إدارة المستخدمين
  (بوابات في الواجهة + لقطة اسم/دور المسجّل مع كل تسجيل)
- **ترحيل قاعدة بيانات صريح 1→2** (جدولا users وrecordings) — بيانات v0.2.0 محفوظة بالكامل
- **خفض تلقائي للموسيقى** أثناء الإعلان وعودتها بعده
- **مشغل خلفي** Media3 MediaSessionService + إشعار تحكم + تخطي الملفات المعطوبة
- **نسخ احتياطي JSON عبر SAF (إصدار 2)** يشمل المستخدمين وبيانات التسجيلات —
  متوافق مع نسخ v1، مع فحص سلامة + نسخة أمان قبل الاستبدال
- **28 اختبار وحدة** (12 جدولة + 16 تسجيلات/أدوار) لمنطق الخدمة الصرف

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
| التخزين | Room 2.6.1 (9 كيانات، مخطط 2 مُصدَّر + ترحيل صريح) + DataStore |
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
├── MainActivity.kt            # NavHost + RTL + انتقالات شاشات + شريط المشغل + صلاحية الإشعارات
├── core/theme/                # نظام التصميم: مخططات فاتح/داكن كاملة + طباعة عربية + أشكال + AppStatusColors
├── core/ui/                   # AppIcons (18 أيقونة متجهة) + Components (ScreenHeader/EmptyStateCard/StatusBadge/MirroredIcon)
├── core/util/                 # ScheduleLogic + RecordingLogic الصرف القابل للاختبار
├── data/local/{entity,dao}/   # Room: 9 كيانات (v2: + users + recordings) + DAOs + ترحيل صريح
├── data/repository/           # المكتبة/القوائم/الجدولة/الجلسة/التسجيلات
├── data/backup/               # تصدير واستعادة JSON عبر SAF (إصدار 2)
├── data/settings/             # DataStore (المدرسة، المظهر، الخفض، المستخدم الحالي)
├── audio/                     # PlaybackService + MicrophoneController + AacFileWriter + RecordingManager + RecordingsPlayer + AudioRoutingManager
├── scheduler/                 # SchedulerManager + AlarmReceiver + BootReceiver
├── feature/{dashboard,library,playlists,recordings,scheduling,settings,player}/
└── di/ServiceLocator.kt       # حاقن اعتمادي يدوي
tools/verify_release.sh        # سكربت التحقق قبل رفع أي إصدار
docs/SIGNING.md                # بصمة التوقيع الثابتة + طريقة التحقق
worklog.md                     # سجل النشر والدروس المستفادة
```

## الترخيص

MIT — انظر [LICENSE](LICENSE).

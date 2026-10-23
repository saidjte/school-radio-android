# الإذاعة المدرسية (School Radio)

تطبيق أندرويد للإذاعة المدرسية: فقرات صوتية (قرآن كريم، حديث وكلمة، وطنيات،
إعلانات إدارية)، جدول أسبوعي للبرنامج، وبث مباشر اختياري — مبنية بالكامل
بـ **Kotlin** على Android (Media3/ExoPlayer، Material 3، واجهة عربية RTL).

## التحميل

| الإصدار | APK (اسم موحّد) | الحزمة المصدرية |
|---------|----------------|-----------------|
| v0.1.0 | [SchoolRadio_v0.1.0.apk](https://github.com/saidjte/school-radio-android/releases/download/v0.1.0/SchoolRadio_v0.1.0.apk) | [SchoolRadio_Source_v0.1.0.zip](https://github.com/saidjte/school-radio-android/releases/download/v0.1.0/SchoolRadio_Source_v0.1.0.zip) |

- صفحة الإصدارات: https://github.com/saidjte/school-radio-android/releases
- المعلماريات: APK عالمي يعمل على **arm64-v8a** و **armeabi-v7a**
  (تطبيق Kotlin خالص دون مكتبات native، وفلتر `abiFilters` مثبّت في إعداد البناء)
- التوقيع: نفس المفتاح في كل الإصدارات — بصمة SHA-256 في [docs/SIGNING.md](docs/SIGNING.md) —
  لذلك يُثبَّت التحديث الجديد فوق النسخة القديمة مباشرة دون حذف.
- داخل التطبيق: تبويب «حول التطبيق» ← **تحقق من التحديثات** يسأل GitHub API
  عن أحدث إصدار ويقترح التحميل مباشرة.

## المزايا (v0.1.0)

- **فقرات الإذاعة**: قائمة تسجيلات مع تشغيل فوري عبر شريط مشغل سفلي
  (تشغيل/إيقاف مؤقت، شريط تقدم، زمن منقضٍ/كلي)
- **كتالوج حيّ**: الفقرات والجدول يُجلبان من `content/catalog.json` على GitHub
  عند توفر الشبكة — تحديث المحتوى لا يحتاج إصدار APK جديد؛ ويعود التطبيق
  تلقائياً إلى آخر نسخة مخبأة ثم إلى النسخة المدمجة عند انقطاع الاتصال
- **الجدول الأسبوعي**: برنامج الإذاعة (اليوم/الساعة/الفقرة/المقدم) من نفس الكتالوج
- **بث مباشر اختياري**: يُفعَّل من الكتالوج عند توفر رابط المدرسة (`live.url`)
- **مدقق تحديثات**: مقارنة الإصدار مع أحدث Release على GitHub ويقترح رابط
  التحميل المباشر بالاسم الموحد `SchoolRadio_vX.Y.Z.apk`
- **إدارة تركيز الصوت**: يتوقف تلقائياً عند المكالمات، ويستأنف بعدها

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
| اللغة | Kotlin 2.0.21 |
| AGP / Gradle | 8.7.3 / 8.10.2 (wrapper مرفق) |
| compileSdk / targetSdk | 35 |
| minSdk | 24 (أندرويد 7.0) |
| الإصدار | `version.properties` هو مصدر الحقيقة الوحيد (versionName + versionCode) |

## CI

سير عمل GitHub Actions (`.github/workflows/build-android.yml`) يبني عند كل
push إلى `main` APK موقّعاً بـ arm64-v8a + armeabi-v7a، يشغّل سكربت التحقق
(`tools/verify_release.sh`)، ويرفعه **كـ Artifact فقط** — الـ Releases تُنشأ
يدوياً عبر REST API (انظر worklog.md: «CI لا ينشئ Release»).

## البنية

```
app/src/main/java/com/saidjte/schoolradio/
├── MainActivity.kt        # التبويبات + شريط المشغل السفلي
├── HomeFragment.kt        # فقرات الإذاعة
├── ScheduleFragment.kt    # الجدول الأسبوعي
├── AboutFragment.kt       # حول + التحقق من التحديثات
├── Segment.kt             # نماذج البيانات + محلل الكتالوج
├── CatalogRepository.kt   # كتالوج حيّ (remote → cache → bundled)
├── PlayerManager.kt       # ExoPlayer مركزي
└── UpdateChecker.kt       # GitHub releases/latest
content/catalog.json        # مصدر المحتوى الحيّ (عدّله لتحديث التطبيق دون إصدار جديد)
tools/verify_release.sh     # سكربت التحقق قبل رفع أي إصدار
docs/SIGNING.md             # بصمة التوقيع الثابتة + طريقة التحقق
worklog.md                  # سجل النشر والدروس المستفادة
```

## الترخيص

MIT — انظر [LICENSE](LICENSE).

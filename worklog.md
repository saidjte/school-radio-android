# worklog — سجل النشر والدروس المستفادة (school-radio-android)

توثيق كل خطوة في نشر هذا التطبيق، والدرس المستفاد من كل خطأ — حتى لا نكرره.

---

## v0.1.0 — الإصدار الأول (2026-10-23)

### خطوات النشر بالترتيب

1. **بيئة البناء**: cmdline-tools + platforms;android-35 + build-tools;35.0.0 + Gradle 8.10.2 + Temurin JDK 17.
   - درس: Java 21 في بيئة البناء كانت JRE فقط (لا `javac`) — فشل Gradle برسالة
     `does not provide the required capabilities: [JAVA_COMPILER]`. الحل: JDK كامل (17).
2. **الكود**: Kotlin 2.0.21 + AGP 8.7.3، minSdk 24 / targetSdk 35، Media3 ExoPlayer 1.4.1.
   - درس: في `build.gradle.kts` لا تكتب `java.util.Properties()` مباشرة — `java` يتعارض
     مع امتداد `java` في Gradle DSL (`Unresolved reference: util`). الحل: `import java.util.Properties` في أعلى الملف.
3. **التوقيع**: مفتاح RSA-2048 صلاحيته 30 سنة، alias `schoolradio`، يُقرأ في Gradle من متغيرات
   البيئة `KS_PATH / KS_PASS / KS_ALIAS / KS_ALIAS_PASS` — لا أسرار في المستودع أبداً.
   - بصمة SHA-256 ثابتة موثقة في `docs/SIGNING.md`؛ سكربت التحقق يرفض أي APK لا تطابقه.
4. **versionCode/versionName**: مصدر الحقيقة الوحيد هو `version.properties` في جذر المستودع —
   يقرأه Gradle، ويقرأه CI، ويقرأه سكربت التحقق. لا أرقام إصدار في أكثر من مكان.
5. **البناء الموقّع محلياً** ثم `tools/verify_release.sh`: فحص versionCode/versionName من
   `aapt dump badging`، فحص المعماريات من محتويات `lib/`، فحص البصمة بـ `apksigner verify --print-certs`.
   - درس: تطبيق Kotlin خالص (Media3 بلا امتدادات native) ينتج **APK عالمياً** بلا مجلد `lib/`
     — يعمل على arm64-v8a و armeabi-v7a معاً. فلتر `abiFilters` مثبّت لإلزام أي مكتبة native
     مستقبلية بالمعماريتين فقط. سكربت التحقق يتعامل مع الحالتين.
6. **CI**: سير عمل `build-android.yml` على push إلى `main`: JDK 17 → SDK → فك مفتاح من
   Actions Secrets → `assembleRelease` → سكربت التحقق → رفع **Artifact** فقط.
7. **الأسرار على GitHub**: تُشفَّر بـ libsodium sealed box باستعمال مفتاح المستودع العام
   (`GET /repos/{owner}/{repo}/actions/secrets/public-key`) ثم `PUT /actions/secrets/{name}`.
   الأسماء نفسها المستعملة في Atlas-Drift: `ANDROID_KEYSTORE_FILE` (base64) + 3 أسرار نصية.

### دروس النشر الحرجة (من منهجية المشاريع السابقة — تُطبَّق حرفياً)

> **CI لا ينشئ Release — الإنشاء يدوي إجبارياً.** سير عمل GitHub Actions يرفع Artifacts فقط.
> الإصدار يُنشأ يدوياً عبر REST API: `POST /repos/{owner}/{repo}/releases` ثم رفع الأصول إلى
> `uploads.github.com/.../releases/{id}/assets`.

- **التوكن**: `repo` + `workflow` — `workflow` ضروري لدفع ملفات `.github/workflows/`، بدونه يُرفض الدفع.
- **اسم APK موحّد**: `SchoolRadio_vX.Y.Z.apk` (يُضبط في Gradle عبر `outputFileName`) — رابط
  التحميل المباشر متوقع الشكل: `.../releases/download/vX.Y.Z/SchoolRadio_vX.Y.Z.apk`.
- **الحزمة المصدرية**: `git archive --format=zip --prefix=school-radio-android/ main -o SchoolRadio_Source_vX.Y.Z.zip`.
- **التحقق قبل التسليم**: كل رابط تحميل يُفحص بـ `curl -I` ويجب أن يعيد **HTTP 200 أو 206**
  قبل تسليمه للمستخدم.
- **الترقية فوق النسخة القديمة**: نفس المفتاح + نفس `applicationId` + versionCode متزايد =
  تثبيت التحديث مباشرة دون حذف. أي تغيير في المفتاح يكسر الترقية نهائياً ولا يُصلح إلا بإصدار جديد من الصفر.
- **دفع workflows**: ملفات `.github/workflows/` لا تُقبل في الدفع إلا إذا كان التوكن يحمل صلاحية `workflow`.
- **صفحة المعاينة**: `docs/index.html` مفعّل عبر GitHub Pages من فرع `main` (المجلد `/docs`) —
  تُحدَّث بروابط كل إصدار جديد بعد التحقق منها.

### قائمة تحقق قبل كل إصدار جديد (v0.X.0)

1. زد `versionCode` بمقدار 1 وحدّث `versionName` في `version.properties`
2. بناء موقّع بنفس المفتاح (`KS_*` من متغيرات البيئة)
3. `bash tools/verify_release.sh <apk>` — يجب أن ينجح كاملاً
4. commit بنمط `vX.Y.Z: bump versionName to X / versionCode to Y + <الوصف>`
5. `git archive` للحزمة المصدرية بالبادئة `school-radio-android/`
6. إنشاء Release عبر REST API (ليس CI!) ورفع `SchoolRadio_vX.Y.Z.apk` + `SchoolRadio_Source_vX.Y.Z.zip`
7. فحص الروابط المباشرة: HTTP 200/206 إلزامي
8. تحديث `docs/index.html` و`README.md` بروابط الإصدار الجديد

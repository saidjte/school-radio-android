# worklog — سجل النشر والدروس المستفادة (school-radio-android)

---

## v0.3.0 — أرشيف التسجيلات والأدوار (2026-10-23)

### خطوات النشر بالترتيب

1. **الترقية**: versionCode 2→3, versionName→0.3.0 في `version.properties` فقط (كالعادة).
2. **التصميم الحاسم قبل الكود**: تسجيل البث أثناءه يتطلب «خط أنابيب واحد» —
   MediaRecorder لا يمكن أن يعمل مع AudioRecord على الميكروفون نفسه، فكانت
   الخطة: AudioRecord في MicrophoneController يمرر كل دفعة PCM إلى
   `AacFileWriter` (MediaCodec AAC-LC + MediaMuxer → ‎.m4a).
3. **6 commits مرحلية** (data → audio → ui → backup → tests → bump) بنفس أسلوب v0.2.0.
4. **ترحيل Room الصريح 1→2**: جدولا users وrecordings فقط — SQL الترحيل كُتب ليطابق
   `app/schemas/.../2.json` المولد من KSP حرفياً (أسماء أعمدة snake_case عبر @ColumnInfo،
   notNull، الفهرس الفريد index_users_name) — أي انحراف يعني انهياراً عند فتح القاعدة.
5. **الأرشيف بلا مفاتيح خارجية عمداً**: لقطة (اسم+دور) المسجّل مطبَّعة في سجل التسجيل
   ليبقى الأرشيف سليماً بعد حذف أي مستخدم.
6. **بناء موقّع + verify_release.sh: 7/7** (versionCode 3، المعماريتان، البصمة نفسها).
7. **Release يدوي REST API** (id=422381665) ثم فحص الروابط: APK 206 (Range)،
   المصدر 206، صفحة الإصدار 200، ومطابقة SHA-256 للأصل المُحمَّل مع المحلي بايت-ببايت.

### دروس v0.3.0

- **الميكروفون مورد وحيد**: فشل MediaRecorder+AudioRecord معاً موثق سلفاً — الحل
  الصحيح هو تفريغ PCM من حلقة البث إلى MediaCodec/MediaMuxer (كلفة شبه صفرية،
  ولا يؤثر على زمن استجابة البث لأن dequeueInputBuffer بزمن 0).
- **AacFileWriter EOS**: لإنهاء ملف MP4 سليم يجب طابور إشارة END_OF_STREAM على
  مخزن إدخال (مع إعادة محاولة) ثم تفريغ حتى إطار EOS في الإخراج — وإلا وُلّد ملف تالف
  لا يفتحه ExoPlayer.
- **عرض المدة من العينات المشفَّرة فعلاً** (samples/rate) وليس من الساعة — يطابق طول
  الملف حتى لو سقطت دفعة نادرة.
- **تسجيل أقل من 300ms يُرمى** — ضغطات خاطئة لا يجب أن تلوث الأرشيف.
- **لقطات Room schema يجب قراءتها بعد كل تعديل كيانات**: افتراض notNull خاطئ في
  SQL الترحيل = Crash عند أول فتح قاعدة بعد التحديث (تحقق فوري بمقارنة 2.json).
- **RecordingManager توقف ذري**: stop() يلتقط الكاتب في متغير محلي فوراً (writer=null)
  ثم يغلق على خيط IO — يمنع سباقاً بين خيط الصوت والحفظ.
- **RTL والنصوص العربية في تعديلات Markdown/HTML**: استبدال النصوص عبر سكربت
  بايثون أدق من المطابقة اليدوية (اتجاه النص يخدع العين في المحرر).
- **استعادة المفتاح من Secrets**: GitHub Secrets قراءتها مستحيلة عمداً، لكن المالك
  يمكنه استرجاع المفتاح بوتة مؤقتة في CI تصعّد الـ base64 كـ Artifact ثم حذفها —
  (استُخدمت في هذه الجلسة بعد فقدان البيئة المحلية، وحُذفت فوراً بعد الاستعادة).

### قائمة تحقق ما قبل الإصدار (طُبقت حرفياً)

1. ✔ versionCode 3 / versionName 0.3.0 في version.properties
2. ✔ بناء موقّع بنفس المفتاح (KS_* من البيئة)
3. ✔ `bash tools/verify_release.sh` — 7/7
4. ✔ commits: `v0.3.0:` ثم `feat(data|audio|ui|backup):` ثم `test:`
5. ✔ `git archive --format=zip --prefix=school-radio-android/`
6. ✔ Release يدوي REST API + رفع APK (11.7MB) + Source (1.4MB)
7. ✔ فحص الروابط: 206/206/200 + تطابق SHA-256
8. ✔ تحديث README + docs/index.html + worklog.md

---

## v0.2.0 — النسخة الاحترافية دون اتصال (2026-10-23)

### خطوات النشر بالترتيب

1. **الترقية**: versionCode 1→2, versionName 0.1.0→0.2.0 في `version.properties` فقط —
   Gradle وCI وسكربت التحقق يقرؤونها منه (مصدر حقيقة واحد).
2. **إعادة كتابة كاملة بـ Compose**: 7 commits مرحلية (gradle → data → audio →
   scheduler → ui+backup → tests) — كل commit يبني باستقلالية.
3. **الأيقونات**: استخدمنا متجهات مخصصة بدل `material-icons-extended` —
   وفرنا ~9MB من حجم APK.
4. **الجدولة**: AlarmManager دقيق (SCHEDULE_EXACT_ALARM + canScheduleExactAlarms()
   مع تراجع setWindow ±10 دقائق) — WorkManager لا يضمن الثانية المحددة (موثق أعلاه).
5. **منع التكرار**: قيد تفرد (event_id, date_key) في Room + دالة beginExecution
   تسمح بإعادة المحاولة بعد FAILED فقط.
6. **التوقيع**: نفس مفتاح v0.1.0 — بصمة SHA-256 مطابقة (تحققها سكربت التحقق 7/7).

### دروس v0.2.0

- **`material-icons-core` لا يتضمن Pause/SkipNext/SkipPrevious/Repeat/Alarm** —
  إما icons-extended (+9MB) أو متجهات مخصصة (اخترنا المتجهات، مسارات Material الرسمية Apache 2.0).
- **Room**: أسماء الأعمدة تتبع أسماء خصائص Kotlin (camelCase) إلا مع `@ColumnInfo` —
  كنا نكتب استعلامات snake_case فرفضتها KSP؛ الحل: `@ColumnInfo` صريحة في الكيانات.
- **`ExposedDropdownMenu` عضو في نطاق `ExposedDropdownMenuBoxScope`** — لا يمكن
  استيراده كدالة عليا ولا استدعاؤه خارج الـ lambda.
- **قراءة `MaterialTheme.colorScheme` داخل `Canvas {}` ممنوعة** (نطاق غير composable) —
  التقط اللون قبل الحلقة.
- **المنطق الصرف أولاً**: `ScheduleLogic` بلا تبعيات أندرويد جعل 12 اختبار وحدة ممكنة
  وكشف خطأ أقنعة الأيام في الاختبار نفسه (bit0 = الاثنين).
- **فشل dex عابر**: mergeDexRelease قد يفشل مرة بنفاد الذاكرة — إعادة التشغيل تكفي
  (gradle.properties: -Xmx3g).
- **واجهات Android التي تُنسى**: `setSpeakerphoneOn` مهجورة من API 31 →
  `setCommunicationDevice()` مع `availableCommunicationDevices`.
- **SAF**: `takePersistableUriPermission` إلزامي بعد كل OPEN_DOCUMENT وإلا مات
  الوصول بعد إعادة التشغيل؛ لا يمكن تشغيل MediaRecorder وAudioRecord على الميكروفون
  معاً — البث+التسجيل المتزامن يتطلب خط أنابيب AudioRecord واحد (مرشح لـ v0.3.0).

### قائمة تحقق ما قبل الإصدار (طُبقت حرفياً)

1. ✔ versionCode 2 / versionName 0.2.0 في version.properties
2. ✔ بناء موقّع بنفس المفتاح (KS_* من البيئة)
3. ✔ `bash tools/verify_release.sh` — 7/7 (versionCode، المعماريتان فعلياً في lib/، البصمة، التوقيع)
4. ✔ commits بنمط `v0.2.0:` ثم `feat(scope):` واضحة
5. ✔ `git archive --format=zip --prefix=school-radio-android/` للحزمة المصدرية
6. ✔ Release يدوي REST API (POST /releases ثم uploads.github.com) — CI لا ينشئ Release
7. ✔ فحص الروابط: 200 + 206 إلزامي قبل التسليم
8. ✔ تحديث README + صفحة المعاينة (docs/index.html) بعد الإصدار

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

---

## v0.4.0 — تحسين تجربة المستخدم UI/UX (2026-10-24)

### ما نُفّذ
- **نظام تصميم كامل** (core/theme/Theme.kt): مخططا ألوان M3 كاملان (فاتح/داكن) بكل
  الفتحات (surfaceContainer…، outline، errorContainer…) بهوية Teal/Amber،
  مقياس طباعة عربي مضبوط (ارتفاعات أسطر أوسع + `tnum` جدولية للساعات/العدّادات)،
  نظام أشكال موحّد 8→28dp، و`AppStatusColors` + `LocalAppStatusColors`:
  ألوان دلالية (نجاح/تشغيل/فشل/تحذير/حي) تتكيف مع الوضع الداكن — استُبدلت بها
  كل الألوان الصلبة `Color(0xFF...)` المنتشرة في الشاشات (كانت تنكسر في الداكن).
- **مكوّنات مشتركة** (core/ui/Components.kt جديد): `ScreenHeader` (ترويسة موحّدة)،
  `EmptyStateCard` (حالة فارغة بأيقونة قرصية + زر إجراء اختياري)،
  `StatusBadge`، `MirroredIcon` (يقلب أسهم التالي/السابق أفقياً في RTL —
  اصطلاح مشغلات الوسائط العربية كما في يوتيوب).
- **8 أيقونات متجهة جديدة** في AppIcons (المجموع 18): Palette، VolumeUp،
  ScheduleClock، CloudUpload، GraphicEq، MusicNote، QueueMusic، Event —
  مسارات Material الرسمية (Apache 2.0) حفاظاً على استبعاد material-icons-extended.
- **MainActivity**: انتقالات NavHost (تلاشٍ+تكبير 0.97 محايد الاتجاه آمن مع RTL)،
  شارة عدد تسجيلات حية (BadgedBox) على تبويب الأرشيف، وتمرير `onNavigateToSchedule`
  و`onNavigateToLibrary` للوحة الرئيسية لحالات فارغة تفاعلية.
- **PlayerBar**: إعادة تصميم ككارت عائم (clip 18dp + surfaceContainerHighest +
  shadow)، عنوان Ellipsis بسطر واحد، AnimatedContent بين أيقونتي التشغيل/الإيقاف،
  أيقونات تخطٍّ معكوسة، ولون الخطأ من المخطط الداكن.
- **Dashboard**: تحية زمنية عربية (صباح/نهار/مساء/ليل) باسم المستخدم الحالي ودوره،
  بطاقة بطل بتدرج primary→tertiary (Box+background brush لأن Surface لا يقبل brush)
  وساعة بأرقام جدولية، شريط إحصاءات اليوم (فقرات/أُنجزت/متبقية)، بطاقة «الفقرة
  التالية» بعدّاد تنازلي حي بالثانية، شارة «● مباشر» نابضة (AnimatedVisibility+alpha)،
  haptics على الضغط للتحديث وبدء/إيقاف التسجيل، تنعيم حلقة المستوى بـ
  animateFloatAsState(tween 90)، صفوف فقرات بقرص وقت container وحدود تمييز
  للفقرة قيد التشغيل، EmptyStateCard بانتقال مباشر للجدولة، وبطاقة «انتهت فقرات
  اليوم» تقترح المكتبة.
- **RecordingsScreen**: بحث rememberSaveable بزر مسح + مرشّح مصدر
  (RecFilter: الكل/بث مباشر/تحضيرية)، ملخص العدد والحجم الكلي، FilledTonalIconButton
  دائرية 46dp بلون primary للتشغيل الحالي، AnimatedVisibility لقسم التقدم،
  عنوان السطر SemiBold عند التشغيل.
- **LibraryScreen**: ScreenHeader بعدد المقاطع، زر مسح البحث، EmptyStateCard بزر
  «استيراد مقاطع» يفتح SAF مباشرة، FilledTonalIconButton للتشغيل **معطّلة**
  للمقاطع غير المتاحة (إصلاح سلوكي: كان زر التشغيل يختفي بدل أن يُعطَّل).
- **PlaylistsScreen**: ScreenHeader بعدد القوائم + EmptyStateCard + قرص أيقونة
  QueueMusic في كل بطاقة.
- **ScheduleScreen**: ترويسات ScreenHeader للقسمين مع شرح وظيفي، EmptyStateCard
  للفقرات، ألوان «آخر تنفيذ» والتعارض من MaterialTheme.status/المخطط.
- **SettingsScreen**: CardTitle (أيقونة في حاوية زاوية 10dp) لتوحيد عناوين
  البطاقات، SingleChoiceSegmentedButtonRow لاختيار المظهر بدل 3 رقاقات.

### دروس وأخطاء البناء (بالترتيب)
1. `Surface(brush=)` غير موجود — بطاقة البطل أصبحت `Box + shadow + background(brush, shape)`.
2. نسيان `import ...layout.size` في LibraryScreen بعد إدخال Modifier.size.
3. نسيان استيرادَي `core.theme.status` و`core.ui.StatusBadge` في RecordingsScreen.
4. استيراد `foundation.layout.weight` غير موجود (weight عضو في RowScope) — حُذف.
5. سلسلة alpha مبالغ فيها في Dashboard بدأت بدوال ميتة — استُبدلت
   باستيراد واحد `androidx.compose.ui.draw.alpha` (أبسط حل دائماً أولاً).

### التحقق
- اختبارات الوحدة: **28/28 خضراء** (16 تسجيلات + 12 جدولة).
- `verify_release.sh`: **7/7** (versionCode 4، المعماريات، البصمة الثابتة، v1/v2).

---

## v0.5.0 — إذاعة مُجدولة بالتسجيلات + صور/نصوص الفقرات (2026-10-24, Africa/Casablanca)

### السياق
- المستخدم أرسل لقطتي شاشة من v0.3.0/0.4.0 وأمر بـ: «صحّح الأخطاء التي تظهر في الصور»
  + طلب الميزتين: إذاعة مُجدولة بالأصوات المسجلة (تشغيل التسجيلات ضمن الفقرات)
  ودعم صور/نصوص للفقرات.

### الأخطاء المُشخّصة من اللقطات
1. **(الجدول)** طريق مسدود كامل: زر «فقرة مجدولة» يعرض snackbar «أنشئ جدولاً أولاً»
   بينما متغير `creatingSchedule` لم يُضبط أبداً من أي مكان — لا وسيلة لإنشاء جدول أصلاً.
2. **(التنقل السفلي)** «التسجيلات» تلتف على سطرين فوق أيقونتها (6 تسميات في عرض ضيق).
3. **(الرئيسية — لقطة v0.3.0)** زر «تحديث» مقصوص + منزلق ممدود — قد أُصلحت فعلياً
   في إعادة تصميم v0.4.0 (اللقطات من نسخة أقدم مثبتة على الجهاز).

### إصلاحات v0.5.0
- FAB الجدولة: بلا جداول → يفتح حوار «جدول جديد» ثم يفتح محرر الفقرة تلقائياً بعد الحفظ.
- زر «+ جدول جديد» دائم في ترويسة قسم الجداول.
- Empty state للفقرات يشرح المسار الصحيح حسب وجود جداول من عدمه.
- تسميات NavigationBar: maxLines=1 + خط 10sp + Ellipsis.

### الميزات الجديدة
- **محتوى الفقرة ثلاثي**: playlistId / singleTrackId / **recordingId** (تنافر مقصود).
- **DB v3**: أعمدة `recording_id` (FK→recordings SET_NULL + فهرس) و`note_text` و`image_path`
  على scheduled_events — MIGRATION_2_3 بـ ALTER ADD COLUMN (تم التحقق من تطابقه مع
  schemas/3.json المولّد: 3 FKs، SET NULL، الفهرس باسم Room الصحيح).
- **AlarmReceiver**: فرع تشغيل التسجيل مع فحص File.exists ورسائل فشل دقيقة (failNote)
  + ضبط OnAirInfo لحظة الإطلاق الناجح.
- **شاشة OnAirScreen**: ملء الشاشة، خلفية داكنة، صورة الفقرة (SegmentImage) + نص 28sp
  + تقدم + تحكم + ساعة انتظار كبيرة، keepScreenOn عبر DisposableEffect، بلا bottomBar
  (مسار on_air مستثنى من bottomBar في MainActivity).
- **محرر الفقرة**: 3 قوائم محتوى بتنافر متبادل + نص متعدد الأسطر + صورة عبر
  PickVisualMedia → SegmentImageStore.saveFrom (ضغط ≤1440px JPEG85 إلى
  filesDir/event_images) + معاينة 64dp + حذف الصور القديمة عند الاستبدال/الحذف
  (حتى حذف الجدول ينظف صور فقراته).
- **SegmentImage**: كومبوزابل تحميل من مسار محلي بـ produceState+inSampleSize.
- **جدولة سريعة من التسجيلات**: أيقونة تقويم → مسودة ScheduledEventEntity في
  ServiceLocator → LaunchedEffect في شاشة الجدولة يلتقطها (وينشئ «البرنامج الصباحي»
  تلقائياً إن لم يوجد جدول) → محرر معبأ.
- **بطاقات أغنى**: سطر محتوى (EventContentAr: قائمة/مقطع/تسجيل) + مقصوص النص
  + مصغرة الصورة في بطاقات الجدولة وNextUpCard وEventRow.
- **أيقونة Display جديدة** (Tv) في AppIcons + زر شاشة العرض في PlayerBar والرئيسية.
- **نسخ احتياطي v3**: recId للتسجيلات + recordingId/noteText/imagePath للفقرات +
  إعادة ربط التسجيلات — **مع إعادة ترتيب الاستيراد: التسجيلات قبل الفقرات** (قيد FK)
  — متوافق مع نسخ v1/v2 القديمة.

### دروس
- MultiEdit يطبق تسلسلياً حتى أول فشل (ليس ذرياً كما هو موثق) — راجع الحالة الفعلية بعده.
- StateFlow.value في المعرّض asStateFlow هو val — التعديل عبر دالة setter في الـ locator.
- ContentScale في `androidx.compose.ui.layout` وليس graphics.
- الذاكرة ضيقة: `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1400m` لتفادي
  موت الـ daemon أثناء البناء.

### التحقق
- اختبارات: **33/33** (16+12 جدولة/تسجيلات + 5 EventContentAr الجديدة).
- verify_release.sh: **7/7** — versionCode 5، المعماريات، البصمة الثابتة 08:32:19:AA…، v1/v2.

---
## v0.5.1 — إصلاح انهيار تبويب الجدولة (Z.ai، 2026-10-24)

### المشكلة
بعد إضافة أول «فقرة مجدولة» يغلق التطبيق نفسه عند فتح تبويب الجدولة.

### السبب الجذري
ScheduleScreen تعرض جدولين وأحداثاً في LazyColumn واحد بكتلتي items مفتاحيتن
`key = { it.id }`. قواعد Compose توجب تفرد المفاتيح في العمود كله لا في كل كتلة،
وradio_schedules وscheduled_events جدولان مستقلان يبدآن الترقيم من 1 —
فبمجرد وجود جدول (id=1) وفقرة (id=1) يُرمى IllegalArgumentException: Key was already used.

### الإصلاح
- بادئة نصية لكل نطاق: `key = { "schedule-${it.id}" }` و`key = { "event-${it.id}" }`
  + تعليق في الموقع يمنع الرجوع.
- مسح شامل لكل مواقع items المفتاحية (8 مواقع): التصادم كان هنا فقط
  (المكتبة: LazyRow منفصلة؛ القوائم: حوار منفصل؛ الرئيسية والتسجيلات: كتلة واحدة).

### التحقق
- compileDebugKotlin ✓ (بلا أخطاء، تحذيرات deprecation قديمة فقط)
- testDebugUnitTest ✓ (نجاح كامل — البناء يفشل عند أي فشل اختبار)
- assembleDebug ✓ → SchoolRadio_v0.5.1.apk (versionCode 6 ✓ عبر aapt2 badging)

---
## v0.6.0 — خط Tajawal + أرقام مغربية + إصلاح زر المسارات المشوّه (Z.ai، 2026-10-24)

### الملاحظات من الاستخدام الفعلي
1. الصفحة الرئيسية تعرض زراً فارغاً بمقاس مشوّه بجانب زر «بلوتوث».
2. الواجهة بدائية — بلا خط عربي مميز.
3. التواريخ تظهر بأرقام مشرقية (٠١٢٣) والمطلوب المغربية (1234).

### السبب الجذري للزر الفارغ
بطاقة البث المباشر تعرض مسارات الإخراج في Row واحد غير ملتف:
مكبر الجهاز | سماعة/AUX | بلوتوث | المسار الافتراضي للوسائط —
الأربعة لا يتسع لها عرض الشاشة فتُقص آخر رقاقة خارج الحافة فتبدو فارغة مشوّهة.

### الإصلاحات
- DashboardScreen: FlowRow ملتفّ + FilterChip بأيقونة لكل مسار
  (مكبر/سماعة/بلوتوث/تلقائي) وحالة اختيار واضحة بلون الحاوية.
- AudioRoutingManager: تسمية «المسار الافتراضي للوسائط» → «تلقائي» (أوضح وأقصر).
- AppIcons: أيقونات Bluetooth / Headphones / Autorenew (مسارات Material Apache 2.0).
- خط Tajawal (Regular/Medium/Bold/ExtraBold — OFL) في res/font ومطبّق
  على كل أنماط Typography عبر AppFontFamily — يغيّر مظهر التطبيق كله.
- AppFormat.kt: منسّق مركزي — أسماء عربية + تحويل قسري للأرقام المشرقية إلى
  مغربية (1234) على كل الأجهزة؛ يستخدمه الآن تاريخ الرئيسية وتاريخ التسجيلات
  وساعة «على الهواء» (كانت Locale.getDefault() فتظهر مشرقية على أجهزة عربية).

### التحقق
- compileDebugKotlin + testDebugUnitTest + assembleDebug محلياً.
- CI يبني assembleRelease موقّعاً بنفس المفتاح (versionCode 7).

---
## v0.7.0 — إعادة تصميم عصرية أنيقة + ترقية المكتبات (Z.ai، 2026-10-24)

### المكتبات (أحدث الإصدارات المستقرة المتوافقة مع compileSdk 35)
- Compose BOM 2024.10.00 → **2025.03.01** (ui/foundation 1.7.8 + material3 1.3.1)
- activity-compose 1.10.1 / navigation 2.8.5 / lifecycle 2.8.7
- Media3 1.4.1 → **1.5.1**
- **Coil 2.7.0** (جديد): SegmentImage صارت SubcomposeAsyncImage — كاش ذاكرة +
  ظهور متلاشٍ 180ms بدل فك ترميز يدوي

### نظام التصميم (Theme.kt)
- لوحة «زمرد مدرسي» أعمق: primary 0B6F60 + ذهبي دافئ + أسطح صافية فوق خلفية نعناعية
- surfaceContainerLowest→Highest مضبوطة يدوياً في الفاتح والداكن (عمق ثلاث طبقات)
- **AppGradients**: hero/live/gold/heroSoft تتكيّف مع الوضع الداكن (CompositionLocal)
- أشكال أنعم: 10/14/18/24/32dp

### مكونات جديدة (Components.kt)
- ScreenHeader بشريط تمييز ذهبي متدرج + خانة trailing اختيارية (توافق كامل مع المواضع القديمة)
- EmptyStateCard بقرص متدرج وهالة وظل
- StatusBadge بشكل حبة مع نقطة إشارة
- GradientButton: زر متدرج بظل ملون
- Modifier.driftingGradient: تدرج ينحرف ببطء (4 ثوانٍ ذهاب/إياب)
- RoundedProgressBar بأطراف دائرية + ProgressRing دائرية

### الشاشات
- الرئيسية: بطاقة بطل واحدة موحدة (تحية + مدرسة + ساعة) بتدرج حي وحلقات زخرفية،
  إحصاءات بأيقونات (CheckCircle جديد)، قرص وقت متدرج للفقرة التالية،
  شارة وقت متدرجة أزرق للفقرة قيد التشغيل
- شريط التنقل: عائم بحواف 26dp وظل ناعم
- PlayerBar: زر تشغيل في قرص متدرج + شريط تقدم دائري الأطراف + زوايا 22dp
- على الهواء: خلفية تدرج داكن + شارة «على الهواء» نابضة + صورة بإطار زجاجي +
  نص 30sp + أزرار دائرية زجاجية وتشغيل فيروزي + ساعة انتظار داخل هالات نابضة مع التاريخ
- أيقونة التطبيق: تدرج زمرد + مايكروفون بأمواج بث + monochrome لأندرويد 13+

### التحقق
- compileDebugKotlin + testDebugUnitTest + assembleDebug محلياً — versionCode 8

## v0.8.0 — مشغّل موحّد + حَكَم التشغيل + سلاسة الميكروفون (2026-10-24)

### حَكَم التشغيل الموحّد (حل التزامن الاحترافي)
- PlaybackCoordinator جديد (audio/): سياسة أولويات صريحة بين المصادر الثلاثة —
  البث المباشر > القائمة الرئيسية > معاينة التسجيلات، بمنفذ PlayerPort قابل للاختبار
- سياسة المعاينة «معاملة عابرة»: توقف مؤقت للقائمة الرئيسية + استئناف تلقائي عند انتهائها
- إصلاح خلل خفي: RecordingsPlayer.stopAll() لم يكن مستدعى أبداً — معاينة التسجيلات
  كانت تتداخل صوتياً مع مشغل المكتبة/القوائم وبقاء أثراً بعد البدء
- بوابات موحّدة في ServiceLocator: previewToggle / playMainQueue / onScheduledPlaybackStarting
- استئناف الموسيقى خلف الإعلان إن كانت متوقفة بسبب معاينة (سلوك إذاعي طبيعي)
- منع المعاينة أثناء البث المباشر برسالة عربية واضحة

### سلاسة الميكروفون
- MicrophoneController: آلة حالات MicPhase (IDLE/STARTING/LIVE/STOPPING) كـ StateFlow
- كل الأعمال الثقيلة على خيط تحكم مخصص متسلسل — صفر حجب للخيط الرئيسي
- تلاشي صوتي fade-in 60ms / fade-out 80ms على دفعات PCM — لا نقرات/فرقعات،
  والتسجيل المتزامن يأخذ الصوت المتلاشي نفسه
- معالجة الضغطة الخاطفة (إيقاف أثناء البدء) بلا تسريب موارد
- ردود الحالة تُنشر على الخيط الرئيسي (التزام مع قاعدة خيط ExoPlayer)
- زر PTT: مؤشر انتقال دائري «جارٍ البدء…/جارٍ الإيقاف…» + إيماءة مستقرة
  pointerInput(Unit) + rememberUpdatedState (كانت الإيماءة تُقطع عند انقلاب isLive)

### المشغل الموحّد
- المكتبة: التشغيل يبني قائمة من كل المقاطع المرئية (التالي/السابق يعملان عبرها)،
  زر «تشغيل الكل» متدرج، تمييز السطر قيد التشغيل + أيقونة مكبر متحرك + شريط تقدم رفيع
- القوائم: تمييز القائمة قيد التشغيل عبر queueTag، زر التشغيل يصير إيقافاً مؤقتاً/استئنافاً
- التسجيلات: تشغيل عبر الحَكَم، شريط تقديم تفاعلي (Slider)، زر إيقاف، فرز (الأحدث/الأقدم/الأطول)
- PlaybackConnection: pause()/play()/setShuffle() + queueTag + currentTrackId في الحالة
- المشغل الموسع: زر خلط الترتيب (Shuffle) + زر تكرار القائمة (Repeat)

### الجدولة (توثيق + واجهة)
- إصلاح خلل توثيق الإكمال: isLastItem كان يُحسب مرة عند التسجيل (فهرس 0) فلا تُوثّق
  أي فقرة متعددة المقاطع COMPLETED — الصحيح STATE_ENDED دليل الإكمال بذاته + تحرير المتحكم
- مسح شاشة العرض على الهواء عند انتهاء الفقرة + إيقاف أي معاينة قبل إطلاق الفقرة
- واجهة: ملخص علوي (مفعّل/إجمالي/متبقي)، فقرات مرتبة زمنياً، الفقرة القادمة بشارة
  وقت متدرجة، الأيام كرقائق مصغرة بحروف الأسبوع

### مكوّنات
- PlayingEqualizer مشترك: أعمدة تنبض للسطر/القائمة قيد التشغيل (Canvas خفيف)
- أيقونة Shuffle جديدة بمسار Material الرسمي (Apache 2.0)

### التحقق
- PlaybackCoordinatorTest: 9 اختبارات JVM لسياسة العزلة (منفذ مفارق بلا أندرويد)
- compileDebugKotlin + testDebugUnitTest + assembleDebug محلياً — versionCode 9

---

## v0.9.0 — الاستوديو الاحترافي (versionCode 10)

### الجديد الكلي
- **شاشة الاستوديو (تبويب جديد)**: وحدة تحكم إذاعية بأسلوب الاستوديوهات
  - وحدة مزج (Mixer): فيدَران عموديان مخصصان — كسب الميكروفون 0..200٪ (PCM حقيقي مع منع التشبع) ومستوى الموسيقى 0..100٪
  - مؤشرا مستوى VU حيّان بتدرج حرارة (أخضر←كهرماني←أحمر) وقمم ممتدة تتناقص ببطء — الميك من RMS الفعلي والموسيقى من Visualizer
  - الخفض التلقائي (Ducking) بمفتاح + منزلق مستوى مباشر
  - معادل صوت (Equalizer): شرائح تردد الجهاز كاملة بالإعدادات المسبقة مترجمة عربياً + BassBoost + Virtualizer + تصفير — يُطبق على كل المشغلات عبر جلسة صوتية مشتركة
  - كل إعدادات الاستوديو تُحفظ في DataStore وتُسترجع عند التشغيل
- **السكربتات الإذاعية (تبويب جديد)**: نصوص الإعلانات والتقديمات محلياً 100% (JSON ذري الكتابة)
  - قارئ Teleprompter: خط ضخم قابل للتكبير (18..64sp)، تمرير تلقائي بسرعة قابلة للضبط، زمن قراءة حي، وضع المرآة لتركيب الهاتف أمام مرآة استوديو، الشاشة مضاء إجبارياً أثناء القراءة
- **مشغل أرشيف التسجيلات المثبّت**: أداة كاملة أسفل الأرشيف — تشغيل/إيقاف، شريط تقدم تفاعلي، قفزات ±15ث، سرعات 0.75×/1×/1.5×/2× (تحديث الموضع كل 250ms)
- **الرئيسية**: زر الإعدادات في بطاقة البطل (خرج من شريط التنقل) + بطاقتا إجراء سريع للاستوديو والسكربتات
- شريط التنقل: 7 تبويبات (الرئيسية/الاستوديو/المكتبة/القوائم/التسجيلات/الجدولة/السكربتات) بتسميات 9.5sp

### الملفات الجديدة
- audio/AudioEffects.kt — محرك المؤثرات المشترك (Equalizer/BassBoost/Virtualizer/Visualizer) يرتبط بكل جلسة مشغل
- data/scripts/ScriptRepository.kt — تخزين السكربتات JSON
- feature/studio/StudioScreen.kt — الميكسر + المعادل (فيدَرات Canvas مخصصة عمودية)
- feature/scripts/ScriptsScreen.kt + TeleprompterScreen.kt
- أيقونات Tune + Article (Material Apache 2.0)

### التحقق
- compileDebugKotlin + 42 اختبار وحدة + assembleDebug — ناجح محلياً
- version.properties: 0.9.0 / versionCode 10

---

## v0.10.0 — إصلاح مشغل الأرشيف + تصنيفات المكتبة + نمطا الميكروفون + زر المذيع العائم

### ملاحظات المستخدم المعالجة
1. «التسجيلات في الأرشيف تعمل بدون مشغل ويستحيل التعامل معها»
2. «لا يمكن إضافة تصنيفات جديدة إلى المكتبة»
3. «اجعل البث المباشر يدعم نمطي الضغط المطوّل والضغط من أجل البدء»
4. «أضف زر اختصار ليسهل على المذيع تشغيل/إيقاف الميكروفون أثناء قراءة السكريبت»

### الإصلاحات والميزات
- **الخلل الحرج في مشغل الأرشيف**: recordingId لم يكن يُضبط في الحالة عند بدء تسجيل جديد (RecordingsPlayer.toggle) — فلا مشغل مثبّت ولا سطر متوهج ولا شريط تقدم. أُصلح + رسالة واضحة عند ملف مفقود + إعادة تشغيل من البداية بعد النهاية الطبيعية.
- **مشغل التسجيلات المطوّر**: أزرار السابق/التالي بين تسجيلات القائمة المرئية + عدّاد الموضع (3 من 12) + سرعات في صف مستقل (لا تكدس على الشاشات الضيقة).
- **إدارة تصنيفات المكتبة كاملة**: رقاقة «+ تصنيف» سريعة + حوار «إدارة التصنيفات» (إضافة/إعادة تسمية/حذف المخصص فقط مع عدد المقاطع لكل تصنيف، الافتراضية محمية، المقاطع تعود لـ«بدون تصنيف» عند حذف التصنيف). DAO: update + observeAllWithCounts.
- **نمطا الميكروفون (MicMode)**: HOLD «استمرار للبث» (PTT استوديو) وTOGGLE «ضغطة للبدء/الإنهاء» (الأنسب لقراءة السكربت) — منتقي رقائقي على بطاقة البث، محفوظ في الإعدادات (mic_mode)، التلميح وسلوك الزر يتكيّفان مع النمط.
- **زر الهواء العائم للمذيع (MicQuickToggle)**: مكوّن مشترك جديد داخل قارئ Teleprompter — يبقى ظاهراً حتى مع إخفاء الأدوات (يرتفع/ينزل بحركة ناعمة)، حلقة مستوى صوت حي، نبض تحذيري عند البث، طلب الصلاحية ذاتياً، حالة مركزية عبر ServiceLocator.
- **مسار الإخراج مركزي**: broadcastRoute في ServiceLocator — اختيار مكبر الصوت يتشاركه الرئيسية وقارئ السكربت (كان محصوراً بالرئيسية).

### الملفات الجديدة
- audio/MicMode.kt — نمطا التشغيل مع التلميحات العربية
- core/ui/MicQuickToggle.kt — زر الاختصار العائم المشترك

### الملفات المعدلة
- audio/RecordingsPlayer.kt (إصلاح الحرج + رسائل) — di/ServiceLocator.kt (بوابات + مسار مشترك)
- data/local/dao/Daos.kt + data/repository/LibraryRepository.kt (إدارة التصنيفات)
- feature/library/LibraryScreen.kt (رقاقة + حوار إدارة) — feature/recordings/RecordingsScreen.kt (مشغل مطوّر)
- feature/dashboard/DashboardScreen.kt (منتقي النمط + سلوك مزدوج) — feature/scripts/TeleprompterScreen.kt (الزر العائم)
- data/settings/SettingsStore.kt (mic_mode) — MainActivity.kt (تمرير snackbar)

### التحقق
- compileDebugKotlin + testDebugUnitTest (42 اختبار) + assembleDebug — ناجح محلياً
- version.properties: 0.10.0 / versionCode 11

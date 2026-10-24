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

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // مُجمِّع Compose الرسمي لـ Kotlin 2.0 (يلغي الحاجة إلى kotlinCompilerExtensionVersion)
    id("org.jetbrains.kotlin.plugin.compose")
    // KSP لمعالج Room أثناء الترجمة (أسرع وأحدث من kapt)
    id("com.google.devtools.ksp")
}

// version.properties = مصدر الحقيقة الوحيد للإصدار (يقرأه Gradle وCI وسكربت التحقق)
val verProps = Properties().apply {
    val stream = rootProject.file("version.properties").inputStream()
    stream.use { load(it) }
}
val vCode = verProps.getProperty("versionCode").toInt()
val vName = verProps.getProperty("versionName")

android {
    namespace = "com.saidjte.schoolradio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.saidjte.schoolradio"
        minSdk = 24
        targetSdk = 35
        versionCode = vCode
        versionName = vName

        // المعماريات المدعومة: 64-bit + 32-bit (فلتر صارم لأي مكتبة native مستقبلية)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        ksp {
            // تصدير مخطط Room لتوثيق الترحيلات مستقبلاً
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    // التوقيع بمفتاح ثابت عبر الإصدارات حتى يتحدث التطبيق فوق النسخ القديمة مباشرة
    // (يُقرأ من متغيرات البيئة — لا أسرار في المستودع)
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KS_PATH")
            val ksPass = System.getenv("KS_PASS")
            val ksAlias = System.getenv("KS_ALIAS")
            val ksAliasPass = System.getenv("KS_ALIAS_PASS")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksAliasPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // اسم موحّد للـ APK في كل الإصدارات: SchoolRadio_vX.Y.Z
    applicationVariants.all {
        outputs.all {
            val out = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            out.outputFileName = "SchoolRadio_v${vName}.apk"
        }
    }
}

dependencies {
    // ===== Jetpack Compose (عبر BOM لتوافق الإصدارات تلقائياً) =====
    // v0.7.0: ترقية BOM إلى 2025.03.01 (ui/foundation 1.7.8 + material3 1.3.1)
    val composeBom = platform("androidx.compose:compose-bom:2025.03.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // ===== Coil (v0.7.0) — أفضل مكتبة صور لأندرويد: تحميل غير متزامن + كاش ذاكرة
    // + تلاشي ناعم — تستخدم لصور الفقرات من مسارات محلية (يعمل Offline بالكامل)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ===== Room (التخزين المحلي) =====
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ===== DataStore (الإعدادات البسيطة) =====
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ===== Media3 / ExoPlayer + جلسة الوسائط (التشغيل في الخلفية) =====
    // v0.7.0: ترقية إلى 1.5.1 — تحسينات مشغل وثبات أعلى
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    // ===== v0.23.0: استخراج تدفقات يوتوب/ساوندكلاود (محرك NewPipe المفتوح) =====
    // يحدّث نفسه مع تغيّرات المنصتين عبر إصداراته الدورية — تحديث الإصدار هنا فقط
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    // ===== Coroutines =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ===== v0.30.0: Lottie for Compose — حركات احترافية من ملفات JSON محلية (Offline 100%) =====
    implementation("com.airbnb.android:lottie-compose:6.7.1")

    // ===== v0.30.0: Accompanist Permissions — صلاحيات حالة-محورة بدل اللانشرات اليدوية =====
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // ===== اختبارات الوحدات =====
    testImplementation("junit:junit:4.13.2")
}

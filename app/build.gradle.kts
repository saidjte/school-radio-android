import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// قراءة الإصدار من version.properties (مصدر الحقيقة الوحيد)
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

        // المعماريات المدعومة: 64-bit + 32-bit
        // (تطبيق Kotlin خالص اليوم، لكن الفلتر يضمن احتواء أي مكتبة native مستقبلية على هاتين المعماريتين فقط)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // التوقيع بمفتاح ثابت عبر الإصدارات حتى يتمكن المستخدمون من التحديث فوق النسخة القديمة مباشرة
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Media3 / ExoPlayer لتشغيل الصوت (تسجيلات + بث)
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
}

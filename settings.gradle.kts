pluginManagement {
    repositories {
        // SR_CN_MIRRORS=1 — بيئة بناء محلية بمرايا أسرع (لا يؤثر على CI إطلاقاً)
        if (providers.environmentVariable("SR_CN_MIRRORS").isPresent) {
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            maven("https://repo.huaweicloud.com/repository/maven/")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.environmentVariable("SR_CN_MIRRORS").isPresent) {
            // المرايا العامة فقط — مجموعة NewPipe حصراً لـ jitpack (مصدرها الوحيد)
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") {
                content { excludeGroup("com.github.TeamNewPipe") }
            }
            maven("https://repo.huaweicloud.com/repository/maven/") {
                content { excludeGroup("com.github.TeamNewPipe") }
            }
        }
        google()
        mavenCentral()
        // v0.23.0: استخراج تدفقات يوتوب/ساوندكلاود عبر NewPipeExtractor —
        // الفلتر يقيّد المضيف لمجموعته فقط فلا يُستشار لأي اعتماد آخر
        maven("https://jitpack.io") {
            content { includeGroup("com.github.TeamNewPipe") }
        }
    }
}

rootProject.name = "SchoolRadio"
include(":app")

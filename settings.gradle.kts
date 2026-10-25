pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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

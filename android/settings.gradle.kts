pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "zender-android"
include(":core")

// Модуль app нужен для сборки APK. Отключить для тестов ядра: -Pzender.skipApp=true
if ((settings.providers.gradleProperty("zender.skipApp").orNull ?: "false") != "true") {
    include(":app")
}

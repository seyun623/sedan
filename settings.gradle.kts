pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    // Google's mirror of Maven Central first: CI runners often get "429 Too Many Requests" from Maven Central.
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    // Google's mirror of Maven Central first: CI runners often get "429 Too Many Requests" from Maven Central.
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
  }
}

rootProject.name = "MoneyFlow"
include(":app")

rootProject.name = "remo"

pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }
}

include("remo")

enableFeaturePreview("VERSION_CATALOGS")

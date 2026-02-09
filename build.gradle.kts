import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper

plugins {
  alias(libs.plugins.spotless)
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.vanniktech.maven.publish) apply false
}

allprojects {
  group = "ru.kode.android"

  buildscript {
    repositories {
      mavenCentral()
    }
  }

  repositories {
    mavenCentral()
  }
}

subprojects {
  plugins.withType<KotlinMultiplatformPluginWrapper> {
    apply(plugin = "com.vanniktech.maven.publish")

    configure<KotlinMultiplatformExtension> {
      explicitApi()
    }
  }
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("!**/build/**/*.*")
    ktlint(libs.versions.ktlint.get()).userData(mapOf("indent_size" to "2", "max_line_length" to "120"))
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("**/*.gradle.kts")
    ktlint(libs.versions.ktlint.get()).userData(mapOf("indent_size" to "2", "max_line_length" to "120"))
    trimTrailingWhitespace()
    endWithNewline()
  }
}

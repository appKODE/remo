@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("maven-publish")
  id(libs.plugins.kotlinMultiplatform.get().pluginId)
  id(libs.plugins.dokka.get().pluginId)
  id(libs.plugins.vanniktech.maven.publish.get().pluginId)
}

group = "ru.kode.android"
version = libs.versions.remoVersion.get()

kotlin {
  jvm()
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  macosX64()
  macosArm64()

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.bundles.coroutines)
        api(libs.kotlinResult)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.bundles.koTestCommon)
        implementation(libs.turbine)
      }
    }

    jvmMain {
      dependencies {
        implementation(kotlin("stdlib-jdk8"))
      }
    }

    jvmTest {
      dependencies {
        implementation(libs.bundles.koTestJvm)
      }
    }
  }
}

mavenPublishing {
  coordinates(artifactId = "remo")

  publishToMavenCentral()
  signAllPublications()

  pom {
    name.set("remo")
    description.set("Domain models with reactive repeatable tasks")
    inceptionYear.set("2022")
    url.set("https://github.com/appKODE/remo")

    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }

    developers {
      developer {
        id.set("dmitrii.suzdalev.dz")
        name.set("DIma")
        email.set("dz@kode.ru")
      }
    }

    scm {
      url.set("https://github.com/appKODE/remo")
      connection.set("scm:git:https://github.com/appKODE/remo.git")
      developerConnection.set("scm:git:ssh://git@github.com:appKODE/remo.git")
    }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    moduleName.set("remo-library")
  }
}

tasks.named<Test>("jvmTest") {
  useJUnitPlatform()
  testLogging {
    showExceptions = true
    showStandardStreams = true
    events = setOf(
      org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
    )
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

listOf("iosX64Test", "iosSimulatorArm64Test", "macosX64Test", "macosArm64Test")
  .forEach { taskName ->
    tasks.named<AbstractTestTask>(taskName) {
      enabled = false
    }
  }

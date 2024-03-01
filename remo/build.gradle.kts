plugins {
  id(libs.plugins.kotlinMultiplatform.get().pluginId)
  id(libs.plugins.dokka.get().pluginId)
  `maven-publish`
}

kotlin {
  jvm {
    compilations.all {
      kotlinOptions {
        jvmTarget = "1.8"
        moduleName = "remo-library"
      }
    }
  }
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

  publishing {
    publications.withType<MavenPublication> {
    }
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

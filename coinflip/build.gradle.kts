import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.banasiak.coinflip"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.banasiak.coinflip"
    minSdk = 26
    targetSdk = 36
    versionCode = 68
    versionName = "2025/05"
  }
  buildFeatures {
    buildConfig = true
    viewBinding = true
  }
  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  testOptions {
    unitTests.all {
      it.useJUnitPlatform()
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

val ktlint: Configuration by configurations.creating

dependencies {
  implementation(libs.androidx.activity)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.hilt.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.material)
  implementation(libs.review.ktx)
  implementation(libs.seismic)
  implementation(libs.timber)
  ksp(libs.hilt.android.compiler)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kluent.android)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk.agent)
  testImplementation(libs.mockk.android)
  testImplementation(libs.turbine)
  testRuntimeOnly(libs.junit.platform.launcher)

  ktlint(libs.ktlint) {
    attributes {
      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
  }
}

val ktlintCheck by tasks.registering(JavaExec::class) {
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  description = "Check Kotlin code style"
  classpath = ktlint
  mainClass.set("com.pinterest.ktlint.Main")
  // see https://pinterest.github.io/ktlint/install/cli/#command-line-usage for more information
  args(
    "**/src/**/*.kt",
    "**.kts",
    "!**/build/**"
  )
}

tasks.check {
  dependsOn(ktlintCheck)
}

tasks.register<JavaExec>("format") {
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  description = "Check Kotlin code style and format"
  classpath = ktlint
  mainClass.set("com.pinterest.ktlint.Main")
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  // see https://pinterest.github.io/ktlint/install/cli/#command-line-usage for more information
  args(
    "-F",
    "**/src/**/*.kt",
    "**.kts",
    "!**/build/**"
  )
}
plugins {
  id("com.android.application")
  id("com.google.dagger.hilt.android")
  id("com.google.devtools.ksp")
  id("de.mannodermaus.android-junit5")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
  namespace = "com.banasiak.coinflip"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.banasiak.coinflip"
    minSdk = 26
    targetSdk = 35
    versionCode = 63
    versionName = "2024.08"
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
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.majorVersion
  }
}

val ktlint: Configuration by configurations.creating

dependencies {
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
  ksp(libs.dagger.compiler)
  ksp(libs.hilt.android.compiler)
  testImplementation(libs.junit.api)
  testImplementation(libs.kluent.android)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk.agent)
  testImplementation(libs.mockk.android)
  testImplementation(libs.turbine)
  testRuntimeOnly(libs.junit.engine)

  ktlint("com.pinterest.ktlint:ktlint-cli:1.1.0") {
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
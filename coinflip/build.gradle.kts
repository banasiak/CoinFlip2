plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.parcelize")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
}

android {
  namespace = "com.banasiak.coinflip"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.banasiak.coinflip"
    minSdk = 26
    targetSdk = 34
    versionCode = 52
    versionName = "2024.1.3"
  }

  buildFeatures {
    buildConfig = true
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
  buildFeatures {
    viewBinding = true
  }
}

val ktlint: Configuration by configurations.creating

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.review.ktx)
  implementation(libs.material)
  implementation(libs.hilt.android)
  implementation(libs.timber)
  implementation(libs.seismic)
  implementation(libs.kotlinx.coroutines.android)
  ksp(libs.dagger.compiler)
  ksp(libs.hilt.android.compiler)
  testImplementation(libs.junit)

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
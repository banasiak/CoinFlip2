plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
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
    versionCode = 49
    versionName = "2024.1.0"
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

val ktlint by configurations.creating

dependencies {
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
  implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
  implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("com.google.android.play:review-ktx:2.0.1")
  implementation("com.google.android.material:material:1.11.0")
  implementation("com.google.dagger:hilt-android:2.50")
  implementation("com.jakewharton.timber:timber:5.0.1")
  implementation("com.squareup:seismic:1.0.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.2")
  ksp("com.google.dagger:dagger-compiler:2.50")
  ksp("com.google.dagger:hilt-android-compiler:2.50")
  testImplementation("junit:junit:4.13.2")

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
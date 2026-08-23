import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.kover)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.banasiak.coinflip"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.banasiak.coinflip"
    minSdk = 26
    targetSdk = 36
    versionCode = 74
    versionName = "2026/07"
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
  testOptions {
    unitTests.all {
      it.useJUnitPlatform()
    }
  }
  lint {
    // pre-existing warnings are captured in the baseline so new ones fail loudly;
    // delete the file and run lint to regenerate after paying down the backlog
    baseline = file("lint-baseline.xml")
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

tasks.withType<Test>().configureEach {
  // CoinResourcesTests reads these straight off disk to assert the coin arrays and the drawables
  // they name still line up. Without declaring them Gradle calls the task up-to-date after a
  // resource-only edit and skips the guard; scoped to the two it actually reads so that editing a
  // translation does not rerun the whole suite.
  inputs
    .file(layout.projectDirectory.file("src/main/res/values/arrays.xml"))
    .withPropertyName("coinArrays")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs
    .dir(layout.projectDirectory.dir("src/main/res/drawable"))
    .withPropertyName("coinDrawables")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}

kover {
  reports {
    filters {
      excludes {
        // Hilt/Dagger and the Compose compiler emit these; nobody wrote them and nobody can test them
        annotatedBy("dagger.internal.DaggerGenerated")
        classes(
          "*.BuildConfig",
          "*.Hilt_*",
          "*_Factory*",
          "*_MembersInjector",
          "*_HiltModules*",
          "*ComposableSingletons*"
        )
        packages("hilt_aggregated_deps", "dagger.hilt.internal")

        // the UI layer is deliberately out of scope while there are no Compose tests. Drop these two
        // exclusions the day androidTest exists, or the report will keep flattering the UI.
        annotatedBy("androidx.compose.runtime.Composable")
        packages("com.banasiak.coinflip.ui.theme")
      }
    }
  }
}

val ktlint: Configuration by configurations.creating

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)
  implementation(libs.google.material)
  implementation(libs.google.review.ktx)
  implementation(libs.hilt.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.seismic)
  implementation(libs.timber)
  ksp(libs.hilt.android.compiler)
  ksp(libs.kotlin.metadata.jvm)
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
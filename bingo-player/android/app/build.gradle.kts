import java.util.Properties
import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val releaseKeystorePropertiesFile = rootProject.file("key.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = releaseKeystorePropertiesFile.exists() &&
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
        !releaseKeystoreProperties.getProperty(it).isNullOrBlank()
    } && releaseKeystoreProperties.getProperty("storeFile")?.let { file(it).isFile } == true
val allowDebugSigningForValidation = providers.gradleProperty("allowDebugSigningForValidation")
    .map(String::toBoolean)
    .getOrElse(false)
val requestsReleaseArtifact = gradle.startParameter.taskNames.any { task ->
    task.substringAfterLast(':') in setOf("bundleRelease", "assembleRelease", "packageRelease")
}

if (requestsReleaseArtifact && !hasReleaseSigning && !allowDebugSigningForValidation) {
    throw GradleException(
        "A signed Player release artifact requires android/key.properties with an existing keystore and all four signing values. " +
            "Debug signing is available only for non-publishing CI validation via -PallowDebugSigningForValidation=true."
    )
}

extensions.configure<ApplicationExtension> {
    namespace = "com.bakaroo.bingo_app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.bakaroo.bingo_app"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // Android 7.0 is the lowest player release target. Keep this explicit
        // so Flutter SDK upgrades cannot silently alter device support.
        minSdk = 24
        targetSdk = 36
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePropertiesFile.exists()) {
                storeFile = file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else if (allowDebugSigningForValidation) {
                signingConfigs.getByName("debug")
            } else {
                null
            }
        }
    }
}

dependencies {
    implementation("com.android.installreferrer:installreferrer:2.2")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

val isProprietary = gradle.startParameter.taskNames.any { task -> task.contains("Proprietary") }

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = libs.versions.app.version.appId.get()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = libs.versions.app.version.versionName.get()
        versionCode = libs.versions.app.version.versionCode.get().toInt()
        setProperty("archivesBaseName", "Gallery+-$versionCode")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // we cannot change the original package name, else PhotoEditorSDK won't work
            //applicationIdSuffix = ".debug"
            // Sign debug builds with the same release key (from the gitignored
            // keystore.properties) so debug installs upgrade over release ones.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("licensing")
    productFlavors {
        register("proprietary")
        register("foss")
        register("prepaid")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        if (isProprietary) {
            getByName("main").java.srcDirs("src/proprietary/kotlin")
        }
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get().toString())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = project.libs.versions.app.build.kotlinJVMTarget.get()
    }

    namespace = libs.versions.app.version.appId.get()

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "META-INF/library_release.kotlin_module"
        }
    }
}

dependencies {
    implementation(libs.simple.tools.commons) {
        exclude(group = "com.booking", module = "rtlviewpager")
        exclude(group = "com.duolingo.open", module = "rtl-viewpager")
        exclude(group = "com.andrognito.patternlockview", module = "patternlockview")
        // NOTE: com.github.naveensingh:rtl-viewpager must NOT be excluded - commons' MyViewPager
        // extends RtlViewPager, so it is needed at compile time. It resolves from jitpack.
    }

    implementation("com.github.naveensingh:androidphotofilters:193f2ae509")
    implementation(libs.bignerdranch.multiselector)
    implementation(libs.android.image.cropper)
    implementation(libs.exif)
    implementation(libs.android.gif.drawable)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.sanselan)
    implementation(libs.androidsvg.aar)
    implementation(libs.gestureviews)
    implementation(libs.subsamplingscaleimageview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.awebp)
    implementation(libs.apng)
    implementation(libs.sdk.panowidget)
    implementation(libs.sdk.videowidget)
    implementation(libs.okio)
    implementation(libs.picasso) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    compileOnly(libs.okhttp)

    ksp(libs.glide.compiler)
    implementation(libs.zjupure.webpdecoder)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "false")
    arg("room.expandProjection", "true")
}

// Apply the PESDKPlugin
if (isProprietary) {
    apply(from = "../gradle/imglysdk.gradle")
}

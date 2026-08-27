import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * local.properties에서 값을 읽습니다. 파일이나 키가 없으면 fallback을 씁니다.
 *
 * local.properties는 git에 올라가지 않습니다. API 키를 절대 이 파일이나
 * 소스 코드에 직접 쓰지 마세요. (docs/api-validation.md "앱 설정에 미치는 영향")
 */
fun localProperty(name: String, fallback: String): String {
    val contents = providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
    if (!contents.isPresent) return fallback
    val props = Properties().apply { load(contents.get().reader()) }
    return props.getProperty(name)?.takeIf { it.isNotBlank() } ?: fallback
}

android {
    namespace = "com.hellstation"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hellstation"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- API 주소 ---
        // 두 도메인 모두 HTTPS를 지원하지 않습니다(2026-08-22 확인).
        // AndroidManifest의 usesCleartextTraffic 설정이 필요한 이유입니다.
        buildConfigField(
            "String",
            "SEOUL_REALTIME_BASE_URL",
            "\"http://swopenapi.seoul.go.kr/api/subway/\"",
        )
        buildConfigField(
            "String",
            "SEOUL_OPENAPI_BASE_URL",
            "\"http://openapi.seoul.go.kr:8088/\"",
        )

        // --- 인증키 ---
        // 키가 없으면 "sample"이 들어갑니다. sample 키로도 빌드와 실행은 되지만
        // 실시간 도착정보는 서울역 하나만 조회됩니다.
        buildConfigField(
            "String",
            "SEOUL_OPENAPI_KEY",
            "\"${localProperty("SEOUL_OPENAPI_KEY", "sample")}\"",
        )
        buildConfigField(
            "String",
            "SEOUL_REALTIME_SUBWAY_KEY",
            "\"${localProperty("SEOUL_REALTIME_SUBWAY_KEY", "sample")}\"",
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // --- AndroidX 기본 ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- 데이터 (데이터·기능 담당용. 지금은 아직 쓰이지 않습니다) ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.okhttp.logging)

    // --- 테스트 ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

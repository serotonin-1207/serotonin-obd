import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/*
 * 배포용 서명 설정.
 *
 * 서명 키와 비밀번호는 저장소에 넣지 않는다. 프로젝트 루트의 `keystore.properties`
 * (버전 관리 제외)에서 읽어 온다. 작성법은 `keystore.properties.example` 참조.
 *
 * 파일이 없으면 release 빌드는 서명되지 않는다.
 * 이 경우 `assembleDebug` 는 정상 동작하므로 개발에는 지장이 없다.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists() &&
    keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.eunho.leafobd"
    // 최신 AndroidX(core 1.19, lifecycle 2.11)가 compileSdk 37 이상을 요구한다.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.eunho.leafobd"
        // API 26 이상: java.time(Instant, DateTimeFormatter)을 코어 라이브러리 디슈가링 없이 사용할 수 있다.
        minSdk = 26
        targetSdk = 36
        // 배포 첫 공개 버전. 업데이트 확인 기능이 이 값을 GitHub 의 최신 값과 비교한다.
        // 새 버전을 낼 때마다 versionCode 를 1씩 올리고 version.json 도 함께 갱신한다.
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 코드 축소·난독화는 켜지 않는다.
            // 오류 메시지에 클래스 이름을 그대로 보여 주어야 사용자가 상황을 설명할 수 있고,
            // 진단 앱에서 원인 추적 가능성이 용량 절감보다 중요하기 때문이다.
            optimization {
                enable = false
            }
            // release 는 기본적으로 debuggable = false 다.
            // 배포 APK 에서 진단 로그를 adb 로 꺼낼 수 없게 하는 핵심 설정이다.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME 을 진단 로그에 기록하기 위해 필요하다.
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

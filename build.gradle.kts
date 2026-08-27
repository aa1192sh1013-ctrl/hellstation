// 최상위 빌드 파일. 여기서는 플러그인 버전만 선언하고 적용은 하지 않습니다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

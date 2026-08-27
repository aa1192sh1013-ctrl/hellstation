package com.hellstation.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import com.hellstation.ui.state.ProvideAppSettings
import com.hellstation.ui.theme.HellStationTheme

/**
 * 앱의 유일한 Activity.
 *
 * 화면 전환은 전부 Compose Navigation이 처리합니다([HellNavHost]).
 * Activity를 더 만들지 마세요.
 *
 * ## 테마
 *
 * [HellStationTheme]이 색·글씨·모양뿐 아니라 화면 크기 구간과
 * "움직임 줄이기" 설정까지 아래로 내려보냅니다. 화면들이 그 값을 받아 쓰므로
 * 여기서 감싸는 것이 필수입니다.
 *
 * 밝게/어둡게는 사용자가 설정에서 직접 고를 수 있습니다. 그 값을 읽어야 테마를 정할 수
 * 있으므로 [ProvideAppSettings]가 [HellStationTheme]보다 **바깥**에 있습니다.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ProvideAppSettings { settings ->
                HellStationTheme(
                    darkTheme = settings.theme.resolveDark(isSystemInDarkTheme()),
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        HellNavHost()
                    }
                }
            }
        }
    }
}

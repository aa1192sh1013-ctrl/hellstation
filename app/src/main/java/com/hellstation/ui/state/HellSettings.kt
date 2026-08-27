package com.hellstation.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hellstation.data.local.settings.AppSettingsStore
import com.hellstation.domain.model.AppSettings
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.ThemeChoice
import kotlinx.coroutines.launch

/**
 * 사용자 설정을 화면에 내려보내는 자리.
 *
 * ## 왜 CompositionLocal 인가
 *
 * 기본 방향은 역 시트·결과 화면처럼 **깊은 곳**에서 필요합니다. 화면마다 인자로 타고
 * 내려가게 하면 관계없는 함수 시그니처가 줄줄이 바뀝니다. 테마와 마찬가지로 취향 값이라
 * 같은 방식으로 다룹니다.
 */

/** 지금 적용 중인 설정. 아직 못 읽었으면 기본값입니다. */
val LocalAppSettings = compositionLocalOf { AppSettings.DEFAULT }

/** 설정을 바꾸는 손잡이. */
class AppSettingsActions(
    val setTheme: (ThemeChoice) -> Unit,
    val setDefaultDirection: (Direction) -> Unit,
    val toggleFavorite: (StationId) -> Unit,
) {
    companion object {
        /** 미리보기용. 눌러도 아무 일도 안 일어납니다. */
        val NONE = AppSettingsActions(
            setTheme = {},
            setDefaultDirection = {},
            toggleFavorite = {},
        )
    }
}

val LocalAppSettingsActions = compositionLocalOf { AppSettingsActions.NONE }

/**
 * 앱 전체를 설정으로 감쌉니다. [com.hellstation.navigation.MainActivity]에서 한 번만 부르세요.
 *
 * **미리보기에서는 저장소를 건드리지 않습니다.** 미리보기는 Android 런타임이 없어
 * DataStore를 열면 그대로 실패합니다.
 */
@Composable
fun ProvideAppSettings(content: @Composable (AppSettings) -> Unit) {
    if (LocalInspectionMode.current) {
        content(AppSettings.DEFAULT)
        return
    }

    val context = LocalContext.current.applicationContext
    val store = remember(context) { AppSettingsStore(context) }
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsStateWithLifecycle(AppSettings.DEFAULT)

    val actions = remember(store, scope) {
        AppSettingsActions(
            setTheme = { scope.launch { store.setTheme(it) } },
            setDefaultDirection = { scope.launch { store.setDefaultDirection(it) } },
            toggleFavorite = { scope.launch { store.toggleFavorite(it) } },
        )
    }

    CompositionLocalProvider(
        LocalAppSettings provides settings,
        LocalAppSettingsActions provides actions,
    ) {
        content(settings)
    }
}

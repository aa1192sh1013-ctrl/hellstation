package com.hellstation.data.local.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hellstation.domain.model.AppSettings
import com.hellstation.domain.model.Direction
import com.hellstation.domain.model.StationId
import com.hellstation.domain.model.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "hellstation_settings")

/**
 * 사용자 취향을 기기에 저장합니다.
 *
 * 역 목록 캐시([com.hellstation.data.local.cache.StationCatalogStore])와 **다른 파일**을 씁니다.
 * 역 목록은 형식이 바뀌면 통째로 버리는 캐시지만, 이건 사용자가 직접 정한 값이라
 * 같이 날아가면 안 됩니다.
 *
 * 값은 문자열로 저장합니다. enum 순번(ordinal)으로 저장하면 나중에 항목 순서를 바꿀 때
 * 저장된 값의 뜻이 조용히 달라집니다.
 */
class AppSettingsStore(private val context: Context) {

    /** 저장된 설정. 읽다가 실패하면 기본값으로 이어 갑니다 — 설정 때문에 앱이 멈추면 안 됩니다. */
    val settings: Flow<AppSettings> = context.appSettingsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                theme = ThemeChoice.parse(prefs[KEY_THEME]),
                defaultDirection = Direction.entries
                    .firstOrNull { it.name == prefs[KEY_DIRECTION] }
                    ?: Direction.UP,
                favorites = prefs[KEY_FAVORITES].orEmpty(),
            )
        }

    suspend fun setTheme(choice: ThemeChoice) {
        context.appSettingsDataStore.edit { it[KEY_THEME] = choice.name }
    }

    suspend fun setDefaultDirection(direction: Direction) {
        context.appSettingsDataStore.edit { it[KEY_DIRECTION] = direction.name }
    }

    /** 즐겨찾기를 켜고 끕니다. 지금 상태를 읽어 뒤집는 것까지 여기서 합니다. */
    suspend fun toggleFavorite(id: StationId) {
        context.appSettingsDataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES].orEmpty()
            prefs[KEY_FAVORITES] =
                if (id.key in current) current - id.key else current + id.key
        }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DIRECTION = stringPreferencesKey("default_direction")
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
    }
}

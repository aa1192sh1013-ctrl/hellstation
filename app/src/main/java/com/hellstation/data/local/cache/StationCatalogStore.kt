package com.hellstation.data.local.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hellstation.domain.model.LatLng
import com.hellstation.domain.model.LineId
import com.hellstation.domain.model.Station
import com.hellstation.domain.model.StationId
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.stationCatalogDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "hellstation_station_catalog")

/**
 * 역 목록을 기기에 저장해 둡니다.
 *
 * **왜 필요한가:** 역 목록을 받으려면 API를 800번 가까이 나눠 불러야 하는데,
 * 지하철 안에서는 신호가 자주 끊깁니다. 한 번 받아 두면 다음부터는 오프라인에서도
 * 지도와 검색이 동작합니다.
 *
 * 저장 형식은 JSON 문자열입니다. 역 800개면 100KB 남짓이라 DataStore로 충분합니다.
 * 노선 개통 등으로 목록이 커지면 Room으로 옮기는 편이 낫습니다.
 */
class StationCatalogStore(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** 저장된 역 목록을 읽습니다. 없거나 깨졌으면 null. */
    suspend fun load(): StationCatalog? {
        val prefs = runCatching { context.stationCatalogDataStore.data.first() }.getOrNull()
            ?: return null
        val raw = prefs[KEY_STATIONS] ?: return null
        val version = prefs[KEY_VERSION] ?: 0L
        if (version != FORMAT_VERSION) return null

        return runCatching {
            val persisted = json.decodeFromString<List<PersistedStation>>(raw)
            StationCatalog(persisted.mapNotNull { it.toStation() })
        }.getOrNull()
    }

    /** 저장 시각(epoch 초). 없으면 null. */
    suspend fun savedAtEpochSeconds(): Long? =
        runCatching { context.stationCatalogDataStore.data.first()[KEY_SAVED_AT] }.getOrNull()

    suspend fun save(catalog: StationCatalog) {
        if (catalog.isEmpty) return
        val payload = runCatching {
            json.encodeToString(catalog.stations.map { PersistedStation.from(it) })
        }.getOrNull() ?: return

        runCatching {
            context.stationCatalogDataStore.edit { prefs ->
                prefs[KEY_STATIONS] = payload
                prefs[KEY_VERSION] = FORMAT_VERSION
                prefs[KEY_SAVED_AT] = System.currentTimeMillis() / 1000
            }
        }
    }

    /**
     * 짧은 이름을 쓰는 이유: 역이 800개라 필드명 길이가 그대로 파일 크기가 됩니다.
     */
    @Serializable
    private data class PersistedStation(
        val l: String,            // 노선 apiCode
        val c: String,            // 역 코드
        val n: String,            // 정규화 이름
        val d: String,            // 표시 이름
        val f: String? = null,    // FR_CODE
        val la: Double? = null,   // 위도
        val lo: Double? = null,   // 경도
        val t: List<String> = emptyList(), // 환승 노선 apiCode
    ) {
        fun toStation(): Station? {
            val line = LineId.fromApiCode(l) ?: return null
            return Station(
                id = StationId(line, c),
                name = n,
                displayName = d,
                frCode = f,
                location = if (la != null && lo != null) LatLng(la, lo) else null,
                transferLines = t.mapNotNull { LineId.fromApiCode(it) },
            )
        }

        companion object {
            fun from(station: Station) = PersistedStation(
                l = station.id.line.apiCode,
                c = station.id.stationCode,
                n = station.name,
                d = station.displayName,
                f = station.frCode,
                la = station.location?.latitude,
                lo = station.location?.longitude,
                t = station.transferLines.map { it.apiCode },
            )
        }
    }

    private companion object {
        /** 저장 형식이 바뀌면 올리세요. 예전 데이터는 자동으로 버려집니다. */
        const val FORMAT_VERSION = 1L

        val KEY_STATIONS = stringPreferencesKey("stations")
        val KEY_VERSION = longPreferencesKey("format_version")
        val KEY_SAVED_AT = longPreferencesKey("saved_at")
    }
}

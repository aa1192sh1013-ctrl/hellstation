package com.hellstation.data.repository

import com.hellstation.data.remote.ApiResult
import com.hellstation.data.remote.SeoulSubwayApi
import com.hellstation.data.remote.mapper.ArrivalMapper
import com.hellstation.data.remote.mapper.StationNameNormalizer
import com.hellstation.domain.model.Arrival
import com.hellstation.domain.model.Loadable
import com.hellstation.domain.model.UnavailableReason
import com.hellstation.domain.repository.ArrivalRepository

/**
 * 실시간 도착정보.
 *
 * 짧게 캐싱합니다. 화면 여러 곳(역 상세, 결과 화면)이 같은 역을 거의 동시에 물어보는데,
 * 그때마다 API를 부르면 쿼터만 태웁니다. 실시간성을 해치지 않도록 캐시 수명은 짧게 둡니다.
 */
class ArrivalRepositoryImpl(
    private val api: SeoulSubwayApi,
    private val mapper: ArrivalMapper,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ArrivalRepository {

    private class CacheEntry(val arrivals: List<Arrival>, val fetchedAtMillis: Long)

    private val cache = HashMap<String, CacheEntry>()
    private val lock = Any()

    override suspend fun arrivalsAt(stationName: String): Loadable<List<Arrival>> {
        val normalized = StationNameNormalizer.normalize(stationName)
        if (normalized.isBlank()) {
            return Loadable.Unavailable(UnavailableReason.NO_DATA, "역 이름이 비어 있습니다")
        }

        cached(normalized)?.let { return Loadable.Ready(it) }

        return when (val result = api.arrivals(normalized)) {
            is ApiResult.Success -> {
                val arrivals = mapper.mapAll(result.value)
                store(normalized, arrivals)
                if (arrivals.isEmpty()) {
                    // 열차가 없는 것과 데이터가 없는 것은 다르지만 여기선 구분할 수 없습니다.
                    // 운행 상태 판정(ArrivalAnalyzer)이 위에서 처리합니다.
                    Loadable.Ready(emptyList())
                } else {
                    Loadable.Ready(arrivals)
                }
            }

            is ApiResult.Failure -> {
                // 오래된 캐시라도 있으면 내려보냅니다. 빈 화면보다 낫습니다.
                staleCached(normalized)?.let {
                    return Loadable.Ready(it, isFallback = true)
                }
                Loadable.Unavailable(result.reason, userMessage(result.reason))
            }
        }
    }

    private fun cached(name: String): List<Arrival>? = synchronized(lock) {
        val entry = cache[name] ?: return null
        val age = nowMillis() - entry.fetchedAtMillis
        if (age <= FRESH_MILLIS) entry.arrivals else null
    }

    private fun staleCached(name: String): List<Arrival>? = synchronized(lock) {
        val entry = cache[name] ?: return null
        val age = nowMillis() - entry.fetchedAtMillis
        if (age <= STALE_USABLE_MILLIS) entry.arrivals else null
    }

    private fun store(name: String, arrivals: List<Arrival>) = synchronized(lock) {
        if (cache.size > MAX_CACHE_ENTRIES) cache.clear()
        cache[name] = CacheEntry(arrivals, nowMillis())
    }

    private fun userMessage(reason: UnavailableReason): String = when (reason) {
        UnavailableReason.NETWORK -> "지금 열차 정보를 받아오지 못했습니다"
        UnavailableReason.NO_KEY -> "실시간 인증키가 없어 이 역은 조회할 수 없습니다"
        UnavailableReason.OUTSIDE_SEOUL -> "서울시 구간 밖이라 실시간 정보가 없습니다"
        UnavailableReason.CLOSED -> "지금은 운행 시간이 아닙니다"
        UnavailableReason.NO_DATA -> "이 역의 열차 정보가 없습니다"
    }

    private companion object {
        /** 이 안에서는 API를 다시 부르지 않습니다. */
        const val FRESH_MILLIS = 15_000L

        /** 통신이 실패했을 때 이 정도까지는 옛 데이터라도 씁니다. */
        const val STALE_USABLE_MILLIS = 5 * 60_000L

        const val MAX_CACHE_ENTRIES = 60
    }
}

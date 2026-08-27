package com.hellstation.data.local.baseline

import com.hellstation.domain.model.BaselineKey
import com.hellstation.domain.model.BaselineSample
import com.hellstation.domain.repository.BaselineSource

/**
 * 여러 통계 소스를 순서대로 시도합니다. 앞엣것이 값을 주면 거기서 멈춥니다.
 *
 * 쓰는 순서:
 * 1. [CsvBaselineSource]      — 서울교통공사 실측 통계 (1~8호선)
 * 2. [ApproximateBaselineSource] — 시간대 패턴 어림값 (모든 노선)
 *
 * 이 순서 덕분에 **1~8호선은 실측값을, 나머지 노선은 어림값을** 받게 됩니다.
 * 어느 쪽이 쓰였는지는 [BaselineSample.quality]에 남으므로 신뢰도가 자동으로 갈립니다.
 */
class ChainedBaselineSource(
    private val sources: List<BaselineSource>,
) : BaselineSource {

    override suspend fun sample(key: BaselineKey): BaselineSample? {
        for (source in sources) {
            source.sample(key)?.let { return it }
        }
        return null
    }

    override suspend fun hasMeasuredData(): Boolean =
        sources.any { it.hasMeasuredData() }
}

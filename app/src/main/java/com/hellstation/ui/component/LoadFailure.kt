package com.hellstation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hellstation.domain.model.CrowdLevel
import com.hellstation.domain.model.UnavailableReason
import com.hellstation.ui.character.HellFace
import com.hellstation.ui.copy.HellCopy
import com.hellstation.ui.theme.HellStationTheme

/**
 * 정보를 못 받았을 때 보여주는 자리.
 *
 * ## 로딩과 실패는 다른 화면이어야 합니다
 *
 * 예전에는 둘 다 "받아오는 중입니다"로 보였습니다. 그러면 지하철에서 신호가 끊겼을 때
 * 사용자는 **가만히 기다리면 되는지, 다시 눌러야 하는지** 알 수 없습니다.
 * 계속 기다려도 아무 일도 일어나지 않는데 화면은 곧 될 것처럼 말하는 셈입니다.
 *
 * ## "다시 시도"를 늘 주지는 않습니다
 *
 * 인증키가 없거나 운행 시간이 아닌 것은 다시 눌러도 그대로입니다
 * ([HellCopy.isRetryable]). 그럴 때는 버튼 없이 이유만 알려 줍니다 —
 * 눌러도 안 되는 버튼은 없느니만 못합니다.
 */
@Composable
fun LoadFailure(
    reason: UnavailableReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 실패했을 때도 캐릭터는 나옵니다. 다만 놀리지 않고 멍한 표정입니다.
        HellFace(level = CrowdLevel.UNKNOWN, modifier = Modifier.size(64.dp))

        Text(
            text = HellCopy.failureHeadline(reason),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = HellCopy.failureSubtitle(reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (HellCopy.isRetryable(reason)) {
            Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                Text("다시 시도")
            }
        }
    }
}

@Preview(name = "실패 — 통신", showBackground = true)
@Composable
private fun PreviewFailureNetwork() {
    HellStationTheme {
        LoadFailure(reason = UnavailableReason.NETWORK, onRetry = {}, modifier = Modifier.padding(20.dp))
    }
}

@Preview(name = "실패 — 다시 눌러도 소용없음", showBackground = true)
@Composable
private fun PreviewFailureNoKey() {
    HellStationTheme {
        LoadFailure(reason = UnavailableReason.NO_KEY, onRetry = {}, modifier = Modifier.padding(20.dp))
    }
}

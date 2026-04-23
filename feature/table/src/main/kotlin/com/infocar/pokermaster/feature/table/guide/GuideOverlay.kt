package com.infocar.pokermaster.feature.table.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 가이드 모드 한 단계를 표현.
 *
 * 최소 3 단계:
 *  - [Welcome] : 최초 진입 안내
 *  - [ActionHint] : 현재 액션에서 어떤 선택을 해야 하는지 힌트
 *  - [Closing] : 핸드/세션 종료 안내
 */
sealed interface GuideStep {

    /** 표시 상단 제목. */
    val title: String

    /** 본문 내용. */
    val body: String

    /** "다음" 버튼 라벨. null 이면 다음 버튼 미표시. */
    val nextLabel: String?

    data object Welcome : GuideStep {
        override val title: String = "가이드 모드"
        override val body: String =
            "환영합니다! 지금부터 포커 테이블의 각 장면마다 간단한 설명이 뜹니다. 용어는 칩을 눌러 자세히 볼 수 있어요."
        override val nextLabel: String = "시작"
    }

    /**
     * 현재 액션에 대한 문맥 힌트.
     *
     * @param text 권장 행동/상황 설명. 예: "지금은 체크가 안전해요."
     * @param highlightTermKey 함께 강조할 용어 키 (옵션). UI 에서 [Glossary.find] 로 조회.
     */
    data class ActionHint(
        val text: String,
        val highlightTermKey: String? = null,
    ) : GuideStep {
        override val title: String = "액션 힌트"
        override val body: String = text
        override val nextLabel: String = "다음"
    }

    data object Closing : GuideStep {
        override val title: String = "수고하셨어요"
        override val body: String =
            "핸드가 끝났습니다. 가이드 모드는 설정에서 언제든 다시 켜거나 끌 수 있어요."
        override val nextLabel: String? = null
    }
}

/**
 * 가이드 모드 오버레이 — v1.1 §1.2.
 *
 * 테이블 위에 반투명 배경으로 깔리며, 중앙 하단에 카드 UI 로 [step] 을 보여준다.
 * [onNext] 는 다음 단계로, [onDismiss] 는 가이드를 닫는다.
 */
@Composable
fun GuideOverlay(
    step: GuideStep,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(PaddingValues(16.dp)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                GuideOverlayButtons(
                    nextLabel = step.nextLabel,
                    onNext = onNext,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun GuideOverlayButtons(
    nextLabel: String?,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Text(text = "가이드 끄기")
        }
        if (nextLabel != null) {
            Button(
                onClick = onNext,
                modifier = Modifier.align(Alignment.CenterEnd),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = nextLabel)
            }
        } else {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Text(text = "닫기")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun GuideOverlayWelcomePreview() {
    PokerMasterTheme {
        GuideOverlay(
            step = GuideStep.Welcome,
            onNext = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun GuideOverlayActionHintPreview() {
    PokerMasterTheme {
        GuideOverlay(
            step = GuideStep.ActionHint(
                text = "상대가 작게 베팅했어요. 핸드가 약하면 폴드, 중간이면 콜이 무난합니다.",
                highlightTermKey = Glossary.Call.key,
            ),
            onNext = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun GuideOverlayClosingPreview() {
    PokerMasterTheme {
        GuideOverlay(
            step = GuideStep.Closing,
            onNext = {},
            onDismiss = {},
        )
    }
}

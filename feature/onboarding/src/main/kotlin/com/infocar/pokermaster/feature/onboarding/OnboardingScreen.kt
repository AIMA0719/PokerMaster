package com.infocar.pokermaster.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.infocar.pokermaster.core.ui.theme.HangameColors
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 첫 실행 4단계 위저드: WELCOME → AGE_GATE → NICKNAME → PERMISSION.
 *
 * 완료 시 [onComplete] 콜백으로 결과 전달 — 영속화(DataStore/SharedPreferences) 는
 * 호출자(app 모듈) 책임. 모듈 자체는 상태 보관·UI만 담당.
 */
@Composable
fun OnboardingScreen(
    onComplete: (OnboardingResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(OnboardingState()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HangameColors.BackgroundBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.Center),
        ) {
            // 상단 진행 바 — step.ordinal / 3f (0..1).
            LinearProgressIndicator(
                progress = { state.step.ordinal / 3f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                color = HangameColors.SeatBorderActive,
                trackColor = HangameColors.SeatBg,
            )

            // 가운데 step 컨텐츠 — AnimatedContent 로 좌→우 슬라이드.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = {
                        val goingForward = targetState.ordinal > initialState.ordinal
                        val dir = if (goingForward) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        (slideIntoContainer(dir, tween(300)) + fadeIn(tween(300)))
                            .togetherWith(slideOutOfContainer(dir, tween(300)) + fadeOut(tween(300)))
                    },
                    label = "onb_step",
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeStep()
                        OnboardingStep.AGE_GATE -> AgeGateStep(
                            state = state,
                            onToggle = { state = state.copy(ageConfirmed = it) },
                        )
                        OnboardingStep.NICKNAME -> NicknameStep(
                            state = state,
                            onChange = { state = state.copy(nickname = it) },
                        )
                        OnboardingStep.PERMISSION -> PermissionStep()
                    }
                }
            }

            // 하단 네비게이션 Row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step != OnboardingStep.WELCOME) {
                    TextButton(onClick = { state = state.copy(step = prevStep(state.step)) }) {
                        Text(stringResource(R.string.onb_back))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))

                val isLast = state.step == OnboardingStep.PERMISSION
                Button(
                    enabled = state.canAdvance,
                    onClick = {
                        if (isLast) {
                            onComplete(
                                OnboardingResult(
                                    nickname = state.nickname,
                                    ageConfirmed = state.ageConfirmed,
                                ),
                            )
                        } else {
                            state = state.copy(step = nextStep(state.step))
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (isLast) R.string.onb_done else R.string.onb_next,
                        ),
                    )
                }
            }
        }
    }
}

private fun nextStep(current: OnboardingStep): OnboardingStep {
    val values = OnboardingStep.entries
    val idx = values.indexOf(current)
    return values.getOrElse(idx + 1) { current }
}

private fun prevStep(current: OnboardingStep): OnboardingStep {
    val values = OnboardingStep.entries
    val idx = values.indexOf(current)
    return values.getOrElse(idx - 1) { current }
}

// ----- Previews -----------------------------------------------------------

@Preview(showBackground = true, name = "1. Welcome")
@Composable
private fun WelcomeStepPreview() {
    PokerMasterTheme { WelcomeStep() }
}

@Preview(showBackground = true, name = "2. Age Gate")
@Composable
private fun AgeGateStepPreview() {
    PokerMasterTheme {
        AgeGateStep(
            state = OnboardingState(step = OnboardingStep.AGE_GATE, ageConfirmed = true),
            onToggle = {},
        )
    }
}

@Preview(showBackground = true, name = "3. Nickname")
@Composable
private fun NicknameStepPreview() {
    PokerMasterTheme {
        NicknameStep(
            state = OnboardingState(step = OnboardingStep.NICKNAME, nickname = "플레이어"),
            onChange = {},
        )
    }
}

@Preview(showBackground = true, name = "4. Permission")
@Composable
private fun PermissionStepPreview() {
    PokerMasterTheme { PermissionStep() }
}

package com.infocar.pokermaster.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 공통 Step 컨테이너: padding 24dp, 20dp 간격. */
@Composable
private fun StepContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

@Composable
fun WelcomeStep(modifier: Modifier = Modifier) {
    StepContainer(modifier = modifier) {
        Text(
            text = stringResource(R.string.onb_welcome_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onb_welcome_subtitle),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onb_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun AgeGateStep(
    state: OnboardingState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    StepContainer(modifier = modifier) {
        Text(
            text = stringResource(R.string.onb_age_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onb_age_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.ageConfirmed,
                onCheckedChange = onToggle,
            )
            Text(
                text = stringResource(R.string.onb_age_check),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (!state.ageConfirmed) {
            Text(
                text = "체크하지 않으면 다음 단계로 진행할 수 없습니다. 거절하실 경우 백키로 앱을 종료하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 약관/개인정보 동의 단계. 둘 다 체크해야 다음 진행. */
@Composable
fun TermsStep(
    state: OnboardingState,
    onTerms: (Boolean) -> Unit,
    onPrivacy: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    StepContainer(modifier = modifier) {
        Text(
            text = "약관 동의",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "포커마스터를 사용하려면 아래 두 가지에 동의가 필요합니다. 본 게임은 가상 칩으로 진행되며 실제 금전 거래는 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.termsAccepted, onCheckedChange = onTerms)
            Text(
                text = "[필수] 이용약관에 동의합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.privacyAccepted, onCheckedChange = onPrivacy)
            Text(
                text = "[필수] 개인정보 처리방침에 동의합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = "전문은 설정 → 법적 고지에서 다시 확인할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NicknameStep(
    state: OnboardingState,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    StepContainer(modifier = modifier) {
        Text(
            text = stringResource(R.string.onb_nick_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val trimmed = state.nickname.trim()
        val showBlankError = state.nickname.isNotEmpty() && trimmed.isEmpty()
        OutlinedTextField(
            value = state.nickname,
            onValueChange = { newValue ->
                if (newValue.length <= 12) onChange(newValue)
            },
            label = {
                Text(
                    text = stringResource(R.string.onb_nick_hint),
                    fontSize = 14.sp,
                )
            },
            supportingText = {
                if (showBlankError) {
                    Text(
                        text = stringResource(R.string.onb_nick_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text("${state.nickname.length} / 12")
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            isError = showBlankError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (state.canAdvance) {
                    keyboard?.hide()
                    onSubmit()
                }
            }),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp),
        )
    }
}

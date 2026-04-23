package com.infocar.pokermaster.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** 공통 Step 컨테이너: padding 24dp, 16dp 간격. */
@Composable
private fun StepContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun WelcomeStep(modifier: Modifier = Modifier) {
    StepContainer(modifier = modifier) {
        Text(
            text = stringResource(R.string.onb_welcome_title),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onb_welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.ageConfirmed,
                onCheckedChange = onToggle,
            )
            Text(
                text = stringResource(R.string.onb_age_check),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun NicknameStep(
    state: OnboardingState,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StepContainer(modifier = modifier) {
        Text(
            text = stringResource(R.string.onb_nick_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        OutlinedTextField(
            value = state.nickname,
            onValueChange = { newValue ->
                // enforce maxLength=12 at input level
                if (newValue.length <= 12) onChange(newValue)
            },
            label = { Text(stringResource(R.string.onb_nick_hint)) },
            singleLine = true,
            isError = state.nickname.isNotEmpty() && !state.canAdvance,
            modifier = Modifier.fillMaxWidth(),
        )
        // 에러 표시: 빈 문자열 입력 직후엔 조용히, 글자 수 초과(이미 차단됨) 또는 공백만 입력 시 표시.
        if (state.nickname.isNotEmpty() && state.nickname.isBlank()) {
            Text(
                text = stringResource(R.string.onb_nick_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun PermissionStep(modifier: Modifier = Modifier) {
    StepContainer(modifier = modifier) {
        Text(
            text = stringResource(R.string.onb_perm_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onb_perm_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

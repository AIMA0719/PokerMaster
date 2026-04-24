package com.infocar.pokermaster.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.DeviceFingerprint
import com.infocar.pokermaster.core.model.ModelEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 모델 게이트 화면 — v1.1 §1.2.A.
 *
 * 상태 흐름:
 *   Checking → (설치됨? Ready : NotInstalled)
 *   NotInstalled → (사용자 시작) → Downloading(Queued/Running)
 *   Downloading → (Succeeded → SHA 재검증 → Ready | Failed → Failed | Cancelled → NotInstalled)
 *   Ready → onReady() 호출 후 다음 라우트로 이동
 */
@Composable
fun ModelGateScreen(
    onReady: () -> Unit,
    entry: ModelEntry = DefaultModels.default,
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val store = remember(appCtx) { ModelStore(appCtx) }
    val repo = remember(appCtx) { ModelDownloadRepository(appCtx) }

    var phase: Phase by remember { mutableStateOf(Phase.Checking) }
    var wifiOnly by remember { mutableStateOf(true) }
    var cellularConsent by remember { mutableStateOf(false) }

    // Phase3b-II: LLM 로드 하드 게이트 — 티어 LOW 또는 ActivityManager.isLowRamDevice() 차단.
    // 게이트에 걸리면 다운로드/로드 자체를 막고 안내 문구 표시.
    LaunchedEffect(entry.id) {
        val tier = DeviceFingerprint.classify(appCtx)
        val blockReason = DeviceFingerprint.llmBlockReason(tier, appCtx)
        if (blockReason != null) {
            phase = Phase.Unsupported(blockReason)
            return@LaunchedEffect
        }
        val verify = withContext(Dispatchers.IO) { store.verify(entry) }
        phase = if (verify is ModelStore.VerifyResult.Valid) Phase.Ready else Phase.NotInstalled
    }

    // WorkInfo 구독 (NotInstalled/Downloading/Failed 모두 observe — Cancelled 수신 필요)
    val liveDownload by repo.observe(entry).collectAsState(initial = DownloadState.Idle)
    LaunchedEffect(liveDownload) {
        when (val d = liveDownload) {
            is DownloadState.Running, DownloadState.Queued -> phase = Phase.Downloading(d)
            DownloadState.Succeeded -> {
                val verify = withContext(Dispatchers.IO) { store.verify(entry) }
                phase = if (verify is ModelStore.VerifyResult.Valid) Phase.Ready
                else Phase.Failed(reason = "모델 무결성 검증 실패 (SHA 불일치)")
            }
            DownloadState.Failed -> phase = Phase.Failed(reason = "다운로드 실패 — 네트워크 상태 확인 후 재시도")
            DownloadState.Cancelled -> phase = Phase.NotInstalled
            DownloadState.Idle -> Unit
        }
    }

    LaunchedEffect(phase) {
        if (phase is Phase.Ready) onReady()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val p = phase) {
                Phase.Checking -> CircularProgressIndicator()
                Phase.NotInstalled -> NotInstalledPanel(
                    entry = entry,
                    wifiOnly = wifiOnly,
                    onToggleWifi = { wifiOnly = it },
                    cellularConsent = cellularConsent,
                    onToggleCellular = { cellularConsent = it },
                    onStart = {
                        // Wi-Fi only 체크 해제되고, 셀룰러 동의되면 CONNECTED 로 enqueue.
                        val effectiveWifiOnly = wifiOnly || !cellularConsent
                        repo.enqueue(entry, wifiOnly = effectiveWifiOnly)
                        phase = Phase.Downloading(DownloadState.Queued)
                    },
                )
                is Phase.Downloading -> DownloadingPanel(
                    entry = entry,
                    state = p.state,
                    onCancel = { repo.cancel(entry) },
                )
                is Phase.Failed -> FailedPanel(
                    reason = p.reason,
                    onRetry = { phase = Phase.NotInstalled },
                )
                is Phase.Unsupported -> UnsupportedPanel(reason = p.reason)
                Phase.Ready -> Unit  // 곧 onReady() 로 나감
            }
        }
    }
}

private sealed interface Phase {
    data object Checking : Phase
    data object NotInstalled : Phase
    data class Downloading(val state: DownloadState) : Phase
    data class Failed(val reason: String) : Phase
    /** 하드웨어 제한으로 LLM 로드 불가 (LOW 티어 / isLowRamDevice). 다운로드 진입 자체를 막는다. */
    data class Unsupported(val reason: String) : Phase
    data object Ready : Phase
}

@Composable
private fun NotInstalledPanel(
    entry: ModelEntry,
    wifiOnly: Boolean,
    onToggleWifi: (Boolean) -> Unit,
    cellularConsent: Boolean,
    onToggleCellular: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "AI 모델 다운로드 필요",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${entry.displayName} · ${formatMb(entry.sizeBytes)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        entry.attributionNotice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        HorizontalDivider()
        SettingRow(
            label = "Wi-Fi 에서만 다운로드",
            checked = wifiOnly,
            onCheckedChange = onToggleWifi,
        )
        SettingRow(
            label = "셀룰러 데이터 사용 동의",
            checked = cellularConsent,
            onCheckedChange = onToggleCellular,
            enabled = !wifiOnly,
        )
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("다운로드 시작")
        }
    }
}

@Composable
private fun DownloadingPanel(
    entry: ModelEntry,
    state: DownloadState,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "${entry.displayName} 다운로드 중",
            style = MaterialTheme.typography.titleMedium,
        )
        val fraction = (state as? DownloadState.Running)?.fraction ?: 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        val caption = when (state) {
            is DownloadState.Running -> "${formatMb(state.downloaded)} / ${formatMb(state.total)}"
            DownloadState.Queued -> "대기 중… (Wi-Fi 연결 확인)"
            DownloadState.Succeeded -> "검증 중…"
            DownloadState.Idle, DownloadState.Failed, DownloadState.Cancelled -> ""
        }
        if (caption.isNotEmpty()) {
            Text(caption, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onCancel) {
            Text("취소")
        }
    }
}

@Composable
private fun UnsupportedPanel(reason: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "LLM 모드 사용 불가",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        Text(reason, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "기본 AI (Monte Carlo + persona) 로 계속 플레이할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun FailedPanel(reason: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "다운로드 실패",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        Text(reason, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("재시도") }
    }
}

@Composable
private fun SettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes.toDouble() / 1_048_576.0)

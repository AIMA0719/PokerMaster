package com.infocar.pokermaster.model

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.infocar.pokermaster.DeviceFingerprint
import com.infocar.pokermaster.core.model.ModelEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 모델 게이트 화면 — v1.1 §1.2.A.
 *
 * 상태 흐름:
 *   Checking → (설치됨? Ready : (PAD? Ready : NotInstalled))
 *   NotInstalled → (사용자 시작) → Downloading(Queued/Running)
 *   Downloading → (Succeeded → 빠른 검증 → Ready | Failed → Failed | Cancelled → NotInstalled)
 *   Ready → onReady() 호출 후 다음 라우트로 이동 (sticky — 한 번 Ready 면 다른 emit 가 덮지 못함)
 *
 * "다 받았는데 대기중..." 버그 방지:
 *  1) Repository.observe() 가 final 파일 존재 시 Succeeded 단락
 *  2) phase = Ready/Unsupported 면 LE#2 no-op (sticky)
 *  3) 옛 Failed/Cancelled WorkInfo 는 phase 가 Downloading 일 때만 반영
 *  4) phase 를 rememberSaveable 로 보존 — 회전/다크 토글마다 Checking 으로 리셋되지 않게
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

    var phase: Phase by rememberSaveable(stateSaver = PhaseSaver) {
        mutableStateOf<Phase>(Phase.Checking)
    }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var cellularConsent by rememberSaveable { mutableStateOf(false) }

    // Android 13+ POST_NOTIFICATIONS 런타임 권한 launcher.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 부여 여부와 무관하게 다운로드는 진행 — 알림이 없을 뿐. */ }

    // Phase3b-II: LLM 로드 하드 게이트 — 티어 LOW 또는 isLowRamDevice 차단.
    LaunchedEffect(entry.id) {
        val tier = DeviceFingerprint.classify(appCtx)
        val blockReason = DeviceFingerprint.llmBlockReason(tier, appCtx)
        if (blockReason != null) {
            phase = Phase.Unsupported(blockReason)
            return@LaunchedEffect
        }
        // 1) 로컬에 이미 있는지 확인 (캐시 hit 시 SHA 재계산 없이 즉시 반환).
        val verify = withContext(Dispatchers.IO) { store.verify(entry) }
        if (verify is ModelStore.VerifyResult.Valid) {
            phase = Phase.Ready
            return@LaunchedEffect
        }
        // 2) PAD install-time 에셋팩에서 추출 시도. 디스크 풀 시 IOException → Failed.
        val padOutcome = withContext(Dispatchers.IO) {
            runCatching { store.extractFromAssetPack(entry) }
        }
        val padExtracted = padOutcome.getOrElse { e ->
            if (e is IOException) {
                phase = Phase.Failed("저장 공간 부족 — 사용 가능 공간을 확보한 뒤 다시 시도하세요.")
                return@LaunchedEffect
            }
            false
        }
        if (padExtracted) {
            val padVerify = withContext(Dispatchers.IO) { store.verify(entry) }
            if (padVerify is ModelStore.VerifyResult.Valid) {
                phase = Phase.Ready
                return@LaunchedEffect
            }
        }
        // 3) 다운로드 필요 (Debug 빌드 / 사이드로딩).
        phase = Phase.NotInstalled
    }

    val liveDownload by repo.observe(entry).collectAsState(initial = DownloadState.Idle)
    LaunchedEffect(liveDownload) {
        // sticky guard: Ready/Unsupported 는 어떤 emit 도 덮지 못함.
        val current = phase
        if (current is Phase.Ready || current is Phase.Unsupported) return@LaunchedEffect

        when (val d = liveDownload) {
            DownloadState.Queued, is DownloadState.Running -> phase = Phase.Downloading(d)
            DownloadState.Succeeded -> {
                // Worker 가 SHA 검증 후 캐시에 mark 했으므로 verify 는 캐시 hit (즉시).
                val verify = withContext(Dispatchers.IO) { store.verify(entry) }
                phase = if (verify is ModelStore.VerifyResult.Valid) Phase.Ready
                else Phase.Failed("모델 무결성 검증 실패 (SHA 불일치)")
            }
            is DownloadState.Failed -> {
                // 옛 WorkInfo 잔존 가드: 사용자가 이번 세션에 다운로드를 시작해서 Downloading
                // 상태일 때만 실패로 본다. 첫 진입 시 옛 Failed 가 emit 되어도 무시.
                if (current is Phase.Downloading) {
                    phase = Phase.Failed(reasonForFailure(d.reason, d.httpCode))
                }
            }
            DownloadState.Cancelled -> {
                if (current is Phase.Downloading) phase = Phase.NotInstalled
            }
            DownloadState.Idle -> Unit
        }
    }

    LaunchedEffect(phase) {
        if (phase is Phase.Ready) onReady()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(com.infocar.pokermaster.core.ui.theme.HangameColors.BackgroundBrush),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
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
                        // Android 13+ 다운로드 진행 알림용 권한 1회 요청.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                appCtx, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
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

private fun reasonForFailure(code: String?, httpCode: Int?): String = when (code) {
    ModelDownloadWorker.FAILURE_DISK_FULL -> "저장 공간 부족 — 일부 데이터를 정리한 뒤 재시도하세요."
    ModelDownloadWorker.FAILURE_HTTP_4XX -> when (httpCode) {
        416 -> "서버 파일 크기와 매니페스트가 어긋남 (HTTP 416) — 모델 메타데이터 갱신이 필요합니다."
        401, 403 -> "다운로드 서버 인증 실패 (HTTP $httpCode) — gated 모델 또는 토큰 만료 가능."
        404 -> "다운로드 URL 을 찾을 수 없습니다 (HTTP 404) — 모델이 이동/삭제됐을 수 있습니다."
        429 -> "다운로드 서버 호출 한도 초과 (HTTP 429) — 잠시 후 다시 시도하세요."
        else -> "다운로드 서버 응답 실패 (HTTP ${httpCode ?: "4xx"}) — 다른 네트워크에서 다시 시도하세요."
    }
    ModelDownloadWorker.FAILURE_SHA_MISMATCH -> "모델 무결성 검증 실패 (SHA 불일치) — 매니페스트와 서버 파일이 일치하지 않습니다."
    else -> "다운로드 실패 — 네트워크 상태 확인 후 재시도"
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

/**
 * Activity recreate (회전, 다크 토글 등) 에 phase 보존.
 * Downloading(state) 은 휘발 — 복원 시 Checking 으로 두면 LE 가 즉시 reconcile.
 */
private val PhaseSaver = mapSaver<Phase>(
    save = { p ->
        when (p) {
            Phase.Checking -> mapOf("t" to "checking")
            Phase.NotInstalled -> mapOf("t" to "notInstalled")
            Phase.Ready -> mapOf("t" to "ready")
            is Phase.Downloading -> mapOf("t" to "checking")  // observe 가 즉시 갱신
            is Phase.Failed -> mapOf("t" to "failed", "r" to p.reason)
            is Phase.Unsupported -> mapOf("t" to "unsupported", "r" to p.reason)
        }
    },
    restore = { m ->
        when (m["t"] as? String) {
            "checking" -> Phase.Checking
            "notInstalled" -> Phase.NotInstalled
            "ready" -> Phase.Ready
            "failed" -> Phase.Failed((m["r"] as? String) ?: "다운로드 실패")
            "unsupported" -> Phase.Unsupported((m["r"] as? String) ?: "지원되지 않는 단말")
            else -> Phase.Checking
        }
    },
)

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
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
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
            DownloadState.Queued -> "대기 중… (Wi-Fi 연결 / 알림 권한 확인)"
            DownloadState.Succeeded -> "검증 중…"
            DownloadState.Idle, is DownloadState.Failed, DownloadState.Cancelled -> ""
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
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
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

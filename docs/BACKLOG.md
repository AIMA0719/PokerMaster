# PokerMaster 작업 백로그

21 phase 누적 코드 작업 후 실기 검증 전 끊음 (2026-04-24). 이 문서는 다음 세션에서
바로 재개할 수 있도록 "할 일 후보" 를 우선순위별 분류. 실기 테스트 결과 발견되는
버그는 최우선, 그 외는 아래 순서.

---

## 0. 실기 검증 체크리스트 (다음 세션 착수 전 필수)

- [ ] `adb install -r app/build/outputs/apk/debug/app-debug.apk` 로 설치.
- [ ] Splash → ModelGate 진입. **DefaultModels.sha256 이 placeholder 라
      Phase1b Ed25519 서명 검증 실패할 가능성 큼** — 첫 블로커 후보.
  - 해결 옵션: (a) 테스트용 서명 키로 manifest 재생성, (b) debug buildType 에서
    검증 skip flag 추가, (c) 실 GGUF + sha256 + signature 로 정식 경로 실험.
- [ ] 저RAM 단말 / DeviceTier LOW 인 경우 `Phase.Unsupported` 차단 동작.
- [ ] Lobby 진입:
  - [ ] 잔고 50k 표시, daily bonus 다이얼로그 1회.
  - [ ] 설정 / 통계 / 히스토리 카드 모두 진입 동작.
- [ ] Table 한 판 플레이 (GGUF 없어도 DecisionCore 폴백 경로로 동작해야 함):
  - [ ] NPC 턴 진행, showdown.
  - [ ] History Repository 에 자동 저장.
  - [ ] Lobby 복귀 시 wallet 차감/적립 반영.
- [ ] History 리스트 → 상세 → Provably Fair SHA-256 검증 "✓ 검증 성공" 표기.
- [ ] Stats 화면에 추가된 핸드 반영.
- [ ] Settings 에서 히스토리 전체 삭제 → History 리스트 empty 상태.
- [ ] 의도적 파산 유발 → 파산 모달 → 재시작 보너스 수령.
- [ ] `ComponentCallbacks2.onTrimMemory` 트리거 (`adb shell am send-trim-memory ...`) 후
      LogCat 에 `PokerMasterApp onTrimMemory(level=...) -> LlmSession.release()` 확인.

실기 이슈 발견 시 원인별로 다음 섹션 중 어디에 속하는지 표시 후 수정.

---

## 1. 예상 가능성 높은 실기 이슈

| 가설 | 영향 | 첫 대응 |
|------|------|---------|
| ModelGate 서명 검증 실패 | 전체 flow 진입 불가 | debug buildType 에 `BuildConfig.SKIP_MANIFEST_VERIFICATION` 플래그 |
| libpokermaster_llm.so 로드 실패 (non-arm64-v8a 또는 NDK mismatch) | LlmEngineHandle.Unavailable — 기본 flow 는 동작해야 함 | LlmAdvisor.suggest 가 null 폴백 경로 정상 확인 |
| Room v2 migration (기존 device 에 v1 있음) | Crash on DB open | `fallbackToDestructiveMigration()` 이 이미 있어 destruction 으로 처리됨 — 확인 |
| `HandHistoryRepository.record` 에서 `initialStateJson` 직렬화 실패 (kotlinx.serialization 누락 클래스) | 핸드 저장 안 됨 | `runCatching { repo.record }` 가 실패 시 무시 — LogCat 확인 |
| `PromptFormatter` 가 Hi/Lo mode 등 미구현 GameMode 에서 터짐 | LLM advisor 경로만 영향 | HOLDEM_NL 만 M3 MVP 에서 지원되므로 현실성 낮음 |
| `TableScreen` 에 주입된 walletRepo=null 일 때 buyIn/settle no-op 예상 | 파산 리셋 단절 | 이미 null safe — 확인 |
| `currentCoroutineContext()[Job]` 가 null 인 케이스 (쓰지 않는 dispatcher) | coroutine cancel → nativeCancel 연결 실패 | 방어적으로 null-safe (현재 구현 OK) |

---

## 2. M4 (LLM) 튜닝 백로그 — 실기 벤치 기반

- [ ] Release buildType 에 `-DCMAKE_BUILD_TYPE=Release` + `-O3 -flto=thin -DNDEBUG`.
      현재 Debug 는 -O2. 실기 벤치에서 first-token 지연 측정 후 결정.
- [ ] `ggml_threadpool_new` + `sched_setaffinity` 로 big 클러스터 pin (감사 Perf#11).
      big.LITTLE 단말에서 LITTLE 코어로 drift 방지.
- [ ] `PowerManager.getCurrentThermalStatus()` 감시 → MODERATE+ 에서 `n_threads=2` 동적 drop.
      `llama_set_n_threads(ctx, n, n_batch)` API 확인 필요 (b8870).
- [ ] logit bias 폴백 — GBNF 실패 시 특정 토큰 확률 조정.
- [ ] regex 최후 폴백 — LLM 응답 JSON 파싱 실패 시 action 문자열을 regex 추출.
- [ ] CI integration test: `nativeVersion()` canary — JNI 심볼 rename/제거 감지.
- [ ] 네이티브 크래시 격리 `:inference` 별도 프로세스 — 크래시 텔레메트리 > 0.1% 시.

---

## 3. M5/M6 UX 확장

### 히스토리
- [ ] Step-by-step scrubber (HandDetailScreen) — action 을 순차 replay 하면서
      각 street 의 중간 state 를 HoldemReducer 재구동으로 렌더.
- [ ] 필터 (mode / winner / 기간) — DAO 쿼리 추가.
- [ ] JSON export (공유 intent) — ACTION_SEND 로 HandHistoryRecord 직렬화.

### 통계
- [ ] VPIP / PFR / 3-bet% — `actionsJson` 파싱 필요. 포지션 라벨링 (UTG/BTN/SB/BB)
      도 인덱스 기반 계산.
- [ ] 모드/페르소나별 승률 매트릭스.
- [ ] 최근 N 핸드 winrate trendline.

### 설정
- [ ] 알림 (누적 플레이 30/60/120 min 알람, 야간 블록 22-07).
- [ ] AI 서브메뉴 (모델 삭제/재검증/폴백 강제, Wi-Fi only, 셀룰러 동의).
- [ ] 게임 옵션 (애니메이션 속도, 사전 액션 기본값).
- [ ] 정보 섹션 확장 (OSS license 목록, 개인정보 처리방침, FAQ).

### Wallet
- [ ] Streak 기반 차등 보상 (1일 1k → 7일 15k 등).
- [ ] `totalEarnedLifetime` UI 노출 (업적/도전과제용).

---

## 4. 아키텍처/인프라 백로그

- [ ] Room `exportSchema = true` + `room.schemaLocation` 지정 (배포 전 migration 대비).
- [ ] Room v1→v2 proper `Migration` 작성 (현재 `fallbackToDestructiveMigration`).
- [ ] Hilt `@HiltViewModel` + `AssistedInject` 로 `TableViewModel` refactor —
      현재 factory 기반이라 Compose `hiltViewModel()` 사용 불가.
- [ ] `:engine:llm-api` 에 JVM 테스트 커버리지 확대 — generateJson default impl
      의 tokenize/generate/detokenize 조합도 fake 로 검증.
- [ ] `consumer-rules.pro` 에 kotlinx.serialization 정책 추가 (R8 활성화 시).
- [ ] feature:table 의 `SettingsRepository` / `SfxPolicy` 등을 `:core:preferences`
      같은 공용 모듈로 추출 — 현재 feature 간 의존성 위화감.

---

## 5. 스코프 제외 (v1)

설계서에 있으나 의도적으로 v1 제외:
- 트레이닝 모드 (§1.2.I) — 사용자 결정.
- 소셜/친구/초대 (§1.4 non-goal).
- 서버 리더보드 / 클라우드 동기화.
- 다국어/로케일 (v1 은 한국어 only).

---

## 6. 커밋 타임라인 (M4~M6)

M4 (LLM 통합, 13 커밋): `9ac7484 → 64e55fe`
M5 (히스토리/리플레이, 4 커밋): → `M5-D`
M6 (설정/통계/지갑, 3 커밋): → `M6-C`

정확한 해시는 `git log --oneline` 으로 확인.

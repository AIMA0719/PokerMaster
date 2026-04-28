# PokerMaster UI/UX 게이미피케이션 강화 플랜

작성일: 2026-04-28
대상: M4+M5+M6 완료 ready-to-ship 상태에서 **체감 만족도 / 자극 / 사운드·이펙트** 강화.

핵심 원칙:
1. **기존 자산 최우선 활용**. 새 자산 도입 전, 이미 만들어둔 SFX 7종 / SfxPolicy / Haptic /
   HangameColors / anim 헬퍼 / pulseFloat / Badge 류가 모든 적합한 지점에 다 적용됐는지 먼저 메우기.
2. **자극은 토글 가능해야**. `SfxPolicy.sfxEnabled / hapticEnabled / a11y.reduceMotion` 가
   기준선. 모든 신규 효과는 이 셋에 hook 걸어 끄면 단정한 기능형 UI 로 회귀.
3. **점진 적용**. Phase 끝마다 빌드 + 컴파일 + 변경 모듈 단위테스트 통과. 한 Phase 당 한
   커밋 원칙. 큰 리팩토링 / 신모듈 신설 자제 — 이미 있는 anim/sfx/theme 패키지 안에서 확장.
4. **사용자 비즈니스 결정** (외부 자산 추가, BGM 도입 여부, 도전과제 보상 규칙) 만 중간
   AskUserQuestion. 나머지는 자율 진행.

---

## 0. 현재 자산 인벤토리 (개선 출발점)

### 0.1 사운드 (`feature/table/src/main/res/raw/`)
- `sfx_allin.ogg`, `sfx_card_deal.ogg`, `sfx_check.ogg`, `sfx_chip_commit.ogg`,
  `sfx_fold.ogg`, `sfx_hand_win.ogg`, `sfx_pot_sweep.ogg` — **7종 OGG, SoundPool 로드**.
- `SoundManager` (`feature/table/sfx/SoundManager.kt`) — load/play API.
- `SfxPolicy` — sfxEnabled / hapticEnabled 토글.
- `Haptic` 유틸 — TICK / CLICK / DOUBLE_CLICK / HEAVY 패턴.
- BGM **없음**.

### 0.2 색상·그라데이션 (`core/ui/theme/HangameColors.kt`)
- 펠트: `FeltInner / FeltMid / FeltOuter` (radialGradient).
- 시트: `SeatBgActive / SeatBg / SeatBgFolded`, `SeatBorderWinner / Active / 기본`.
- 액션 컬러: `BtnBet / BtnCall / BtnFold / BtnDisabled`, `TextChip / TextLime / TextMuted`,
  `HiLoHiBadge / HiLoLoBadge / HiLoScoopBadge`, `BtnSaveLife (구사)`.
- 헬퍼: `BackgroundBrush`, `feltBrush()`, `buttonBrush(top, bottom)`.

### 0.3 애니메이션 (`feature/table/anim/`)
- `ChipMoveSpec` — 칩 이동 spec.
- `ButtonScale` — 버튼 press 1.0→0.94→1.0 (~120ms).
- `FadeSlide` — 일반 enter/exit.
- `pulseFloat(initial, target, periodMs, label)` — 펄스 헬퍼.

### 0.4 이미 적용된 마이크로 임팩트 (살아있음)
- `SeatLayout`: `isToAct / isWinner` 시 외곽선 펄스 (1.5→3 dp, 700ms).
- `WinnerBanner`: scaleIn + fadeIn + 골드 보더 펄스 (550ms→1.0, 850ms).
- `AllInBadge / PayoutBadge`: AnimatedVisibility (fadeIn + scaleIn 220ms).
- `ActionButton`: 그라데이션 + AtomicLong 350ms throttle.
- `pulseFloat` 헬퍼 재사용 가능.

### 0.5 UX 데이터 (UI 미연결 상태)
- `WalletState.totalEarnedLifetime` — Phase4 에서 LobbyHeader 노출 완료 (이전 커밋).
- `streakDays` — Lobby 헤더 "🔥 N 일" 단순 텍스트.
- BACKLOG §3 미체크: 통계 VPIP/PFR UI / trendline / 알림 / streak 차등 보상 / step scrubber 등.

### 0.6 부족·미진 영역 (강화 타깃)
- BGM 부재.
- SFX 베리에이션 0 (한 종당 1 파일, 매번 동일 톤).
- 레어 핸드 hit (스트레이트 플러시 / 풀하우스 / 로열) 별도 시그널 없음.
- 칩 이동이 자취 / trail 없이 단순 이동 추정.
- 잔고 변경 카운트업 부재.
- payout 시 시트 옆 floating "+N" 부재 (PayoutBadge 외).
- daily bonus / 파산 모달이 정적 — 칩 burst / count-up 없음.
- streak 시각화 단순 텍스트.
- tier / 레벨 / 도전과제 시스템 부재 (totalEarnedLifetime 활용 안 됨).
- 본인 차례 장기 무응답 alert 없음.
- 테이블 진입 4.5초 휴식이 단순 delay (cinematic intro 없음).
- Splash 정적.

---

## 1. Phase 분할

각 Phase 는 독립 커밋. 의존성:
- Phase 1 → 다른 모든 Phase (자산 정상화 베이스).
- Phase 2 → Phase 5, 6 (액션 임팩트 적용 후 마이크로 인터랙션 결정).
- Phase 3 (자산 추가) 는 독립적으로 진행 가능 — Phase 4~7 신규 사운드 필요할 때 dependency.
- Phase 4 (진행/리워드) ↔ Phase 7 (도전과제) 은 totalEarnedLifetime / streak 상호 활용.

### Phase 1 — 기존 SFX/Haptic 적용 갭 메우기 (자산 활용 극대화)

**목표**: 이미 만든 자산이 모든 적합한 지점에 정확히 hook 되어 있는지 점검 + 빈 곳 채우기. 새 자산 0.

**작업**:
1. 액션 디스패치 사운드 매트릭스 검증 — `onHumanAction` 이 BET/RAISE/CALL/CHECK/FOLD/ALL_IN/DECLARE
   각각에 맞는 SFX 호출하는지 1대1 점검. 누락 시 매핑 추가.
2. NPC 액션도 동일 SFX 트리거 (현재 인간 한정일 가능성). 단 인간/NPC pitch 살짝 차이 (NPC 0.95).
3. Haptic 매트릭스 — BET/RAISE → HEAVY, CALL → CLICK, CHECK → TICK, FOLD → 짧은 TICK,
   ALL_IN → DOUBLE_CLICK, DECLARE → CLICK.
4. 카드 딜링 — `sfx_card_deal` 가 한 장당 1회씩 stagger 재생 (현재 한 번에 일괄일 가능성).
5. Pot sweep — 핸드 종료 → payout 단계에서 정확히 1회 + 시트→pot 칩 이동 애니와 sync.
6. Hand win — 본인이 이긴 경우만 강조 (피치 +0.05) / NPC 승리 일반 톤.
7. SFX 동시 재생 conflict 점검 — SoundPool 동시 stream 한도 (보통 4~8) 초과 시 deal 같은
   고빈도 SFX 가 끊어짐. priority/queue 정책 확인.

**수용 기준**:
- 모든 ActionType 이 SFX + Haptic 호출 path 에 있다 (단위테스트 또는 직접 grep).
- SfxPolicy.sfxEnabled = false 이면 어떤 SFX 도 안 남.
- a11y.reduceMotion = true 면 펄스/스케일 애니 단축 또는 skip.

**위험**: 낮음. 자산 정상화라 회귀 위험 < 디자인 변경.

**견적**: 0.5~1일.

---

### Phase 2 — 핵심 액션 임팩트 강화 (Action Bar · Pot · Showdown)

**목표**: "한 번 누를 때마다 손맛" — 액션 버튼 / 팟 / 승자 발표가 더 강하게 때림.

**작업**:
1. **ActionButton** (이미 그라데이션 + 350ms throttle):
   - press 시 ButtonScale (1.0→0.94→1.0) + 동시에 외곽 글로우 1회 (color = tint, 240ms).
   - BET/RAISE 는 칩 색 펄스 (BtnBet → BtnBetGlow), FOLD 는 어두운 dim.
   - disabled 시 회색 그라데이션 유지 (현재 OK).
2. **베팅 슬라이더** — drag 시 매 25 칩 (혹은 N 단계) 마다 TICK 햅틱 + 미세 SFX (재사용:
   sfx_chip_commit pitch 0.85). drag 끝나면 "확정" 햅틱 (CLICK).
3. **Bet Chip → Pot 이동** (`ChipMoveSpec` 기반):
   - 시트 commit chip 이 pot 으로 move 할 때 잔상 trail (이전 위치 fadeOut 220ms).
   - 큰 액수 (>= bigBlind*5) 는 chip burst (3~5 작은 chip 분기 트레일 후 합쳐짐).
4. **Pot Sweep**:
   - 승자 결정 직후 pot → 승자 시트 ChipMove (현재 있을 듯) + 시트에 +N "PayoutBadge" 강화:
     0→N 카운트업 (300ms, FastOutSlow), 골드 outline 펄스 1회.
   - 본인 승리 시 추가 burst (시트 주변 ★ 5개 scaleIn + fadeOut 600ms).
5. **WinnerBanner**:
   - 본인 승리 일 때만 화면 가벼운 shake (translation x ±4dp, 2회, 180ms).
   - confetti 도입은 Phase 3 자산 결정 후. 우선 기존 pulse 만 강화 (initial 0.55→0.7, period
     650ms 로 더 빠르고 강하게).
6. **Showdown Cards Reveal** — 펼치기 stagger (좌→우, 100ms 간격) + 각 카드 flip 애니.
   카드 컴포넌트가 이미 있는지 점검 후 결정 (없으면 Phase 3 후속).

**수용 기준**:
- BET/RAISE/CALL 액션 후 200ms 이내 (a) 햅틱 (b) SFX (c) 시각 임팩트 모두 발생.
- pot sweep 가 시트→pot→승자시트 일관 흐름. 승자 시트 +N 카운트업 동작.
- reduceMotion=true 시 모든 신규 펄스/shake 가 skip 되고 fade only.

**위험**: 중. ChipMoveSpec / SeatLayout 좌표 시스템 만져야 함. 좌표 잘못 계산하면 칩이 엉뚱한
곳으로 날아감 — 단계별 dryrun 가능한 dev flag 둘 것.

**견적**: 2~3일.

---

### Phase 3 — 사운드·그래픽 자산 보강 (외부 자산 도입)

**목표**: BGM + SFX 베리에이션 + 레어 hit 시그널 + tier acquire 사운드.

**자산 결정 (사용자 비즈니스)**:
- **BGM**: 도입 vs skip. CC0 출처 (Kenney / OpenGameArt / Freesound CC0) 제안 후 1~2 트랙 선정.
  Lobby ambient (조용/lounge) + Table ambient (slight tension). 토글 ON 기본은 OFF
  (배터리 + 모바일 사용자 호불호).
- **SFX 베리에이션**: chip_commit 3종 (commit_a/b/c, pitch+volume 미세차) + deal 2종 (deal_a/b).
  코드에서 random pick 으로 단조로움 제거. 자산 없으면 코드만 random pitch 0.92~1.08 적용해도 효과
  큼 (자산 추가 0).
- **레어 hit**: 스트레이트 플러시 / 풀하우스 / 로열 / 4-of-a-kind 등 강도 임계치 이상 핸드
  발현 시 별도 시그널 (sfx_rare_hit_a) + 시각 강조 (시트 골드 광채 1.5초). 자산 1개 추가.
- **Tier acquire**: Phase 4 에서 도입할 tier 진급 시 sfx_tier_up.

**작업**:
1. CC0 출처 후보 1~2개 사용자 confirm 받고 raw 추가.
2. `SoundManager` 에 random-variant API: `play(SoundKey.CHIP_COMMIT)` 가 등록된 베리에이션 중
   하나 선택 + pitch/rate jitter (±0.06).
3. 레어 hit 트리거 — `HandEvaluator` 결과 카테고리에 따라 `Showdown` 단계에서 시그널 발사.
4. BGM 시스템 — `MediaPlayer` (SoundPool 부적합, 길이 김) + Lifecycle observer (앱 백그라운드
   pause). SfxPolicy 에 `bgmEnabled` 토글 추가, Settings 화면에 새 row.

**수용 기준**:
- BGM 토글 작동 (Settings → 즉시 반영, 종료 시 release).
- 같은 SFX 5회 연속 재생 시 들어보면 미세하게 다름 (random variant).
- 풀하우스 이상 핸드 종결 시 골드 광채 + 레어 사운드.
- 모든 신규 자산 출처/라이선스 `licenses/` 또는 `third_party/AUDIO_LICENSES.md` 에 기록.

**위험**: 중. 외부 자산 라이선스 검증 필수. APK 크기 증가 (BGM 1트랙 ~500KB OGG 기준 1MB 이내
유지). MediaPlayer lifecycle 누수 주의.

**견적**: BGM 도입 시 2일, skip 시 1일.

---

### Phase 4 — 진행 / 리워드 시각화 강화 (Streak · Daily Bonus · Tier · 잔고)

**목표**: 게이미피케이션의 핵심 — "내가 진행하고 있다" 감각 강화. totalEarnedLifetime 활용.

**작업**:
1. **잔고 변경 카운트업** — Lobby `WalletHeader` / TableScreen 좌상단 chips:
   - 이전 값 → 새 값 으로 600ms count-up (FastOutSlow). 증가 시 라임, 감소 시 빨강 짧게 dim.
   - `Animatable<Float>` 또는 `animateLongAsState`.
2. **Streak 시각화** — 현재 "🔥 N 일" 텍스트만:
   - 7일 진행 바 (0~7 dot, 채워진 만큼 골드). 7일 도달 시 큰 ★ 1개.
   - 20+ 일 등 강한 streak 시 불꽃 파티클 (간소화: SVG 펄스 + 색상 강화).
   - daily bonus claim 시 progress dot 채워지는 애니 + sfx_chip_commit + 햅틱 CLICK.
3. **Daily Bonus 다이얼로그 화려화**:
   - 현재 텍스트형. 칩 burst (8~12개 chip drawable scaleIn → 화면 중앙 뭉치 → 잔고 위치로
     날아가 흡수) + sfx_pot_sweep + 잔고 카운트업 sync.
   - streak N 일 표기 강조 (큰 폰트 + 골드 outline 펄스).
4. **파산 모달 cinematic**:
   - 현재 단순 모달. 화면 vignette 어둡게 + 잔고 0 강조 (빨강 펄스) + 짧은 햅틱 HEAVY.
   - "재시작 보너스" 버튼 누르면 칩 burst 가 잔고로 흡수 (Daily Bonus 와 동일 system 재사용).
5. **Tier 시스템 (totalEarnedLifetime 활용)**:
   - 임계치 4단계: BRONZE (0) / SILVER (50k) / GOLD (200k) / PLATINUM (1M) / DIAMOND (5M).
   - LobbyHeader "누적 N" 옆에 tier 뱃지 (드러나는 방식 — 이모지 또는 작은 색상 dot).
   - 진급 순간 감지 → tier-up 모달 (full-screen 1.5초, sfx_tier_up + chip burst + 골드 펄스
     + 다음 tier 안내).
   - 임계치 / 보상 / 모달 텍스트는 사용자 confirm.
6. **Lobby 모드 카드 hover/press**:
   - 현재 enabled 상태만 색상 분기. press 시 ButtonScale + 글로우 + 모드별 테마 색 (HOLDEM 그린,
     7-STUD 시안, HI-LO 골드).

**수용 기준**:
- 잔고 매 변경 시 600ms 카운트업.
- streak 진행 dot UI 가 1~7 사이 정확히 표시. 7일 도달 시 ★.
- daily bonus claim → chip burst + sfx + 잔고 카운트업이 모두 sync (시각상 끊김 없음).
- 첫 SILVER 도달 시 tier-up 모달 1회만 노출 (재진입 시 X) — DataStore 에 last seen tier
  저장.

**위험**: 중. tier 임계치는 사용자 결정 (게임 밸런스). 잔고 카운트업이 매 핸드 종료 settle 마다
일어나면 시각 노이즈 — settle 만 카운트업 / 작은 미세 변화는 즉시 적용으로 이원화.

**견적**: 2~3일.

---

### Phase 5 — 마이크로 인터랙션 / 시트 디테일

**목표**: 100ms 단위의 "찰진 느낌" — 폴드 / 차례 alert / payout floating.

**작업**:
1. **폴드 카드 페이드** — 폴드 직후 시트 hole cards alpha 1→0.3 (300ms) + saturation 줄임 (gray
   shift). 시트 자체 SeatBgFolded 는 이미 적용 중.
2. **본인 차례 장기 무응답 alert**:
   - toAct=human 이고 30초 경과 → 시트 외곽선 펄스 강도 ↑ (1.5→4 dp, 400ms period) + 미세 진동
     햅틱 TICK 5초마다.
   - 60초 → 자동 폴드 (기존 정책 있으면 sync, 없으면 skip).
3. **+N PayoutBadge 트레일** — 승자 시트 옆 floating "+N" 텍스트 (이미 있음) 강화:
   - scaleIn 1.2 → 1.0, 동시에 위로 -16dp translateY, 800ms fadeOut.
   - 본인일 때 골드, NPC 일 때 회색.
4. **카드 reveal flip** (Phase 2 미해결 잔여):
   - hole cards / community cards 펼칠 때 90deg 좌→우 회전 240ms (rotationY 0→90 hide,
     90→180 show 새 카드).
5. **Bet Chip stack 깊이감** — 시트 앞 commit chip 이 단순 단일 칩이면 액수에 따라 stack 높이
   시각화 (1~5 chip overlay).
6. **DealerButton 회전** — BTN move 시 이전 위치 → 새 위치 arc 이동 + 반바퀴 회전 360ms.

**수용 기준**:
- 폴드 직후 카드/시트 시각적 dim 된다.
- 30초 무응답 시 펄스 강도 변화가 인지된다.
- +N 트레일이 위로 떠오르며 사라진다.

**위험**: 낮~중. 카드 flip 은 좌표 / Compose graphicsLayer 익숙해야 자연스러움. 어색하면 Phase
6 으로 미룸.

**견적**: 1.5~2일.

---

### Phase 6 — Onboarding · Lobby · 테이블 진입 cinematic

**목표**: 첫 진입과 모드 전환 순간을 영화처럼.

**작업**:
1. **Splash 강화**:
   - 현재 정적 가능성. 로고 scale 0.8→1.0 (FastOutSlow 800ms) + 골드 광채 펄스 + chip 분산
     배경 (5~8 chip drawable 회전+이동 background, 매우 약하게).
   - sfx_pot_sweep 재사용 (한 번, 짧게 fadeIn) 또는 BGM 시작.
2. **ModelGate 진행 시각화** — 모델 다운로드 / 검증 단계 progress 시각:
   - 현재 텍스트형 가능성. circular progress + chip 회전 (chip drawable rotate 360deg loop) +
     단계별 라벨.
3. **Onboarding 카드 슬라이드** (이미 있을 가능성) — 페이지 전환 spring 애니 + 좌→우 슬라이드
   + 다음 페이지 chip drawable peek.
4. **Lobby 진입** — Onboarding 완료 → Lobby 전환 시 fade + 잔고 카운트업 (0→STARTING_BANKROLL),
   sfx_chip_commit 한 번. 이미 있는 Daily Bonus 다이얼로그가 그 위에 등장.
5. **테이블 진입 cinematic intro** (현재 4.5초 휴식):
   - 카메라 줌인 effect (Box scale 1.1→1.0 + opacity fade) + 시트들 fadeIn stagger (좌→우 또는
     앉는 순서) + 카드 딜링 시작.
   - 4.5초 휴식 안에서 1.5초 intro + 2초 카드 딜링 + 1초 안정화.
6. **History 상세 reveal** — 진입 시 카드 / 액션 stagger 등장 (이미 있을 수 있음, 점검).

**수용 기준**:
- Splash 첫 진입 시 정적 → 동적으로 명확히 느껴진다.
- 테이블 진입 4.5초 안에 intro → 카드 딜링 → 안정화 흐름이 끊김 없이 연결.
- reduceMotion=true 시 모든 cinematic intro 가 simple fade 로 대체.

**위험**: 중. Splash / Onboarding 은 `feature/onboarding` 별도 모듈, 의존성/네비 영향 점검 필요.
테이블 진입 4.5초 변경은 사용자가 답답해질 수 있어 옵션화 고려.

**견적**: 2~2.5일.

---

### Phase 7 — 도전과제 / 일일 미션 (선택적, 사용자 confirm 후)

**목표**: 단기 목표 → 장기 목표 사이 다리. 잔존율 확보.

**작업** (사용자 결정 후):
1. **일일 미션 3종**:
   - "5핸드 플레이" / "1번 승리" / "1번 폴드 안 함" 같은 가벼운 목표.
   - 보상: chip 1k~5k 또는 streak +1 booster.
   - HandHistory 기반 자동 카운팅 (카테고리 1 orphan 의 observeRecent 활용).
2. **업적 (long-term)**:
   - "풀하우스 5회" / "올인 승리 3회" / "1만 칩 한 핸드 획득" / "tier GOLD 도달".
   - 진행률 % UI + 달성 시 chip burst 모달.
3. **Settings → 도전과제 화면 추가**:
   - 현재 진행 중 / 달성 / 미달성 분기.
   - 보상 수령 버튼.
4. **알림 통합** (BACKLOG §3 알림 항목과 합침):
   - 미션 임박 (오늘 만료 30분 전) 알림. 야간 22-07 블록.

**수용 기준**:
- 일일 미션 3개 매일 00:00 갱신, 달성 시 보상 수령 가능.
- 업적 진행률 정확.
- 알림 정상 발송 / 야간 블록 동작.

**위험**: 높음. 새 데이터 모델 (Mission/Achievement Entity) + 새 Screen + 새 모듈 가능성. 사용자
정책 "기능 추가 신중" 위배 위험. **Phase 7 은 사용자 명시 confirm 후에만 진행**.

**견적**: 4~5일 (도입 시).

---

### Phase 8 — A11y · ReduceMotion · 회귀 테스트 · 성능 점검

**목표**: 모든 신규 효과가 SfxPolicy / a11y 정책 따르고 저사양 단말 성능 회귀 없음.

**작업**:
1. **A11y matrix 정비**:
   - reduceMotion=true → 펄스/scale/shake/burst 모두 simple fade (220ms) 로 대체.
   - sfxEnabled=false → 모든 SFX skip.
   - hapticEnabled=false → 모든 Haptic skip.
   - bgmEnabled (Phase 3) — 기본 OFF.
2. **자체 회귀 매트릭스** — 모든 모드 (HOLDEM_NL / SEVEN_STUD / SEVEN_STUD_HI_LO) × 좌석 (2/3/4)
   × 각 a11y 토글 조합으로 1핸드씩 플레이 + 스냅샷 비교.
3. **퍼포먼스 점검**:
   - showdown 단계 frame drop (target 16ms) — 시트 6개 동시 펄스 + chip burst + WinnerBanner
     + count-up 동시 발생 시 프레임 측정.
   - Compose recomposition count 점검 (`Modifier.composed { ... }` 남용 회피).
   - SoundPool stream 동시 한도 초과 회피 (Phase 1 에서 이미 점검).
4. **APK 사이즈 회귀**:
   - 자산 추가 (BGM, 신 SFX) 후 APK 증가량 < 5MB 유지 (현재 ~89.6 MB → < 95 MB).

**수용 기준**:
- 모든 a11y 토글 조합에서 정상 동작.
- showdown 시 frame drop < 5%.
- APK < 95 MB.

**위험**: 낮음. 단 점검 누락 시 후속 발견 비용 큼.

**견적**: 1~1.5일.

---

## 2. 우선순위 / 권장 시퀀스

```
Phase 1 (자산 정상화)            ─┐
                                  ├─→ Phase 2 (액션 임팩트)
                                  │
Phase 3 (자산 보강) ─────────────┴─→ Phase 4 (진행/리워드)
                                       │
                                       ├─→ Phase 5 (마이크로)
                                       └─→ Phase 6 (cinematic)

Phase 7 (도전과제, optional)  ←─ 사용자 confirm 후
Phase 8 (회귀/A11y)            ←─ Phase 1~6 마지막에 wrap
```

**최소 셋** (= "효과 좋은데 위험 낮음"): Phase 1 + 2 + 4 + 5 + 8.
**최대 셋**: 전체 8 단계.
**추천 시작**: Phase 1 → Phase 2 → Phase 3 자산 결정 분기 (BGM 도입 vs skip 사용자 confirm) →
Phase 4 → Phase 5 → Phase 6 → Phase 8 → (Phase 7 별도 결정).

---

## 3. 자산 도입 정책 (Phase 3 분기)

| 자산 | 출처 후보 | 라이선스 | 크기 영향 | 자율 / confirm |
|---|---|---|---|---|
| Lobby BGM 1트랙 (lounge) | Kenney / OpenGameArt CC0 | CC0 | ~500KB OGG | confirm |
| Table BGM 1트랙 (tension low) | 동상 | CC0 | ~500KB OGG | confirm |
| chip_commit_b/c (variant) | 자체 pitch jitter (자산 0) | n/a | 0 | 자율 |
| deal_b (variant) | 자체 pitch jitter | n/a | 0 | 자율 |
| sfx_rare_hit_a | OpenGameArt CC0 | CC0 | ~50KB | confirm |
| sfx_tier_up | OpenGameArt CC0 | CC0 | ~80KB | confirm |
| chip drawable (Splash 배경) | 이미 있는 PayoutBadge / chip 자산 재사용 | 자체 | 0 | 자율 |

**원칙**: pitch jitter / 기존 자산 재사용으로 가능한 효과는 신규 자산 0 으로 처리. 진짜 새 톤이
필요한 BGM / rare hit / tier up 만 외부 도입.

---

## 4. 측정 / 검증

각 Phase 마지막에:
1. `./gradlew.bat :feature:table:compileDebugKotlin :feature:lobby:compileDebugKotlin :app:assembleDebug`
2. 변경 모듈 단위테스트.
3. 실기 install 후 BACKLOG.md §0 체크리스트 + 추가:
   - SfxPolicy / Haptic / a11y 토글 ON/OFF 시나리오.
   - 새 효과가 모든 모드 / 좌석에서 정상 동작.
4. 커밋 메시지에 "Phase N: 작업 요약 + 영향 모듈" 명기.

---

## 5. 위험 / 완화

| 위험 | 영향 | 완화 |
|---|---|---|
| 사용자가 자극 강도 과하다고 느낌 | 잔존 ↓ | SfxPolicy 토글 + a11y reduceMotion 기본선 |
| APK 사이즈 폭증 | 다운로드 이탈 | Phase 3 자산 도입 시 < 5MB 증가 cap, BGM 도입 confirm |
| 저사양 단말 frame drop | 게임 진행 freeze | Phase 8 성능 점검, DeviceTier LOW 시 강한 효과 자동 dim |
| Compose recomposition 폭주 | 배터리/발열 | `remember(key)` 정확히 적용, derivedStateOf 사용 |
| 외부 자산 라이선스 누락 | 법적 | `licenses/` 또는 `third_party/AUDIO_LICENSES.md` 강제 |
| 신규 모듈/추상화 도입 욕구 | 사용자 정책 위배 | 모든 효과를 기존 anim/sfx/theme 패키지 안에서 확장 |
| Phase 7 (도전과제) 스코프 폭증 | 일정 지연 | 별도 사용자 confirm 후에만 진행 |

---

## 6. 시작 신호

이 플랜에 동의하면 Phase 1 부터 자율 진행:
- Phase 끝마다 "N files changed, build passed" 정도 짧은 보고 + 커밋.
- Phase 3 직전 BGM/외부 자산 도입 여부만 AskUserQuestion.
- Phase 7 (도전과제) 는 별도 confirm.
- 중간에 stop 원하면 명시적으로 "stop" 또는 "Phase X 까지만".

조정 사항 있으면 (효과 강도 / Phase 추가/삭제 / 우선순위 / tier 임계치 / BGM 결정 등) 지금 말해주세요.

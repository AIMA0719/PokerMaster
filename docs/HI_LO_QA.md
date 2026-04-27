# 한국식 7스터드 Hi-Lo Declare — 매뉴얼 QA 체크리스트

본 문서는 5팀 병렬 작업 (Rules / Process / UI / AI / Tests) 머지 직후 통합 단계에서
사람 손으로 검증할 시나리오를 정리한다. 각 항목은 `- [ ]` 체크박스 — 통과 시 `- [x]`.

룰 요약 (재확인용):
- 7th 베팅 종료 후 모든 alive 좌석이 HI / LO / BOTH 동시 선언.
- HI 후보 = HI ∪ BOTH, LO 후보 = LO ∪ BOTH.
- STRICT BOTH: BOTH 선언자는 두 방향 모두 단독(또는 sole-best) 우승해야 scoop. 한 방향이라도
  지거나 동률이면 forfeit → 그 좌석을 제외하고 양 사이드 재계산.
- 8-or-better qualifier 없음. 페어/스트레이트/플러시는 그냥 약한 LO 로 평가.
- Wheel A-2-3-4-5 가 가장 강한 LO.
- 절반/절반 + 홀수칩 hi 측 우선.

---

## 1. 2인 Hi-Lo (헤즈업)

- [ ] 둘 다 BOTH 선언, 한쪽이 sole-best both → 그 좌석이 팟 전체 scoop. PotSummary.scoopWinnerSeats 에 표시.
- [ ] 둘 다 BOTH 선언, 한쪽이 LO 만 동률 (HI 단독 우승) → 룰상 forfeit (single tie 도 sole 아님). 양쪽 forfeit 시 팟 어떻게 처리되는지 확인 — dead money 인지 다음 핸드 이월인지 정책 확정.
- [ ] 한쪽 HI / 한쪽 LO → 절반씩 분배. 홀수칩 발생 시 hi 측이 +1 받는지 확인.
- [ ] 한쪽 HI / 한쪽 BOTH 이고 BOTH 가 양방향 우세 → BOTH 가 scoop (HI 선언자는 0).
- [ ] 한쪽 HI / 한쪽 BOTH 이고 HI 가 hi 우세 → BOTH 는 forfeit, HI 가 hi 절반 + lo 절반 (lo 후보 0이라 lo 도 hi 측에 합산).
- [ ] 칩 보존 — 핸드 후 모든 좌석 chips 합 = 핸드 전 합.

## 2. 3인 Hi-Lo

- [ ] A=HI, B=LO, C=BOTH 이고 C 가 양방향 우세 → C scoop.
- [ ] A=HI, B=LO, C=BOTH 이고 C 가 LO 동률, A 가 HI 단독 우승 → C forfeit, A=hi 절반, B=lo 절반.
- [ ] A=HI, B=LO, C=BOTH, C 가 HI 동률 → C forfeit, A 가 hi 절반(혹은 동률 분할), B 가 lo 절반.
- [ ] 셋 다 HI 선언 → 베스트 HI 가 팟 전체 (LO 후보 0이라 hiLoSplit 비활성).
- [ ] 셋 다 LO 선언 → 베스트 LO 가 팟 전체 (HI 후보 0).
- [ ] A=HI, B=BOTH, C=BOTH, B/C 둘 다 양방향 우세 (불가능하지만 동률 케이스) → 둘 다 forfeit 후 A 가 hi 단독 → 팟 전체 (lo 후보 0).

## 3. 4인 Hi-Lo + 사이드팟

- [ ] 4인 중 1명 5th 에서 all-in, 나머지 7th 까지 베팅 → 메인팟/사이드팟 각각 자격자 기준 별도 분배.
- [ ] 사이드팟 자격자에 메인팟 all-in 좌석 미포함 + 그 좌석이 BOTH 선언 후 forfeit → 사이드팟 hi/lo 재계산.
- [ ] uncalled bet 환급 — 7th 마지막 베팅이 콜 못 받으면 그 좌석에 즉시 환급 (declare 진입 전 환급).
- [ ] 짝수/홀수 chips 모두 칩 보존 검증.

## 4. Edge cases

- [ ] 5th 에서 all-in 한 좌석이 7th 까지 카드 받고 declare 단계에서도 선언해야 한다 (allIn 이지만 alive). UI 가 자동 선언 옵션 또는 명시 입력 요구하는지 확인.
- [ ] 7th 진입 전 모두 fold → DECLARE phase 건너뛰고 즉시 SHOWDOWN (단독 승자, declaration 비어있음).
- [ ] 7th 에서 베팅 진행 중 한쪽 fold → 단독 승자, DECLARE phase 건너뛰기.
- [ ] 인간이 SB/BB(=ante 포지션) 에서 declare prompt 받음. UI 위치 확인 (시트 위 오버레이 vs 하단 액션바 대체).
- [ ] AI declare 타이밍 — 자동 결정, UI lag 없음 (1초 내). DecisionCore 가 LowValue + HandValue 모두 평가.
- [ ] DECLARE 단계에서 인간이 앱 백그라운드 → resume 시 DECLARE 단계 재개 가능한가? (resumeRepo 비활성이라 noop 이지만, snapshot serialize 시 declaration 필드 포함 여부 확인)
- [ ] 모든 좌석 all-in 으로 4th 베팅 종료 → 5/6/7th 자동 진행 + DECLARE 단계 도달 → 자동 declare? 아니면 사람도 수동 입력?

## 5. Visual / UX

- [ ] 7스터드 4 visible upcards 가 2/3/4 좌석 레이아웃에서 모두 깔끔하게 렌더 — 카드 겹침 없음.
- [ ] declare 3-button 바 (HI / LO / BOTH) 가 Street.DECLARE 진입 시점에만 노출, 그 외 숨김.
- [ ] 다른 좌석의 declaration 은 SHOWDOWN 직전까지 hidden (동시 선언 시뮬레이션).
- [ ] 본인 declaration 은 본인 시트 라벨에 즉시 표시 (선택 후).
- [ ] PotSummary 분배 행에 `★ H` / `★ L` / `★ HL` 라벨 표기 정확.
- [ ] 양방향 BOTH forfeit 케이스에 `× 실패` (또는 비슷한 표시) 가 forfeit 좌석에 떠야 함.
- [ ] 자동 다음 핸드 카운트다운(3초) 이 declare 후 정산 화면에서도 동일하게 동작.
- [ ] 한국어 카테고리 라벨 (예: "휠", "8-low", "7-low") 이 LO 평가에서도 노출되는지 확인.
- [ ] HandEndSheet 의 best-five 카드 강조 — Hi 5장 + Lo 5장 모두 표기 가능 (현재 `bestFiveBySeat` 는 hi 만).

## 6. AI 합리성

- [ ] AI 가 BOTH 를 선언하는 케이스가 truly strong both ways 일 때만 (예: A234 + 풀하우스). 마진 케이스에서는 HI 또는 LO 단방향 보수.
- [ ] AI 가 wheel 베이스 + 페어 (hi 매우 약, lo 강) 일 때 LO 선언 — BOTH 시도하지 않음.
- [ ] AI 가 borderline equity 에서 BOTH 선언하지 않는다 — Persona.kt 의 looseness 와 무관하게 보수적 임계값 적용.
- [ ] AI 가 forfeit 가능성이 보이는 동률 직전 핸드 (예: 7-6-low + 두 페어) 에 LO 선언으로 보수.
- [ ] AI 가 LO 자격 0 (페이스 카드만) + HI 약 (high card) → HI 선언 (LO 무자격이지만 BOTH 는 무조건 forfeit).

## 7. 로비 + 진입

- [ ] LobbyScreen 의 `7스터드 Hi-Lo` 카드가 wallet > 0 일 때 활성화. 잔고 0 이면 비활성 + "잔고 부족" 라벨.
- [ ] Buy-in 시 wallet 에서 정상 차감 (humanBuyIn 만큼).
- [ ] 테이블 진입 시 mode = SEVEN_STUD_HI_LO, ante=10 / bringIn=25 (TableViewModel.createDefault).
- [ ] 핸드 종료 후 history 에 mode="SEVEN_STUD_HI_LO" 로 기록되고, declarations + payouts 가 ShowdownSummary 에 직렬화.
- [ ] 핸드 도중 settle (앱 백버튼 → 로비) 시 wallet 에 인간 좌석 finalChips 정상 입금. settle 중복 방지(_settled flag) 동작.

---

## Known issues at parallel-work cutoff (5팀 병렬 작업 컷오프 시점)

본 섹션은 Team Tests 가 5팀 병렬 작업 베이스(`feat/hi-lo-declare`) + 자기 worktree 에서
발견한 이슈를 정리한다. 각 항목 fix 는 owner team 의 브랜치에서 수행 (스코프 위반 회피).

### B1. Persona.kt — `when (c.action)` 비-exhaustive (Build BREAK)

- File: `engine/decision/src/main/kotlin/com/infocar/pokermaster/engine/decision/Persona.kt:56`
- Symptom: `engine:decision` 컴파일 실패. 아래 의존 모듈(controller, table) 도 모두 빌드 차단.
  ```
  e: ... Persona.kt:56:36 'when' expression must be exhaustive.
     Add the 'DECLARE_HI', 'DECLARE_LO', 'DECLARE_BOTH' branches or an 'else' branch.
  ```
- Likely root cause: `feat/hi-lo-declare` 베이스가 `ActionType` enum 에 DECLARE_HI/LO/BOTH 를 추가했지만,
  PersonaBias.apply 의 when 식이 업데이트되지 않음.
- Suggested fix: 3개 enum 값에 페르소나 bias 0.0 (선언은 amount=0 강제 액션 — bias 적용 의미 없음)
  branch 추가. Team Tests 가 본 worktree 에서 별도 commit `fix(hi-lo): Persona when ...` 로 처리.
- Owner team: **unowned** (Team AI 의 명시 owned 파일은 EquityCalculator/AiDriver 두 개뿐).
- 처리 상태: **이 브랜치에서 fix 적용** (커밋 `fix(hi-lo): Persona PersonaBias.apply 누락 DECLARE 브랜치 추가`).

### B2. StudReducer.act `when (action.type)` 비-exhaustive

- File: `engine/controller/src/main/kotlin/com/infocar/pokermaster/engine/controller/StudReducer.kt:171`
- Symptom: `engine:controller` 컴파일 실패. controller 에 의존하는 controller-test / feature-table /
  app 모듈 모두 차단.
- Likely root cause: DECLARE_HI/LO/BOTH 를 받아 declaration 필드에 기록하고 다음 toAct 으로 전이하는
  분기가 없음. 정상 동작은 Street.DECLARE 단계에서만 허용.
- Suggested fix (Team Process):
  ```kotlin
  ActionType.DECLARE_HI, ActionType.DECLARE_LO, ActionType.DECLARE_BOTH ->
      applyDeclaration(state, seat, action.type)
  ```
  + `applyDeclaration` private 함수 신규: `state.street == Street.DECLARE` require, players[seat]
  declaration 채움, actedThisStreet=true. 모든 alive 좌석이 declaration!=null 이면 runShowdown 호출.
- Owner team: **Process** (StudReducer.kt 명시 소유).
- 처리 상태: 본 워크트리에서 미수정. 머지 시 Process 브랜치가 해결.

### B3. SidePotDistribution `perWinner` 계산 — Hi-Lo 시 분모 오류

- File: `feature/table/src/main/kotlin/com/infocar/pokermaster/feature/table/SidePotDistribution.kt:43-44`
- Symptom: Hi-Lo 모드에서 한 좌석은 hi 우승, 다른 좌석은 lo 우승일 때 `winnerCount = pot.winnerSeats.size = 2`
  → `perWinner = pot.amount / 2`. 실제로는 hi 측 절반과 lo 측 절반이 각각 1명씩 가져가는 거라 `perWinner`
  표기는 맞지만, hi/lo 동률(예: 2명 lo tie + 1명 hi) 케이스에선 분모가 3이 되고 실제 lo 분배는
  `(pot/2) / 2` 라서 표기와 어긋남.
- Likely root cause: `winnerSeats` 는 hi ∪ lo 합집합. half-split 분기가 무시됨.
- Suggested fix (Team UI):
  - `isHiLoBranch == true` 일 때 hi 측은 `(pot.amount/2 + oddChip) / pot.hiWinnerSeats.size`,
    lo 측은 `pot.amount/2 / pot.loWinnerSeats.size` 로 분리 표기.
  - 또는 우측 "+분배" 라벨에 두 줄로 분리 (★H +X / ★L +Y).
- Owner team: **UI** (feature/table 소유).
- 처리 상태: 본 워크트리에서 미수정.

### B4. SidePotDistribution `isHiLoBranch` 판정 — LO 자격자 0 케이스에서 라벨 누락

- File: `feature/table/src/main/kotlin/com/infocar/pokermaster/feature/table/SidePotDistribution.kt:101`
- Symptom: Hi-Lo 모드에서 LO qualifier 0 (한국식이라 페어/스트레이트만 있어도 약한 lo 평가됨 — 사실상
  거의 항상 누군가는 lo 자격) 케이스가 거의 없지만, 모든 좌석이 LO 후보 0 (다 폴드 등) 이면
  `isHiLoBranch = false` 로 떨어져 `★ H` 라벨 누락 (그냥 `★ name`).
- Likely root cause: `loWinnerSeats.isNotEmpty()` 만 보고 모드 자체를 보지 않음.
- Suggested fix (Team UI): `state.mode == SEVEN_STUD_HI_LO` 를 prop 으로 전달하거나, loWinnerSeats 비어있고
  hiWinnerSeats 만 있을 때도 모드 체크해서 `★ H` 라벨링.
- Owner team: **UI**.
- 처리 상태: 본 워크트리에서 미수정.

### B5. TableUiMapper.mapActionBar 가 Street.DECLARE 인지 비반영

- File: `feature/table/src/main/kotlin/com/infocar/pokermaster/feature/table/TableUiContracts.kt:61`
- Symptom: DECLARE 단계에서도 mapActionBar 가 일반 ActionBarState (toCall/raise 등) 를 리턴하면
  사용자가 잘못된 옵션 (Call/Raise/Fold) 을 클릭할 수 있다.
- Likely root cause: ActionBarState 가 declare 옵션을 모름. 별도 DeclareBarState 또는 상위에서
  `state.street == Street.DECLARE` 분기 필요.
- Suggested fix (Team UI):
  - mapActionBar 에서 `state.street == Street.DECLARE` 면 null 반환, 별도 mapDeclareBar 신설.
  - 또는 ActionBarState 에 `inDeclarePhase: Boolean` + `canDeclareHi/Lo/Both` flag 추가.
- Owner team: **UI**.
- 처리 상태: 본 워크트리에서 미수정.

### B6. TableViewModel.maybeRecordFinishedHand `lastRecordedHandIndex` 초기값 -1

- File: `feature/table/src/main/kotlin/com/infocar/pokermaster/feature/table/TableViewModel.kt:383`
- Symptom: 핸드 1번 (handIndex=1) 의 첫 쇼다운에서도 `state.handIndex == lastRecordedHandIndex`
  (둘 다 -1L 비교 → false) 라 정상 기록되지만, 만약 향후 handIndex 가 0L 부터 시작하도록 바뀌면
  -1 비교 OK, 0 비교 false — 안전. 다만 매직 넘버 -1 보다 nullable 가독성↑.
- Likely root cause: 단순 가독성 / 향후 제로 기반 인덱스 호환.
- Suggested fix (Team UI): `Long?` 로 바꾸거나 sentinel 을 `Long.MIN_VALUE` 로 변경.
- Owner team: **UI** (소유). 우선순위 낮음.
- 처리 상태: 본 워크트리에서 미수정.

### B7. TableViewModel `onNextHand` 가 declare 도중 호출되면 ackShowdown 만 처리, 새 핸드 진입 못함

- File: `feature/table/src/main/kotlin/com/infocar/pokermaster/feature/table/TableViewModel.kt:196`
- Symptom: declare 단계에서 사용자가 "다음 핸드" 버튼 누를 일은 없지만, 자동 다음 핸드 카운트다운
  타이밍이 declare 진행 중에 트리거되면 (정상은 pendingShowdown 채워질 때만 트리거되므로 안전하지만,
  state emission race condition 시) 의도치 않게 핸드가 전환될 수 있다. 현재 코드는
  `s.pendingShowdown != null` 만 검사 — declare 단계는 pendingShowdown=null 이라 분기 통과 안 함 (안전).
- Likely root cause: 안전하긴 한데 `state.street == Street.DECLARE` 일 때 cancelAutoNext 가 호출되는지
  확인 필요. 현재는 pendingShowdown 채워질 때만 startAutoNextCountdown 이라 race 없음.
- Suggested fix: 방어적 체크 — `if (s.street == Street.DECLARE) return`. 우선순위 매우 낮음.
- Owner team: **UI**.
- 처리 상태: 미수정.

### B8. AppNav.kt `entry` 변수 그림자 (smell, 동작은 OK)

- File: `app/src/main/kotlin/com/infocar/pokermaster/AppNav.kt:206-212`
- Symptom: `Routes.TABLE` composable 람다에서 outer `entry: NavBackStackEntry` 를 inner
  `val entry = remember(...)` 로 그림자(shadow). 컴파일 OK, 동작 OK, 단 readability 저하 + 디버거 혼란.
- Likely root cause: 변수 명 충돌.
- Suggested fix: inner 변수를 `llmEntry` 등으로 rename.
- Owner team: **unowned** (app/ 모듈, 누구도 명시 소유 안 함).
- 처리 상태: 본 워크트리에서 미수정 (스타일 이슈, 우선순위 낮음).

### B9. WalletRepository.recordCheckIn — 신규 사용자 첫 진입 시 streak=1 부여

- File: `core/data/src/main/kotlin/com/infocar/pokermaster/core/data/wallet/WalletRepository.kt:125-128`
- Symptom: `lastCheckInEpochDay == 0L` 인 신규 사용자 진입 시 `todayEpoch - 0 != 1` → `newStreak = 1`.
  이건 의도 — 첫 체크인은 streak 1 부터 시작. 단 epoch 0 (1970-01-01) 이 진짜로 last check-in 이었던
  엣지 케이스 (사실상 불가능) 에서도 streak 단절로 해석.
- Likely root cause: 0 을 sentinel 로 사용하는 디자인.
- Suggested fix: 우선순위 매우 낮음. `lastCheckInEpochDay: Long?` 로 변경하면 깔끔하지만 Room migration
  필요. 현재 동작은 사용자 룰에 부합.
- Owner team: **unowned**. (data layer, 누구도 명시 소유 X.)
- 처리 상태: 본 워크트리에서 미수정 (currently-correct, 의도된 동작).

### B10. Lobby `canEnterTable()` Compose 외부에서 호출 시 wallet.value 가 stale

- File: `feature/lobby/src/main/kotlin/com/infocar/pokermaster/feature/lobby/LobbyViewModel.kt:82`
- Symptom: `canEnterTable(): Boolean = wallet.value.balanceChips > 0L`. wallet 은 stateIn 으로
  WhileSubscribed(5_000) 라 Compose 가 collect 안 하면 5초 후 stop → wallet.value 는 마지막 emit 값
  유지 (Room 변경 반영 안 됨). LobbyScreen 이 collectAsState 하므로 정상이지만, 다른 호출자(테스트 등)
  가 wallet observe 없이 canEnterTable 호출하면 초기값(STARTING_BANKROLL=50_000) 으로 항상 true 리턴.
- Likely root cause: stateIn 의 lazy collection.
- Suggested fix: canEnterTable 을 `suspend` 로 만들고 `walletRepo.getState()` 호출. 또는 호출부에
  observe 강제. 우선순위 낮음 — UI 만 호출하므로 현재 정상.
- Owner team: **unowned** (Lobby).
- 처리 상태: 미수정 (스코프 외 + 동작 정상).

---

## Known build issues — to be resolved at integration

본 worktree 에서 다음 명령:

```
./gradlew :engine:rules:test :engine:controller:test :engine:decision:test :feature:table:compileDebugKotlin --no-daemon
```

실행 결과 (B1 fix 적용 후):

- `:engine:rules:test` → **PASS**
- `:engine:decision:test` → **PASS** (Persona.kt fix 후)
- `:engine:controller:test` → **FAIL (compile)** — B2 (StudReducer when 비-exhaustive). Team Process 머지 시 해결.
- `:feature:table:compileDebugKotlin` → **FAIL (compile)** — B2 의존성으로 차단. Team Process 머지 시 해결.

추가 통합 단계에서 확인:
- HiLoDeclareIntegrationTest 의 `@Disabled` 4건 활성화 (Team Rules + Process 머지 후).
- 위 시나리오 1~7번 모든 항목 사람 손으로 통과 확인.
- LobbyScreen 의 `SEVEN_STUD_HI_LO` 카드 진입 → 실제 Hi-Lo 핸드 1회 완주 → history 에 기록.

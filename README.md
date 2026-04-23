# 포커마스터 (PokerMaster)

오프라인 싱글플레이 + 온디바이스 AI 한국식 포커 게임.

- 7카드 스터드 (한국식)
- 7스터드 하이로우 (8 or better)
- 텍사스 홀덤 (No-Limit)

## 설계서

- `Desktop/PokerMaster_기획설계서_v1.0.md` — 본 설계
- `Desktop/PokerMaster_기획설계서_v1.1_보강판.md` — 5팀 감사 후 패치본 (v1.0 우선 충돌 시 v1.1 적용)

두 문서를 함께 읽고 구현. 본 README 는 빌드/구조 안내만.

## 빌드

요구 사항:
- JDK 17+ (현재 환경 JDK 21 동작 검증)
- Android SDK 35 + NDK 28.x (16KB page size 대응, ADR-008/v1.1 §5.5)
- Android Studio Hedgehog 이상 권장

```bash
# 디버그 APK
./gradlew :app:assembleDebug

# 단위 테스트
./gradlew test

# Lint
./gradlew lint
```

## 모듈 구조 (M0)

```
:app                       Compose NavHost (Splash → Lobby)
:core:model                도메인 모델 (Card, GameMode, Action) — JVM only
:core:ui                   디자인 토큰 (PokerMasterTheme, PokerColors)
:feature:lobby             메인 로비 화면

:build-logic:convention    Gradle 컨벤션 플러그인 5종
                           pokermaster.android.application
                           pokermaster.android.library
                           pokermaster.jvm.library
                           pokermaster.android.compose
                           pokermaster.android.hilt
```

페이즈별 모듈 활성 계획:
- **M1**: `:engine:rules`, `:core:data` (Room)
- **M2**: `:engine:decision`
- **M3**: `:feature:table`, `:feature:settings`
- **M4**: `:engine:llm`, `:native:llamacpp`
- **M5**: `:feature:history`, `:feature:training`

## 정책

- `docs/legal/PRIVACY_POLICY.md` — 개인정보 처리방침
- `docs/legal/TERMS_OF_USE.md` — 이용약관
- `docs/legal/YOUTH_PROTECTION.md` — 청소년 보호 정책
- `docs/legal/THIRD_PARTY_NOTICES.md` — 오픈소스 라이선스 (수동 + 추후 oss-licenses-plugin 자동화)
- `docs/legal/IARC_QUESTIONNAIRE.md` — IARC 등급분류 응답 가이드
- `docs/legal/KIPRIS_TRADEMARK_CHECK.md` — 상표 검색 체크리스트

이 6개 문서는 출시 전 필수.

## 라이선스

본 프로젝트의 코드: TBD (출시 직전 결정).
사용하는 OSS 및 모델 라이선스는 `docs/legal/THIRD_PARTY_NOTICES.md` 참조.
모델은 Llama 3.2 Community License 의무 표기 — 앱 내 "Built with Llama" 표기 (M4).

## 환경 변수 / 설정

`local.properties` (git 제외) 에 SDK/NDK 경로 지정:
```
sdk.dir=C\:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk\\ndk\\28.2.13676358
```

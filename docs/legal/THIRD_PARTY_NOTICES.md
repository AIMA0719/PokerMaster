# 오픈소스 라이선스 고지

본 앱은 다음 오픈소스 소프트웨어 및 모델을 사용합니다. 각 라이선스 사본은 앱 내 [설정 > 정보 > 오픈소스 라이선스] 에서 전문 확인 가능합니다(M6에 자동 생성 도구로 빌드 시점 주입).

> 본 문서는 v1 출시 전까지 의존성을 추가할 때마다 함께 갱신합니다.

---

## 빌드/런타임 라이브러리

### Android Gradle Plugin / Jetpack Compose / AndroidX
- 라이선스: Apache License 2.0
- 저작권자: Google LLC, The Android Open Source Project
- 출처: https://developer.android.com / https://github.com/androidx/androidx

### Kotlin / kotlinx.coroutines
- 라이선스: Apache License 2.0
- 저작권자: JetBrains s.r.o.
- 출처: https://kotlinlang.org

### Hilt (Dagger)
- 라이선스: Apache License 2.0
- 저작권자: Google LLC
- 출처: https://dagger.dev/hilt

### Material Symbols (Icons)
- 라이선스: Apache License 2.0
- 저작권자: Google LLC
- 출처: https://fonts.google.com/icons
- **표기 의무**: NOTICE 파일 동봉 + 출처 명시

---

## 테스트

### JUnit 4 / 5
- 라이선스: EPL 2.0 (JUnit 5) / EPL 1.0 (JUnit 4)

### Google Truth
- 라이선스: Apache License 2.0
- 출처: https://truth.dev

### MockK
- 라이선스: Apache License 2.0
- 출처: https://mockk.io

### Turbine
- 라이선스: Apache License 2.0
- 저작권자: Cash App
- 출처: https://github.com/cashapp/turbine

---

## AI 모델 (M4 통합 예정)

### Llama 3.2 1B Instruct
- 라이선스: **Llama 3.2 Community License**
- 저작권자: Meta Platforms, Inc.
- 출처: https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct
- **앱 내 표기 의무 (4가지)**:
  1. **"Built with Llama"** 명시 — 앱 내 라이선스 화면 + 사용처
  2. 월간 활성 사용자 7억 미만 조건 충족 (운영자 자가 보증)
  3. Llama 3.2 Acceptable Use Policy 준수
  4. 파생 모델 명명 시 "Llama" 접두 유지

### Qwen2.5 1.5B Instruct (옵션)
- 라이선스: Apache License 2.0
- 저작권자: Alibaba Cloud
- 출처: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct

### Gemini Nano (Google AI Edge SDK, 단말 분기 옵션)
- 라이선스: Google Generative AI 사용 약관 (단말 OS가 호스팅하는 모델 호출)
- 출처: https://ai.google.dev/edge

---

## AI 추론 런타임 (M4 통합 예정)

### llama.cpp
- 라이선스: MIT License
- 저작권자: Georgi Gerganov 외 컨트리뷰터
- 출처: https://github.com/ggerganov/llama.cpp
- **표기 의무**: 저작권 표시 + 라이선스 사본 동봉

---

## 폰트 (M5 도입 예정)

### Pretendard
- 라이선스: SIL Open Font License 1.1
- 저작권자: Kil Hyung-jin
- 출처: https://github.com/orioncactus/pretendard
- 임베딩 가능, 단독 판매 금지

### Inter
- 라이선스: SIL Open Font License 1.1
- 저작권자: Rasmus Andersson
- 출처: https://rsms.me/inter

---

## 자체 자산 / 카드 페이스

표준 52장 카드 그래픽은 자체 일러스트로 제작 또는 CC0 자산을 사용합니다.
한게임/피망 등 타사의 카드 디자인·아바타·BGM 은 사용하지 않습니다.

---

## 감사 사이클

본 문서는 의존성 추가/변경 시마다 갱신합니다. 빌드 시점 자동 검증은 M6 의 `oss-licenses-plugin` 도입으로 처리합니다.

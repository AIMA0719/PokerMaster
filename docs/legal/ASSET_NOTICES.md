# 이미지 자산 라이선스 고지

본 앱이 사용하는 이미지(그래픽) 자산의 출처와 라이선스를 정리합니다.
모든 자산은 CC0 (Creative Commons Zero, 퍼블릭 도메인) 또는 Apache-2.0 / MIT 등
**재배포·상업적 이용에 제약이 없는 라이선스**의 자산만 포함합니다.

CC0 자산은 표기 의무가 없지만, 원작자에 대한 감사 표시 및 검증 가능성을 위해 출처를 명시합니다.

> 새 이미지 자산을 추가할 때는 이 문서에 한 행을 추가하고, 라이선스 텍스트 사본을
> `licenses/` 또는 자산 폴더 인근에 함께 보존해야 합니다.

---

## 카드 페이스 / 카드 백 (52 + 1 = 53장)

| 파일                                          | 출처                                                       | 라이선스 | 저작자                          |
| --------------------------------------------- | ---------------------------------------------------------- | -------- | ------------------------------- |
| `feature/table/.../drawable-nodpi/card_*_*.png` (52장) | https://kenney.nl/assets/boardgame-pack (Boardgame Pack v2) | CC0 1.0  | Kenney Vleugels (www.kenney.nl) |
| `feature/table/.../drawable-nodpi/card_back.png` (1장) | 동상 (Boardgame Pack v2, `cardBack_blue2.png` 리네임)        | CC0 1.0  | Kenney Vleugels                 |

- **Resolution**: 140 × 190 px (PNG, RGBA)
- **Aspect**: ≈ 1 : 1.357 (실제 카드 비율과 근사)
- **렌더링**: `feature/table/.../ImageCardComposable.kt` 가
  `Image(painterResource(...), contentScale = ContentScale.Fit)` 로 그린다.
- **활성화**: 설정 > 외관 > "이미지 카드 사용" 토글 (기본 OFF). OFF 면 기존 Canvas+Text
  자체 렌더 경로(`CardComposable.kt`) 가 동작 — 어떤 외부 자산도 사용하지 않는다.
- **라이선스 사본**: 원본 zip 의 `license.txt` 내용은 다음과 같다:

```
Boardgame pack v2 by Kenney Vleugels (www.kenney.nl)

CC0 License (Creative Commons Zero)
http://creativecommons.org/publicdomain/zero/1.0/

You may use these graphics in personal and commercial projects.
Credit (Kenney or www.kenney.nl) would be nice but is not mandatory.
```

---

## (보류) 펠트 배경 텍스처

검증 가능한 CC0 펠트 텍스처를 확보하지 못해 현재 슬롯은 비어 있고,
`HangameFelt` 의 라디얼 그라데이션이 기본 배경 역할을 한다.
향후 CC0 자산을 발견하면 `feature/table/.../drawable-nodpi/felt_bg.png` 로 드롭하고
`TableScreen.HangameFelt` 의 TODO 주석에 따라 `Modifier.paint(...)` 를 활성화한다.

---

## (보류) 칩 / 아바타

이번 슬라이스 범위 밖. 향후 별도 스프린트에서 CC0 자산으로 추가 예정.

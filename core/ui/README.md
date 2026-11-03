# 차연 디자인 시스템

`dev.chayeon.core.ui`의 명명된 컴포넌트를 사용합니다. 화면에서 Material 기본 표면을 섞거나 색·글자 토큰을 다시 만들지 않습니다. 토큰의 근거는 `docs/design/screens/RedStyle.dc.html`, `RedHome.dc.html`, `RedConnected.dc.html`입니다.

## 시작

- `ChayeonTheme(policy, animatorScaleOverride)`가 주행 정책과 장식 모션을 제공합니다. 정책 기본값은 `UNKNOWN`입니다. `animatorScaleOverride`는 갤러리·테스트용이며 제품에서는 시스템 배율을 그대로 사용합니다.
- `ScreenScaffold(topBar, bottomBar, primary, secondary)`는 1100dp 이상에서 두 열, 그 미만에서 스크롤 가능한 한 열을 만듭니다. 기본 화면은 1682×1210dp입니다.
- `ChayeonColors`, `ChayeonType`, `ChayeonSpacing`, `ChayeonRadii`, `ChayeonElevation`, `ChayeonMotion`을 사용합니다.
- `JellyButton`·토글·라디오·체크박스·하단 메뉴는 `ALLOWED`일 때만 동작합니다. 나가기에는 `JellyButton(allowWhenRestricted = true)`를 명시합니다. 기능의 ViewModel과 저장소도 별도로 정책을 검사해야 합니다.

## 컴포넌트

| 묶음 | API |
|---|---|
| 표면 | `ChayeonBackground`, `GlassCard`, `HeroPanel` |
| 입력 | `JellyButton`, `GelToggle`, `GelRadioRow`, `GelCheckbox`, `PresetButton` |
| 표시 | `ChayeonText`, `IconOrb`, `StatusChip`, `InterestTag`, `ChatBubble`, `StateCard` |
| 탐색 | `BackButton`, `StepIndicator`, `GearStrip`, `TopBanner`, `BottomNav`, `ScreenScaffold` |
| 에셋 | `MochiAvatar`, `AvatarDish`, `ThreadStage`, `ChayeonAssets` |
| 시그니처 | `SignatureKnot(seed, interlockedWith)`, `ThreadView(stage)` |

`ExperienceState` 18종은 `StateCard`에 대응합니다. `onAction`이 없으면 행동 버튼을 생략합니다. 상대 차단 여부나 비공개 상태의 이유를 추측해 표시하지 않습니다. 의미 있는 에셋에는 설명이 있고, 중복 장식에는 `decorative = true`를 씁니다.

`SignatureKnot`은 시드를 섞어 고리의 위상·기울기·폭을 정하고, 3차원 곡선을 투영해 깊이 순서로 그립니다. 어두운 외곽·붉은 몸체·가는 반사광을 따로 그려 교차점의 위아래를 보존합니다. 두 매듭도 같은 깊이 정렬에 참여합니다.

## 글꼴과 이미지

Asta Sans 원본은 [Google Fonts의 ofl/astasans](https://github.com/google/fonts/tree/main/ofl/astasans)에서 가져왔습니다. `src/main/res/font/asta_sans.ttf`와 배포 필수 원문 `src/main/res/raw/asta_sans_ofl.txt`를 함께 보관합니다. 원본 이미지 14개는 `drawable-nodpi`에 동일 내용으로 복사했고 파일명의 하이픈만 밑줄로 바꿨습니다.

## 검증

```sh
./gradlew :core:ui:recordRoborazziDebug
./gradlew :core:ui:verifyRoborazziDebug :core:ui:lintDebug
```

스크린샷 기준은 `src/test/screenshots`입니다. 갤러리는 모든 구성 요소와 18 상태, 넓은 창·좁은 창을 포함합니다. 개별 구성 요소는 같은 `w1682dp-h1210dp-land-mdpi` 환경에서 독립 캡처합니다. 시스템 애니메이션은 테스트에서 0으로 고정합니다. 갤러리의 여러 매듭과 아바타는 가상 데이터입니다.

# UI v2 에셋 출처

이 이미지들은 이 프로젝트를 위해 이미지 생성 모델로 만들었어요. 앱에는 변환된 WebP만 포함하며 아래는 원본 제작 프롬프트와 출처 기록이에요. 원본 PNG는 저장소에 포함하지 않아요.

# 차연 일러스트 세트 · w11-art

내장 `image_gen` 도구로 생성한 기본 세트 11장과 프로필 아바타 12장, 합성 검토 시트 1장이에요. 총 PNG 24장이며, 프로필 아바타 추가 세트는 문서 뒷부분에 정리했어요. 앱 코드는 수정하지 않았어요. 검정·딥 크림슨 배경, 젤리 몸체, 좌상단 흰 하이라이트와 은은한 붉은 림 라이트를 공통으로 사용해요.

## 파일과 사용 화면

배경은 불투명 RGB, 캐릭터와 페어 히어로는 투명 알파가 있는 RGBA PNG예요. 크기는 가로×세로 픽셀, 용량은 실제 파일 바이트예요.

| 파일 | 크기 | 용량 | 사용 화면 | 설명 |
|---|---|---:|---|---|
| [bg-parking.png](bg-parking.png) | 1536×1024 | 1,844,922 B | 주차 중 발견·만남 | 도시 불빛과 작은 쉼터가 보이는 조용한 밤 주차장. |
| [bg-road.png](bg-road.png) | 1536×1024 | 1,464,948 B | 주행 중·음성 통화 | 먼 붉은 후미등과 최소한의 반사만 남긴 어두운 도로. |
| [bg-home.png](bg-home.png) | 1536×1024 | 1,910,015 B | 집 보호 구역·쉬는 중 | 따뜻한 창문 불빛이 있는 고요한 주택가. |
| [bg-dawn.png](bg-dawn.png) | 1536×1024 | 1,631,830 B | 온보딩·브랜드 | 붉은 보랏빛 여명이 희미하게 번지는 열린 도로. |
| [mochi-wave.png](mochi-wave.png) | 1024×1024 | 508,417 B | 홈·온보딩 | 붉은 실을 들고 밝게 손을 흔드는 모찌. |
| [mochi-thread.png](mochi-thread.png) | 1024×1024 | 625,220 B | 연결 요청 보내기·받기 | 실의 끝을 내밀며 연결을 제안하는 모찌. |
| [mochi-call.png](mochi-call.png) | 1024×1024 | 643,401 B | 음성 통화 | 작은 헤드셋을 쓰고 실을 전화선처럼 든 모찌. |
| [mochi-sleep.png](mochi-sleep.png) | 1024×1024 | 498,175 B | 집 보호 구역·쉬는 중 | 실을 베개처럼 두르고 편안하게 잠든 모찌. |
| [mochi-bye.png](mochi-bye.png) | 1024×1024 | 574,611 B | 연결 종료 | 두 조각으로 끊어진 실과 따뜻한 작별 인사. |
| [mochi-knot.png](mochi-knot.png) | 1024×1024 | 617,746 B | 애프터·연락처 교환 | 두 손 사이에 작은 매듭을 선물처럼 내미는 모찌. |
| [pair-call.png](pair-call.png) | 1536×1024 | 1,249,616 B | 통화 화면 대형 히어로 | 좌우의 빨강·흰색 모찌를 하나의 느슨한 붉은 실로 연결. |

## 참조와 제작 과정

- `../ref/colleague-reference.png`에서는 공간감, 따뜻한 영화 조명, 큰 캐릭터의 상황 표현만 참고했어요. 노란 마스코트·노을 색상·주유소·화면 구성은 사용하지 않았어요.
- `../ref/current-home.png`, `../ref/av-*.webp`, `../ref/kv.jpg`, `../ref/thread-*.webp`를 이미지 뷰어로 모두 확인했어요. 기존 모찌의 말랑한 몸체·작은 눈·볼의 홍조·유광 표면을 유지했어요.
- 첫 `mochi-wave` 생성본을 기준 캐릭터로 먼저 만들었어요. 이후 다섯 포즈와 페어 히어로에는 이 초기 기준 이미지를 `referenced_image_paths`로 직접 전달했어요. 마지막 wave 재생성도 같은 기준 이미지를 참조했어요.
- 배경 4장은 `transparent_background=false`, 캐릭터 7장은 `transparent_background=true`로 생성했어요.
- 도구가 반환한 모찌 원본은 요청한 픽셀 크기와 달라, ImageMagick으로 비율을 유지해 1024×1024에 맞췄어요. 투명 알파를 보존했어요. 배경과 페어 히어로는 반환된 1536×1024 원본을 그대로 복사했어요.
- 아래 프롬프트는 생성 요청에 사용한 영어 원문이에요. 배경·포즈 프롬프트는 해당 공통 문단과 파일별 추가 문단을 순서대로 이어 붙인 내용이에요.

## 배경 공통 프롬프트

```text
Use case: stylized-concept. Premium cinematic 3D environment illustration for Chayeon automotive app, landscape exactly 1536x1024. Black #070304 and deep crimson #3A0710 with restrained scarlet-red light accents, no hot pink. Beautiful atmospheric depth, soft rich materials, restrained glossy wet-asphalt reflections, gentle soft illumination from upper left, discreet red rim accents. Environment only, no characters, no people, no visible cars or vehicle bodies, no vehicle branding, no license plates, no gas stations, no signs, no text, letters, numbers, logos or watermarks. Not a UI screenshot: no panels or overlays. Keep left 45 percent and bottom 35 percent significantly darker with broad low-detail negative space for white typography and translucent glass cards. Preserve detail and cinematic mood primarily in the upper-right distance. Calm premium atmosphere, not science fiction, not cyberpunk, not a yellow sunset.
```

### bg-parking.png

```text
Scene: quiet urban rest-area parking lot at night, subtle empty parking bay lines only on the right, softly rounded concrete planters and dark trees at the far edges, distant city skyline dissolved into sparse red taillight-like and warm ivory bokeh, a small architectural shelter far back on right with a muted warm ceiling light. Broad charcoal foreground, deep black night sky, gentle burgundy reflections. Camera standing still at human eye level, wide spacious establishing shot, intimate welcoming atmosphere.
```

### bg-road.png

```text
Scene: exceptionally calm dark night highway from a stable driver eye-level viewpoint, but no dashboard or car interior. Simple road recedes slowly toward an off-center right vanishing point, distant city is a soft barely visible silhouette; just two very faint short red taillight trails near the distant right horizon and a few dim ivory bokeh dots. No rushing motion, no radial blur, no streaks crossing the foreground, no dramatic turns, no close bright lamps. Very dark low-contrast foreground and quiet black sky, minimal visual detail suitable behind a driving voice-call screen. A sparse muted reflection follows the road at right; almost-black left foreground.
```

### bg-home.png

```text
Scene: a peaceful residential street late at night, softly rounded contemporary low-rise homes on the right side with a few warmly glowing ivory windows and sheltered doorways, dark trees, an empty street curving very gently into the distance. The muted warm windows are comforting small accents, surrounded by black and dark wine tones, very subtle red reflection on damp asphalt, dark far-left foliage. No pedestrians, no vehicles, no house numbers, no mailbox lettering. A safe quiet resting mood. Architecture is understated and convincingly three-dimensional, middle-right focal detail, broad dark foreground.
```

### bg-dawn.png

```text
Scene: the earliest dawn over a quiet open road toward the right horizon, vast soft near-black violet sky with a very faint narrow red-violet glow low on the horizon, layered distant hills and sparse small city bokeh far right. Road and landscape remain dark charcoal and deep crimson with a softly glossy damp surface. Dawn just beginning, no sun disc, no orange-yellow sunset, no bright saturated magenta sky. Contemplative hopeful brand and onboarding background with generous uncluttered sky and deep dark left/bottom negative space.
```

## 기준 캐릭터와 wave 프롬프트

### mochi-wave.png

아래 요청으로 최종 wave를 재생성했어요. `REGENERATION` 앞의 문단은 초기 기준 캐릭터 생성 프롬프트와 같아요.

```text
Use case: stylized-concept. Create one premium 3D mascot asset for Chayeon, a calm black-red-white car connection app. Output exactly 1024x1024 PNG with true transparent background. Subject: one lovable glossy scarlet-red mochi jelly blob, squat softly domed pear-like body, broad rounded bottom, translucent soft subsurface scattering, smooth pristine surface, two tiny dark espresso bead eyes with small white glints, subtle warm blush cheeks and a tiny cheerful curved open smile. Exactly two short stubby arms, no fingers and no legs. A small soft ivory droplet-shaped tuft sits on top of its head, like the viewed av-dawn mochi DNA, refined and simple. One arm raised to wave hello, the other gently holding a thin glossy crimson-red thread. Thread makes one loose loop across lower front and trails a short distance right. Character centered, entire tuft, arms and thread inside canvas with 12 percent clear margin; silhouette occupies about 74 percent of width and height. High-end mobile-game key-art 3D rendering, soft white key light from upper left, broad controlled white specular highlights, subtle red rim lighting, rich red not hot pink, clean readable silhouette at 200px and exquisite at 600px. No ground plane, no backdrop, no cast background shadow, no text, letters, numbers, logos, watermarks, hearts, extra characters or extra limbs. This is the canonical base character for six consistent poses. REGENERATION: maintain this exact reference character and original cheerful wave pose. Clean professional alpha cutout: eliminate every stray red/white speck outside the silhouette, remove all cast shadows, floating artifacts and colored fringes. Wider empty transparent padding, subject occupies only 70 percent of canvas. Smooth continuous antialiased edge. Same top-left light and red rim on body only, no external glow. Do not change identity or add anything.
```

## 포즈 공통 프롬프트

입력 이미지는 초기 wave 기준 캐릭터예요.

```text
Use case: identity-preserve. Create a new pose of the exact Chayeon mascot shown in the reference image. Preserve its scarlet-red translucent glossy jelly body, squat rounded pear/dome silhouette, ivory droplet tuft, tiny dark espresso bead eyes, warm blush, simple small mouth, exactly TWO short stubby fingerless arms and NO legs. Match facial proportions, premium soft 3D material, upper-left broad white specular highlights and subtle red rim light. Same character, not a redesign. Exactly 1024x1024 PNG with real transparent background. Center full subject with ample 12 percent margin on ALL sides, no cropping, no backdrop, no ground plane, no background shadow, no scattered particles, no text, letters, numbers, logos, watermarks, additional characters or extra limbs. Refine alpha silhouette edges; no stray opaque pixels. Thin glossy scarlet thread remains visibly distinct with its highlight.
```

### mochi-thread.png

```text
Pose: holding out the FREE END of one red thread toward the viewer as a friendly invitation. One short arm extends forward at lower right holding the visible thread tip; the other supports a single loose loop at lower left. Small warm expectant smile, head tipped a little to the side. Thread is thin with one elegant broad loop, not a tangled rope. Ensure the offered free end reads clearly and is not attached to another object.
```

### mochi-call.png

```text
Pose: happily talking on a voice call. Wear one tiny minimal ivory over-head headset with two small earpieces and a short microphone near the smiling open mouth, keeping the ivory tuft clearly visible. One arm lightly touches its earpiece; the other holds one thin glossy red thread that loops gently away to the right like a relaxed phone cord. Thread loops only once. Calm seated blob stance, friendly attentive expression. No phone handset, no speech bubble, no sound-wave marks needed because the headset explains the call.
```

### mochi-sleep.png

```text
Pose: sleeping peacefully, body gently squashed and tilted sideways, eyes replaced by two simple closed curved eyelids, tiny contented closed smile. Both short arms relaxed tucked close under cheek. A single thin glossy red thread curls in two broad soft loops beneath its cheek like a pillow, not a separate fabric pillow. Ivory droplet tuft stays attached and recognizable. No sleep symbols, no letters, no floating marks. Peaceful compact silhouette.
```

### mochi-bye.png

```text
Regenerate the goodbye pose precisely. Warm wistful small CLOSED smile, head slightly tilted, one arm raised in gentle goodbye wave. Other arm holds a short red thread whose END stops just beyond that hand at lower right. Exactly ONE other thread piece lies softly curved below it, separated by one clearly visible gap. TOTAL TWO THREAD PIECES ONLY, with clearly visible rounded ends, no long loops in front of belly, no third strand. Keep original body and ivory tuft unchanged. Absolutely no cast shadow or colored glow outside the character silhouette: all empty canvas areas must be clean fully transparent. Leave generous 15 percent empty margin around the full pose.
```

### mochi-knot.png

```text
Pose: both short arms cupped together at the middle-front, proudly offering a SMALL NEAT RED THREAD KNOT like a precious gift. One tidy rounded overhand knot with a clearly readable crossing and two short relaxed tails, not a bow ribbon, no gift box. Knot floats only between the two hands, thin cord with bright clean highlight makes it visible against the red torso. Tender joyful closed smile, eyes look at viewer, upright relaxed body.
```

## 페어 히어로 프롬프트

### pair-call.png

입력 이미지는 초기 wave 기준 캐릭터예요.

```text
Use case: stylized-concept, identity-preserve. Make exactly 1536x1024 landscape PNG with true transparent background, premium 3D key art for Chayeon call screen. Reference image is the CANONICAL RED MOCHI: preserve its glossy soft scarlet jelly dome body, ivory droplet tuft, tiny espresso bead eyes, subtle warm blush, proportions and upper-left white studio highlights. Compose TWO characters far apart, red on far LEFT around x=300, white on far RIGHT around x=1230. Red is the exact reference mascot with two stubby arms, no legs. White companion has the same mochi body/proportions and dot eyes but pearl-white jelly, soft blush and a little red droplet tuft. Both have tiny minimal ivory/red headsets, happy small open smiles, gently turned toward one another like a warm voice conversation. Exactly TWO arms per character. Both hold opposite ends of ONE thin glossy red thread, one continuous unbroken strand which hangs in a broad gentle U between them across the lower-middle. No knot, no additional thread loops, no disconnected cord pieces. Whole composition contained with 8 percent margin; characters each occupy about 28 percent width and 52 percent height, sitting in LOWER two thirds. Reserve large completely empty transparent middle-top area, x=530..1000 y=0..470 for title overlay. No background, no ground plane, no cast shadows, no stray glowing particles, clean antialiased alpha edges. No text, letters, numbers, logos, watermarks, additional characters or extra limbs. Soft premium mobile game key-art finish, not hot pink.
```

## 재생성 기록

| 파일 | 횟수 | 이유와 최종 선택 |
|---|---:|---|
| `mochi-wave.png` | 1회 | 초기 미리보기의 가장자리 잔상과 좁은 여백을 개선하도록 요청했어요. 동일한 캐릭터의 두 번째 생성본을 채택했어요. |
| `mochi-bye.png` | 1회 | 첫 생성본의 실이 세 조각처럼 보여, 두 조각과 하나의 분리된 틈이 분명한 형태로 다시 만들었어요. 두 번째 생성본을 채택했어요. |
| 나머지 9장 | 없음 | 첫 생성본을 최종 채택했어요. |

총 13회 생성했어요. 생성 도구의 직접 미리보기에서는 투명 영역의 RGB가 잔상이나 발광처럼 보이는 경우가 있어, 최종 판단은 알파를 적용해 밝은 배경과 어두운 배경에 합성한 결과를 기준으로 했어요. 탈락한 두 원본은 최종 `assets/`에 포함하지 않았어요.

## 검수와 한계

- 배경 4장과 캐릭터 7장을 모두 육안 확인했어요. 문자·숫자·로고·워터마크·사람·차량 식별 요소가 보이지 않아요. 캐릭터의 팔 개수와 실의 상태, 색상·표정·조명을 확인했어요.
- 모찌 6장은 각각 600px 미리보기와 200px 축소본에서 확인했어요. 흰색 배경에서도 확인했으며, 페어 히어로는 밝고 어두운 배경 양쪽에서 확인했어요.
- Pillow로 11개 파일의 정확한 크기와 형식, 투명 이미지의 알파 채널을 검사했어요. 모든 투명 이미지에는 알파 0 영역이 있고, 가장 불투명한 픽셀은 254 또는 255예요. 네 모서리의 알파는 최대 1/255예요.
- 페어 히어로의 중앙 상단 영역(x=530–1000, y=0–470)은 알파 최대 1/255로, 제목을 놓을 여백을 확인했어요. 생성 알파에는 육안으로 드러나지 않는 1/255 수준의 잔여 값이 일부 있어요. 수작업으로 알파를 다시 그리지는 않았어요.
- `python3 scripts/dev.py verify`를 현재 작업 폴더에서 실행했지만 `scripts/dev.py`가 없어 실행되지 않았어요. 이미지 전용 브리프이며 앱 코드·의존성은 변경하지 않았어요. 별도의 이미지 규격·알파 검사는 통과했어요.
- 실제 Android Automotive 화면 합성, 밝기·텍스트 대비, 실차 표시와 Roborazzi 검증은 이번 이미지 제작 범위에서 실행하지 않았어요. 배경은 왼쪽·하단을 어둡게 설계했으며 최종 UI의 텍스트 대비는 통합 단계에서 확인해야 해요.
- 미제작 항목은 없어요. 커밋하지 않았어요.


## 프로필 아바타 추가 세트

승인된 모찌 세트에 맞춰 사용자 프로필 아바타 6종을 기본·통화 포즈로 만들었어요. 최종 투명 PNG 12장과 검토 시트 1장을 `avatars/`에 저장했어요. 기존 기본 세트 파일은 유지했어요.

### 파일과 사용 화면

모든 개별 아바타는 1024×1024 RGBA PNG예요. 기본 포즈는 프로필 선택·아바타 원형·후보·연결 기록, 통화 포즈는 사용자 자신의 아바타를 보여 주는 통화 화면에 사용해요.

| 이름 | 파일 | 크기 | 용량 | 사용 화면·설명 |
|---|---|---|---:|---|
| 초승달 | [avatars/dawn-idle.png](avatars/dawn-idle.png) | 1024×1024 | 484,852 B | 붉은 젤리 몸체와 크림색 초승달. 프로필용 정면·작은 미소·편안한 팔. |
| 초승달 | [avatars/dawn-call.png](avatars/dawn-call.png) | 1024×1024 | 513,662 B | 붉은 젤리 몸체와 크림색 초승달. 통화용 오른쪽 시선·헤드셋·짧은 붉은 실. |
| 햇님 | [avatars/slow-idle.png](avatars/slow-idle.png) | 1024×1024 | 484,089 B | 우윳빛 몸체와 붉은 열매 왕관. 프로필용 정면·작은 미소·편안한 팔. |
| 햇님 | [avatars/slow-call.png](avatars/slow-call.png) | 1024×1024 | 549,847 B | 우윳빛 몸체와 붉은 열매 왕관. 통화용 오른쪽 시선·헤드셋·짧은 붉은 실. |
| 음표 | [avatars/jazz-idle.png](avatars/jazz-idle.png) | 1024×1024 | 452,271 B | 검은 젤리 몸체와 붉은 음표. 프로필용 정면·작은 미소·편안한 팔. |
| 음표 | [avatars/jazz-call.png](avatars/jazz-call.png) | 1024×1024 | 504,227 B | 검은 젤리 몸체와 붉은 음표. 통화용 오른쪽 시선·헤드셋·짧은 붉은 실. |
| 물결 | [avatars/sea-idle.png](avatars/sea-idle.png) | 1024×1024 | 452,892 B | 흰 몸체와 붉은 물결 베레모. 프로필용 정면·작은 미소·편안한 팔. |
| 물결 | [avatars/sea-call.png](avatars/sea-call.png) | 1024×1024 | 491,271 B | 흰 몸체와 붉은 물결 베레모. 통화용 오른쪽 시선·헤드셋·짧은 붉은 실. |
| 새싹 | [avatars/forest-idle.png](avatars/forest-idle.png) | 1024×1024 | 492,456 B | 붉은 몸체와 크림색 두 잎 새싹. 프로필용 정면·작은 미소·편안한 팔. |
| 새싹 | [avatars/forest-call.png](avatars/forest-call.png) | 1024×1024 | 546,166 B | 붉은 몸체와 크림색 두 잎 새싹. 통화용 오른쪽 시선·헤드셋·짧은 붉은 실. |
| 별 | [avatars/star-idle.png](avatars/star-idle.png) | 1024×1024 | 458,391 B | 검은 젤리 몸체와 붉은 별. 프로필용 정면·작은 미소·편안한 팔. |
| 별 | [avatars/star-call.png](avatars/star-call.png) | 1024×1024 | 536,102 B | 검은 젤리 몸체와 붉은 별. 통화용 오른쪽 시선·헤드셋·짧은 붉은 실. |
| 전체 검토 | [avatars/sheet-avatars.png](avatars/sheet-avatars.png) | 3072×1024 | 5,917,465 B | 배경 #1A0408, 6열×2행 검토 시트 |

### 검토 시트 배치

왼쪽부터 **초승달 → 햇님 → 음표 → 물결 → 새싹 → 별**이에요. 첫 행은 `idle`, 둘째 행은 `call`이에요. 셀은 512×512이며, 이미지 자체에는 글자나 라벨을 넣지 않았어요.

![프로필 아바타 6종 기본·통화 포즈](avatars/sheet-avatars.png)

### 제작·검수

- 내장 `image_gen`을 사용했어요. 기본 포즈는 `mochi-wave.png`를 재질·조명·비율 참조로, 해당 `../ref/av-*.webp`를 캐릭터 정체성 참조로 전달했어요.
- 초승달은 마스코트의 소용돌이 대신 크림색 초승달을 달았어요. 음표는 이전 참조의 흰색과 달리 이번 브리프대로 붉은 음표예요. 새싹은 참조의 크림색 두 잎을 유지했어요.
- 모든 통화 포즈는 해당 캐릭터의 새 `idle` 파일을 정체성 참조로 사용했어요. 물결·새싹의 최종 통화 포즈는 `mochi-call.png`도 참조했어요. 초승달·햇님·음표·별의 최종 재생성에는 새싹의 통화 포즈를 두 번째 참조로 사용해 오른쪽 시선과 손·실 배치를 맞췄어요.
- 개별 원본을 ImageMagick으로 비율 유지 축소해 정확히 1024×1024로 맞추고 알파를 보존했어요. 배경을 손으로 지우거나 캐릭터를 다시 그리지는 않았어요.
- 시트는 생성된 최종 12장을 순서대로 축소·합성한 검토 산출물이에요. 별도의 이미지 생성 프롬프트는 없어요. 합성 사양은 `3072×1024 / 6열×2행 / 셀 512×512 / 배경 #1A0408 / 라벨 없음`이에요.
- 12장을 모두 직접 확인했어요. 80×80 축소본에서 정체성 장식의 구분을, 어두운 배경과 흰 배경 합성에서 가장자리·팔 개수·헤드셋·실의 형태를 확인했어요. 통화 포즈는 화면 오른쪽을 향하고 실은 오른쪽 손 밖으로 짧게 뻗어요. 몸체를 감싸는 고리는 없어요.
- Pillow로 개별 이미지 12장의 크기·RGBA·알파 범위를 검사했고, 알파 32/255 이상인 눈에 보이는 영역이 모든 캔버스 가장자리에서 최소 20px 이상 떨어져 있음을 확인했어요. 시트의 3072×1024 규격, RGB 형식, 배경 픽셀 `(26, 4, 8)`도 검사했어요. 모두 통과했어요.
- 생성 도구의 직접 미리보기에서 보이는 투명 영역의 색 잔상은 알파를 적용한 합성본으로 구분했어요. 생성 알파의 아주 낮은 잔여 값은 그대로 보존했어요.
- `python3 scripts/dev.py verify`는 이번에도 현재 폴더에 스크립트가 없어 실행되지 않았어요. 앱 코드·의존성은 변경하지 않았고, 실제 Android UI 합성과 실차 검증은 수행하지 않았어요.

### 추가 세트 재생성 기록

| 파일 | 재생성 | 이유 |
|---|---:|---|
| `dawn-call.png` | 1회 | 오른쪽 회전을 더 분명하게 하고 몸 안쪽으로 늘어진 두 번째 실 끝을 없앴어요. |
| `slow-call.png` | 1회 | 첫 결과가 화면 왼쪽을 향해, 화면 오른쪽을 보는 포즈로 수정했어요. |
| `jazz-call.png` | 1회 | 왼쪽 시선을 오른쪽으로 수정하고 몸 안쪽의 실 끝을 없앴어요. |
| `star-call.png` | 1회 | 몸 안쪽으로 늘어진 두 번째 실 끝을 없애고 손 바깥쪽의 짧은 가닥만 남겼어요. |
| 기본 포즈 6장·`sea-call.png`·`forest-call.png` | 없음 | 첫 생성본을 최종 채택했어요. |

추가 세트는 총 16회 생성해 12장을 채택했고, 최종 파일로 검토 시트 1장을 만들었어요. 미제작 항목은 없으며 커밋하지 않았어요.

### 추가 세트 최종 프롬프트

아래는 각 최종 파일의 실제 생성 프롬프트 원문이에요. 모든 요청에 `transparent_background=true`를 사용했어요. 실제 파일 규격은 생성 후 1024×1024로 맞췄어요.

#### avatars/dawn-idle.png

참조 1: `mochi-wave.png` · 참조 2: `../ref/av-dawn.webp`

```text
Use case: identity-preserve, premium stylized 3D profile avatar. Image 1 is the approved Chayeon mochi STYLE AND PROPORTION master: match its soft glossy jelly material, rounded squat pear/dome body, small bead eyes, subtle blush cheeks, short fingerless arms, white upper-left softbox highlight and soft red rim light, pristine mobile-game key-art quality. Image 2 is ONLY the old avatar IDENTITY reference; do not copy its low-resolution rendering or old face. Create ONE character, 1024x1024 PNG, true transparent background. EXACTLY TWO short stubby arms, NO legs or fingers. Same eye-level near-orthographic camera as style master, friendly front-facing symmetric portrait, tiny warm closed curved smile, arms relaxed low at sides. No headset and no thread in this idle pose. Six-avatar grid alignment: body centered at x=512, body dome top y=310, rounded base y=865, body width about 650 pixels; top ornament stays between y=145 and y=340. Overall subject fits x=115..909 y=130..890 with generous transparent margins. Match those body dimensions rather than enlarging small ornaments. Clear simple facial features and distinctive top ornament must read at 80px. No background, ground plane, external glow or cast shadow. No text, letters, numbers, logos or watermarks. No hot pink. Clean alpha edges; no stray pixels. Retain only the specified identity ornament, not the mascot's original swirl. Identity DAWN: rich scarlet-red jelly body; replace the mascot's ivory swirl with ONE small cream-white crescent moon shaped soft cap, an unmistakable thick rounded crescent with two softly pointed tips opening toward viewer right. Crescent rests on top of head, not floating. No swirl or droplet tuft. Dark espresso bead eyes with tiny white highlights.
```

#### avatars/dawn-call.png

참조 1: `avatars/dawn-idle.png` · 참조 2: `avatars/forest-call.png`

```text
Use case: identity-preserve. Image 1 is THIS EXACT USER AVATAR's approved idle portrait. Image 2 is the approved FOREST CALL POSE reference: copy its camera, rightward face turn, low conversational left hand, headset placement and outward-only thread, but retain every identity feature and color of image 1. Make the CALL POSE of image 1; preserve its body color, exact head ornament shape/color, eye design, proportions, material, silhouette family and recognizable identity. Do NOT copy the mascot's swirl or thread loops from image 2. Premium glossy soft jelly 3D, upper-left broad white key light and subtle red rim, no hot pink. Output exactly 1024x1024 PNG with genuine transparent background. Same near-orthographic eye-level camera and body scale as image 1; base near y=865, body width about 650 pixels, body center x=500. Rotate the body and face only 15 degrees toward the VIEWER'S RIGHT (a gentle three-quarter-right orientation, not a profile). Friendly happy talking smile, exactly TWO short stubby fingerless arms, NO legs. Wear a small minimal cream/ivory headset with small earpieces and a short microphone near mouth, never covering or replacing the top identity ornament. The hand on image LEFT is raised only a little, conversationally, not high overhead. The hand on image RIGHT holds ONE short end of a thin glossy scarlet RED THREAD. Thread exits that right hand toward image RIGHT in a single almost-horizontal gentle curve, visible free rounded tip around x=930 y=730, clear of body silhouette. It MUST NOT loop, wrap over torso, cross the belly, trail left, form a knot, or leave the canvas. No other loose cord ends or headphone cables. Leave a clear transparent margin around full character and thread, nothing clipped. No text, letters, numbers, logos, watermarks, background, ground plane, external glow or cast shadow; clean antialiased alpha silhouette. Direction clarification: show a gentle turn toward SCREEN RIGHT, not screen left. Shift the face group (eyes and mouth) to the RIGHT of the body's vertical centerline; the LEFT cheek is more exposed, the far eye on the right slightly narrower. Gaze toward screen right. The near left headset earpiece is slightly more prominent than the far right earpiece. Keep the body centered and its width/height identical to the idle reference. Thread begins at the outer edge of the image-right hand and extends only outward to the right; ZERO thread visible on belly or to left of that hand. Identity reminder: Identity DAWN: rich scarlet-red jelly body; replace the mascot's ivory swirl with ONE small cream-white crescent moon shaped soft cap, an unmistakable thick rounded crescent with two softly pointed tips opening toward viewer right. Crescent rests on top of head, not floating. No swirl or droplet tuft. Dark espresso bead eyes with tiny white highlights. Correction emphasis: exactly one visible 160px thread segment on the OUTSIDE RIGHT of the holding hand. Its left endpoint is hidden inside that hand. Absolutely no second end protruding toward the belly. Preserve body scale of idle image, no enlargement.
```

#### avatars/slow-idle.png

참조 1: `mochi-wave.png` · 참조 2: `../ref/av-slow.webp`

```text
Use case: identity-preserve, premium stylized 3D profile avatar. Image 1 is the approved Chayeon mochi STYLE AND PROPORTION master: match its soft glossy jelly material, rounded squat pear/dome body, small bead eyes, subtle blush cheeks, short fingerless arms, white upper-left softbox highlight and soft red rim light, pristine mobile-game key-art quality. Image 2 is ONLY the old avatar IDENTITY reference; do not copy its low-resolution rendering or old face. Create ONE character, 1024x1024 PNG, true transparent background. EXACTLY TWO short stubby arms, NO legs or fingers. Same eye-level near-orthographic camera as style master, friendly front-facing symmetric portrait, tiny warm closed curved smile, arms relaxed low at sides. No headset and no thread in this idle pose. Six-avatar grid alignment: body centered at x=512, body dome top y=310, rounded base y=865, body width about 650 pixels; top ornament stays between y=145 and y=340. Overall subject fits x=115..909 y=130..890 with generous transparent margins. Match those body dimensions rather than enlarging small ornaments. Clear simple facial features and distinctive top ornament must read at 80px. No background, ground plane, external glow or cast shadow. No text, letters, numbers, logos or watermarks. No hot pink. Clean alpha edges; no stray pixels. Retain only the specified identity ornament, not the mascot's original swirl. Identity SLOW: milky pearl-WHITE jelly body, tiny dark espresso eyes with white glints, soft coral blush. On top sits a small crown of glossy SCARLET RED BERRIES: one slightly larger round central red berry with five smaller red berries arranged in a simple half-circle crown around it, a cohesive ornament rather than a fruit pile. No leaves, no stems, no ivory swirl. Preserve the old reference's berry-sun identity.
```

#### avatars/slow-call.png

참조 1: `avatars/slow-idle.png` · 참조 2: `avatars/forest-call.png`

```text
Use case: identity-preserve. Image 1 is THIS EXACT USER AVATAR's approved idle portrait. Image 2 is the approved FOREST CALL POSE reference: copy its camera, rightward face turn, low conversational left hand, headset placement and outward-only thread, but retain every identity feature and color of image 1. Make the CALL POSE of image 1; preserve its body color, exact head ornament shape/color, eye design, proportions, material, silhouette family and recognizable identity. Do NOT copy the mascot's swirl or thread loops from image 2. Premium glossy soft jelly 3D, upper-left broad white key light and subtle red rim, no hot pink. Output exactly 1024x1024 PNG with genuine transparent background. Same near-orthographic eye-level camera and body scale as image 1; base near y=865, body width about 650 pixels, body center x=500. Rotate the body and face only 15 degrees toward the VIEWER'S RIGHT (a gentle three-quarter-right orientation, not a profile). Friendly happy talking smile, exactly TWO short stubby fingerless arms, NO legs. Wear a small minimal cream/ivory headset with small earpieces and a short microphone near mouth, never covering or replacing the top identity ornament. The hand on image LEFT is raised only a little, conversationally, not high overhead. The hand on image RIGHT holds ONE short end of a thin glossy scarlet RED THREAD. Thread exits that right hand toward image RIGHT in a single almost-horizontal gentle curve, visible free rounded tip around x=930 y=730, clear of body silhouette. It MUST NOT loop, wrap over torso, cross the belly, trail left, form a knot, or leave the canvas. No other loose cord ends or headphone cables. Leave a clear transparent margin around full character and thread, nothing clipped. No text, letters, numbers, logos, watermarks, background, ground plane, external glow or cast shadow; clean antialiased alpha silhouette. Direction clarification: show a gentle turn toward SCREEN RIGHT, not screen left. Shift the face group (eyes and mouth) to the RIGHT of the body's vertical centerline; the LEFT cheek is more exposed, the far eye on the right slightly narrower. Gaze toward screen right. The near left headset earpiece is slightly more prominent than the far right earpiece. Keep the body centered and its width/height identical to the idle reference. Thread begins at the outer edge of the image-right hand and extends only outward to the right; ZERO thread visible on belly or to left of that hand. Identity reminder: Identity SLOW: milky pearl-WHITE jelly body, tiny dark espresso eyes with white glints, soft coral blush. On top sits a small crown of glossy SCARLET RED BERRIES: one slightly larger round central red berry with five smaller red berries arranged in a simple half-circle crown around it, a cohesive ornament rather than a fruit pile. No leaves, no stems, no ivory swirl. Preserve the old reference's berry-sun identity. Correction emphasis: exactly one visible 160px thread segment on the OUTSIDE RIGHT of the holding hand. Its left endpoint is hidden inside that hand. Absolutely no second end protruding toward the belly. Preserve body scale of idle image, no enlargement.
```

#### avatars/jazz-idle.png

참조 1: `mochi-wave.png` · 참조 2: `../ref/av-jazz.webp`

```text
Use case: identity-preserve, premium stylized 3D profile avatar. Image 1 is the approved Chayeon mochi STYLE AND PROPORTION master: match its soft glossy jelly material, rounded squat pear/dome body, small bead eyes, subtle blush cheeks, short fingerless arms, white upper-left softbox highlight and soft red rim light, pristine mobile-game key-art quality. Image 2 is ONLY the old avatar IDENTITY reference; do not copy its low-resolution rendering or old face. Create ONE character, 1024x1024 PNG, true transparent background. EXACTLY TWO short stubby arms, NO legs or fingers. Same eye-level near-orthographic camera as style master, friendly front-facing symmetric portrait, tiny warm closed curved smile, arms relaxed low at sides. No headset and no thread in this idle pose. Six-avatar grid alignment: body centered at x=512, body dome top y=310, rounded base y=865, body width about 650 pixels; top ornament stays between y=145 and y=340. Overall subject fits x=115..909 y=130..890 with generous transparent margins. Match those body dimensions rather than enlarging small ornaments. Clear simple facial features and distinctive top ornament must read at 80px. No background, ground plane, external glow or cast shadow. No text, letters, numbers, logos or watermarks. No hot pink. Clean alpha edges; no stray pixels. Retain only the specified identity ornament, not the mascot's original swirl. Identity JAZZ: dark near-BLACK cherry jelly body, visibly dark/black rather than red, with broad soft white speculars and restrained red rim so its silhouette reads on dark UI. Two small pearl-white oval bead eyes as in old reference with tiny dark inset pupils, warm muted blush and tiny warm ivory closed smile. On top, ONE small solid SCARLET RED sculptural eighth music note with a rounded oval notehead, short vertical stem and one curved flag. The note is a 3D identity ornament, not printed text. Important: music ornament is RED even though old reference has a white one. No swirl.
```

#### avatars/jazz-call.png

참조 1: `avatars/jazz-idle.png` · 참조 2: `avatars/forest-call.png`

```text
Use case: identity-preserve. Image 1 is THIS EXACT USER AVATAR's approved idle portrait. Image 2 is the approved FOREST CALL POSE reference: copy its camera, rightward face turn, low conversational left hand, headset placement and outward-only thread, but retain every identity feature and color of image 1. Make the CALL POSE of image 1; preserve its body color, exact head ornament shape/color, eye design, proportions, material, silhouette family and recognizable identity. Do NOT copy the mascot's swirl or thread loops from image 2. Premium glossy soft jelly 3D, upper-left broad white key light and subtle red rim, no hot pink. Output exactly 1024x1024 PNG with genuine transparent background. Same near-orthographic eye-level camera and body scale as image 1; base near y=865, body width about 650 pixels, body center x=500. Rotate the body and face only 15 degrees toward the VIEWER'S RIGHT (a gentle three-quarter-right orientation, not a profile). Friendly happy talking smile, exactly TWO short stubby fingerless arms, NO legs. Wear a small minimal cream/ivory headset with small earpieces and a short microphone near mouth, never covering or replacing the top identity ornament. The hand on image LEFT is raised only a little, conversationally, not high overhead. The hand on image RIGHT holds ONE short end of a thin glossy scarlet RED THREAD. Thread exits that right hand toward image RIGHT in a single almost-horizontal gentle curve, visible free rounded tip around x=930 y=730, clear of body silhouette. It MUST NOT loop, wrap over torso, cross the belly, trail left, form a knot, or leave the canvas. No other loose cord ends or headphone cables. Leave a clear transparent margin around full character and thread, nothing clipped. No text, letters, numbers, logos, watermarks, background, ground plane, external glow or cast shadow; clean antialiased alpha silhouette. Direction clarification: show a gentle turn toward SCREEN RIGHT, not screen left. Shift the face group (eyes and mouth) to the RIGHT of the body's vertical centerline; the LEFT cheek is more exposed, the far eye on the right slightly narrower. Gaze toward screen right. The near left headset earpiece is slightly more prominent than the far right earpiece. Keep the body centered and its width/height identical to the idle reference. Thread begins at the outer edge of the image-right hand and extends only outward to the right; ZERO thread visible on belly or to left of that hand. Identity reminder: Identity JAZZ: dark near-BLACK cherry jelly body, visibly dark/black rather than red, with broad soft white speculars and restrained red rim so its silhouette reads on dark UI. Two small pearl-white oval bead eyes as in old reference with tiny dark inset pupils, warm muted blush and tiny warm ivory closed smile. On top, ONE small solid SCARLET RED sculptural eighth music note with a rounded oval notehead, short vertical stem and one curved flag. The note is a 3D identity ornament, not printed text. Important: music ornament is RED even though old reference has a white one. No swirl. Correction emphasis: exactly one visible 160px thread segment on the OUTSIDE RIGHT of the holding hand. Its left endpoint is hidden inside that hand. Absolutely no second end protruding toward the belly. Preserve body scale of idle image, no enlargement.
```

#### avatars/sea-idle.png

참조 1: `mochi-wave.png` · 참조 2: `../ref/av-sea.webp`

```text
Use case: identity-preserve, premium stylized 3D profile avatar. Image 1 is the approved Chayeon mochi STYLE AND PROPORTION master: match its soft glossy jelly material, rounded squat pear/dome body, small bead eyes, subtle blush cheeks, short fingerless arms, white upper-left softbox highlight and soft red rim light, pristine mobile-game key-art quality. Image 2 is ONLY the old avatar IDENTITY reference; do not copy its low-resolution rendering or old face. Create ONE character, 1024x1024 PNG, true transparent background. EXACTLY TWO short stubby arms, NO legs or fingers. Same eye-level near-orthographic camera as style master, friendly front-facing symmetric portrait, tiny warm closed curved smile, arms relaxed low at sides. No headset and no thread in this idle pose. Six-avatar grid alignment: body centered at x=512, body dome top y=310, rounded base y=865, body width about 650 pixels; top ornament stays between y=145 and y=340. Overall subject fits x=115..909 y=130..890 with generous transparent margins. Match those body dimensions rather than enlarging small ornaments. Clear simple facial features and distinctive top ornament must read at 80px. No background, ground plane, external glow or cast shadow. No text, letters, numbers, logos or watermarks. No hot pink. Clean alpha edges; no stray pixels. Retain only the specified identity ornament, not the mascot's original swirl. Identity SEA: milky pearl-WHITE jelly body, tiny dark espresso bead eyes with white glints, soft coral blush. Wear ONE glossy scarlet-red soft beret-like WAVE cap, wide low curved sculptural wave shape draped gently across the top, softly swooping down on viewer right, matching the old reference's tilted red wave beret silhouette. No swirl or droplet tuft, no berries, no real hat fabric.
```

#### avatars/sea-call.png

참조 1: `avatars/sea-idle.png` · 참조 2: `mochi-call.png`

```text
Use case: identity-preserve. Image 1 is THIS EXACT USER AVATAR's approved idle portrait. Image 2 is the approved Chayeon mochi-call quality, lighting and headset reference only. Make the CALL POSE of image 1; preserve its body color, exact head ornament shape/color, eye design, proportions, material, silhouette family and recognizable identity. Do NOT copy the mascot's swirl or thread loops from image 2. Premium glossy soft jelly 3D, upper-left broad white key light and subtle red rim, no hot pink. Output exactly 1024x1024 PNG with genuine transparent background. Same near-orthographic eye-level camera and body scale as image 1; base near y=865, body width about 650 pixels, body center x=500. Rotate the body and face only 15 degrees toward the VIEWER'S RIGHT (a gentle three-quarter-right orientation, not a profile). Friendly happy talking smile, exactly TWO short stubby fingerless arms, NO legs. Wear a small minimal cream/ivory headset with small earpieces and a short microphone near mouth, never covering or replacing the top identity ornament. The hand on image LEFT is raised only a little, conversationally, not high overhead. The hand on image RIGHT holds ONE short end of a thin glossy scarlet RED THREAD. Thread exits that right hand toward image RIGHT in a single almost-horizontal gentle curve, visible free rounded tip around x=930 y=730, clear of body silhouette. It MUST NOT loop, wrap over torso, cross the belly, trail left, form a knot, or leave the canvas. No other loose cord ends or headphone cables. Leave a clear transparent margin around full character and thread, nothing clipped. No text, letters, numbers, logos, watermarks, background, ground plane, external glow or cast shadow; clean antialiased alpha silhouette. Direction clarification: show a gentle turn toward SCREEN RIGHT, not screen left. Shift the face group (eyes and mouth) to the RIGHT of the body's vertical centerline; the LEFT cheek is more exposed, the far eye on the right slightly narrower. Gaze toward screen right. The near left headset earpiece is slightly more prominent than the far right earpiece. Keep the body centered and its width/height identical to the idle reference. Thread begins at the outer edge of the image-right hand and extends only outward to the right; ZERO thread visible on belly or to left of that hand. Identity reminder: Identity SEA: milky pearl-WHITE jelly body, tiny dark espresso bead eyes with white glints, soft coral blush. Wear ONE glossy scarlet-red soft beret-like WAVE cap, wide low curved sculptural wave shape draped gently across the top, softly swooping down on viewer right, matching the old reference's tilted red wave beret silhouette. No swirl or droplet tuft, no berries, no real hat fabric.
```

#### avatars/forest-idle.png

참조 1: `mochi-wave.png` · 참조 2: `../ref/av-forest.webp`

```text
Use case: identity-preserve, premium stylized 3D profile avatar. Image 1 is the approved Chayeon mochi STYLE AND PROPORTION master: match its soft glossy jelly material, rounded squat pear/dome body, small bead eyes, subtle blush cheeks, short fingerless arms, white upper-left softbox highlight and soft red rim light, pristine mobile-game key-art quality. Image 2 is ONLY the old avatar IDENTITY reference; do not copy its low-resolution rendering or old face. Create ONE character, 1024x1024 PNG, true transparent background. EXACTLY TWO short stubby arms, NO legs or fingers. Same eye-level near-orthographic camera as style master, friendly front-facing symmetric portrait, tiny warm closed curved smile, arms relaxed low at sides. No headset and no thread in this idle pose. Six-avatar grid alignment: body centered at x=512, body dome top y=310, rounded base y=865, body width about 650 pixels; top ornament stays between y=145 and y=340. Overall subject fits x=115..909 y=130..890 with generous transparent margins. Match those body dimensions rather than enlarging small ornaments. Clear simple facial features and distinctive top ornament must read at 80px. No background, ground plane, external glow or cast shadow. No text, letters, numbers, logos or watermarks. No hot pink. Clean alpha edges; no stray pixels. Retain only the specified identity ornament, not the mascot's original swirl. Identity FOREST: rich scarlet-RED jelly body, tiny dark espresso bead eyes with white glints and subtle warm blush. Top ornament is a small soft IVORY WHITE SPROUT with one short stem and exactly TWO plump rounded leaves reaching up-left and up-right, matching old reference. No green, no extra leaves, no swirl or droplet tuft.
```

#### avatars/forest-call.png

참조 1: `avatars/forest-idle.png` · 참조 2: `mochi-call.png`

```text
Use case: identity-preserve. Image 1 is THIS EXACT USER AVATAR's approved idle portrait. Image 2 is the approved Chayeon mochi-call quality, lighting and headset reference only. Make the CALL POSE of image 1; preserve its body color, exact head ornament shape/color, eye design, proportions, material, silhouette family and recognizable identity. Do NOT copy the mascot's swirl or thread loops from image 2. Premium glossy soft jelly 3D, upper-left broad white key light and subtle red rim, no hot pink. Output exactly 1024x1024 PNG with genuine transparent background. Same near-orthographic eye-level camera and body scale as image 1; base near y=865, body width about 650 pixels, body center x=500. Rotate the body and face only 15 degrees toward the VIEWER'S RIGHT (a gentle three-quarter-right orientation, not a profile). Friendly happy talking smile, exactly TWO short stubby fingerless arms, NO legs. Wear a small minimal cream/ivory headset with small earpieces and a short microphone near mouth, never covering or replacing the top identity ornament. The hand on image LEFT is raised only a little, conversationally, not high overhead. The hand on image RIGHT holds ONE short end of a thin glossy scarlet RED THREAD. Thread exits that right hand toward image RIGHT in a single almost-horizontal gentle curve, visible free rounded tip around x=930 y=730, clear of body silhouette. It MUST NOT loop, wrap over torso, cross the belly, trail left, form a knot, or leave the canvas. No other loose cord ends or headphone cables. Leave a clear transparent margin around full character and thread, nothing clipped. No text, letters, numbers, logos, watermarks, background, ground plane, external glow or cast shadow; clean antialiased alpha silhouette. Direction clarification: show a gentle turn toward SCREEN RIGHT, not screen left. Shift the face group (eyes and mouth) to the RIGHT of the body's vertical centerline; the LEFT cheek is more exposed, the far eye on the right slightly narrower. Gaze toward screen right. The near left headset earpiece is slightly more prominent than the far right earpiece. Keep the body centered and its width/height identical to the idle reference. Thread begins at the outer edge of the image-right hand and extends only outward to the right; ZERO thread visible on belly or to left of that hand. Identity reminder: Identity FOREST: rich scarlet-RED jelly body, tiny dark espresso bead eyes with white glints and subtle warm blush. Top ornament is a small soft IVORY WHITE SPROUT with one short stem and exactly TWO plump rounded leaves reaching up-left and up-right, matching old reference. No green, no extra leaves, no swirl or droplet tuft.
```

#### avatars/star-idle.png

참조 1: `mochi-wave.png` · 참조 2: `../ref/av-star.webp`

```text
Use case: identity-preserve, premium stylized 3D profile avatar. Image 1 is the approved Chayeon mochi STYLE AND PROPORTION master: match its soft glossy jelly material, rounded squat pear/dome body, small bead eyes, subtle blush cheeks, short fingerless arms, white upper-left softbox highlight and soft red rim light, pristine mobile-game key-art quality. Image 2 is ONLY the old avatar IDENTITY reference; do not copy its low-resolution rendering or old face. Create ONE character, 1024x1024 PNG, true transparent background. EXACTLY TWO short stubby arms, NO legs or fingers. Same eye-level near-orthographic camera as style master, friendly front-facing symmetric portrait, tiny warm closed curved smile, arms relaxed low at sides. No headset and no thread in this idle pose. Six-avatar grid alignment: body centered at x=512, body dome top y=310, rounded base y=865, body width about 650 pixels; top ornament stays between y=145 and y=340. Overall subject fits x=115..909 y=130..890 with generous transparent margins. Match those body dimensions rather than enlarging small ornaments. Clear simple facial features and distinctive top ornament must read at 80px. No background, ground plane, external glow or cast shadow. No text, letters, numbers, logos or watermarks. No hot pink. Clean alpha edges; no stray pixels. Retain only the specified identity ornament, not the mascot's original swirl. Identity STAR: dark near-BLACK cherry jelly body, visibly dark/black rather than red, with broad soft white speculars and restrained red rim so its silhouette reads on dark UI. Two small pearl-white oval bead eyes as in old reference with tiny dark inset pupils, warm muted blush and tiny warm ivory closed smile. On top, ONE small glossy SCARLET RED five-point star with fat rounded tips, standing upright, matching old reference's red star identity. No swirl or music note.
```

#### avatars/star-call.png

참조 1: `avatars/star-idle.png` · 참조 2: `avatars/forest-call.png`

```text
Use case: identity-preserve. Image 1 is THIS EXACT USER AVATAR's approved idle portrait. Image 2 is the approved FOREST CALL POSE reference: copy its camera, rightward face turn, low conversational left hand, headset placement and outward-only thread, but retain every identity feature and color of image 1. Make the CALL POSE of image 1; preserve its body color, exact head ornament shape/color, eye design, proportions, material, silhouette family and recognizable identity. Do NOT copy the mascot's swirl or thread loops from image 2. Premium glossy soft jelly 3D, upper-left broad white key light and subtle red rim, no hot pink. Output exactly 1024x1024 PNG with genuine transparent background. Same near-orthographic eye-level camera and body scale as image 1; base near y=865, body width about 650 pixels, body center x=500. Rotate the body and face only 15 degrees toward the VIEWER'S RIGHT (a gentle three-quarter-right orientation, not a profile). Friendly happy talking smile, exactly TWO short stubby fingerless arms, NO legs. Wear a small minimal cream/ivory headset with small earpieces and a short microphone near mouth, never covering or replacing the top identity ornament. The hand on image LEFT is raised only a little, conversationally, not high overhead. The hand on image RIGHT holds ONE short end of a thin glossy scarlet RED THREAD. Thread exits that right hand toward image RIGHT in a single almost-horizontal gentle curve, visible free rounded tip around x=930 y=730, clear of body silhouette. It MUST NOT loop, wrap over torso, cross the belly, trail left, form a knot, or leave the canvas. No other loose cord ends or headphone cables. Leave a clear transparent margin around full character and thread, nothing clipped. No text, letters, numbers, logos, watermarks, background, ground plane, external glow or cast shadow; clean antialiased alpha silhouette. Direction clarification: show a gentle turn toward SCREEN RIGHT, not screen left. Shift the face group (eyes and mouth) to the RIGHT of the body's vertical centerline; the LEFT cheek is more exposed, the far eye on the right slightly narrower. Gaze toward screen right. The near left headset earpiece is slightly more prominent than the far right earpiece. Keep the body centered and its width/height identical to the idle reference. Thread begins at the outer edge of the image-right hand and extends only outward to the right; ZERO thread visible on belly or to left of that hand. Identity reminder: Identity STAR: dark near-BLACK cherry jelly body, visibly dark/black rather than red, with broad soft white speculars and restrained red rim so its silhouette reads on dark UI. Two small pearl-white oval bead eyes as in old reference with tiny dark inset pupils, warm muted blush and tiny warm ivory closed smile. On top, ONE small glossy SCARLET RED five-point star with fat rounded tips, standing upright, matching old reference's red star identity. No swirl or music note. Correction emphasis: exactly one visible 160px thread segment on the OUTSIDE RIGHT of the holding hand. Its left endpoint is hidden inside that hand. Absolutely no second end protruding toward the belly. Preserve body scale of idle image, no enlargement.
```



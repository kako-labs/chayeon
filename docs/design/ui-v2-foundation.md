# UI v2 공용 기반 작업 보고

`core:ui`의 장면·에셋·컴포넌트와 `app`의 상단 크롬·하단 메뉴 연결을 구현했어요. feature 소스는 수정하지 않았고, 공용 변경을 반영한 기준 이미지만 갱신했어요. 새 의존성과 커밋은 없어요.

## 컴포넌트와 API

선택 인자와 조립 예시는 [공용 UI 안내](../../core/ui/README.md#ui-v2-공용-기반)에 있어요.

| API | 기능 |
|---|---|
| `SceneBackground(scene, modifier, dimmed, scrim, content)` | crop 장면 이미지와 가독성 스크림. 모든 배경은 정적이에요 |
| `ScreenScaffold(..., scene, sceneScrim, contentPadding)` | 공용 배경, 두 열·1100dp 미만 한 열 스크롤, 정책에 따른 ROAD 우선 적용 |
| `Mascot(pose, modifier)` | WAVE·THREAD·CALL·SLEEP·BYE·KNOT와 한국어 설명·부드러운 그림자 |
| `AvatarDish(avatar, modifier, decorative)` | 기존 API로 새 idle 아바타 표시 |
| `CallAvatar(avatar, mirrored, modifier)` | call 포즈와 상대의 좌우 반전 |
| `CallHero(me, partner, modifier)` | `CallParticipant(avatar, name, title)` 두 명·이름·상대 칭호와 9/5/1.6dp 붉은 실 |
| `BrandMark(modifier)` | 하트 공과 차연 브랜드 |
| `DrivingStatusPill(gear, policy, modifier)` | P·D·R·N·확인 불가 및 정책 상태 |
| `BackButton(..., inChrome)` | 상세 화면의 원래 콜백과 잠금을 유지하는 상단 뒤로 버튼 |
| `ChromeBackRegistry` | 본문 BackButton의 콜백·활성 상태를 앱 크롬에 등록하고 화면 해제 때 정리 |
| `BottomNav(..., incomingAfterCount)` | 같은 너비의 홈·인연·내 프로필, 선택 pill과 받은 애프터 개수 |
| `DiscoveryChip(checked, onCheckedChange, modifier, resting, enabled)` | 다음 홈 히어로에서 사용할 발견 스위치 |
| `HeroHeadline(title, supportingLine, modifier, style)` | 큰 제목과 보조 문장 |
| `StatTile(icon, label, value, modifier)` | 아이콘·작은 라벨·큰 값 |
| `ScoreChip(trait, modifier, large)` | 공개 점수의 이름과 굵은 숫자, 큰 정적 변형 |
| `PublishedScoreChips(signature, modifier, limit, large)` | 유효 공개 점수를 정렬해 최대 3개, 주행 제한·불명에서는 최대 2개 |
| `PrimaryButton(text, onClick, modifier, icon, enabled, access, driving, compact)` | 빛나는 붉은 기본 행동, 주행 180dp·목록 76dp 변형 |
| `OutlineButton(...)` | 같은 행동 정책을 갖는 테두리 보조 버튼 |
| `InfoLine(text, modifier, icon)` | 작은 안내 문장 |
| `GlassPanel(modifier, contentPadding, content)` | 반투명 Brush 레이어 패널, 플랫폼 blur 없음 |
| `TopicLine(text, modifier)` | 체크와 공통 주제 문장 |
| `ListRow(avatar, name, subLine, modifier, status, trailingAction)` | 인연 목록 행과 행동 슬롯 |
| `BondStatusBadge(status, modifier)` | 받은 애프터·이어짐·답 기다림 상태 |

`ChayeonType`에 84sp Headline, 72sp SceneTitle, 64sp CallTitle, 54sp CardTitle, 48sp TileValue, 40sp SceneButton과 22–30sp 본문·라벨 계열을 추가했어요. 기존 Asta Sans와 팔레트를 유지하며 새 색·간격·크기·곡률을 명명된 토큰으로 제공해요.

## 에셋

| 종류 | 개수 | 크기 | 합계 용량 |
|---|---:|---|---:|
| 장면 | 4 추가 | 1536×1024 | 186,034 B |
| 모찌 | 6 추가 | 768×768, 투명 | 238,946 B |
| idle 아바타 | 6 교체 | 512×512, 투명 | 120,988 B |
| call 아바타 | 6 추가 | 512×512, 투명 | 153,880 B |
| 합계 | 22 | WebP | 699,848 B |

제공된 WebP 22개와 SHA-256이 모두 일치해요. 원본 PNG는 추가하지 않았어요. 생성 모델 사용 안내와 원본 프롬프트 기록은 [assets-v2.md](assets-v2.md)에 있어요. [디자인 기준](README.md)에 승인된 UI v2 목업이 기존 Red 시안보다 우선함을 기록했어요.

## 장면과 다음 화면 작업

장면 선택은 앱의 `appScene`이 소유해요.

| 화면 | 장면 |
|---|---|
| 홈·만남·요청·주차 통화 | PARKING |
| 보호 중 홈·집 보호 설정 | HOME |
| 참여 준비·인연·애프터 | DAWN |
| 종료·주행 | ROAD |
| 프로필·특성 근거·공개 범위·주행 연결 설정 | PLAIN |

제한·상태 불명에서는 스캐폴드가 ROAD와 주행 스크림을 우선해요. `SceneScrim.RIGHT`는 오른쪽 본문용, `CALL`은 주차 통화의 중앙 연출용이에요. feature 단독 스크린샷은 경로 정보가 없어 기본 PARKING을 사용하므로, 다음 화면 조립에서는 필요한 장면을 명시하면 돼요.

- 홈에는 기존 발견 스위치가 남아 있어요. 다음 히어로는 `ScreenChrome.discoveryOn/discoveryResting/discoveryBusy/onDiscoveryToggle`을 사용할 수 있어요. 중앙 발견 오브는 제거했어요.
- v2 화면은 `SceneScreenHorizontal/SceneScreenTop/SceneScreenBottom`으로 56/44/40dp 여백을 선택해요. 기존 화면은 커진 상태 pill의 높이를 고려한 32/8dp 여백으로 본문 가시성을 유지해요.
- `CallHero`는 항상 정적이에요. 주행 통화는 상대 AvatarDish·칭호·공개 점수 두 개를 중심으로 구성해요.
- `ButtonAccess.PARKED_ONLY`가 기본이에요. `RESTRICTED_CALL`은 허용된 통화 조작·RING/AUTO 수신, `SAFE_EXIT`는 안전한 통화 종료에만 써요. ViewModel의 명령 검사도 유지해야 해요.
- 앱 상단으로 옮겨진 뒤로 버튼도 원래 화면의 콜백·저장 잠금을 따라요. 단순 뒤로 이동으로 화면 규칙을 우회하지 않아요.
- 기존 본문·매듭·폼 구조는 다음 화면 작업에서 바꿔요. 이번에 새 모찌 히어로나 새 ListRow를 기능 화면에 직접 배치하지 않았어요.

## 기준 이미지와 검증

| 모듈 | 기존 이미지 변경 | 신규 | 전체 기준 이미지 |
|---|---:|---:|---:|
| core:ui | 19 | 15 | 103 |
| app | 32 | 7 | 40 |
| feature:meet | 120 | 0 | 120 |
| feature:history | 122 | 0 | 129 |
| feature:signature | 82 | 0 | 82 |
| 합계 | 375 | 22 | 474 |

새 갤러리 22장은 모두 이미지 뷰어로 확인했어요. 기존 화면은 홈·후보·요청·주차/주행 통화·종료·참여 준비·프로필 입력·동의·집 보호·인연·연락처·애프터·차단·정리·시그니처·특성·공개 범위·주행 설정·주행 요약·첫 시그니처와 좁은 창 표본을 확인했어요. 기존 스크롤과 가장자리 페이드는 유지했어요.

`python3 scripts/dev.py verify`가 통과했어요. clean·재실행·빌드 캐시 미사용으로 Gradle 작업 492개를 실행했고, 구조 검사·Kotlin 컴파일·엄격한 Android lint·빌드·Roborazzi 검증을 완료했어요.

| 테스트 | 통과 수 |
|---|---:|
| app | 85 |
| core:engine | 50 |
| core:model | 25 |
| core:signature | 58 |
| core:ui | 121 |
| data:social | 47 |
| data:vehicle | 28 |
| data:voice | 39 |
| feature:history | 192 |
| feature:meet | 192 |
| feature:signature | 145 |
| server | 64 |
| JUnit 합계 | 1,046 |
| Python | 13 |

실패·오류·건너뜀은 모두 0이에요. 스크린샷 검증은 위 테스트에 포함되어 있으며 전체 기준 파일은 474개예요. `git diff --check`도 통과했어요.

`e2e.py`와 `server_e2e.py`는 제거된 오브 대신 기존 홈 스위치의 라벨을 선택하도록 바꿨어요. 실제 Pleos 에뮬레이터 E2E·실차·GPU 성능은 이번 작업에서 실행하거나 측정하지 않았어요.

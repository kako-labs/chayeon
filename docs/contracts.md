# 공통 계약

`core:model/src/main/kotlin/dev/chayeon/core/model/`가 앱과 서버의 단일 계약입니다. Android와 HTTP 구현에 의존하지 않습니다. 공개 타입을 바꿔야 할 때는 계약 소유 역할에 먼저 알립니다. 이 문서는 구현 완료 주장이 아니라 후속 작업자가 지켜야 할 경계와 사용 방법입니다.

## 파일과 책임

| 파일 | 내용 |
|---|---|
| `Common.kt` | `AppResult`, 오류 7종, 관찰 상태, 명령 식별자, 주행 정책, 상태 카드 18종 |
| `Profile.kt` | 테스트 계정, 세션, 내·공개 프로필, 관심사, 동의 기록, 참여 설정 |
| `Discovery.kt` | 발견 상태와 최소 공개 후보 |
| `Relationships.kt` | 요청, 일시 연결, 인사, 기록, 애프터, 차단·신고 |
| `Signature.kt` | 공개 시그니처, 개인 근거, 주행 집계, 매듭 시드, 궁합 |
| `Repositories.kt` | 기능이 사용하는 관찰·명령 인터페이스와 기기 안의 명령 |
| `ServerTime.kt` | 서버 시각과 기기의 단조 시계 수신 기준점, 기한 표시용 계산 |
| `ApiRequests.kt`, `ApiResponses.kt`, `ApiRoutes.kt` | 서버 DTO, 경로, 긴 폴링 전체 스냅샷 |

| 구현 역할 | 담당 |
|---|---|
| FOUNDATION | 계약, 빌드, 디자인 시스템, 실행 골격 |
| MEET | 발견·상대·요청·일시 연결·종료·주행 제한 화면과 화면별 ViewModel |
| HISTORY | 첫 실행·프로필 등록·동의·기록·애프터·차단·신고·정리 화면 |
| SIGNATURE | `core:signature`의 순수 계산, 칭호·궁합·시드; 내 프로필·근거·공개 범위·요약 화면 |
| VEHICLE | SDK와 `CarPropertyManager`, 주행 정책, 포그라운드 수집, 기기 안의 지표 저장, `SignatureRepository`, `VehicleConsole` |
| ENGINE | `core:engine`의 관계 상태 기계, 명령 검증·원자성, 만료·이탈 판정, 시연 상대. 서버와 기기 안 모드가 공유 |
| SOCIAL | `data:social`의 기기 안 백엔드와 HTTP 백엔드, 동기화·명령 재시도, `ServerTimeProvider` |
| SERVER | 토큰 인증·공간 격리, HTTP 경로, 긴 폴링, 엔진 호스팅 |
| APP | 생성자 주입, 최상위 이동, 기기 데이터 정리 연결, 알림과 시연 제어 조립 |

기능은 `core:model` 인터페이스만 주입받습니다. 다른 기능, 데이터 구현, HTTP, 차량 SDK를 참조하지 않습니다. 일시 연결은 `Connection`, 스친 기록은 `Encounter`, 후속 연결은 `AfterConnection`으로 분리되어 있습니다.

## 신원, 공개 경계, 동의

- `/v1/session/enter`만 인증 토큰을 발급하는 진입 경로입니다. `PersonaCode.ACCOUNT_A/B`는 시연 자격 선택이고 행위자 ID가 아닙니다. 서버가 공간과 계정을 결정해 토큰을 발급합니다. 서버 모드는 `SERVER_DEMO`, 기기 안에서만 도는 모드는 `SOLO`입니다. `roomCode`는 시연 공간 구분 값입니다.
- 그 밖의 모든 경로는 `Authorization: Bearer <token>`에서 행위자를 정합니다. 요청 DTO에는 `actorId`, `senderId`, `accountId`, `ownerId`가 없습니다. 상대 프로필·후보·요청·연결 ID는 대상 식별자이며 소유권 증거가 아닙니다.
- `RecordConsentRequest`는 현재 문서 버전에 대한 `GRANT/REVOKE` 명령입니다. 서버가 인증된 계정의 `ConsentRecord`를 저장합니다. 연결 요청·수락 DTO에는 동의 플래그가 없으며 서버는 자신의 저장 상태를 읽습니다. `ADULT_ATTESTATION`은 시연용 자기 확인이고 연령 검증 주장이 아닙니다.
- `PublicProfile`과 `Candidate`는 닉네임, 가상 아바타, 관심사, 공개 시그니처와 궁합만 포함합니다. `ProfileKind.VIRTUAL_PROFILE`은 `가상 프로필`, `DEMO_PEER`는 `시연용 가상 상대`로 표시합니다.
- 위치는 `LocationInput`으로 들어오는 요청에만 있습니다. 메모리에서 근접 판단에만 사용하고 응답·UI·로그에 넣지 않습니다. 요청과 위치 객체의 `toString()`도 값을 숨깁니다. 합성 위치는 `LocationSource.SYNTHETIC`으로 구분합니다. 발견에는 좌표 대신 신선도와 권한 상태만 있습니다.
- 공개 시그니처에는 칭호, 공개 특성의 단계, 매듭 시드, 출처, 범주형 근거량만 있습니다. 상대에게 개인 지표·횟수·점수·순위를 전하지 않습니다. `RECORDING` 특성은 공개 타입 생성 단계에서 거부합니다.
- `MySignature`, `TraitEvidence`, `PrivateMetric`, `DerivedTripMetrics`, `TripRecap`은 기기 안에서만 쓰며 직렬화 가능한 서버 그래프에 들어가지 않습니다. `DerivedTripMetrics`의 이동량과 수신율은 유효 주행 판정에만 사용하고 출력 문자열에서 숨깁니다. 원시 신호는 이 계약에도 저장 모델에도 없습니다.
- `UNSUPPORTED`인 특성은 숫자 지표 목록이 비어 있어야 합니다. 없는 값을 0으로 채우지 않습니다. `NIGHT_DRIVE`는 기본 비공개이고 전체 시그니처 게시도 기본 꺼짐입니다.
- 차단은 본인의 `blocks`에서만 확인합니다. 서버는 상대에게 `PeerUnavailable`, 요청 `UNAVAILABLE`, 중립적인 종료 이유로 투영해야 합니다. `BLOCKED` 종료 이유는 차단을 실행한 본인에게만 허용합니다. 해제해도 과거 연결·대화가 살아나지 않습니다.

## 버전과 명령

변경되는 엔티티는 `VersionedEntity`의 `id: String`, `version: Long`을 가집니다. 시간은 UTC Unix 밀리초입니다. 값 객체(특성, 문구, 매듭 시드, 입력 초안)는 독립 엔티티가 아닙니다.

- 서버는 같은 공간의 상태 변경마다 증가하는 리비전을 발급하고 변경된 엔티티의 버전에 넣습니다. 기기 전용 지표는 로그인 계정별 별도 증가 리비전을 사용합니다. 둘을 비교하지 않습니다.
- `CommandMeta.commandId`는 클라이언트가 생성한 멱등 키입니다. 동일 명령 재시도에는 같은 키와 같은 본문을 씁니다. 성공한 결과를 다시 돌려주어 중복 연결·기록·인사가 생기지 않게 합니다. 서로 다른 새 명령은 새 키를 씁니다.
- `expectedVersion`은 낙관적 동시성 조건입니다. 이를 생략해도 당사자·최종 상태·동의·발견·근접 검증을 생략하지 않습니다. 경쟁에 진 명령은 실제 상태에 맞는 `AlreadyClosed`, `Expired`, `PeerUnavailable` 등으로 수렴하고 새 스냅샷을 읽습니다.
- 메시지는 `clientMessageId`도 유지합니다. 전송 실패 UI가 같은 메시지를 다시 보낼 때 동일 식별자를 사용합니다. 서버가 메시지 엔티티 ID와 버전을 정합니다.
- 기능은 명령마다 별도 진행 상태를 갖습니다. 관찰은 `Flow<LoadState<T>>` 또는 버전이 있는 `DiscoveryState`, `SessionState`, `DrivingState`입니다. 실패 관찰에는 오래된 공개 카드가 들어가지 않습니다.
- 오류는 `NotAuthorized`, `AlreadyClosed`, `Expired`, `PeerUnavailable`, `Restricted`, `Network`, `Unknown` 7종입니다. 내부 예외·주소·차단 사유를 사용자 오류에 넣지 않습니다. 입력 유효성 표시는 화면에서 처리하고 서버도 다시 검증합니다.
- 닉네임 범위, 관심사 최대 개수, 동의 문서 버전, 신고 보관일은 `ParticipationPolicy`로 받습니다. 시안의 임시 값을 계약 상수로 고정하지 않습니다. 보관일이 `null`이면 미확정이므로 UI가 임의 기간을 안내하지 않습니다.

## 긴 폴링

`GET /v1/sync?since=<version>`은 `SyncResponse(AppResult<SyncSnapshot>)`를 반환합니다. `SyncRequest.since`는 쿼리 값이며 GET 본문이 아닙니다. 각 성공 응답은 **인증된 계정이 볼 수 있는 전체 상태**입니다. 부분 변경 목록으로 해석하면 안 됩니다.

1. 첫 요청은 `since=0`입니다. 변경이 없으면 서버는 제한된 시간 동안 기다린 뒤 현재 전체 스냅샷을 반환할 수 있습니다.
2. 클라이언트는 같은 `SyncSnapshot.id`(현재 세션 범위) 안에서 더 큰 버전만 원자적으로 반영합니다. 중복·역순 응답은 버립니다. 새 세션에서는 이전 저장 상태와 버전 커서를 지웁니다.
3. 빈 목록은 해당 목록을 비운다는 뜻입니다. `profile/settings=null`은 없음이며, 변경 없음이라는 뜻이 아닙니다. 전체 스냅샷에서 빠진 객체는 로컬에서도 제거합니다.
4. `Tombstone(kind, id, version)`은 삭제 명령과 로그아웃 전후 동기화를 위한 보조 표식입니다. 오래된 객체를 되살리는 데 사용하지 않습니다. 전체 대체가 기준이므로 표식 보관이 끝나도 재접속 시 삭제가 적용됩니다.
5. SOCIAL은 유효한 응답의 `serverTimeEpochMillis`와 수신 순간의 단조 시계를 묶어 기기 전용 `ServerTimeAnchor(serverEpochMillis, receivedAtMonotonicMillis)`로 보관하고 `ServerTimeProvider.serverTime`으로 공개합니다. 기능은 `anchor.remainingMillisUntil(expiresAtEpochMillis, provider.monotonicTimeMillis())`로 남은 시간을 구합니다. 기기의 벽시계는 사용하지 않습니다. 재신청 가능 시각인 `retryAvailableAtEpochMillis`와 `afterAvailableAtEpochMillis`도 같은 기준을 씁니다.
6. 스냅샷에는 토큰과 개인 시그니처 근거가 없고, 응답받은 모든 목록을 한 번에 대체합니다. `connections`의 `CONNECTED/CHECKING` 엔티티는 계정당 최대 하나입니다.

`ServerTimeProvider`는 요청·애프터 기능의 Deps에 다른 Repository와 함께 주입합니다. 수신 기준점과 `monotonicTimeMillis()`는 반드시 같은 단조 시계를 사용합니다(Android 구현은 `elapsedRealtime`). 새 세션·로그아웃·프로세스 재시작에서는 기준점을 `null`로 시작합니다. 기준점이 없거나 시계가 역행하면 남은 시간도 알 수 없으므로 `응답 시간을 확인하고 있어요`로 표시하고 기기 시각으로 만료를 추정하지 않습니다. 유효한 현재 세션의 긴 폴링 응답은 상태 버전이 같은 heartbeat여도 시간 기준점을 갱신할 수 있지만, 이전 세션·역순 응답은 기준점도 갱신하지 않습니다. 전송 지연만큼의 표시 오차는 남으며, 0초는 서버의 `EXPIRED` 상태를 뜻하지 않습니다. 최종 만료·수락 가능 여부는 서버 상태와 명령 결과로 확정합니다.

## 서버 명령 경로

모든 명령은 POST입니다. 각 행은 같은 이름의 `…Request`와 `…Response` DTO를 사용합니다. 경로 상수는 `ApiRoutes`에 있습니다. 명령 응답의 `result`는 Repository 반환값과 같은 `AppResult<T>`입니다.

| 이름 | 경로 | 성공 값 |
|---|---|---|
| EnterSession | `/v1/session/enter` | Session |
| SignOut | `/v1/session/sign-out` | CommandReceipt |
| DeleteAccount | `/v1/account/delete` | CommandReceipt |
| SaveProfile | `/v1/profile/save` | MyProfile |
| RecordConsent | `/v1/profile/consent` | ParticipationSettings |
| UpdateSettings | `/v1/profile/settings` | ParticipationSettings |
| PublishSignature | `/v1/profile/signature` | MyProfile |
| SetDiscovery | `/v1/discovery/set` | DiscoveryState |
| RefreshDiscovery | `/v1/discovery/refresh` | DiscoveryState |
| UpdateLocation | `/v1/presence/location` | CommandReceipt |
| SetSyntheticPresence | `/v1/presence/synthetic` | CommandReceipt |
| RequestConnection | `/v1/connection/request` | ConnectionRequest |
| RespondConnection | `/v1/connection/respond` | ConnectionRequest |
| CancelConnection | `/v1/connection/cancel` | ConnectionRequest |
| SendGreeting | `/v1/connection/greeting` | ConnectionGreeting |
| LeaveConnection | `/v1/connection/leave` | Connection |
| DeleteEncounter | `/v1/encounters/delete` | CommandReceipt |
| DeleteAllEncounters | `/v1/encounters/delete-all` | CommandReceipt |
| RequestAfter | `/v1/after/request` | AfterRequest |
| RespondAfter | `/v1/after/respond` | AfterRequest |
| WithdrawAfter | `/v1/after/withdraw` | AfterRequest |
| SendAfterMessage | `/v1/after/message` | AfterMessage |
| EndAfterConnection | `/v1/after/end` | AfterConnection |
| BlockPeer | `/v1/safety/block` | Block |
| UnblockPeer | `/v1/safety/unblock` | CommandReceipt |
| ReportPeer | `/v1/safety/report` | Report |

`PublishSignatureRequest.signature=null`은 게시 철회입니다. 서버는 제공된 공개 시그니처의 ID·버전을 인증 정보로 사용하지 않고 해당 계정의 새 버전을 부여합니다. 공개 범위 변경·주행 제외로 공개 내용이 바뀌면 다시 게시하거나 철회해 과거 공개 특성이 남지 않게 합니다. 서버는 후보뿐 아니라 요청·연결·기록에 들어가는 상대 공개 프로필 투영과 궁합도 현재 공개 범위로 갱신합니다.

기기 전용 `SignatureRepository.recordTrip/updateVisibility/excludeTrip/markRecapSeen/clearLocalData` 및 `VehicleConsole.setGear/replay/stopReplay`에는 서버 경로가 없습니다. `SignatureRepository`는 계산을 `core:signature`에 맡기고 원시 신호 없이 파생 지표만 저장합니다. `recordTrip`은 recap과 시그니처를 같은 변경으로 갱신합니다. 제외한 주행은 재집계에서 빼고 삭제되지 않은 나머지 유효 주행으로 다시 계산합니다.

## 상태 전이와 조작 정책

- 요청 `PENDING`은 `ACCEPTED` 또는 `DECLINED/CANCELLED/EXPIRED/UNAVAILABLE`로 끝납니다. 수락의 성공은 자동 생성된 `connectionId`와 동기화의 `Connection`으로 확인합니다. 취소·만료 요청은 다시 수락할 수 없습니다.
- 일시 연결은 `CONNECTED → CHECKING → CONNECTED`가 가능하고, 마지막은 `ENDED`입니다. `CHECKING`은 상태를 확인 중이라는 뜻이며 이탈 확정이 아닙니다. `DISTANCE/PEER_LEFT/SELF_LEFT/STATUS_UNKNOWN/DISCOVERY_OFF/BLOCKED`를 분리합니다. `ENDED`에서 이전 연결로 돌아갈 수 없습니다.
- 실제 성립해 끝난 일시 연결만 하나의 `Encounter`를 만듭니다. 거절·취소·만료 요청은 기록이 되지 않습니다. 내 기록 삭제는 상대 기록과 차단 상태를 바꾸지 않습니다.
- 애프터 요청 `PENDING`은 `ACCEPTED` 또는 `DECLINED/WITHDRAWN/EXPIRED/UNAVAILABLE`로 끝납니다. 별도 `AfterConnection`은 `CONNECTED → CLOSED`이며 다시 열지 않습니다.
- 일시 인사는 `PresetCode`만 보낼 수 있습니다. 애프터에는 `AfterMessageContent.Preset`과 `Text`가 있습니다. 시안의 프리셋 UI와 향후 입력 UI가 같은 계약을 씁니다.
- `DrivingPolicy.ALLOWED`에서만 탐색·요청·수락·입력·편집을 허용합니다. `RESTRICTED`, `UNKNOWN`에서는 나가기만 허용합니다. 서버 요청에 클라이언트가 허용 여부를 주장하는 필드는 없습니다. 기기 정책은 UI와 명령 진입점에서 확인하고, 서버는 자신의 인증·동의·관계 상태를 별도로 검사합니다.
- `DrivingState` 초기값은 `UNKNOWN`입니다. P와 실제 주행 상태 0을 확인한 차량 구현만 `ALLOWED`를 냅니다. 속도 0만으로 허용하지 않습니다.
- 로그아웃·계정 삭제 성공 전에 `SignatureRepository.clearLocalData()`와 로컬 프로필·대화·토큰 캐시를 정리합니다. APP이 정리 의존성을 연결하고 SOCIAL의 SessionRepository 구현이 완료를 보장합니다. 정리 실패를 성공으로 숨기지 않습니다. 계정 삭제와 단순 로그아웃의 서버 보존 범위는 다릅니다.

## 화면별 계약 확인

| 경험 명세의 화면 | 관찰 | 명령·화면에서 필요한 값 |
|---|---|---|
| 첫 실행 | SessionRepository.session/personas/availableModes | enter; SOLO/SERVER_DEMO, roomCode, ACCOUNT_A/B. app 설정에 서버 주소가 있을 때만 availableModes에 SERVER_DEMO 포함 |
| 프로필 등록·편집 | ProfileRepository.profile, Session.policy | saveProfile(ProfileDraft); 로컬 초안 보존 후 동일 명령 재시도 |
| 차량 데이터·위치·공개 범위 | ProfileRepository.settings, SignatureRepository.signature | recordConsent, updateVisibility, setDiscovery; 공개 미리보기 |
| 오늘의 인연 | DiscoveryRepository.discovery | setDiscovery, refresh; title/traits/chemistry.lines/공통 관심사 |
| 상대 카드·궁합 | discovery.candidates | PublicProfile.signature, KnotSeed, Chemistry; requestConnection(candidateId) |
| 요청 보냄 | ConnectionRepository.requests, ServerTimeProvider.serverTime | cancel; 서버 기준 expiresAtEpochMillis, retryAvailableAtEpochMillis |
| 요청 받음 | ConnectionRepository.requests, ServerTimeProvider.serverTime | respond(ACCEPT/DECLINE); peer와 chemistry, 서버 기준 응답 기한 |
| 연결 중 | ConnectionRepository.connection, observeGreetings | sendGreeting; PresetCode, clientMessageId, delivery, availablePresets |
| 연결 중·주행 제한 / 주행 중 | DrivingStateProvider.drivingState, connection | 나가기만; UNKNOWN도 같은 잠금, 기어·합성 표시 |
| 연결 중·좁은 창 | 위와 같음 | 레이아웃만 변경, 다른 데이터 계약 없음 |
| 연결 종료 | ConnectionRepository.connection | endReason, endedAtEpochMillis, encounterId |
| 스친 인연 / 기록 상세 | EncounterRepository.encounters/observeEncounter | deleteEncounter/deleteAllEncounters; peer, chemistry, afterEligibility |
| 애프터 신청 | AfterRepository.requests, observeEncounter, ServerTimeProvider.serverTime | requestAfter/withdraw; 서버 기준 기한과 재신청 가능 시각 |
| 애프터 응답 | AfterRepository.requests, ServerTimeProvider.serverTime | respond; peer, chemistry, encounterId, 서버 기준 응답 기한 |
| 이어진 인연 | AfterRepository.connections/observeMessages | sendMessage/endRelationship; 메시지별 delivery, 관계 종료 확인은 화면 상태 |
| 내 프로필 | ProfileRepository.profile/settings, SignatureRepository.signature | saveProfile/updateSettings/publishSignature |
| 차단·신고 | SafetyRepository.blocks | block/unblock/report; 사유 5종, 선택적 관련 엔티티 |
| 기록·계정 정리 | encounters/connections/session | 발견 끄기, leave, endRelationship, deleteEncounter, signOut, deleteAccount |
| 내 시그니처 | SignatureRepository.signature | 매듭, 공개 칭호, 개인 근거, updateVisibility |
| 특성 상세 | SignatureRepository.observeEvidence | TraitEvidence의 기간·신호·지표·최근 변화; 본인에게만 |
| 공개 범위 | SignatureRepository.signature | updateVisibility; 공개 미리보기 후 publishSignature 또는 철회 |
| 주행 후 요약 | SignatureRepository.recaps/observeRecap | excludeTrip/markRecapSeen; 이전·현재 매듭과 변화 문장 |
| 시연 제어 | VehicleConsole.consoleState, DrivingStateProvider.drivingState | setGear/replay/stopReplay, setSyntheticPresence; 항상 시연 표시 |

## 상태 카드 18종 확인

| 시안 상태 | ExperienceState | 도달 근거 |
|---|---|---|
| 근처 참여자 준비 중 | DISCOVERY_LOADING | DiscoveryStatus.LOADING |
| 위치 권한 꺼짐 | LOCATION_PERMISSION_OFF | DiscoveryStatus.LOCATION_PERMISSION_OFF |
| 위치 오래됨 | LOCATION_STALE | DiscoveryStatus.LOCATION_STALE |
| 후보 없음 | NO_CANDIDATES | DiscoveryStatus.EMPTY |
| 서버 연결 실패 | NETWORK_FAILURE | DiscoveryStatus.NETWORK_FAILURE 또는 AppError.Network |
| 주행 제한 | DRIVING_RESTRICTED | DrivingPolicy.RESTRICTED |
| 주행 상태 확인 불가 | DRIVING_UNKNOWN | DrivingPolicy.UNKNOWN |
| 이미 연결 중 | ALREADY_CONNECTED | DiscoveryStatus.ALREADY_CONNECTED |
| 프로필 저장 실패 | PROFILE_SAVE_FAILED | saveProfile의 AppResult.Failure와 보존된 ProfileDraft |
| 요청 거절 | REQUEST_DECLINED | ConnectionRequestStatus.DECLINED |
| 요청 만료 | REQUEST_EXPIRED | ConnectionRequestStatus.EXPIRED |
| 받은 요청 취소 | REQUEST_CANCELLED | ConnectionRequestStatus.CANCELLED |
| 수락 시 연결 불가 | PEER_UNAVAILABLE | AppError.PeerUnavailable 또는 요청 UNAVAILABLE |
| 인사 전송 실패 | MESSAGE_SEND_FAILED | DeliveryState.FAILED 또는 sendGreeting의 Failure |
| 연결 상태 확인 중 | CONNECTION_CHECKING | ConnectionStatus.CHECKING |
| 애프터 거절 | AFTER_DECLINED | AfterRequestStatus.DECLINED |
| 애프터 만료 | AFTER_EXPIRED | AfterRequestStatus.EXPIRED |
| 받은 애프터 철회 | AFTER_WITHDRAWN | AfterRequestStatus.WITHDRAWN |

화면 ViewModel은 이 실제 상태에서 카드를 선택합니다. 모든 상태가 enum에 있다는 사실만으로 서버 경쟁·권한·주행 잠금이 검증된 것은 아닙니다. 후속 구현에서 전이 테스트와 스크린샷으로 각각 증명해야 합니다.

## 계약 검증

`ContractTest`는 전체 응답 직렬화 그래프에서 위치·원시 신호·개인 근거 타입을 배제하고, 명령에서 행위자·동의 주장 필드를 배제합니다. 공개 RECORDING 거부, UNSUPPORTED 숫자 거부, 야간 기본 비공개, UNKNOWN 초기 정책, 프리셋과 후속 텍스트 구분, 18개 상태, 엔티티 ID·버전, 전체 스냅샷 직렬화도 검사합니다. 명령의 원자성·토큰 권한·실제 기어 수신·버전 증가 구현은 각 담당 모듈의 테스트 범위입니다.

`ServerTimeAnchorTest`는 기기 벽시계가 5분 빠르거나 느려도 120초 기한이 정확히 120초에서 시작하는지, 단조 시간 경과·기준점 없음·시계 재시작·정수 범위를 검증합니다.

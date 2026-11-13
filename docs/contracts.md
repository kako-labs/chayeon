# 공통 계약

`core:model/src/main/kotlin/dev/chayeon/core/model/`가 앱과 서버의 단일 계약입니다. Android와 HTTP 구현에 의존하지 않습니다. 공개 타입을 바꿔야 할 때는 계약 소유 역할에 먼저 알립니다. 이 문서는 구현 완료 주장이 아니라 후속 작업자가 지켜야 할 경계와 사용 방법입니다.

## 관계 엔진 구현 규칙

- `core:engine`의 `ChayeonEngine`을 서버와 SOLO가 함께 사용해요. `EngineClock`, `EngineIdGenerator`, `SignatureRules`, `EngineStore`를 주입하며 명령·tick·스냅샷은 하나의 잠금 안에서 실행해요. 저장 성공 후에만 메모리 상태와 `revision`을 공개해요. 저장 실패 시 관계 변경을 되돌려요.
- 호스트는 `EngineApi.execute(actor, route, body)`로 같은 DTO 처리를 사용해요. 인증 주체는 본문과 분리해요. `EngineJson`은 제네릭 `AppResult.Success`의 허용된 응답 타입 serializer를 등록하므로, 서버와 클라이언트 모두 이 설정을 사용해야 해요. 성공 값의 `type` 식별자도 이 설정으로 왕복해요.
- 같은 행위자와 `commandId`의 동일 명령은 최근 512개 결과 범위에서 원래 결과를 돌려줘요. 공개 철회·차단 뒤에는 과거 후보와 상대 공개 정보를 되살리지 않도록 현재 공개 범위로 다시 투영해요. 교환 연락처는 현재 관계 권한으로 다시 투영해 종료·차단·삭제 뒤 과거 결과로 되살리지 않아요. 다른 본문은 `Restricted`예요. 결과를 보관 범위 밖으로 밀어내도 키와 본문 지문은 보존하며, 같은 오래된 명령은 `AlreadyClosed`로 거절해요. 키 재사용으로 과거 수정이 다시 적용되지 않아요.
- 로그아웃·삭제 후 토큰은 일반 경로에서 즉시 무효예요. 응답 유실 복구를 위해 서버는 최대 512개, 5분 동안 정확히 같은 종료 경로와 본문의 영수증만 다시 반환해요. 다른 명령과 새 본문은 허용하지 않아요.
- 새 세션 토큰으로 과거 로그아웃 명령을 재전송하면 저장된 영수증만 돌려줘요. 이미 처리한 로그아웃 때문에 새 세션 토큰을 폐기하지 않아요.
- `EngineState`에는 좌표를 넣지 않아요. DEVICE 좌표는 엔진 메모리에만 존재하고 재시작하면 사라져요. 저장된 위치 수신 시각만으로 근접을 복원하지 않으며 새 위치 전까지 STALE이에요. 기존 연결은 CHECKING을 거쳐 확인 불가 유예가 지나면 STATUS_UNKNOWN으로 끝나요. 합성 위치는 좌표 없는 `SyntheticPresence`만 복원해요. 합성 상대가 포함된 쌍은 합성 시나리오로 근접을 판단하며 실위치 판정으로 주장하지 않아요.
- 시간 전이는 `tick(now)`가 예정 시각 순서로 처리해요. 드문 tick도 잦은 tick과 같은 종료 결과를 만들어요. 서버 타이머는 250ms, SOLO 타이머는 1초 간격이며 모든 명령과 스냅샷 전에도 tick해요.
- 한 연결이 끝나면 양측에 별도 기록을 하나씩 만들어요. 내 기록 삭제는 상대의 기록·애프터 신청 자격을 바꾸지 않아요. 삭제된 기록을 애프터 응답 때문에 다시 만들지 않아요.
- 시연 행동은 `DemoCast.peers`에 모여 있어요. 느린오후는 수락·이탈/복귀·애프터 수락, 바다보러가요는 거절, 숲길산책은 무응답, 재즈한잔은 발견 후 먼저 요청, 별보는밤은 수락과 애프터 수락을 해요. 이전 연결의 위치 이벤트가 새 연결을 바꾸지 않아요.

### 현재 엔진 정책

아래 값은 `EnginePolicy` 생성자로 바꿀 수 있어요. 관심사 최대 5개는 시안 값이에요. 요청 시안의 1:48과 애프터 시안의 23시간은 남은 시간 표시이므로 전체 기한은 각각 2분·24시간으로 정했어요. 닉네임 범위, 거리·유예·재신청 값은 시안에 명시된 확정값이 아니라 이번 시연의 구현 정책이에요.

| 항목 | 값 |
|---|---|
| 연결 요청 기한 / 재신청 대기 | 2분 / 30초 |
| 애프터 기한 / 재신청 대기 | 24시간 / 24시간 |
| 진입 / 이탈 반경 | 200m / 300m, 엔진 내부 계산 전용 |
| 이탈 / 상태 확인 불가 유예 | 10초 / 30초 |
| DEVICE 위치 유효 기간 | 30초 |
| 닉네임 / 관심사 | 유니코드 코드 포인트 2~12자 / 최대 5개 |
| 연락처 | 카카오톡 ID 4~20자, 인스타그램 ID 1~30자; 선택한 ID 하나 이상으로 애프터 신청·수락 |
| 동의 문서 버전 | 2026-11-03 |
| 신고 기록 보관일 | null, 미정 |

서버는 본문 32KiB, 방 64개, 방 유휴 30분, 긴 폴링 최대 25초를 `ServerPolicy`로 제한해요. 서버 저장은 메모리이며 프로세스 재시작을 넘는 서버 계정 보존은 제공하지 않아요.

### 앱 조립

`SocialGraph(context, serverUrl, rules, localCleanup, drivingStateProvider)`의 `sessionRepository`, `profileRepository`, `discoveryRepository`, `connectionRepository`, `encounterRepository`, `afterRepository`, `safetyRepository`, `serverTimeProvider`를 기능 Deps에 연결해요. 계약의 여덟 번째 `SignatureRepository`는 VEHICLE 구현이며 `localCleanup = { signatureRepository.clearLocalData() }`로 연결해요. 주행 제공자는 생성자 또는 `bindDrivingState()`로 넣으며 없으면 UNKNOWN으로 조작을 막아요. 종료할 때 `SocialGraph.close()`를 호출해요.

`SignatureRules`는 `DefaultSignatureEngine.title/chemistry`에 위임해요. 이 작업트리의 해당 구현은 아직 중립 결과를 내는 scaffold이므로 실제 칭호·궁합의 완성은 SIGNATURE 작업 통합에 달려 있어요. 엔진 테스트는 가짜 규칙으로 공개 특성만 전달되는지를 검증해요.

앱에서 `SignatureRules`를 직접 구성하려면 선언된 모듈 그래프에 맞게 `app → core:engine` 의존성도 연결해야 해요. 공개 범위 변경·주행 제외로 `SignatureRepository.signature`의 공개 결과가 바뀌면 `ProfileRepository.publishSignature(PublishSignatureRequest(...))`에 새 공개 시그니처 또는 null을 전달하는 연결도 필요해요. 기기 전용 근거와 지표는 이 호출에 포함하지 않아요.

로컬 상태와 서버 토큰은 앱 전용 `noBackupFilesDir/chayeon-social`에 원자적으로 저장해요. 서버 주소가 없으면 SOLO만 제공해요. Android에서 개발용 HTTP 서버를 사용할 때의 cleartext 허용은 앱 소유 역할이 호스트별로 정해야 하며 이 모듈은 manifest를 변경하지 않아요.

`RemoteBackend`는 `ClientSecretStorage`를 필수로 받아요. `SocialGraph`가 `PrivateClientSecretStorage`를 연결하며 서버 주소·방마다 SecureRandom 32바이트 비밀값을 생성해 `client-secrets.json`에 저장해요. 토큰 유실과 로그아웃 뒤에도 같은 계정에 다시 들어갈 수 있도록 이 복구 비밀값은 세션 정리와 분리해 보존해요. 앱 데이터 삭제·재설치 뒤에는 복구할 수 없어요. SOLO에는 적용하지 않아요.

스냅샷 저장소는 세션뿐 아니라 각 동기화 요청의 시작 세대도 확인해요. 통신 실패가 나면 진행 중이던 이전 요청들의 응답은 더 큰 버전이어도 상태를 복구할 수 없어요. 실패 뒤 새로 시작한 요청으로 확인된 전체 스냅샷만 복구해요. 같은 버전 heartbeat는 내용이 같을 때 시각 기준점을 갱신할 수 있어요.

로그아웃·삭제는 부작용 전에 앱 전용 정리 표식을 저장해요. 원격 결과를 받으면 표식에 보존하고 저장소와 기기 전용 지표 정리가 모두 끝나야 표식을 지워요. 정리 실패 후 다른 종료 명령의 성공 영수증을 대신 사용하지 않아요. 재시작은 표식 정리를 먼저 수행하며, 정리 전에는 새 계정으로 들어갈 수 없어요. 정리 hook이 회복되면 새 입장 시도에서도 로컬 정리만 재시도할 수 있어요. 이 동작은 이전의 원격 계정 삭제 성공을 추정하지 않아요.

## 파일과 책임

| 파일 | 내용 |
|---|---|
| `Common.kt` | `AppResult`, 오류 7종, 관찰 상태, 명령 식별자, 주행 정책, 상태 카드 17종 |
| `ContactIds.kt` | 비공개 연락처 ID, 정규화·유효성 검사·출력 숨김 |
| `Profile.kt` | 설치별 계정, 세션, 내·공개 프로필, 관심사, 동의 기록, 참여 설정 |
| `Discovery.kt` | 발견 상태와 최소 공개 후보 |
| `Voice.kt` | `VoiceCallState`, `VoiceCallRepository`, `VoiceTransport`, `VoiceChannel`, PCM 형식 |
| `Relationships.kt` | 요청, 일시 연결, 기록, 연락처 교환 애프터, 차단·신고 |
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
| SOCIAL | `data:social`의 기기 안 백엔드와 HTTP/WebSocket 백엔드, 동기화·명령 재시도, `ServerTimeProvider` |
| SERVER | 토큰 인증·공간 격리, HTTP 경로, 긴 폴링, 엔진 호스팅 |
| APP | 생성자 주입, 최상위 이동, 기기 데이터 정리 연결, 알림과 시연 제어 조립 |

기능은 `core:model` 인터페이스만 주입받습니다. 다른 기능, 데이터 구현, HTTP, 차량 SDK를 참조하지 않습니다. 일시 연결은 `Connection`, 스친 기록은 `Encounter`, 후속 연결은 `AfterConnection`으로 분리되어 있습니다.

## 신원, 공개 경계, 동의

- `/v1/session/enter`만 인증 토큰을 발급하는 진입 경로입니다. 클라이언트가 설치별로 생성한 `clientSecret`과 공간으로 서버가 계정을 결정하고 토큰을 발급합니다. 클라이언트가 행위자 ID를 선택하지 않습니다. 서버 모드는 `SERVER_DEMO`, 기기 안에서만 도는 모드는 `SOLO`입니다. `roomCode`가 없으면 `default` 공간입니다. 앱 UI는 공간을 선택하지 않으며 별도 공간은 테스트용입니다. 새 프로필은 빈 닉네임과 DAWN 아바타로 시작합니다.
- 서버의 `roomCode`는 영문·숫자·밑줄·하이픈 4~32자예요. 서버 입장에는 32~256자의 설치별 `clientSecret`이 필수예요. 공간과 비밀 값의 SHA-256으로 독립 계정을 찾으므로 다른 유효 비밀 값은 별도 계정을 만들고 기존 계정에 들어가지 못해요. 비밀 값이 없거나 길이가 맞지 않으면 `NotAuthorized`예요. 같은 설치의 새 입장은 기존 계정을 유지하고 이전 세션 토큰을 교체해요. bearer token은 비밀 값을 키로 공간·세션에 HMAC-SHA256을 적용하며 저장 파일에는 token 원문 대신 SHA-256 digest를 보관해요. 같은 입장 명령 재시도에는 같은 세션과 token을 반환해요. 계정 삭제 후 같은 설치가 다시 입장하면 삭제한 프로필을 복원하지 않는 새 계정을 만들어요. 비밀 값은 응답·로그·`toString`에 넣지 않아요.
- 그 밖의 모든 경로는 `Authorization: Bearer <token>`에서 행위자를 정합니다. 요청 DTO에는 `actorId`, `senderId`, `accountId`, `ownerId`가 없습니다. 상대 프로필·후보·요청·연결 ID는 대상 식별자이며 소유권 증거가 아닙니다.
- `RecordConsentRequest`는 현재 문서 버전에 대한 `GRANT/REVOKE` 명령입니다. 서버가 인증된 계정의 `ConsentRecord`를 저장합니다. 연결 요청·수락 DTO에는 동의 플래그가 없으며 서버는 자신의 저장 상태를 읽습니다. `ADULT_ATTESTATION`은 시연용 자기 확인이고 연령 검증 주장이 아닙니다.
- `PublicProfile`과 `Candidate`는 닉네임, 가상 아바타, 관심사, 공개 시그니처와 궁합만 포함합니다. `ProfileKind.VIRTUAL_PROFILE`은 `가상 프로필`, `DEMO_PEER`는 `시연용 가상 상대`로 표시합니다.
- 위치는 `LocationInput`으로 들어오는 요청에만 있습니다. 메모리에서 근접 판단에만 사용하고 응답·UI·로그에 넣지 않습니다. 요청과 위치 객체의 `toString()`도 값을 숨깁니다. 합성 위치는 `LocationSource.SYNTHETIC`으로 구분합니다. 발견에는 좌표 대신 신선도와 권한 상태만 있습니다.
- 공개 시그니처에는 칭호, 공개 특성의 단계와 `score: Int?`, 매듭 시드, 출처, 범주형 근거량만 있습니다. 점수 특성은 공개를 켜고 60점 이상일 때만 포함하며 서버가 60..100과 단계 일치를 검사합니다. LONG_ROAD/NIGHT_DRIVE는 score=null만 허용합니다. 상대에게 개인 지표·주행별 횟수·기록·순위를 전하지 않습니다. `RECORDING` 특성은 공개 타입 생성 단계에서 거부합니다.
- `MySignature`, `TraitEvidence`, `PrivateMetric`, `DerivedTripMetrics`, `TripRecap`은 기기 안에서만 쓰며 직렬화 가능한 서버 그래프에 들어가지 않습니다. `DerivedTripMetrics`의 이동량과 수신율은 유효 주행 판정에만 사용하고 출력 문자열에서 숨깁니다. 원시 신호는 이 계약에도 저장 모델에도 없습니다.
- `UNSUPPORTED`인 특성은 숫자 지표 목록이 비어 있어야 합니다. 없는 값을 0으로 채우지 않습니다. `NIGHT_DRIVE`는 기본 비공개이고 전체 시그니처 게시도 기본 꺼짐입니다.
- 차단은 본인의 `blocks`에서만 확인합니다. 서버는 상대에게 `PeerUnavailable`, 요청 `UNAVAILABLE`, 중립적인 종료 이유로 투영해야 합니다. `BLOCKED` 종료 이유는 차단을 실행한 본인에게만 허용합니다. 해제해도 과거 연결·교환 연락처가 살아나지 않습니다.
- 차단당한 쪽이 받는 상대 프로필은 공개 동의 철회·계정 삭제와 같은 `가상 프로필` 익명 투영이에요. 닉네임·아바타·관심사·시그니처의 후속 변경을 노출하지 않아요. 차단한 쪽의 차단 목록에는 상대 프로필이 남아요. 공개 프로필 동의 철회로 끝난 연결은 상대에게 `PEER_LEFT`로 보여 차단과 구별되는 사유를 만들지 않아요.
- 계정 삭제는 저장한 개인 ID와 교환 ID를 제거해요. 이미 닫힌 관계도 교환 연락처를 복원하지 않으며 `CLOSED / ACCOUNT_DELETED`로 남아요.

## 버전과 명령

변경되는 엔티티는 `VersionedEntity`의 `id: String`, `version: Long`을 가집니다. 시간은 UTC Unix 밀리초입니다. 값 객체(특성, 문구, 매듭 시드, 입력 초안)는 독립 엔티티가 아닙니다.

- 방 내부 리비전과 응답 버전을 분리해요. 응답 버전은 계정이 볼 수 있는 전체 내용(시간·버전 제외)이 바뀔 때만 증가하며 저장소에 보존해요. 스냅샷과 새 명령 응답의 엔티티 버전은 해당 계정의 버전을 사용해요. `expectedVersion`도 이 버전과 비교해요. 숨겨진 상대 활동은 버전 증가·버전 간격·낙관적 동시성 오류·긴 폴링 조기 응답을 만들지 않아요. 기기 전용 지표의 별도 리비전과 비교하지 않아요.
- `CommandMeta.commandId`는 클라이언트가 생성한 멱등 키입니다. 동일 명령 재시도에는 같은 키와 같은 본문을 씁니다. 성공한 결과를 다시 돌려주어 중복 연결·기록·연락처 교환이 생기지 않게 합니다. 서로 다른 새 명령은 새 키를 씁니다.
- `expectedVersion`은 낙관적 동시성 조건입니다. 이를 생략해도 당사자·최종 상태·동의·발견·근접 검증을 생략하지 않습니다. 경쟁에 진 명령은 실제 상태에 맞는 `AlreadyClosed`, `Expired`, `PeerUnavailable` 등으로 수렴하고 새 스냅샷을 읽습니다.
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
| LeaveConnection | `/v1/connection/leave` | Connection |
| DeleteEncounter | `/v1/encounters/delete` | CommandReceipt |
| DeleteAllEncounters | `/v1/encounters/delete-all` | CommandReceipt |
| RequestAfter | `/v1/after/request` | AfterRequest |
| RespondAfter | `/v1/after/respond` | AfterRequest |
| WithdrawAfter | `/v1/after/withdraw` | AfterRequest |
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
- 텍스트·프리셋 메시지 엔티티, Repository 관찰·전송, HTTP 경로와 알림 설정은 없어요. 애프터는 선택한 연락처를 상호 수락 뒤 교환하는 관계예요.
- `DrivingPolicy.ALLOWED`에서만 탐색·수동 발신 요청·일반 수락·입력·편집을 허용해요. RESTRICTED의 활성 통화에서는 음소거/해제와 통화 끊기를 허용하고 RING/AUTO의 수신 화면에서 받기/거절을 추가 허용해요. AUTO의 사전 동의·조건·보호 규칙을 모두 만족한 자동 요청·수락은 앱 조정기만 실행해요. UNKNOWN에서는 통화 끊기만 허용해요. 서버 요청에 클라이언트가 허용 여부를 주장하는 필드는 없습니다. 기기 정책은 UI와 명령 진입점에서 확인하고, 서버는 자신의 인증·동의·관계 상태를 별도로 검사합니다.
- `DrivingState` 초기값은 `UNKNOWN`입니다. P와 실제 주행 상태 0을 확인한 차량 구현만 `ALLOWED`를 냅니다. 속도 0만으로 허용하지 않습니다.
- 로그아웃·계정 삭제 성공 전에 `SignatureRepository.clearLocalData()`와 로컬 프로필·연락처·토큰 캐시를 정리합니다. APP이 정리 의존성을 연결하고 SOCIAL의 SessionRepository 구현이 완료를 보장합니다. 정리 실패를 성공으로 숨기지 않습니다. 계정 삭제와 단순 로그아웃의 서버 보존 범위는 다릅니다.

## 화면별 계약 확인

| 경험 명세의 화면 | 관찰 | 명령·화면에서 필요한 값 |
|---|---|---|
| 첫 실행 | SessionRepository.session/availableModes | enter; SOLO/SERVER_DEMO, 선택적 roomCode/clientSecret. app 설정에 서버 주소가 있을 때만 availableModes에 SERVER_DEMO 포함 |
| 프로필 등록·편집 | ProfileRepository.profile, Session.policy | saveProfile(ProfileDraft); 로컬 초안 보존 후 동일 명령 재시도 |
| 차량 데이터·위치·공개 범위 | ProfileRepository.settings, SignatureRepository.signature, VoiceCallRepository.microphonePermission | recordConsent, updateVisibility, setDiscovery; 선택 requestMicrophone, 공개 미리보기 |
| 오늘의 인연 | DiscoveryRepository.discovery | setDiscovery, refresh; title/traits/chemistry.lines/공통 관심사 |
| 상대 카드·궁합 | discovery.candidates | PublicProfile.signature, KnotSeed, Chemistry; requestConnection(candidateId) |
| 요청 보냄 | ConnectionRepository.requests, ServerTimeProvider.serverTime | cancel; 서버 기준 expiresAtEpochMillis, retryAvailableAtEpochMillis |
| 요청 받음 | ConnectionRepository.requests, ServerTimeProvider.serverTime | respond(ACCEPT/DECLINE); peer와 chemistry, 서버 기준 응답 기한 |
| 연결 중 | ConnectionRepository.connection, VoiceCallRepository.state, ProfileRepository.profile | 자동 통화, setMuted, requestMicrophone, leave; 공개 공통 관심사·궁합·칭호의 읽기 전용 이야깃거리 |
| 연결 중·주행 제한 / 주행 중 | DrivingStateProvider.drivingState, connection, VoiceCallRepository.state | RESTRICTED는 음소거/해제·통화 끊기, UNKNOWN은 통화 끊기만. 통화 상태 live region |
| 주행 중 요청 받음 | requests, settings.drivingConnectMode | RING 또는 AUTO fallback+RESTRICTED에서 받기/거절. 아바타·닉네임·칭호·공개 점수 최대 2개, 집 보호 수락 차단 |
| 연결 중·좁은 창 | 위와 같음 | 레이아웃만 변경, 다른 데이터 계약 없음 |
| 연결 종료 | ConnectionRepository.connection | endReason, endedAtEpochMillis, encounterId |
| 스친 인연 / 기록 상세 | EncounterRepository.encounters/observeEncounter | deleteEncounter/deleteAllEncounters; peer, chemistry, afterEligibility |
| 애프터 신청 | AfterRepository.requests, observeEncounter, ServerTimeProvider.serverTime | requestAfter(contacts, saveToProfile)/withdraw; 서버 기준 기한과 재신청 가능 시각 |
| 애프터 응답 | AfterRepository.requests, ServerTimeProvider.serverTime | respond(decision, contacts, saveToProfile); peer, chemistry, encounterId, 서버 기준 응답 기한 |
| 이어진 인연 | AfterRepository.connections | sharedContacts/ownSharedContacts, endRelationship; 관계 종료 확인은 화면 상태 |
| 내 프로필 | ProfileRepository.profile/settings, SignatureRepository.signature | saveProfile/updateSettings/publishSignature |
| 차단·신고 | SafetyRepository.blocks | block/unblock/report; 사유 5종, 선택적 관련 엔티티 |
| 기록·계정 정리 | encounters/connections/session | 발견 끄기, leave, endRelationship, deleteEncounter, signOut, deleteAccount |
| 내 시그니처 | SignatureRepository.signature | 매듭, 공개 칭호, 개인 근거, updateVisibility |
| 특성 상세 | SignatureRepository.observeEvidence | TraitEvidence의 점수·지원/통과 횟수·기간·최근 주행; 본인에게만 |
| 공개 범위 | SignatureRepository.signature | updateVisibility; 공개 미리보기 후 publishSignature 또는 철회 |
| 주행 후 요약 | SignatureRepository.recaps/observeRecap | excludeTrip/markRecapSeen; 이전·현재 매듭과 변화 문장 |
| 시연 제어 | VehicleConsole.consoleState, DrivingStateProvider.drivingState | setGear/replay/stopReplay, setSyntheticPresence; 항상 시연 표시 |

## 상태 카드 17종 확인

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
| 연결 상태 확인 중 | CONNECTION_CHECKING | ConnectionStatus.CHECKING |
| 애프터 거절 | AFTER_DECLINED | AfterRequestStatus.DECLINED |
| 애프터 만료 | AFTER_EXPIRED | AfterRequestStatus.EXPIRED |
| 받은 애프터 철회 | AFTER_WITHDRAWN | AfterRequestStatus.WITHDRAWN |

화면 ViewModel은 이 실제 상태에서 카드를 선택합니다. 모든 상태가 enum에 있다는 사실만으로 서버 경쟁·권한·주행 잠금이 검증된 것은 아닙니다. 후속 구현에서 전이 테스트와 스크린샷으로 각각 증명해야 합니다.

## 계약 검증

`ContractTest`는 전체 응답 직렬화 그래프에서 위치·원시 신호·개인 근거 타입을 배제하고, 명령에서 행위자·동의 주장 필드를 배제합니다. 공개 RECORDING 거부, UNSUPPORTED 숫자 거부, 야간 기본 비공개, UNKNOWN 초기 정책, 연락처의 공개 투영 제외와 정규화, 17개 상태, 엔티티 ID·버전, 전체 스냅샷 직렬화도 검사합니다. 명령의 원자성·토큰 권한·실제 기어 수신·버전 증가 구현은 각 담당 모듈의 테스트 범위입니다.

`ServerTimeAnchorTest`는 기기 벽시계가 5분 빠르거나 느려도 120초 기한이 정확히 120초에서 시작하는지, 단조 시간 경과·기준점 없음·시계 재시작·정수 범위를 검증합니다.

## 집 보호 구역의 기기 전용 계약

`HomeZone.kt`의 `HomeZoneRadius(NEAR=300, MEDIUM=500, WIDE=1000)`, `HomeZoneStatus`, `HomeZoneState(status, radius, source)`, `HomeZoneRepository`는 직렬화하지 않아요. 좌표를 가진 공개 타입을 추가하지 않아요. `setFromCurrentPosition(radius, source)`, `changeRadius(radius)`, `clear()`는 주차 상태에서만 실행해요. `HomeZoneState.source`는 DEVICE와 SYNTHETIC을 구분하며 합성 집을 실제 집처럼 표시하지 않아요.

NOT_SET와 신선한 위치로 확인한 OUTSIDE만 발견을 허용해요. INSIDE·LOCATION_UNKNOWN·PERMISSION_OFF는 보호 상태예요. 앱 조립은 HomeZoneRepository를 MeetDeps와 HistoryDeps에 주입하고, 선택한 현재 위치를 얻기 위한 권한 요청을 HistoryDeps.prepareHomeLocation에 연결해요. SignatureExit.OpenHomeZone은 HistoryDestination.HOME_ZONE으로 이동해요.

새 DTO·경로·응답 필드·종료 이유를 만들지 않고 기존 `SetSyntheticPresenceRequest(AWAY)`만 보내요. 좌표와 반경은 서버에 도달하지 않아요. 상대의 전체 SyncSnapshot은 집 보호와 presence lease 만료 모두 같은 AWAY 경로로 생성돼요. 상대에게 앱 종료·차량 전원 종료·통신 단절과 같은 결과를 보여요. 다만 서버 운영자는 명시적 AWAY의 도착을 볼 수 있고, 이번 빌드에 실제 위치 기반 이탈이 없으므로 집 보호 때문이라고 추측할 수 있어요.

`EnginePolicy.presenceLeaseMillis`는 기본 null이고 서버 기본 모듈에서 60초예요. 내부 계정의 기한은 응답에 포함하지 않아요. 계정 생성과 NEARBY가 기한을 정하고 다른 presence는 기한을 지워요. DemoCast는 제외해요. 이미 NEARBY인 갱신은 버전을 올리지 않아요. 서버 ticker가 만료를 AWAY로 바꾸고 기존 유예 뒤 DISTANCE로 끝내요. 앱이 죽은 뒤 발견 가능 상태는 최대 lease 길이만큼 남을 수 있어요.

`HomeZoneCoordinator.prepareSession`은 계정의 로컬 집을 연결한 뒤 네트워크 응답 없이 반환해요. `endSession`도 진행 중 전송을 기다리지 않아요. 보호 중에는 후보를 숨기고 로컬 연결 요청·수락을 거절해요. 발견 ON만 presence 확인을 요구하며 직접 꺼 둔 발견은 켜지 않아요. 서버 모드에서 성공한 NEARBY는 새 키로 20초마다 갱신해요. heartbeat의 네트워크 실패는 UI 오류나 확인 실패를 만들지 않아요. 보호 전환·세션 종료는 진행 중 전송의 재시도도 무효화해요. 위치 동의 전 거절된 presence는 동의 후 새 멱등 키로 보내고 일반 네트워크 재시도는 같은 키를 유지해요. 로컬 정리는 `LocalHomeZoneRepository.clearLocalData()`까지 성공해야 완료해요. 차량 기록 동의 유무와 무관해요.

위치 사용 동의는 계정과 동의 문서 버전에 맞춰 서비스 설정 흐름에서 확인해요. 미확인은 LOCATION_UNKNOWN, 철회는 PERMISSION_OFF로 보호하며 GPS 구독과 location 서비스 사용을 멈춰요. PERMISSION_OFF는 런타임 권한 거절과 앱의 위치 동의 철회를 모두 포함해요. 실제 위치로 집 정하기는 동의가 없으면 fix 대기 없이 거절하고, 합성 집 설정은 동의를 요구하지 않아요. 동의 화면은 각 선택을 바로 저장하며 저장 중 집 설정 진입을 막아요. 집 화면은 저장된 동의를 다시 확인해요.

presence가 Restricted로 거절되면 heartbeat와 AWAY 자동 재시도를 멈춰요. 동의 복원 후 발견 ON은 최초 가입·홈·하단 오브·동의 화면 모두 HomeProtectedDiscoveryRepository.setDiscovery → confirmPresence를 통해 새 키로 presence를 확인해요. 보호 상태 변화만으로 거절된 전송을 재개하지 않아요. 직접 꺼 둔 발견도 자동으로 켜지 않아요.

ContractTest는 기기 전용 타입과 집·구역·반경 필드가 요청·응답 직렬화 그래프에 들어오지 않는지 검사해요. PresenceLeaseTest는 실제 엔진의 lease 만료와 명시적 AWAY의 전체 상대 스냅샷·DISTANCE 시각을 비교해요. HomeZoneCoordinatorTest의 명시적 AWAY 비교는 조정기가 동일한 명령만 보낸다는 증거이며 실제 위치 기반 이탈의 증거는 아니에요. HomeZoneRemotePrivacyTest는 mock HTTP의 모든 요청 본문·헤더와 UpdateLocation 미호출을 확인해요.

## 음성 통화 계약

- `VoiceCallState`는 connectionId, IDLE/CONNECTING/ACTIVE/RECONNECTING, muted, 마이크 AVAILABLE/DENIED/UNAVAILABLE/NOT_USED를 담는 기기 전용 상태예요. 음성 데이터나 음성 활동 수치를 담지 않아요.
- `VoiceCallRepository.microphonePermission`은 통화 여부·SOLO 여부와 독립적인 기기 권한 관찰이에요. 온보딩을 다시 열어도 실제 권한 표시를 복원하며 마이크를 열지 않아요.
- `VoiceTransport.open(connectionId)`는 인증된 `VoiceChannel`을 열어요. incoming과 send는 PCM16 little endian, 16 kHz, mono, 20 ms, 정확히 640 bytes예요. 마이크가 없거나 음소거면 프레임을 보내지 않아요. 침묵 프레임으로 대체하지 않아요.
- `data:voice` 내부 `DemoPeerVoice.start(peer, connectionId)/setFocusAvailable/stop`은 데모 재생 수명 경계이고 `DemoSpeechEngine.prepare/speak/stop/release` 뒤에 번들 클립 재생(`BundledClipSpeechEngine`, `ClipPlayer`)과 Pleos TextToSpeech 3.3.1 ON_DEVICE 대체 구현을 둬요. SOLO 또는 상대가 DEMO_PEER이면 모드와 무관하게 마이크와 음성 WebSocket을 열지 않아요. 상대별 세 문장을 순서대로 재생하고, 같은 연결이 확인 중에서 돌아오면 마지막으로 끝까지 말한 문장 다음부터 이어 가며, 문장 시작 시각 기준 약 20초 간격을 유지해요. 포커스가 없으면 재생하지 않고 종료 시 즉시 stop해요. SDK 시작 이벤트는 실제 소리 시작 신호로 사용하지 않아요.
- `ParticipationSettings.drivingConnectMode`는 OFF/RING/AUTO이며 기본 OFF예요. 기존 `receiveRequestsWhileDriving=true`는 RING, false는 OFF로 읽어요. `autoConnectConditions`는 점수 특성별 최소 점수(60..100, 5점 단위)와 공통 관심사 요구를 담아요. 일반 화면 관찰과 자동 조정기의 관찰을 구분하고, 실제 전송과 재시도 직전에 정책·설정·요청 방향·집 보호를 다시 검사해요.
- 정상 요청 화면의 수락과 opt-in 수신 화면의 받기는 같은 respond(ACCEPT) 명령이에요. `통화 끊기`와 알림 action은 기존 leave 명령이에요. 새로운 종료 사유·애프터 음성 계약은 추가하지 않아요.
- 서버는 현재 CONNECTED만 참여·중계해요. CHECKING과 ENDED로 바뀌면 이미 열린 양측 소켓도 닫고 늦은 join은 거절해요. 단순 네트워크 끊김에는 관계를 종료하지 않아 아직 CONNECTED라면 재참여할 수 있어요. 토큰 교체·로그아웃·계정 삭제 시 기존 토큰 소켓도 닫아요.
- 프레임은 640 byte이며 초당 50개, 버스트 50개 예산을 사용해요. 순간 초과는 DROP으로 처리하고 연결을 유지해요. 잘못된 크기 또는 초당 100개 초과가 5개 연속 1초 구간에 걸치면 소켓을 닫아요. 참가자의 전송률 예산과 과부하 관측은 재접속으로 초기화하지 않아요. 상대별 큐는 5프레임 drop-oldest예요. 입장·명령·revision·ticker가 현재 참가자를 다시 확인하며 프레임마다 engine export나 prune을 호출하지 않아요. 녹음·저장·내용 및 프레임별 메타데이터 로그는 없어요.

기기 PCM 재생은 첫 4프레임을 모은 뒤 시작해요. 오디오 분량은 80ms이며 첫 프레임 도착 후 대기 시간은 약 60ms예요. 수신 큐는 5프레임으로 제한하고 blocking AudioTrack sink의 쓰기가 소비 속도를 맞춰요.

## 연락처 교환 계약

- `ContactIds(kakaoTalkId: String? = null, instagramId: String? = null)`는 `normalized()`와 `isValid(requireAtLeastOne = false)`를 제공해요. 양끝 공백 제거·소문자화와 인스타그램 앞 `@` 제거 뒤 카카오톡 `[a-z0-9_.-]{4,20}`, 인스타그램 `[a-z0-9._]{1,30}`을 검사해요. 인스타그램의 앞뒤·연속 마침표는 거부해요. `toString()`은 값을 숨겨요.
- `MyProfile.contacts`와 `ProfileDraft.contacts`는 기본 빈 값이고 인증된 소유자에게만 보여요. `PublicProfile`, 후보, 연결 요청, `Encounter`와 알림에는 포함하지 않아요. `ParticipationSettings`의 알림 설정은 `notifyRequests`이며 메시지 알림 필드는 없어요.
- `RequestAfterRequest(meta, encounterId, contacts, saveToProfile = false)`는 이번에 공유할 유효한 ID 하나 이상을 요구해요. `RespondAfterRequest(meta, requestId, decision, contacts = ContactIds(), saveToProfile = false)`는 ACCEPT일 때만 유효한 ID 하나 이상을 요구해요. 응답 전까지 신청자 ID는 상대에게 보내지 않아요. `saveToProfile`은 인증된 본인의 저장 값에만 적용해요. 애프터 ACCEPT는 실제 전송·재시도 직전 집 보호 조건도 확인하고, 보호 중에는 거절해요. DECLINE·관계 종료는 이 수락 조건을 적용하지 않아요.
- 상호 수락한 현재 `AfterConnection.sharedContacts`는 상대가 선택한 ID, `ownSharedContacts`는 내가 선택한 ID예요. 둘 다 nullable이며 관계 종료·차단·삭제에는 null로 투영해요. 저장 결과·스냅샷·명령 재시도에서도 현재 권한을 다시 확인해요. 계정 삭제는 저장·교환 사본을 제거해요.
- 인연 목록·ID·QR은 ALLOWED에서만 관찰해요. `SocialGraph.requestSignals`는 수신 대기 요청의 불투명 ID만 제공하며 교환 ID를 실어 나르지 않아요. HTTP 스냅샷 캐시와 로그에 연락처를 추가하지 않아요.
- Instagram QR은 검증된 ID의 `https://instagram.com/<id>`를 기기에서 ZXing Core 3.5.3(Apache-2.0)으로 생성해요. 카카오톡 ID에는 공개 URL을 추정하거나 QR을 만들지 않아요. 시연 상대의 인스타그램 값은 실제 계정이 될 수 없는 하이픈 포함 예약 값이며 사용자 입력 검증과 분리해요.

## 공개 점수와 자동 연결 계약 개정 · 2026-11-13

- 경로는 `/v1`을 유지해요. 점수·연결 모드·자동 요청 표시는 필드 추가 개정이며 `automatic=false` 기본값으로 기존 요청을 읽어요. 엔진과 차량 저장 형식에는 별도 버전 번호를 추가하지 않아요. `StoredEvidence`의 새 값은 null/0/빈 목록 기본값으로 읽고 로컬 저장소가 보관된 `DerivedTripMetrics`에서 새 점수와 근거를 재계산해요.
- 점수 공식은 최근 30일의 유효·비제외 주행에 대해 `round(100 × Σ passingSampleCount / Σ supportedSampleCount)`예요. 전체 유효 주행 3회 전에는 점수가 없어요. 점수 단계는 60/75/90 경계와 일치하고 취향 특성은 null이에요. 공개 DTO에 원시 신호나 분모·분자·개별 주행을 추가하지 않아요.
- 기기 전용 `TraitEvidence.score`, `passingSampleCount`, `recentTrips`와 `TraitTripEvidence`는 내 프로필과 근거 차트·표를 위한 값이에요. 개인 이력은 wire 직렬화 그래프에서 계속 제외해요. 제외 주행은 표에 표시하되 점수와 차트에 포함하지 않아요.
- `DrivingConnectMode`와 `AutoConnectConditions`는 참여 설정의 계약이에요. 레거시 boolean true는 RING으로 마이그레이션하며 설정이 없으면 OFF예요. 설정 저장은 ALLOWED에서만 허용해요. 자동 표시가 있는 정상 연결 요청도 행위자 ID나 동의 플래그를 인증 근거로 쓰지 않아요.
- 자동 조정기는 AUTO·발견 ON·RESTRICTED·집 보호 아님·차단 아님·활성 연결 없음·보낸 대기 요청 없음에서 조건을 통과한 후보를 선택해요. 최소 점수 초과 조건 수 → 공통 관심사 수 → 고정 ID 순이에요. 공개되지 않은 특성은 조건 실패예요.
- 수신 AUTO 조건 충족은 기존 respond(ACCEPT), 불충족은 RING 화면이에요. RING은 수동 응답, OFF는 주차 대기이며 ALLOWED에서는 모든 모드가 수동이에요. 동일 요청의 중복 실행을 막고 요청 유효 기한과 엔진의 단일 활성 연결 규칙을 유지해요.
- 통화 종료 뒤 10분 자동 동작 금지, 거절·취소·종료·만료 상대의 동일 달력 날짜 자동 재시도 금지를 적용해요. 자동으로 연결된 connectionId마다 Pleos ON_DEVICE로 닉네임과 연결 사실을 한 번 알리며 녹음·오디오 저장·로그를 추가하지 않아요.

`AutoConnectSnapshot`은 서비스 전용 기기 계약이며 계정·참여 설정·내 관심사·발견·요청·연결·차단 프로필 ID만 담아요. 연락처와 내 점수 근거를 포함하지 않아요. `AutoConnectCoordinator`는 이 흐름과 주행 정책·집 보호를 관찰하고 `PrivateAutoConnectStore`의 계정별 noBackup 기록에 종료 대기·당일 재시도 금지·이미 알린 연결을 유지해요. 자동 요청과 수락의 실제 전송·재시도 직전에도 현재 설정과 보호 조건을 다시 검사해 오래된 스냅샷이 동작을 허용하지 않게 해요.

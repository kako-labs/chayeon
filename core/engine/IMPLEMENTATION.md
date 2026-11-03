# Social stack 구현 계획

목표: 같은 결정적 엔진으로 기기 안 모드와 서버 모드의 동의·관계·공개 범위를 보장해요.

공통 제약: core:model은 변경하지 않아요. 소유 경로만 변경하고 커밋하지 않아요. 좌표는 메모리에만 보관해요. 모든 명령은 인증된 행위자를 별도로 받아요. 상태 변경은 단일 잠금과 저장 트랜잭션을 거쳐요.

1. core/engine: EnginePolicy, 직렬화 가능한 EngineState와 저장 인터페이스, ChayeonEngine, EngineApi, 시연 상대를 만들어요. 엔진 테스트는 가짜 시계로 만료·수락·차단·공개 철회·재시작·시연을 확인해요.
2. data/social: SocialBackend, LocalBackend, RemoteBackend, 원자적 SnapshotStore와 SocialGraph를 만들어요. 기존 Empty 구현은 유지해요. 중복/역순 응답, 실패 후 메시지 재시도, 로그아웃 정리 실패를 테스트해요.
3. server: 공간별 엔진, 난수 토큰 인증, 모든 계약 경로, 25초 긴 폴링, 타이머, 본문·공간·유휴 제한을 구현해요. 실제 동시성·권한·격리·응답 유출을 테스트해요.
4. 최종: 지정된 Gradle 검증, Android 환경 변수 없는 서버 빌드, 실제 HTTP 두 계정 시나리오, 적대적 diff 검토를 진행해요.

## 엔진 연결 API

`ChayeonEngine(rules: SignatureRules, clock: EngineClock = ..., idGenerator: EngineIdGenerator = ..., store: EngineStore = MemoryEngineStore(), policy: EnginePolicy = EnginePolicy(), roomId: String = "local", demoEnabled: Boolean = true)`를 사용해요.

- `EngineClock.now(): Long`, `EngineIdGenerator.next(): String`
- `EngineStore.load(): EngineState?`, `EngineStore.save(state: EngineState)`는 원자적 저장이며 실패하면 예외를 내요.
- `enterSession(actor: String, request: EnterSessionRequest): AppResult<Session>`는 토큰 없는 세션을 반환해요. 호스트가 인증 토큰을 발급해요.
- 모든 명령은 DTO 이름에서 Request를 뺀 lowerCamelCase 함수로, `(actor: String, request: XRequest): AppResult<T>`예요.
- `snapshotFor(actor: String): AppResult<SyncSnapshot>`, `tick(now: Long = clock.now())`, `exportState(): EngineState`, `revision: StateFlow<Long>`를 제공해요.
- `EngineApi(engine).execute(actor: String, route: String, body: String): String`은 계약 Response JSON을 반환해요. 진입 경로도 지원해요. JSON은 엄격하게 해석하고 오류 본문은 `AppError`만 포함해요.
- 세션 ID는 SyncSnapshot.id와 같아요. 같은 계정 재입장은 새 세션 ID를 발급하고 이전 호스트 토큰은 폐기해요.

## 재시작

합성 위치는 좌표 없는 SyntheticPresence 상태로 복원해요. DEVICE 위치는 저장하지 않아 새 입력 전까지 STALE이며 연결은 CHECKING과 STATUS_UNKNOWN 유예로 처리해요. 판정 시각과 종료 상태는 저장하되 위치를 복원했다고 주장하지 않아요.

## 검토 기록

엔진·서버 검토에서 캐시 후보의 공개 철회, 내 기록 삭제 후 상대 애프터, 캐시 폐기 후 오래된 명령 재실행, 이전 시연 연결의 위치 이벤트 개입을 수정하고 회귀 테스트를 추가했어요. 종료 토큰은 영수증 재시도에만 제한적으로 사용해요. 결과 캐시 밖의 멱등 키는 안전하게 거절하며 이 한계를 계약 문서에 명시했어요.

데이터 검토에서 종료 명령 종류가 바뀐 재시도, 실패 전 요청의 지연 응답, 재시작 중 정리 표식 소실을 수정했어요. 실제 검증 결과와 통합 전제는 각 WORK_REPORT와 최종 보고를 따르세요.

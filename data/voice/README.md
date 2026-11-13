# 통화 오디오

`VoiceCallController`는 Application 수명의 `VoiceCallRepository` 구현입니다. 앱은 현재 세션의 연결이 `CONNECTED`이고 집 보호로 숨겨지지 않았을 때만 `updateTarget(id, mode, microphoneGranted, peer)`를 호출합니다. 종료·`CHECKING`·로그아웃·집 보호에는 `updateTarget(null, ...)` 또는 `stop()`을 호출합니다. 애프터 관계는 입력하지 않습니다.

- `VoiceAudio`: 포커스, 통신 모드, 녹음, 재생 경계입니다. `focusAvailable`이 꺼지면 녹음을 닫고, 다시 켜지면 활성·음소거 해제·권한 허용 조건을 다시 확인합니다.
- `CallService`: 통화 알림과 서비스 수명입니다. `VoiceServiceOwner.endVoiceCall()`은 앱의 기존 연결 나가기 명령을 실행합니다.
- `VoiceTransport`: 네트워크 구현은 `data:social`에 있습니다. 재접속 중에는 마이크를 닫습니다.
- 관찰 실패나 `CHECKING` 뒤 같은 연결이 복구되면 음소거 선택을 유지합니다. 최근 연결 하나의 선택만 메모리에 보관하며 새 연결은 기본 음소거 해제 상태로 시작합니다.
- `DemoPeerVoice`: SOLO와 서버 모드의 DEMO_PEER 모두 사용합니다. 앱이 `BundledClipSpeechEngine`(번들 클립을 `AndroidClipPlayer`로 재생)을 주입하며 네트워크와 마이크를 열지 않습니다. 클립이 없거나 재생하지 못한 문장만 Pleos ON_DEVICE TTS로 말하고, TTS는 처음 필요할 때 준비합니다. 공개 프로필에 맞는 세 문장을 약 20초 시작 간격으로 말하고 조용해집니다. 같은 연결이 확인 중에서 돌아오면 인사를 처음부터 반복하지 않고, 마지막으로 끝까지 말한 문장 다음부터 이어 갑니다(말하던 중 끊긴 문장은 다시 말합니다). 클립과 TTS가 모두 실패하면 무음으로 통화를 유지합니다. 종료·집 보호는 즉시 재생을 정지하고 엔진을 해제합니다.

PCM16, 16 kHz, mono, 20 ms의 한 프레임은 640 bytes입니다. 방향당 약 256 kbps이며 패킷 부가 비용은 별도입니다. 재생 큐는 최대 5프레임이고 오래된 프레임을 버립니다. 최초와 언더런 뒤 4프레임(80 ms 분량)을 모읍니다. 프레임별 타이머 없이 blocking AudioTrack 쓰기가 재생 속도를 맞춥니다. 오디오는 파일·로그에 기록하지 않습니다.

Android 구현은 `VOICE_COMMUNICATION`으로 녹음하고 가능한 AEC·NS·AGC를 켭니다. `USAGE_VOICE_COMMUNICATION`/`CONTENT_TYPE_SPEECH`로 재생하며 통화 중에만 `MODE_IN_COMMUNICATION`과 transient audio focus를 사용합니다. 마이크 권한 거부, 기기 음소거, 플랫폼의 녹음 silenced 상태, 시작 실패는 수신을 유지하며 `DENIED` 또는 `UNAVAILABLE`로 표시합니다. 음소거는 녹음기를 해제하며 프레임을 전송하지 않습니다.

서비스는 마이크 사용이 허용되면 `microphone|mediaPlayback`, 수신 전용이나 데모 상대 통화에서는 `mediaPlayback` 타입을 사용합니다. Android 14 이상은 `RECORD_AUDIO` 없이 microphone 타입을 시작할 수 없으므로 수신 전용에 재생 타입이 필요합니다. 서비스 승격을 확인한 뒤 오디오 포커스를 요청합니다. Android 15의 포커스 제한도 이 순서로 대응합니다.

통화가 이미 시작된 뒤 다른 앱으로 이동하는 흐름을 대상으로 합니다. 앱이 이미 백그라운드인 상태에서 처음 통화가 성립하면 Android의 새 foreground service 시작 제한을 받을 수 있습니다. 이 경우 실패를 잡고 마이크를 열지 않습니다. 실차의 오디오 라우팅·AEC 품질·기기 전체 마이크 스위치 연동은 기기 검증이 필요합니다.

검증: `:data:voice:testDebugUnitTest :data:voice:lintDebug`. 테스트는 가짜 채널·오디오·서비스의 수명과 Robolectric의 서비스 타입·알림·포커스/모드 해제를 검사합니다. 실제 음질이나 하드웨어 입력 성공을 보장하는 테스트는 아닙니다.

내비게이션의 CAN_DUCK은 통화를 유지합니다. LOSS_TRANSIENT/LOSS에는 녹음·재생을 쉬고 GAIN에 복구합니다. TTS의 onStart는 실제 소리 시작으로 해석하지 않아 발화 중 표시를 만들지 않습니다. 이전 앱의 발화 지연 측정은 차연의 기기 검증을 대신하지 않습니다.

# Chayeon working rules

Read before working: docs/architecture.md, docs/product/drive-signature.md, docs/product/experience-spec.md, docs/product/quality-bar.md, docs/design/README.md.

## People and privacy

- Refer to people by role only. The contest bans developer real names in shared material.
- Never add real names, personal accounts, absolute personal paths, keys, tokens, local.properties, SDK binaries, AVDs or caches to Git.
- Never read, store or transmit VIN, CRN, plate, vehicle model or exterior data. Never call CarInfo.getVINInfo or getCRNInfo.
- Coordinates, distance, direction and trajectories never appear in any API response, UI or log.
- Raw vehicle signals stay in memory on the device. Persist derived metrics only.
- Contact IDs are private owner data. Public profiles, candidates, requests, encounters, logs and notifications never contain them. Share only the IDs selected for an after request after both participants accept. Revoke exchanged IDs on relationship end, block or account deletion, including cached command results.
- Contact IDs and Instagram QR codes are visible only under DrivingPolicy.ALLOWED. Never derive a KakaoTalk link from an ID.

## Architecture

- Follow the module graph in docs/architecture.md. New modules and edges must be declared in allowedEdges.
- Features depend on interfaces in core:model, never on data modules or other features. app owns wiring and top-level navigation; each feature exposes its own navigation graph.
- android.car and the Vehicle SDK live only in data:vehicle. HTTP/WebSocket lives only in data:social; AudioRecord/AudioTrack and the call service live only in data:voice.
- core:model and core:signature are pure Kotlin with no Android dependency.
- UI uses screen-level ViewModels and immutable StateFlow UI state with unidirectional data flow. No shared global busy flag.
- Identity comes from the auth token on the server. Never trust a client-supplied sender id or consent flag.

## Safety rules

- Only DrivingPolicy.ALLOWED permits browsing, manual sending, normal acceptance, typing, and settings edits. RESTRICTED permits mute/unmute and hang-up, answer/decline in RING or AUTO fallback, and client-driven automatic requests/acceptance under pre-given AUTO consent and matching published scores. Automatic actions require discovery ON, no active connection or pending outgoing request, no home protection or block, a 10-minute post-call cooldown, and no same-person repeat that calendar day after decline/cancel/end/expiry. Parked connections stay manual; UNKNOWN permits safe hang-up only. Speed 0 does not mean parked.
- Voice is relayed only between the two participants of a currently CONNECTED temporary connection. Never record, persist or log audio. Open the microphone only during an active, unmuted server call with permission; SOLO never opens it. Home protection and every connection end stop capture and playback immediately.
- Microphone consent is optional. Never launch permission dialogs while RESTRICTED or UNKNOWN. Driving connect mode defaults to OFF; home protection always blocks accepting.
- No live score or coaching while driving. Only the partner's title and at most two static published score chips may accompany an incoming or active call. No other numbers or decorative motion while driving.
- Never reward speed or aggressive driving. Other users see only explicitly published trait scores from 60 to 100, never private scores, trip counts/history, ranks, or negative judgments. LONG_ROAD and NIGHT_DRIVE remain unscored preference badges.
- Unsupported signals are UNSUPPORTED, never zero.
- SOLO는 첫 모드 선택에서 가상 상대임을 알리고, 내 시그니처 근거에서 데이터 출처를 한 줄로 보여줍니다. 다른 화면에서는 반복하지 않습니다. SERVER_DEMO는 실제 사람과 섞이는 가상 상대와 합성 데이터에 작은 표시를 유지합니다.

## Design

- docs/design/screens/Red*.dc.html is the visual source of truth. Copy exact values from its inline styles. docs/product/experience-spec.md lists the deliberate differences.
- All colors, spacing, radii, type and elevation come from core:ui tokens. No default Material look: draw jelly buttons, glass cards, glossy orbs and avatar dishes with layered Brush and Canvas.
- Layout target is 1682x1210dp at 160dpi, with a one-column fallback under 1100dp width. Touch targets at least 76dp. Text at least 20sp.
- Every screen and state gets a Roborazzi screenshot test.

## Code

- Comments and identifiers in English. User-facing text and docs in Korean.
- Korean UI copy follows the rules in docs/product/experience-spec.md.
- Do not reference issue numbers in comments.
- Keep changes inside your assigned paths. If a shared contract must change, report it instead of changing it, unless your brief owns that contract.
- Do not add dependencies beyond those your brief or the version catalog allows without saying why in your report.

## Verification

- Run python3 scripts/dev.py verify before reporting completion, or the narrower command your brief names.
- Report untested platform assumptions plainly. Never claim verification you did not run.
- Do not commit. The controller commits accepted work.

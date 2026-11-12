# Chayeon working rules

Read before working: docs/architecture.md, docs/product/drive-signature.md, docs/product/experience-spec.md, docs/product/quality-bar.md, docs/design/README.md.

## People and privacy

- Refer to people by role only. The contest bans developer real names in shared material.
- Never add real names, personal accounts, absolute personal paths, keys, tokens, local.properties, SDK binaries, AVDs or caches to Git.
- Never read, store or transmit VIN, CRN, plate, vehicle model or exterior data. Never call CarInfo.getVINInfo or getCRNInfo.
- Coordinates, distance, direction and trajectories never appear in any API response, UI or log.
- Raw vehicle signals stay in memory on the device. Persist derived metrics only.

## Architecture

- Follow the module graph in docs/architecture.md. New modules and edges must be declared in allowedEdges.
- Features depend on interfaces in core:model, never on data modules or other features. app owns wiring and top-level navigation; each feature exposes its own navigation graph.
- android.car and the Vehicle SDK live only in data:vehicle. HTTP lives only in data:social.
- core:model and core:signature are pure Kotlin with no Android dependency.
- UI uses screen-level ViewModels and immutable StateFlow UI state with unidirectional data flow. No shared global busy flag.
- Identity comes from the auth token on the server. Never trust a client-supplied sender id or consent flag.

## Safety rules

- Only DrivingPolicy.ALLOWED permits browsing, requesting, accepting, typing or editing. RESTRICTED and UNKNOWN block them. Speed 0 does not mean parked.
- No live score or coaching while driving. No decorative motion while driving.
- Never reward speed or aggressive driving. Other users never see numbers, ranks or negative judgments.
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

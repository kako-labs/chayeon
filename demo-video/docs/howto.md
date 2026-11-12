# 만드는 법

## 같은 녹화로 다시 렌더

저장소 루트에서 실행합니다. Node.js, pnpm, Python 3.11, ffmpeg가 필요합니다.

```sh
mkdir -p demo-video/public/rec
cp ~/Documents_LOCAL/chayeon-video/render-sources/rec/story.mp4 demo-video/public/rec/story.mp4
cd demo-video
pnpm install --frozen-lockfile
python3.11 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements.txt
pnpm run typecheck
pnpm run render
```

렌더는 `ChayeonStory`, 1920×1080, 30fps, 무음, CRF 18, 동시성 4입니다. 결과 폴더를 바꾸려면 `.venv/bin/python tools/render.py --output <폴더>`를 사용합니다. 같은 명령이 720p CRF 27 미리보기와 1fps 8×10 프레임 시트도 만듭니다.

`render.log`의 `Session closed`, `Target closed`, `Error:`를 감시하며, 오류나 180초 무응답이면 프로세스 그룹을 종료합니다. 종료 코드와 ffprobe 결과를 함께 검사합니다. 프레임 시트는 사람이 모든 장을 확인해야 합니다. 다시 후처리만 하려면 `--post-only`를 붙입니다.

## 처음부터 촬영

전용 기기 `emulator-5570`, AVD `Pleos_Connect_v2_0`, 사용자 10, gRPC 8590만 사용합니다. 다른 기기로 대체하지 않습니다. `--prepare`는 차연을 삭제하고 지정 릴리스 APK를 새로 설치하므로 촬영용 계정에만 실행합니다.

```sh
adb -s emulator-5570 emu avd name
pnpm --dir demo-video/tools/rec install --frozen-lockfile
export CHAYEON_EMULATOR_SERIAL=emulator-5570
python3 demo-video/tools/rec/story_take.py ~/Documents_LOCAL/chayeon-video/preparation --prepare
python3 demo-video/tools/rec/story_take.py ~/Documents_LOCAL/chayeon-video/render-sources/take
python3 demo-video/tools/rec/prepare.py ~/Documents_LOCAL/chayeon-video/render-sources/take
```

- APK: `apk/chayeon-release.apk`. 온보딩은 녹화 전에 끝냅니다.
- 기존 `scripts/e2e.py`의 Device·Console을 재사용합니다. 앱 글자로 탭을 찾고 좌표와 호스트 시각을 로그에 적습니다.
- `adb forward tcp:8088 tcp:8088`과 Virtual Console 세션 heartbeat를 사용합니다. 콘솔 브라우저를 따로 열면 속도 값을 덮어쓸 수 있으므로 열지 않습니다.
- 시계 차이는 촬영 전후 각각 7번 측정합니다. 영상과 탭은 같은 호스트 시계를 사용하며, 기기 시계 차이는 증빙으로 보존합니다.
- 1920×1080 gRPC 프레임을 최신 화면으로 갱신하고, 벽시계에 맞춰 30fps로 바로 인코딩합니다. 변화가 없는 동안 같은 프레임을 반복하는 것이 정상입니다.
- 이 환경에서는 VideoToolbox 압축 세션이 실패하여 `libx264`, ultrafast, CRF 20, GOP 15를 사용합니다. 프레임 덤프를 만들지 않습니다.
- 원본은 `screen.mp4`, `screen.json`, `events.json`입니다. `prepare.py`는 로그를 첫 프레임 기준으로 정규화하고 `public/rec/story.mp4`로 복사합니다.
- 촬영을 다시 하면 실제 시각이 달라지므로 `scenes.ts`를 새 마커와 맞춰 검토해야 합니다.

## 편집 원칙

Pleos 전체 화면을 (176, 20)에 1568×882로 둡니다. 가로세로 비율을 유지하고 왼쪽 계기를 자르지 않습니다. 검정·빨강·흰색은 `core:ui` 토큰을 따릅니다. 이전 키트의 Noto Sans KR과 앱의 실제 아이콘을 사용합니다.

모든 원본 구간은 1배속이며, 화면 위의 탭 원은 로그로만 그립니다. 시그니처 생성 대기, 시연 제어, 집으로 가는 주행 대기만 컷하며 하단 띠에 표시합니다. 자막과 표시 시각은 `scenes.ts` 한곳에서 관리합니다. 마무리에 에뮬레이터·Virtual Console·가상 상대·실차 미확인을 밝힙니다.

## 검수

```sh
pnpm --dir demo-video run typecheck
ffprobe -v error -show_format -show_streams ~/Documents_LOCAL/chayeon-video/chayeon-story.mp4
ffmpeg -i ~/Documents_LOCAL/chayeon-video/chayeon-story.mp4 -vf 'fps=1,scale=384:216,tile=8x10' ~/Documents_LOCAL/chayeon-video/sheets/sheet-%02d.jpg
```

전체 시트에서 잘림·늘어남·검정 화면·시연 제어·출처 문구 반복·자막 넘침·컷 표시 누락을 확인합니다. 주행 장면은 기어/속도와 잠금이 함께 보여야 합니다. 정지 화면의 체류와 인코더 정지는 원본 시계·차량 입력 로그로 구분합니다. 타입 검사와 영상 검수를 이 트랙의 좁은 검증으로 사용하며 앱 전체 verify는 대신했다고 주장하지 않습니다.

## 이 제작 환경의 렌더 제한

이 호스트에서는 Chromium이 `bootstrap_check_in ... Permission denied (1100)`로 실행되지 않습니다. 권한 제한을 바꾸지 않고 FFmpeg 경로를 추가했습니다.

```sh
cd demo-video
python3.11 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements.txt
.venv/bin/python tools/render.py --renderer ffmpeg
```

`auto`(기본)는 Remotion을 먼저 시도하고 위 브라우저 실행 권한 오류에만 FFmpeg로 전환합니다. 다른 렌더 오류는 실패로 처리합니다. `--renderer remotion`은 대체 없이 Remotion만 실행합니다.

FFmpeg 경로도 TypeScript의 `src/story/scenes.ts`, `src/config/theme.ts`를 컴파일해 가져옵니다. 장면·시각·자막·색·화면 좌표가 별도 표로 갈라지지 않습니다. Pillow 9.5.0은 같은 Noto Sans KR 가변 글꼴의 굵기를 적용하여 자막 띠와 타이틀을 그리는 용도로 추가했습니다. 탭 로그는 ASS 벡터 원과 0.7초 페이드로 표시합니다. 모든 장면을 동시성 4, 장면당 인코더 스레드 2로 렌더합니다.

최종 초안은 FFmpeg 경로로 만듭니다. 타이틀·마무리의 글자 배치는 같은 디자인을 재현한 것으로, Chromium의 CSS 결과와 픽셀 단위로 같다고 검증하지 않았습니다. 실제 앱 영상과 컷 시각은 동일한 원본 구간입니다.

# 차연 이야기 영상

차연 릴리스 앱의 한 사람 하루를 따라가는 3분 13초 영상 키트입니다. 고정 화면·하단 자막·1배속·탭 로그 방식을 사용합니다. 앱 소리(Pleos 안내, 차량 효과음)는 넣고 가상 상대의 목소리는 뺍니다.

- 편집: `src/story/scenes.ts` (한 줄에 원본 구간, 길이, 장, 자막, 선택 강조)
- 촬영: `tools/rec/story_take.py`(`--part auto|home`으로 나눠 찍기), `tools/rec/screen.js`, `tools/rec/audio.js`
- 제작·복원: [만드는 법](docs/howto.md)
- 실제 타임라인과 변경 이유: [이야기 표](docs/story.md)
- 결과: `~/Documents_LOCAL/chayeon-video/v2/chayeon-story.mp4`, `chayeon-story-preview.mp4`

```sh
cd demo-video
pnpm install --frozen-lockfile
python3.11 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements.txt
# docs/howto.md에 따라 녹화본을 public/rec/에 복원한 뒤
pnpm run typecheck
pnpm run render
```

영상·녹화본·렌더 로그는 Git에 넣지 않습니다. 앱 코드는 바꾸지 않습니다.

기본 영상 `chayeon-story.mp4`는 이 Remotion 프로젝트로 렌더했습니다(2026-11-13, `npx remotion render src/index.ts ChayeonStory out/chayeon-story-remotion.mp4 --concurrency 4`, 3분 9.4초). 처음 초안은 Chromium을 실행할 수 없는 작업 환경에서 같은 장면 표로 만든 FFmpeg 렌더(`tools/render_ffmpeg.py`)이고, `chayeon-story-ffmpeg.mp4`로 남겨 두었습니다. 두 렌더는 같은 시점의 화면 배치, 자막, 컷 표시가 같습니다.

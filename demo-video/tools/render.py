"""Render with four workers, fail on renderer death, and produce review artifacts."""
import argparse
import json
import os
from pathlib import Path
import selectors
import signal
import subprocess
import time


def render(kit, output):
    command = ['pnpm', 'exec', 'remotion', 'render', 'src/index.ts', 'ChayeonStory',
               str(output / 'chayeon-story.mp4'), '--concurrency=4', '--crf=18', '--audio-codec=aac', '--sample-rate=48000']
    with (output / 'render.log').open('w') as log:
        process = subprocess.Popen(command, cwd=kit, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ)
        last_output = time.monotonic()
        tail = ''
        try:
            while process.poll() is None:
                for key, _ in selector.select(timeout=1):
                    chunk = os.read(key.fileobj.fileno(), 65536).decode(errors='replace')
                    if chunk:
                        log.write(chunk)
                        log.flush()
                        tail = (tail + chunk)[-8192:]
                        last_output = time.monotonic()
                        if any(error in tail for error in ['Session closed', 'Target closed', 'Error:']):
                            raise RuntimeError('Renderer error; see render.log')
                if time.monotonic() - last_output > 180:
                    raise RuntimeError('Renderer produced no output for 180 seconds')
            remaining = process.stdout.read().decode(errors='replace')
            log.write(remaining)
            if process.returncode:
                raise RuntimeError('Render process failed; see render.log')
        finally:
            selector.close()
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=Path.home() / 'Documents_LOCAL/chayeon-video')
    parser.add_argument('--post-only', action='store_true')
    parser.add_argument('--renderer', choices=['auto', 'remotion', 'ffmpeg'], default='auto')
    args = parser.parse_args()
    output = args.output.expanduser().resolve()
    output.mkdir(parents=True, exist_ok=True)
    kit = Path(__file__).resolve().parents[1]
    subprocess.run(['pnpm', 'exec', 'tsc', '--noEmit'], cwd=kit, check=True)
    if not args.post_only:
        if args.renderer == 'ffmpeg':
            from render_ffmpeg import render_ffmpeg
            render_ffmpeg(kit, output)
        else:
            try:
                render(kit, output)
            except RuntimeError:
                failure = (output / 'render.log').read_text()
                if args.renderer != 'auto' or 'Permission denied' not in failure or 'Failed to launch' not in failure:
                    raise
                print('Chromium launch denied by host; using the same timeline with FFmpeg.', flush=True)
                from render_ffmpeg import render_ffmpeg
                render_ffmpeg(kit, output)
    video = output / 'chayeon-story.mp4'
    info = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_format', '-show_streams', '-of', 'json', str(video)]))
    stream = next(s for s in info['streams'] if s['codec_type'] == 'video')
    audio = [s for s in info['streams'] if s['codec_type'] == 'audio']
    assert len(info['streams']) == 2 and len(audio) == 1 and audio[0]['sample_rate'] == '48000', 'Expected one video and one 48 kHz audio stream'
    assert (stream['width'], stream['height'], stream['r_frame_rate']) == (1920, 1080, '30/1')
    assert 165 <= float(info['format']['duration']) <= 195
    (output / 'probe.json').write_text(json.dumps(info, indent=2))
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(video), '-vf', 'scale=1280:720',
                    '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '27', '-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart',
                    str(output / 'chayeon-story-preview.mp4')], check=True)
    sheets = output / 'sheets'
    sheets.mkdir(exist_ok=True)
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(video),
                    '-vf', 'fps=1,scale=384:216,tile=8x10', '-fps_mode', 'vfr', str(sheets / 'sheet-%02d.jpg')], check=True)
    print('Rendered and probed. Visually review every sheet before delivery.')


if __name__ == '__main__':
    main()

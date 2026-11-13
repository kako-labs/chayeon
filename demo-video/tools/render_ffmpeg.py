"""Render the exported Remotion scene table when the host cannot launch Chromium."""
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
from pathlib import Path
import subprocess

from PIL import Image, ImageColor, ImageDraw, ImageFont

DISCLOSURE = ['Pleos Connect 에뮬레이터에서 녹화했어요.',
              '주행은 Virtual Console 신호이고, 상대는 앱이 만든 가상 계정과 미리 만든 목소리예요.',
              '실차에서는 확인하지 않았어요.']


def export_timeline(kit):
    subprocess.run(['pnpm', 'exec', 'tsc', '--module', 'commonjs', '--moduleResolution', 'node',
                    '--noEmit', 'false', '--outDir', 'out/timeline'], cwd=kit, check=True)
    script = "const s=require('./out/timeline/story/scenes.js');const t=require('./out/timeline/config/theme.js');console.log(JSON.stringify({...s,...t}));"
    timeline = json.loads(subprocess.check_output(['node', '-e', script], cwd=kit))
    assert 165 * 30 <= timeline['TOTAL_FRAMES'] <= 195 * 30, 'Timeline must be 2:45–3:15'
    (kit / 'out/timeline.json').write_text(json.dumps(timeline, ensure_ascii=False, indent=2))
    return timeline


def render_ffmpeg(kit, output):
    timeline = export_timeline(kit)
    theme, frame = timeline['THEME'], timeline['FRAME']
    folder = kit / 'out/ffmpeg'
    folder.mkdir(parents=True, exist_ok=True)
    recording = json.loads((kit / 'src/data/story.json').read_text())
    for scene in timeline['SCENES']:
        assert scene['from'] + scene['duration'] <= recording['frames'], scene['id']
    font_path = kit / 'public/fonts/NotoSansKR-VF.ttf'

    def blend(color, opacity):
        foreground = ImageColor.getrgb(color)
        background = ImageColor.getrgb(theme['bg'])
        return tuple(round(a * opacity + b * (1 - opacity)) for a, b in zip(foreground, background))

    def font(size, weight=700):
        result = ImageFont.truetype(str(font_path), size)
        result.set_variation_by_axes([weight])
        return result

    def center(draw, text, y, size, color, weight=700):
        face = font(size, weight)
        width = draw.textlength(text, font=face)
        draw.text(((1920 - width) / 2, y), text, font=face, fill=color, anchor='lt')

    def brand(image, y):
        draw = ImageDraw.Draw(image)
        subtitle = '길 위에서 시작되는 인연'
        width = 140 + 30 + draw.textlength(subtitle, font=font(34, 500))
        x = round((1920 - width) / 2)
        icon = Image.open(kit / 'public/story/app-icon.jpg').convert('RGB').resize((140, 140), Image.Resampling.LANCZOS)
        mask = Image.new('L', (140, 140))
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, 139, 139), radius=32, fill=255)
        image.paste(icon, (x, y), mask)
        draw.text((x + 170, y - 3), '차연', font=font(88, 900), fill=theme['text'], anchor='lt')
        draw.text((x + 170, y + 105), subtitle, font=font(34, 500), fill=theme['secondary'], anchor='lt')

    title = Image.new('RGB', (1920, 1080), theme['bg'])
    brand(title, 460)
    title.save(folder / 'title.png')
    ending = Image.new('RGB', (1920, 1080), theme['bg'])
    brand(ending, 340)
    draw = ImageDraw.Draw(ending)
    center(draw, '영상에 나온 차량 기능', 550, 24, theme['accent'], 500)
    center(draw, '기어 · 속도 · 주행 신호 · 위치 · Pleos 음성', 598, 34, theme['text'], 500)
    for i, text in enumerate(DISCLOSURE):
        center(draw, text, 855 + i * 45, 25, theme['secondary'], 500)
    ending.save(folder / 'ending.png')

    def timestamp(seconds):
        cs = round(max(0, seconds) * 100)
        return f'{cs // 360000}:{cs // 6000 % 60:02}:{cs // 100 % 60:02}.{cs % 100:02}'

    jobs = []
    for index, scene in enumerate(timeline['SCENES']):
        overlay = Image.new('RGBA', (1920, 1080))
        draw = ImageDraw.Draw(overlay)
        x = frame['x']
        for chapter in timeline['CHAPTERS']:
            active = chapter == scene['chapter']
            face = font(21, 800 if active else 500)
            draw.text((x, 928), chapter, font=face, fill=theme['accent'] if active else blend(theme['secondary'], 0.5), anchor='lt')
            x += draw.textlength(chapter, font=face) + 25
        if scene.get('cut'):
            face = font(21)
            width = draw.textlength(scene['cut'], font=face) + 32
            left = frame['x'] + frame['w'] - width
            draw.rounded_rectangle((round(left), 920, round(left + width), 960), radius=18, outline=blend('#FFC8D0', 0.24), width=1)
            draw.text((left + 16, 928), scene['cut'], font=face, fill=theme['accent'], anchor='lt')
        face = font(40, 800)
        assert draw.textlength(scene['caption'], font=face) <= frame['w'], scene['id']
        draw.text((frame['x'], 974), scene['caption'], font=face, fill=theme['text'], anchor='lt')
        if scene.get('highlight'):
            x, y, w, h = scene['highlight']
            scale = frame['w'] / 2560
            draw.rectangle((frame['x'] + x * scale, frame['y'] + y * scale,
                            frame['x'] + (x + w) * scale, frame['y'] + (y + h) * scale), outline=theme['accent'], width=2)
        stem = f'{index + 1:02}-{scene["id"]}'
        overlay.save(folder / f'{stem}.png')
        ass = ['[Script Info]', 'ScriptType: v4.00+', 'PlayResX: 1920', 'PlayResY: 1080',
               '[V4+ Styles]', 'Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding',
               'Style: Tap,Arial,20,&HD9A69AFF,&HD9A69AFF,&H00A69AFF,&HFF000000,0,0,0,0,100,100,0,0,1,3,0,7,0,0,0,1',
               '[Events]', 'Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text']
        for tap in recording['taps']:
            local = tap['sec'] - scene['from'] / 30
            if 0 <= local < scene['duration'] / 30:
                x = frame['x'] + tap['x'] * frame['w'] / 2560 - 21
                y = frame['y'] + tap['y'] * frame['h'] / 1440 - 21
                path = 'm 21 0 b 33 0 42 9 42 21 b 42 33 33 42 21 42 b 9 42 0 33 0 21 b 0 9 9 0 21 0'
                drawing = f'{{\\an7\\pos({x:.2f},{y:.2f})\\p1\\fad(0,700)}}{path}'
                ass.append(f'Dialogue: 0,{timestamp(local)},{timestamp(local + 0.7)},Tap,,0,0,0,,{drawing}')
        (folder / f'{stem}.ass').write_text('\n'.join(ass) + '\n')
        filters = f'[0:v]scale={frame["w"]}:{frame["h"]}:flags=lanczos,pad=1920:1080:{frame["x"]}:{frame["y"]}:color={theme["bg"]},setsar=1[base];[base][1:v]overlay=0:0,ass=out/ffmpeg/{stem}.ass,format=yuv420p[v]'
        jobs.append((stem, ['-ss', str(scene['from'] / 30), '-i', 'public/rec/story.mp4', '-loop', '1', '-framerate', '30', '-i', f'out/ffmpeg/{stem}.png', '-filter_complex', filters, '-map', '[v]', '-map', '0:a', '-frames:v', str(scene['duration']), '-t', f"{scene['duration'] / 30:.6f}"]))
    silence = ['-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo']
    jobs.insert(0, ('00-title', ['-loop', '1', '-framerate', '30', '-i', 'out/ffmpeg/title.png', *silence, '-vf', 'fade=t=in:st=0:d=0.4,fade=t=out:st=3.166667:d=0.333333', '-map', '0:v', '-map', '1:a', '-frames:v', str(timeline['TITLE_FRAMES']), '-t', f"{timeline['TITLE_FRAMES'] / 30:.6f}"]))
    jobs.append(('99-ending', ['-loop', '1', '-framerate', '30', '-i', 'out/ffmpeg/ending.png', *silence, '-map', '0:v', '-map', '1:a', '-frames:v', str(timeline['ENDING_FRAMES']), '-t', f"{timeline['ENDING_FRAMES'] / 30:.6f}"]))

    def encode(job):
        stem, args = job
        command = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-filter_complex_threads', '1', *args,
                   '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-ac', '2', '-c:v', 'libx264', '-threads', '2', '-preset', 'fast', '-crf', '18', '-pix_fmt', 'yuv420p',
                   '-r', '30', '-video_track_timescale', '15360', str(folder / f'{stem}.mp4')]
        with (folder / f'{stem}.log').open('w') as log:
            subprocess.run(command, cwd=kit, stderr=log, stdout=log, check=True, timeout=600)
        return stem

    with (output / 'ffmpeg-render.log').open('w') as log:
        log.write('Renderer: ffmpeg; concurrency: 4; source: exported src/story/scenes.ts\n')
        log.flush()
        with ThreadPoolExecutor(max_workers=4) as pool:
            for future in as_completed([pool.submit(encode, job) for job in jobs]):
                stem = future.result()
                print('Encoded ' + stem, flush=True)
                log.write('Encoded ' + stem + '\n')
                log.flush()
    (folder / 'concat.txt').write_text(''.join(f"file '{stem}.mp4'\n" for stem, _ in jobs))
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', str(folder / 'concat.txt'),
                    # Delivered silent (owner decision 2026-11-14): captions and the screen carry the story.
                    '-c:v', 'copy', '-an', '-movflags', '+faststart', str(output / 'chayeon-story.mp4')], check=True)
    (output / 'timeline.json').write_text(json.dumps(timeline, ensure_ascii=False, indent=2))

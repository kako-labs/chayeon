"""Normalize the recording clock, place the app audio on the screen clock, and keep a seek-friendly source."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import wave


def place_audio(take, meta, wav_path):
    """Lay the gRPC audio packets on the screen clock: contiguous runs stay contiguous, gaps stay silent."""
    info = json.loads((take / 'audio.json').read_text())
    rate, frame_bytes = info['rate'], 2 * info['channels']
    raw = (take / 'audio.pcm').read_bytes()
    total = round(meta['frames'] / meta['fps'] * rate) * frame_bytes
    track = bytearray(total)
    expected = None
    for packet in info['packets']:
        at = round((packet['atMs'] - meta['startedAtMs']) / 1000 * rate) * frame_bytes
        position = expected if expected is not None and abs(at - expected) <= 0.05 * rate * frame_bytes else at
        chunk = raw[packet['offset']:packet['offset'] + packet['length']]
        if 0 <= position < total:
            chunk = chunk[:total - position]
            track[position:position + len(chunk)] = chunk
        expected = position + packet['length']
    with wave.open(str(wav_path), 'wb') as out:
        out.setnchannels(info['channels'])
        out.setsampwidth(2)
        out.setframerate(rate)
        out.writeframes(bytes(track))
    return rate, frame_bytes, track


def loudest(track, rate, frame_bytes, begin, end):
    """Peak sample (0..32767) in a time window, to prove audio landed where the scene expects it."""
    import array
    window = array.array('h', bytes(track[int(begin * rate) * frame_bytes:int(end * rate) * frame_bytes]))
    return max((abs(v) for v in window), default=0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('takes', type=Path, nargs='+', help='one or more take folders, in story order')
    args = parser.parse_args()
    kit = Path(__file__).resolve().parents[2]
    offset = 0.0
    marks, taps, console, clocks, release = {}, [], [], [], []
    videos, tracks, first = [], [], None
    for folder in args.takes:
        take = folder.expanduser()
        meta = json.loads((take / 'screen.json').read_text())
        events = json.loads((take / 'events.json').read_text())
        if meta.get('frames', 0) < 30:
            raise RuntimeError(f'Recording is incomplete: {take}')
        first = first or meta
        seconds = lambda event, m=meta, o=offset: round(o + (event['t'] - m['startedAtMs']) / 1000, 4)
        # A later take re-films a moment, so its marks replace any earlier ones of the same name.
        marks.update({e['name']: seconds(e) for e in events if e['kind'] == 'mark'})
        taps += [{'sec': seconds(e), 'x': e['x'], 'y': e['y'], 'text': e['text']} for e in events if e['kind'] == 'tap']
        console += [{**{k: v for k, v in e.items() if k not in ('t', 'kind')}, 'sec': seconds(e)} for e in events if e['kind'] == 'console']
        clocks += [e for e in events if e['kind'] == 'clock']
        release += [e for e in events if e['kind'] == 'release']
        videos.append(take / 'screen.mp4')
        if (take / 'audio.json').exists():
            wav = take / 'audio.wav'
            tracks.append(place_audio(take, meta, wav) + (wav,))
        offset += meta['frames'] / meta['fps']
    frames = round(offset * first['fps'])
    normalized = {
        'fps': first['fps'], 'width': first['width'], 'height': first['height'], 'frames': frames,
        'takes': [folder.name for folder in args.takes],  # names only; no personal paths in Git 'marks': marks, 'taps': taps, 'console': console,
        'clocks': clocks, 'release': release,
    }
    if 'end' not in marks:
        raise RuntimeError('Story did not complete')
    (kit / 'public/rec').mkdir(parents=True, exist_ok=True)
    work = kit / 'out/rec'
    work.mkdir(parents=True, exist_ok=True)
    (work / 'videos.txt').write_text(''.join(f"file '{v}'\n" for v in videos))
    if len(tracks) == len(videos):
        rate, frame_bytes = tracks[0][0], tracks[0][1]
        joined = bytearray().join(track for _, _, track, _ in tracks)
        with wave.open(str(work / 'audio.wav'), 'wb') as out:
            out.setnchannels(frame_bytes // 2)
            out.setsampwidth(2)
            out.setframerate(rate)
            out.writeframes(bytes(joined))
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', str(work / 'videos.txt'),
                        '-i', str(work / 'audio.wav'), '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
                        '-shortest', str(kit / 'public/rec/story.mp4')], check=True)
        names = sorted(marks, key=marks.get)
        normalized['audioPeaks'] = {name: loudest(joined, rate, frame_bytes, marks[name], marks[later])
                                    for name, later in zip(names, names[1:])}
        print('audio peaks per mark window:', json.dumps(normalized['audioPeaks'], ensure_ascii=False))
    else:
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', str(work / 'videos.txt'),
                        '-c', 'copy', str(kit / 'public/rec/story.mp4')], check=True)
    (kit / 'src/data/story.json').write_text(json.dumps(normalized, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(marks, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

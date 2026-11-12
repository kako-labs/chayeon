"""Normalize the recording clock and keep a seek-friendly source without re-recording."""
import argparse
import json
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('take', type=Path)
    args = parser.parse_args()
    kit = Path(__file__).resolve().parents[2]
    take = args.take.expanduser()
    meta = json.loads((take / 'screen.json').read_text())
    events = json.loads((take / 'events.json').read_text())
    if meta.get('frames', 0) < 30:
        raise RuntimeError('Recording is incomplete')
    seconds = lambda event: round((event['t'] - meta['startedAtMs']) / 1000, 4)
    normalized = {
        'fps': meta['fps'], 'width': meta['width'], 'height': meta['height'],
        'frames': meta['frames'], 'received': meta['received'],
        'marks': {e['name']: seconds(e) for e in events if e['kind'] == 'mark'},
        'taps': [{'sec': seconds(e), 'x': e['x'], 'y': e['y'], 'text': e['text']} for e in events if e['kind'] == 'tap'],
        'console': [{**{k: v for k, v in e.items() if k not in ('t', 'kind')}, 'sec': seconds(e)} for e in events if e['kind'] == 'console'],
        'clocks': [e for e in events if e['kind'] == 'clock'],
        'release': [e for e in events if e['kind'] == 'release'],
    }
    if 'end' not in normalized['marks']:
        raise RuntimeError('Story did not complete')
    (kit / 'src/data/story.json').write_text(json.dumps(normalized, ensure_ascii=False, indent=2) + '\n')
    (kit / 'public/rec').mkdir(parents=True, exist_ok=True)
    shutil.copyfile(take / 'screen.mp4', kit / 'public/rec/story.mp4')
    print(json.dumps(normalized['marks'], ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

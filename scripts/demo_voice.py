#!/usr/bin/env python3
"""Regenerate the bundled demo-partner call clips.

Fetches each line from the ElevenLabs text-to-speech API into a local cache (skipped when the
cached file exists), then trims silence, normalizes loudness and writes the app resources.
The API key is read only from ELEVENLABS_API_KEY and never printed or stored in outputs.

Usage: python3 scripts/demo_voice.py [--cache build/demo-voice-cache]
Requires curl and ffmpeg.
"""
import argparse
import json
import os
import subprocess
import tempfile
from pathlib import Path

MODEL = "eleven_v3"
# Voice library voice "Jjeong - Warm, Calm and Measured" (shared library voice, not a personal clone).
VOICE = "DMkRitQrfpiddSQT5adl"
ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "data/voice/src/main/res/raw"

# Keys must match DemoCallClips in data:voice; text must match DemoCallScripts exactly.
LINES = {
    "star": ["안녕하세요, 별보는밤이에요.", "밤 풍경을 사진에 담는 걸 좋아해요.", "사진에 남기고 싶은 순간이 있나요?"],
    "jazz": ["안녕하세요, 재즈한잔이에요.", "재즈를 들으면 마음이 편안해져요.", "요즘 자주 듣는 곡이 있나요?"],
    "slow": ["안녕하세요, 느린오후예요.", "잔잔한 음악과 커피를 좋아해요.", "어떤 커피를 즐겨 마시나요?"],
    "sea": ["안녕하세요, 바다보러가요라고 해요.", "여행 이야기를 듣는 게 좋아요.", "서핑을 해 본 적이 있나요?"],
    "forest": ["안녕하세요, 숲길산책이에요.", "캠핑하며 쉬는 시간을 좋아해요.", "캠핑할 때 꼭 챙기는 게 있나요?"],
}

# Trim leading/trailing silence, keep a short natural pad, normalize to speech loudness.
FILTER = (
    "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,"
    "areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.12,areverse,"
    "loudnorm=I=-16:TP=-1.5:LRA=11"
)


def fetch(text: str, path: Path) -> None:
    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key:
        raise SystemExit("ELEVENLABS_API_KEY is not set and the clip is not cached: " + path.name)
    body = json.dumps({"text": text, "model_id": MODEL}, ensure_ascii=False)
    with tempfile.NamedTemporaryFile("w", delete=False, suffix=".h") as header:
        header.write("xi-api-key: " + key + "\n")
        header_path = header.name
    try:
        code = subprocess.run(
            ["curl", "-s", "-o", str(path), "-w", "%{http_code}", "-X", "POST",
             f"https://api.elevenlabs.io/v1/text-to-speech/{VOICE}?output_format=mp3_44100_64",
             "-H", "@" + header_path, "-H", "Content-Type: application/json", "--data-binary", body],
            capture_output=True, text=True, check=True).stdout
    finally:
        os.unlink(header_path)
    if code != "200":
        path.unlink(missing_ok=True)
        raise SystemExit(f"text-to-speech failed for {path.name}: HTTP {code}")


def process(src: Path, dst: Path) -> None:
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(src), "-af", FILTER,
                    "-ar", "44100", "-ac", "1", "-c:a", "libmp3lame", "-b:a", "64k",
                    "-map_metadata", "-1", "-id3v2_version", "0", "-write_xing", "0", str(dst)], check=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", default=str(ROOT / "build/demo-voice-cache"))
    args = parser.parse_args()
    cache = Path(args.cache)
    cache.mkdir(parents=True, exist_ok=True)
    RES.mkdir(parents=True, exist_ok=True)
    for key, lines in LINES.items():
        for index, text in enumerate(lines, 1):
            name = f"demo_voice_{key}_{index}"
            raw = cache / f"{name}.mp3"
            if not raw.exists():
                fetch(text, raw)
            process(raw, RES / f"{name}.mp3")
            print("wrote", (RES / f"{name}.mp3").relative_to(ROOT))


if __name__ == "__main__":
    main()

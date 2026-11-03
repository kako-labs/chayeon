#!/usr/bin/env python3
"""Portable Chayeon development commands using only the Python standard library."""

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import time
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]


def properties(path):
    return dict(
        line.split("=", 1)
        for line in path.read_text(encoding="utf-8").splitlines()
        if "=" in line and not line.lstrip().startswith("#")
    )


def sdk_path():
    value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    local = ROOT / "local.properties"
    if not value and local.exists():
        value = properties(local).get("sdk.dir", "").replace("\\:", ":").replace("\\\\", "\\")
    if value:
        return Path(value).expanduser()
    candidates = [Path.home() / "Library/Android/sdk", Path.home() / "Android/Sdk"]
    if os.environ.get("LOCALAPPDATA"):
        candidates.append(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
    return next((candidate for candidate in candidates if candidate.is_dir()), None)


def java_home():
    configured = os.environ.get("JAVA_HOME")
    if configured:
        return Path(configured)
    if sys.platform == "darwin":
        result = subprocess.run(
            ["/usr/libexec/java_home", "-v", "17"], capture_output=True, text=True, check=False
        )
        if result.returncode == 0:
            return Path(result.stdout.strip())
    return None


def execution_environment(android=False):
    env = os.environ.copy()
    java = java_home()
    if java:
        env["JAVA_HOME"] = str(java)
    if android:
        sdk = sdk_path()
        if sdk:
            env["ANDROID_HOME"] = str(sdk)
    return env


def doctor(android=True):
    errors = []
    home = java_home()
    java = str(home / "bin" / ("java.exe" if os.name == "nt" else "java")) if home else shutil.which("java")
    if java:
        try:
            result = subprocess.run([java, "-version"], capture_output=True, text=True, check=False)
            if result.returncode or not re.search(r'version "17[.\"]', result.stdout + result.stderr):
                errors.append("JDK 17이 필요해요. JAVA_HOME을 설치한 JDK 17로 설정하세요.")
        except OSError:
            errors.append("Java를 실행할 수 없어요. JDK 17을 설치하세요.")
    else:
        errors.append("Java를 찾지 못했어요. JDK 17을 설치하세요.")
    if android:
        sdk = sdk_path()
        config = properties(ROOT / "config/android-test.properties")
        if not sdk or not sdk.is_dir():
            errors.append("Android SDK가 필요해요. ANDROID_HOME 또는 local.properties의 sdk.dir을 설정하세요.")
        else:
            for relative in ["platforms/android-" + config["compileSdk"], "build-tools/" + config["buildTools"]]:
                if not (sdk / relative).is_dir():
                    errors.append("SDK 패키지가 없어요: " + relative + ". setup-sdk를 실행하세요.")
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        errors.append("Gradle wrapper가 없어요. 저장소 파일을 확인하세요.")
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("준비됐어요: JDK 17, Gradle wrapper" + (", Android SDK 35." if android else "."), flush=True)
    return 0


def gradle(tasks, android=True):
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    return subprocess.call([str(wrapper), *tasks, "--no-daemon"], cwd=ROOT, env=execution_environment(android))


def sdk_tool(folder, name):
    sdk = sdk_path()
    suffix = ".exe" if os.name == "nt" else ""
    candidate = sdk / folder / (name + suffix) if sdk else None
    return str(candidate) if candidate and candidate.is_file() else shutil.which(name)


def setup_sdk():
    sdk = sdk_path()
    executable = "sdkmanager.bat" if os.name == "nt" else "sdkmanager"
    manager = sdk / "cmdline-tools/latest/bin" / executable if sdk else None
    if not manager or not manager.is_file():
        print("SDK Manager에서 Android SDK Command-line Tools (latest)를 먼저 설치하세요.", file=sys.stderr)
        return 1
    config = properties(ROOT / "config/android-test.properties")
    return subprocess.call(
        [str(manager), "platforms;android-" + config["compileSdk"],
         "build-tools;" + config["buildTools"], "platform-tools", "emulator"],
        cwd=ROOT, env=execution_environment(android=True),
    )


def emulator(visible=False):
    executable = sdk_tool("emulator", "emulator")
    if not executable:
        print("emulator를 찾지 못했어요. setup-sdk를 실행하세요.", file=sys.stderr)
        return 1
    flags = ["-avd", "Pleos_Connect_v2_0", "-no-snapshot", "-no-audio", "-no-boot-anim",
             "-gpu", "host", "-netdelay", "none", "-netspeed", "full"]
    if not visible:
        flags.append("-no-window")
    return subprocess.call([executable, *flags], env=execution_environment(android=True))


def console_request(base_url, method, route, **parameters):
    query = "?" + urlencode(parameters) if parameters else ""
    request = Request(base_url.rstrip("/") + route + query, method=method)
    with urlopen(request, timeout=3) as response:
        response.read()


def drive(scenario, base_url, serial="emulator-5556", duration_seconds=None):
    import json
    import math
    adb = sdk_tool("platform-tools", "adb")
    if not adb:
        print("adb를 찾지 못했어요. setup-sdk를 실행하세요.", file=sys.stderr)
        return 1
    fixture = ROOT / "fixtures/drive" / (scenario + ".json")
    if not fixture.is_file():
        print("합성 주행 시나리오를 찾지 못했어요.", file=sys.stderr)
        return 1
    data = json.loads(fixture.read_text(encoding="utf-8"))
    if data.get("source") != "SYNTHETIC" or data.get("schemaVersion") != 1:
        print("합성 주행 형식을 확인하세요.", file=sys.stderr)
        return 1
    packaged = ROOT / "data/vehicle/src/main/assets/drive" / fixture.name
    if packaged.read_bytes() != fixture.read_bytes():
        print("앱과 도구의 합성 주행 파일이 달라요. 같은 파일로 맞추세요.", file=sys.stderr)
        return 1
    duration = duration_seconds if duration_seconds is not None else data["durationMillis"] / 1000
    if not math.isfinite(duration) or duration <= 0:
        print("재생 시간은 0초보다 길어야 해요.", file=sys.stderr)
        return 1
    if subprocess.call([adb, "-s", serial, "forward", "tcp:8088", "tcp:8088"]) != 0:
        return 1
    stopped = threading.Event()
    heartbeat_failed = threading.Event()

    def heartbeat():
        while not stopped.wait(0.7):
            try:
                console_request(base_url, "POST", "/browser/session", client="chayeon-drive")
            except (OSError, URLError):
                heartbeat_failed.set()
                return

    def controls(speed=0, brake=False, steering=0):
        if heartbeat_failed.is_set():
            raise OSError("Virtual Console session expired")
        console_request(base_url, "PUT", "/vehicle/controls", speed=round(speed),
                        accelerator=15 if speed and not brake else 0,
                        brake=20 if brake else 0, steering=round(steering))

    worker = None
    try:
        console_request(base_url, "POST", "/browser/session", client="chayeon-drive")
        worker = threading.Thread(target=heartbeat, daemon=True)
        worker.start()
        controls()
        console_request(base_url, "PUT", "/vehicle/gear", gear="Park")
        if "energy" in data["signals"]:
            console_request(base_url, "PUT", "/vehicle/ev-battery", field="soc", value=65)
        if "chargeLimit" in data["signals"]:
            console_request(base_url, "PUT", "/vehicle/ev-battery", field="percent_limit", value=80)
        if "airCleaning" in data["signals"]:
            console_request(base_url, "PUT", "/vehicle/hvac-air-cleaner", activated="true")
        if "tire" in data["signals"]:
            for area in [1114113, 1114191, 1179649, 1179727]:
                console_request(base_url, "PUT", "/vehicle/tire-pressure", area=area, pressure_kpa=240)
        time.sleep(3)
        console_request(base_url, "PUT", "/vehicle/gear", gear="Drive")
        began = time.monotonic()
        frames = data["frames"]
        previous_indicator = None
        while time.monotonic() - began < duration:
            elapsed = min(data["durationMillis"], (time.monotonic() - began) / duration * data["durationMillis"])
            before = next(frame for frame in reversed(frames) if frame["atMillis"] <= elapsed)
            after = next((frame for frame in frames if frame["atMillis"] > elapsed), before)
            ratio = 0 if before is after else (elapsed - before["atMillis"]) / (after["atMillis"] - before["atMillis"])
            speed = before.get("speedKph", 0) + ratio * (after.get("speedKph", 0) - before.get("speedKph", 0))
            controls(speed, before.get("brake", False), before.get("steering", 0))
            indicator = before.get("indicator", False)
            if "indicator" in data["signals"] and indicator != previous_indicator:
                console_request(base_url, "PUT", "/vehicle/turn-signal", mode="BlinkLeft" if indicator else "Off")
                previous_indicator = indicator
            time.sleep(min(0.5, max(0, duration - (time.monotonic() - began))))
        controls()
        console_request(base_url, "PUT", "/vehicle/turn-signal", mode="Off")
        console_request(base_url, "PUT", "/vehicle/gear", gear="Park")
        console_request(base_url, "GET", "/vehicle/state")
        print("합성 주행 시나리오를 적용했어요. 에뮬레이터에서 만든 신호이며 실차 검증이 아니에요.")
        if scenario == "unsupported":
            print("플랫폼의 신호 지원 여부는 바꾸지 않아요. 신호 누락은 앱의 합성 재생에서 확인해요.")
        return 0
    except (OSError, URLError, KeyboardInterrupt):
        print("합성 주행을 마치지 못했어요. Virtual Console 연결을 확인하세요.", file=sys.stderr)
        return 1
    finally:
        try:
            console_request(base_url, "POST", "/browser/session", client="chayeon-drive")
            console_request(base_url, "PUT", "/vehicle/controls", speed=0, accelerator=0, brake=0, steering=0)
            console_request(base_url, "PUT", "/vehicle/turn-signal", mode="Off")
            console_request(base_url, "PUT", "/vehicle/gear", gear="Park")
        except (OSError, URLError):
            pass
        stopped.set()
        if worker:
            worker.join(timeout=4)


def release():
    result = gradle([":app:assembleRelease"])
    if result:
        return result
    target = ROOT / "apk/chayeon-release-unsigned.apk"
    target.parent.mkdir(exist_ok=True)
    shutil.copy2(ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk", target)
    print("apk/chayeon-release-unsigned.apk를 만들었어요. 제출용 서명은 배포 담당자가 진행하세요.")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ["doctor", "setup-sdk", "verify", "server", "e2e", "release"]:
        commands.add_parser(command)
    emulator_parser = commands.add_parser("emulator")
    emulator_parser.add_argument("--visible", action="store_true")
    drive_parser = commands.add_parser("drive")
    drive_parser.add_argument("scenario", choices=["gentle", "early-brake", "signal-manners", "care", "unsupported"])
    drive_parser.add_argument("--console-url", default="http://127.0.0.1:8088")
    drive_parser.add_argument("--serial", default="emulator-5556")
    drive_parser.add_argument("--duration-seconds", type=float)
    args = parser.parse_args()
    if args.command == "doctor":
        return doctor()
    if args.command == "setup-sdk":
        return setup_sdk()
    if args.command == "e2e":
        print("종단 검증은 아직 구현되지 않았어요. 기능·차량 작업자가 Pleos 시나리오를 연결한 뒤 실행하세요.", file=sys.stderr)
        return 2
    if args.command == "drive":
        return drive(args.scenario, args.console_url, args.serial, args.duration_seconds)
    if args.command == "emulator":
        return emulator(args.visible)
    if doctor(android=args.command != "server"):
        return 1
    if args.command == "server":
        return gradle(["-Pchayeon.serverOnly=true", ":server:run"], android=False)
    if args.command == "release":
        return release()
    return gradle(["verifyAll"])


if __name__ == "__main__":
    sys.exit(main())

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


def drive(scenario, base_url):
    adb = sdk_tool("platform-tools", "adb")
    if not adb:
        print("adb를 찾지 못했어요. setup-sdk를 실행하세요.", file=sys.stderr)
        return 1
    if subprocess.call([adb, "forward", "tcp:8088", "tcp:8088"]) != 0:
        return 1
    stopped = threading.Event()
    heartbeat_failed = threading.Event()

    def heartbeat():
        while not stopped.wait(0.7):
            try:
                console_request(base_url, "POST", "/browser/session", client="chayeon")
            except (OSError, URLError):
                heartbeat_failed.set()
                return

    def controls(speed=0, accelerator=0, brake=0, steering=0):
        if heartbeat_failed.is_set():
            raise OSError("Virtual Console session expired")
        console_request(base_url, "PUT", "/vehicle/controls", speed=speed,
                        accelerator=accelerator, brake=brake, steering=steering)

    worker = None
    try:
        console_request(base_url, "POST", "/browser/session", client="chayeon")
        worker = threading.Thread(target=heartbeat, daemon=True)
        worker.start()
        gear = {"park": "Park", "neutral": "Neutral", "reverse": "Reverse"}.get(scenario, "Drive")
        controls()
        console_request(base_url, "PUT", "/vehicle/gear", gear=gear)
        if scenario in {"gentle", "stop-and-go"}:
            profile = [0, 5, 10, 15, 20, 25, 30, 30, 25, 20, 15, 10, 5, 0]
            if scenario == "stop-and-go":
                profile = [0, 10, 20, 10, 0, 0, 10, 20, 10, 0]
            for speed in profile:
                controls(speed=speed, accelerator=10 if speed else 0, brake=0)
                time.sleep(1)
            controls()
            console_request(base_url, "PUT", "/vehicle/gear", gear="Park")
        print("합성 주행 시나리오를 적용했어요. 실제 차량 데이터가 아니에요.")
        return 0
    except (OSError, URLError):
        print("Virtual Console에 연결하지 못했어요. Pleos 에뮬레이터와 콘솔 상태를 확인하세요.", file=sys.stderr)
        return 1
    finally:
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
    drive_parser.add_argument("scenario", choices=["park", "drive", "neutral", "reverse", "gentle", "stop-and-go"])
    drive_parser.add_argument("--console-url", default="http://127.0.0.1:8088")
    args = parser.parse_args()
    if args.command == "doctor":
        return doctor()
    if args.command == "setup-sdk":
        return setup_sdk()
    if args.command == "e2e":
        print("종단 검증은 아직 구현되지 않았어요. 기능·차량 작업자가 Pleos 시나리오를 연결한 뒤 실행하세요.", file=sys.stderr)
        return 2
    if args.command == "drive":
        return drive(args.scenario, args.console_url)
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

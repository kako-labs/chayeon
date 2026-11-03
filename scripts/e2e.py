#!/usr/bin/env python3
"""On-device acceptance proof. Never starts or stops the shared emulator."""

import contextlib
import hashlib
import json
from pathlib import Path
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

try:
    from . import dev
except ImportError:
    import dev


class Device:
    def __init__(self, serial="emulator-5556", output="build/e2e"):
        self.adb = dev.sdk_tool("platform-tools", "adb")
        if not self.adb:
            raise RuntimeError("adb를 찾지 못했어요")
        self.serial = serial
        self.output = dev.ROOT / output
        self.output.mkdir(parents=True, exist_ok=True)
        self.steps = []

    def run(self, *args, binary=False, check=True, timeout=35):
        return subprocess.run([self.adb, "-s", self.serial, *args], check=check, capture_output=True,
                              text=not binary, timeout=timeout).stdout

    def shell(self, *args, **kwargs):
        return self.run("shell", *args, **kwargs)

    def launch(self):
        self.shell("am", "start", "--user", "10", "-n", "dev.chayeon/.MainActivity")

    def tree(self):
        output = self.shell("uiautomator", "dump", "--compressed", "/sdcard/chayeon-window.xml")
        if "dumped" not in output:
            raise RuntimeError("화면 구조를 읽지 못했어요: " + output)
        return ET.fromstring(self.shell("cat", "/sdcard/chayeon-window.xml"))

    @staticmethod
    def labels(tree):
        return [node.get("text") or node.get("content-desc") for node in tree.iter("node")
                if node.get("text") or node.get("content-desc")]

    def find(self, label, tree=None, exact=True):
        if tree is None:
            tree = self.tree()
        for node in tree.iter("node"):
            values = [node.get("text", ""), node.get("content-desc", "")]
            if any(value == label if exact else label in value for value in values):
                return node
        return None

    def wait(self, label, timeout=30, exact=True):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            try:
                tree = self.tree()
                node = self.find(label, tree, exact)
                if node is not None:
                    return node
            except (subprocess.TimeoutExpired, subprocess.CalledProcessError, RuntimeError, ET.ParseError):
                pass
            time.sleep(0.35)
        self.capture("failure")
        raise AssertionError("화면에서 찾지 못했어요: " + label + " / " + str(self.labels(self.tree())))

    def confirm(self, label):
        """Tap the action inside the confirmation sheet, drawn above an identically named button."""
        time.sleep(0.8)
        self.tap(label, last=True)

    def tap(self, label, exact=True, scroll=False, last=False):
        for attempt in range(8):
            tree = self.tree()
            parents = {child: parent for parent in tree.iter() for child in parent}
            node = None
            matches = []
            candidates = list(tree.iter("node"))
            if last:
                candidates.reverse()
            for candidate in candidates:
                values = [candidate.get("text", ""), candidate.get("content-desc", "")]
                if not any(value == label if exact else label in value for value in values):
                    continue
                matches.append(candidate)
                target = candidate
                while target is not None and target.get("clickable") != "true":
                    target = parents.get(target)
                if target is not None and target.get("enabled") == "true":
                    node = target
                    break
            if node is None and len(matches) == 1 and matches[0].get("enabled") == "true":
                node = matches[0]
            if node is not None:
                values = list(map(int, re.findall(r"\d+", node.get("bounds"))))
                x1, y1, x2, y2 = values
                if x2 > x1 and y2 > y1:
                    self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                    time.sleep(0.4)
                    return
            if scroll and attempt > 0:
                self.shell("input", "swipe", "2250", "1120", "2250", "470", "350")
            time.sleep(0.4)
        raise AssertionError("누를 항목을 찾지 못했어요: " + label)

    def card(self, title, button="프로필 보기"):
        for _ in range(7):
            tree = self.tree()
            node = self.find(title, tree)
            if node is not None:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                parents = {child: parent for parent in tree.iter() for child in parent}
                target = node
                while target is not None and target.get("clickable") != "true":
                    target = parents.get(target)
                if target is not None and target.get("enabled") == "true":
                    self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                    return
                center = (x1 + x2) // 2
                choices = []
                for target in tree.iter("node"):
                    if target.get("text") != button:
                        continue
                    bx1, by1, bx2, by2 = map(int, re.findall(r"\d+", target.get("bounds")))
                    if abs((bx1 + bx2) // 2 - center) < 300 and by1 > y2:
                        choices.append((by1 - y2, (bx1 + bx2) // 2, (by1 + by2) // 2))
                if choices:
                    _, x, y = min(choices)
                    self.shell("input", "tap", str(x), str(y))
                    return
            self.shell("input", "swipe", "2250", "1120", "2250", "470", "350")
        raise AssertionError("카드를 찾지 못했어요: " + title)

    def permissions(self):
        for index in range(4):
            tree = self.tree()
            if self.find("허용", tree) is None:
                return
            self.capture("permission-" + str(len(self.steps)))
            self.tap("허용")

    def trip_summary(self):
        import sqlite3
        import tempfile
        with tempfile.TemporaryDirectory(prefix="chayeon-derived-") as folder:
            path = Path(folder) / "signature.db"
            contents = self.run("exec-out", "run-as", "dev.chayeon", "--user", "10", "cat", "databases/drive-signature.db", binary=True)
            if not contents.startswith(b"SQLite format 3"):
                raise AssertionError("앱의 파생 지표 저장소를 읽지 못했어요")
            path.write_bytes(contents)
            with sqlite3.connect(path) as database:
                trips = [json.loads(row[0]) for row in database.execute("SELECT derived_metrics FROM trips")]
            return {"rows": len(trips), "vehicleRows": sum(t["source"] == "VEHICLE" for t in trips),
                    "validVehicleRows": sum(t["source"] == "VEHICLE" and t["eligibility"] == "ELIGIBLE" and t["duration"] >= 180000 for t in trips)}

    def capture(self, name, contains=()):
        tree = self.tree()
        for label in contains:
            if self.find(label, tree, exact=False) is None:
                raise AssertionError(name + ": 화면 검증 실패: " + label)
        (self.output / (name + ".xml")).write_text(ET.tostring(tree, encoding="unicode"), encoding="utf-8")
        (self.output / (name + ".png")).write_bytes(self.run("exec-out", "screencap", "-p", binary=True))
        result = "failed" if name == "failure" else "passed"
        self.steps.append({"step": name, "asserted": list(contains), "result": result})
        (self.output / "results.json").write_text(json.dumps(self.steps, ensure_ascii=False, indent=2), encoding="utf-8")
        print(("FAIL " if result == "failed" else "PASS ") + name + ": " + ", ".join(contains), flush=True)


class Console:
    def __init__(self, device):
        self.device = device
        self.stop = threading.Event()
        self.failure = None
        self.worker = None

    def request(self, method, route, **parameters):
        dev.console_request("http://127.0.0.1:8088", method, route, **parameters)

    def __enter__(self):
        self.device.run("forward", "tcp:8088", "tcp:8088")
        self.request("POST", "/browser/session", client="chayeon-e2e")

        def keepalive():
            while not self.stop.wait(0.8):
                try:
                    self.request("POST", "/browser/session", client="chayeon-e2e")
                except Exception as error:
                    self.failure = error
                    return
        self.worker = threading.Thread(target=keepalive, daemon=True)
        self.worker.start()
        self.gear("Park")
        return self

    def gear(self, gear, speed=0):
        if self.failure:
            raise RuntimeError("Virtual Console 연결이 끊어졌어요") from self.failure
        self.request("PUT", "/vehicle/controls", speed=speed, accelerator=15 if speed else 0, brake=0, steering=0)
        self.request("PUT", "/vehicle/gear", gear=gear)
        time.sleep(1)

    def __exit__(self, *args):
        with contextlib.suppress(Exception):
            self.gear("Park")
        self.stop.set()
        self.worker.join(timeout=4)


def assert_locked(device, hidden_peers=()):
    tree = device.tree()
    labels = [node.get("text", "") for node in tree.iter("node") if node.get("package") == "dev.chayeon"]
    if any(re.search(r"[0-9]", text) for text in labels):
        raise AssertionError("주행 제한 화면에 숫자가 있어요")
    app_labels = [value for node in tree.iter("node") if node.get("package") == "dev.chayeon"
                  for value in (node.get("text", ""), node.get("content-desc", ""))]
    if any(peer in value for peer in hidden_peers for value in app_labels):
        raise AssertionError("주행 중 성립한 연결의 상대 신원이 보이고 있어요")
    actions = [node for node in tree.iter("node") if node.get("package") == "dev.chayeon" and node.get("clickable") == "true" and node.get("enabled") == "true"]
    for node in actions:
        if device.find("나가기", node) is None:
            raise AssertionError("주행 중 나가기 외 조작이 열려 있어요")


def enter_solo(device, prefix=""):
    device.launch()
    device.wait("이 계정으로 시작")
    device.capture(prefix + "01-first-run", ["혼자 둘러보기(가상 상대)", "주차 중"])
    device.tap("이 계정으로 시작")
    device.wait("저장하고 다음")
    device.capture(prefix + "02-profile", ["프로필 만들기", "가상 프로필"])
    device.tap("저장하고 다음", scroll=True)
    device.wait("차량 데이터·위치·공개 범위")
    device.capture(prefix + "03-consent", ["차량 데이터·위치·공개 범위"])
    device.tap("성인이고, 직접 참여해요")
    device.tap("차량 데이터 사용에 동의해요")
    device.permissions()
    device.tap("위치 사용에 동의해요")
    device.tap("프로필 공개에 동의해요")
    device.permissions()
    device.tap("선택한 설정으로 시작")
    device.wait("내 시그니처 만들기")


def create_signature(device, choice, prefix=""):
    device.tap("발견 켜기")
    device.wait("시연용 가상 상대", exact=False)
    tree = device.tree()
    invitation = device.find("내 시그니처 만들기", tree)
    candidate = device.find("느린오후", tree)
    if invitation is None or candidate is None:
        raise AssertionError("시그니처 초대와 후보가 홈에 함께 보이지 않아요")
    if int(re.findall(r"\d+", invitation.get("bounds"))[1]) >= int(re.findall(r"\d+", candidate.get("bounds"))[1]):
        raise AssertionError("시그니처 초대가 후보 위에 있지 않아요")
    if any("잘 맞는 점" in label for label in device.labels(tree)):
        raise AssertionError("내 시그니처를 공개하기 전에 궁합이 보여요")
    device.capture(prefix + "04-home-invitation", ["내 시그니처 만들기", "시연용 가상 상대"])
    device.tap("시그니처 만들러 가기")
    device.wait("합성 주행으로 먼저 만나보기")
    device.tap("합성 주행으로 먼저 만나보기")
    device.wait("어떤 주행이 나를 닮았나요?")
    device.capture(prefix + "04-demo", ["시연 제어", "합성 주행", "기어는 차량의 Virtual Console"])
    began = time.monotonic()
    device.tap(choice)
    device.wait("합성 주행 2/3", timeout=25)
    device.capture(prefix + "04-live-progress", ["합성 주행 2/3"])
    device.wait("내 주행이 매듭이 됐어요", timeout=40)
    device.wait("상대에게 어떻게 보일지 고르기")
    if time.monotonic() - began >= 60:
        raise AssertionError("합성 주행 공개에 1분 이상 걸렸어요")
    titles = [label for label in device.labels(device.tree()) if label.endswith("드라이버")]
    if len(titles) != 1:
        raise AssertionError("첫 시그니처 칭호가 하나가 아니에요: " + str(titles))
    title = titles[0]
    device.capture(prefix + "05-first-signature", ["내 주행이 매듭이 됐어요", title, "합성 주행"])
    device.tap("상대에게 어떻게 보일지 고르기")
    device.wait("공개 범위")
    device.capture(prefix + "05-private-preview", ["나만 간직해요", "상대에게 이렇게 보여요"])
    device.tap("시그니처 공개")
    device.wait("고른 특성이 상대에게 보여요.")
    device.capture(prefix + "05-public-preview", ["상대에게 이렇게 보여요", title, "합성 주행"])
    device.tap("오늘의 인연")
    device.wait("잘 맞는 점", exact=False)
    if device.find("내 시그니처 만들기", device.tree()) is not None:
        raise AssertionError("공개 후에도 시그니처 초대가 남아 있어요")
    device.capture(prefix + "06-cast", ["시연용 가상 상대", "잘 맞는 점", "드라이버"])
    return title


def run(with_real_drive=False, skip_build=False, apk=None):
    device = Device(output="build/e2e/release" if apk else "build/e2e")
    if not skip_build and dev.gradle([":app:assembleDebug", "-Pchayeon.demo=true"]):
        return 1
    debug_proof = apk is None
    apk = Path(apk) if apk else dev.ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    metadata = {
        "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "withRealDrive": with_real_drive,
        "normalInstall": True, "serial": device.serial, "user": 10,
    }
    packages = device.shell("pm", "list", "packages", "--user", "10").splitlines()
    for package in packages:
        name = package.removeprefix("package:")
        if name.startswith("dev.chayeon") and name.endswith(".signatureproof"):
            device.run("uninstall", name)
    with Console(device) as console:
        device.run("uninstall", "dev.chayeon", check=False)
        device.run("install", "--user", "10", str(apk))
        installed_path = device.shell("pm", "path", "--user", "10", "dev.chayeon").strip().removeprefix("package:")
        installed_hash = hashlib.sha256(device.run("exec-out", "cat", installed_path, binary=True)).hexdigest()
        metadata["installedApkSha256"] = installed_hash
        metadata["installedApkMatches"] = installed_hash == metadata["apkSha256"]
        if not metadata["installedApkMatches"]:
            raise AssertionError("설치된 APK와 입력 파일의 SHA-256이 달라요")
        (device.output / "run-metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        enter_solo(device)
        first_title = create_signature(device, "다음 움직임을 알려 주는 사람")
        device.card("별보는밤")
        device.wait("연결 요청 보내기")
        device.capture("07-candidate", ["별보는밤", "함께하면 잘 맞는 점", "시그니처 매듭", "합성 주행", "한 분은 편안하게 달리고, 한 분은 함께 타는 공기까지 신경 써요"])
        device.tap("연결 요청 보내기")
        console.gear("Drive")
        device.wait("나가기")
        assert_locked(device, hidden_peers=("별보는밤", "새벽드라이브"))
        device.capture("07-formed-while-driving", ["주행 중", "나가기"])
        console.gear("Park")
        device.wait("주고받은 인사")
        device.capture("08-connected", ["별보는밤 님과 이어져 있어요", "주고받은 인사"])
        device.tap("안녕하세요", exact=False)
        device.wait("보냈어요", exact=False)
        device.capture("09-greetings", ["받았어요", "보냈어요", "실이 이어졌어요"])
        console.gear("Drive")
        device.wait("주차하면", exact=False)
        device.capture("10-drive-zero-locked", ["주행 중", "나가기"])
        assert_locked(device)
        console.gear("Neutral")
        device.capture("11-neutral-locked", ["주행 중", "나가기"])
        assert_locked(device)
        console.gear("Drive", speed=40)
        device.capture("12-drive-moving-locked", ["주행 중", "나가기"])
        assert_locked(device)
        console.gear("Park")
        device.wait("주고받은 인사")
        device.capture("13-park-resumed", ["주고받은 인사"])
        if debug_proof:
            device.shell("am", "broadcast", "--user", "10", "-n", "dev.chayeon/.VehicleConnectionProofReceiver", "--es", "operation", "disconnect")
            device.wait("주행 상태를 확인하고 있어요")
            device.capture("14-car-disconnected", ["주행 상태를 확인하고 있어요", "나가기"])
            assert_locked(device)
            device.shell("am", "broadcast", "--user", "10", "-n", "dev.chayeon/.VehicleConnectionProofReceiver", "--es", "operation", "reconnect")
            device.wait("주고받은 인사")
        device.tap("시연 제어")
        device.tap("서로 멀어져요")
        device.shell("input", "keyevent", "KEYCODE_BACK")
        device.wait("연결 상태를 확인하고 있어요", timeout=8)
        device.capture("15-checking", ["연결 상태를 확인하고 있어요"])
        device.wait("멀어져서 연결이 끝났어요")
        device.capture("16-distance-ended", ["멀어져서 연결이 끝났어요"])
        device.tap("기록 보기")
        device.wait("애프터 신청")
        device.capture("17-encounter", ["별보는밤", "멀어짐", "애프터 신청"])
        device.tap("애프터 신청")
        device.tap("애프터 신청 보내기")
        device.wait("이어진 인연 보기")
        device.tap("이어진 인연 보기")
        device.wait("서로 이어졌어요")
        device.tap("안녕하세요")
        device.wait("보냈어요")
        reply = "다시 이야기하게 되어 반가워요. 야경 보는 걸 좋아하세요?"
        device.wait(reply)
        device.capture("18-after-message", ["서로 이어졌어요", "보냈어요", reply])
        device.tap("차단·신고", scroll=True)
        device.tap("별보는밤 님 차단")
        device.confirm("별보는밤 님 차단")
        device.wait("차단했어요")
        device.capture("19-blocked", ["차단했어요"])
        device.tap("뒤로")
        device.wait("이 대화는 닫혔어요")
        device.capture("20-after-closed", ["이 대화는 닫혔어요"])
        device.tap("내 프로필")
        device.tap("시연 제어")
        device.tap("가까이 있어요")
        device.tap("오늘의 인연")
        device.wait("잘 맞는 점", exact=False)
        tree = device.tree()
        if device.find("별보는밤", tree) is not None:
            raise AssertionError("차단한 상대가 발견 화면에 남아 있어요")
        device.capture("21-peer-removed", ["잘 맞는 점", "시연용 가상 상대"])
        if with_real_drive:
            before = device.trip_summary() if debug_proof else None
            print("RUN platform drive: 210 seconds", flush=True)
            if dev.drive("care", "http://127.0.0.1:8088", device.serial, duration_seconds=210):
                raise AssertionError("실제 플랫폼 주행 입력에 실패했어요")
            device.wait("주행 후 요약")
            device.capture("22-vehicle-recap", ["주행 후 요약", "차량 주행"])
            if debug_proof:
                after = device.trip_summary()
                if after["validVehicleRows"] != before["validVehicleRows"] + 1:
                    raise AssertionError("새 유효 차량 주행이 한 건 저장되지 않았어요")
                (device.output / "drive-proof.json").write_text(json.dumps({"before": before, "after": after}, indent=2), encoding="utf-8")
                print("PASS derived vehicle trip: new eligible row, duration >= 180 seconds", flush=True)
            device.tap("내 시그니처 보기")
        finish_session(device, debug_proof)
        device.run("uninstall", "dev.chayeon")
        device.run("install", "--user", "10", str(apk))
        enter_solo(device, "second-")
        second_title = create_signature(device, "편안하게 달리는 사람", "second-")
        if first_title == second_title:
            raise AssertionError("다른 주행 시나리오가 같은 시그니처 칭호를 만들었어요")
        metadata["scenarioTitles"] = {"SIGNAL_MANNERS": first_title, "GENTLE": second_title}
        (device.output / "run-metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
        print("PASS distinct fresh-install scenarios: " + first_title + " / " + second_title, flush=True)
        return 0


def finish_session(device, debug_proof):
    device.shell("am", "force-stop", "--user", "10", "dev.chayeon")
    device.launch()
    device.wait("내 프로필")
    device.tap("내 프로필")
    device.wait("합성 주행")
    device.capture("23-profile-restored", ["새벽드라이브", "합성 주행"])
    device.tap("스친 인연")
    device.wait("스친 인연")
    device.capture("24-records-restored", ["멀어짐"])
    device.tap("내 프로필")
    device.tap("기록과 계정 정리")
    device.tap("로그아웃", scroll=True)
    device.confirm("로그아웃")
    device.wait("이 계정으로 시작")
    device.capture("25-signed-out", ["혼자 둘러보기(가상 상대)", "이 계정으로 시작"])
    if debug_proof and device.trip_summary()["rows"] != 0:
        raise AssertionError("로그아웃 뒤 차량 주행 지표가 남아 있어요")
    print("PASS clean install → signature → relationship → driving lock → after → block → restart → sign out", flush=True)
    return 0

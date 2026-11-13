#!/usr/bin/env python3
"""On-device acceptance proof. Never starts or stops the shared emulator."""

import contextlib
import hashlib
import json
from pathlib import Path
import re
import secrets
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

try:
    from . import dev
except ImportError:
    import dev


class Device:
    PERMISSION_ACTIONS = ("앱 사용 중에만 허용", "앱 사용 중에 허용", "허용")

    def __init__(self, serial=None, output="build/e2e"):
        self.adb = dev.sdk_tool("platform-tools", "adb")
        if not self.adb:
            raise RuntimeError("adb를 찾지 못했어요")
        self.serial = dev.emulator_serial(serial)
        self.output = dev.ROOT / output
        self.output.mkdir(parents=True, exist_ok=True)
        self.steps = []
        deadline = time.monotonic() + 120
        while self.shell("getprop", "sys.boot_completed").strip() != "1":
            if time.monotonic() >= deadline:
                raise RuntimeError("에뮬레이터 부팅이 끝나지 않았어요")
            time.sleep(1)

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

    def tap(self, label, exact=True, scroll=False, last=False, description_only=False):
        for attempt in range(8):
            tree = self.tree()
            # Consent writes can trigger a runtime dialog after the explicit permission drain.
            if label not in self.PERMISSION_ACTIONS and any(
                    "permissioncontroller" in node.get("package", "") for node in tree.iter("node")) and any(
                    self.find(action, tree) is not None for action in self.PERMISSION_ACTIONS):
                self.permissions()
                continue
            parents = {child: parent for parent in tree.iter() for child in parent}
            node = None
            matches = []
            candidates = list(tree.iter("node"))
            if last:
                candidates.reverse()
            for candidate in candidates:
                values = [candidate.get("content-desc", "")] if description_only else [candidate.get("text", ""), candidate.get("content-desc", "")]
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
                # Two-column screens scroll each pane on its own, so alternate between the right and left pane.
                x = "2250" if attempt % 2 else "1300"
                self.shell("input", "swipe", x, "1120", x, "470", "350")
            time.sleep(0.4)
        raise AssertionError("누를 항목을 찾지 못했어요: " + label)

    def card(self, title, button="프로필 보기", navigate=True):
        for _ in range(7):
            tree = self.tree()
            node = self.find(title, tree)
            if node is not None:
                if not navigate:
                    return
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
            label = next((value for value in self.PERMISSION_ACTIONS if self.find(value, tree) is not None), None)
            if label is None:
                return
            self.capture("permission-" + str(len(self.steps)))
            self.tap(label)

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
        (self.output / (name + ".xml")).write_text(ET.tostring(tree, encoding="unicode"), encoding="utf-8")
        (self.output / (name + ".png")).write_bytes(self.run("exec-out", "screencap", "-p", binary=True))
        missing = [label for label in contains if self.find(label, tree, exact=False) is None]
        result = "failed" if missing or name == "failure" else "passed"
        self.steps.append({"step": name, "asserted": list(contains), "result": result})
        (self.output / "results.json").write_text(json.dumps(self.steps, ensure_ascii=False, indent=2), encoding="utf-8")
        print(("FAIL " if result == "failed" else "PASS ") + name + ": " + ", ".join(contains), flush=True)
        if missing:
            raise AssertionError(name + ": 화면 검증 실패: " + missing[0])


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
    if "시연 제어" in app_labels:
        raise AssertionError("주행 제한 화면에 시연 제어가 보여요")
    if any(label in app_labels for label in ("이야깃거리", "주고받은 인사", "안녕하세요")):
        raise AssertionError("주행 제한 화면에 이야깃거리나 메시지 조작이 보여요")
    if any(peer in value for peer in hidden_peers for value in app_labels):
        raise AssertionError("주행 중 성립한 연결의 상대 신원이 보이고 있어요")
    actions = [node for node in tree.iter("node") if node.get("package") == "dev.chayeon" and node.get("clickable") == "true" and node.get("enabled") == "true"]
    for node in actions:
        if not any(device.find(label, node) is not None for label in ("통화 끊기", "음소거", "음소거 해제")):
            raise AssertionError("주행 중 통화 조작 외의 기능이 열려 있어요")


def enter_solo(device, prefix=""):
    device.launch()
    device.wait("이 계정으로 시작")
    device.capture(prefix + "01-first-run", ["혼자 둘러보기(가상 상대)", "주차 중", "가상 상대와 편하게 만나봐요", "계정 A", "노을산책"])
    device.tap("혼자 둘러보기(가상 상대)")
    device.tap("이 계정으로 시작")
    device.wait("저장하고 다음")
    device.capture(prefix + "02-profile", ["프로필 만들기"])
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


def assert_solo_surface(device, tree=None):
    tree = tree if tree is not None else device.tree()
    labels = [value for node in tree.iter("node")
              for value in (node.get("text", ""), node.get("content-desc", ""))]
    for hidden in ["시연용 가상 상대", "합성 주행", "합성 위치", "가상 프로필", "가상 집"]:
        if any(hidden in value for value in labels):
            raise AssertionError("SOLO 기본 화면에 출처 안내가 반복돼요: " + hidden)


def assert_demo_icon(device, tree=None):
    tree = tree if tree is not None else device.tree()
    icons = [node for node in tree.iter("node") if node.get("content-desc") == "시연 제어"]
    if len(icons) != 1 or any(node.get("text") == "시연 제어" for node in tree.iter("node")):
        raise AssertionError("상단 시연 제어는 설명이 붙은 아이콘 하나여야 해요")
    parents = {child: parent for parent in tree.iter() for child in parent}
    icon = icons[0]
    while icon is not None and icon.get("clickable") != "true":
        icon = parents.get(icon)
    gear = device.find("주차 중", tree)
    if icon is None or gear is None:
        raise AssertionError("상단의 시연 제어 아이콘을 찾지 못했어요")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", icon.get("bounds")))
    _, gy1, _, gy2 = map(int, re.findall(r"\d+", gear.get("bounds")))
    if not (y1 <= (gy1 + gy2) // 2 <= y2 and y2 - y1 >= 76 and 76 <= x2 - x1 < 220):
        raise AssertionError("시연 제어가 상단의 충분한 크기인 아이콘 버튼이 아니에요")


def assert_no_arrival_banner(device):
    labels = device.labels(device.tree())
    if any("확인하기" in label and ("인사" in label or "요청" in label or "애프터" in label) for label in labels):
        raise AssertionError("열어 본 대화나 요청의 도착 배너가 남아 있어요")


def create_signature(device, choice, prefix=""):
    device.tap("발견 켜기")
    device.wait("별보는밤")
    tree = device.tree()
    invitation = device.find("내 시그니처 만들기", tree)
    candidate = device.find("별보는밤", tree)
    if invitation is None or candidate is None:
        raise AssertionError("시그니처 초대와 후보가 홈에 함께 보이지 않아요")
    if int(re.findall(r"\d+", invitation.get("bounds"))[1]) >= int(re.findall(r"\d+", candidate.get("bounds"))[1]):
        raise AssertionError("시그니처 초대가 후보 위에 있지 않아요")
    if any("잘 맞는 점" in label for label in device.labels(tree)):
        raise AssertionError("내 시그니처를 공개하기 전에 궁합이 보여요")
    care = device.find("공기까지 신경 쓰는 드라이버", tree)
    if care is None or abs(int(re.findall(r"\d+", care.get("bounds"))[1]) - int(re.findall(r"\d+", candidate.get("bounds"))[1])) > 110:
        raise AssertionError("첫 홈 카드 줄에 차량 관리 칭호가 없어요")
    assert_demo_icon(device, tree)
    assert_solo_surface(device, tree)
    device.capture(prefix + "04-home-invitation", ["내 시그니처 만들기", "근처에서 5명이 발견을 켰어요", "공기까지 신경 쓰는 드라이버"])
    # The scheduled incoming row shifts the invitation. Resolve tap bounds after it arrives.
    device.wait("받은 연결 요청 보기", timeout=20)
    device.tap("시그니처 만들러 가기")
    device.wait("어떤 주행이 나를 닮았나요?")
    tree = device.tree()
    for hidden in ["시연 제어", "기어 제어", "합성 위치", "지원하지 않는 신호"]:
        if device.find(hidden, tree) is not None:
            raise AssertionError("시그니처 만들기에 제어 도구가 보여요: " + hidden)
    care = device.find("떠나기 전에 챙기는 사람", tree)
    gentle = device.find("편안하게 달리는 사람", tree)
    if care is None or gentle is None or int(re.findall(r"\d+", care.get("bounds"))[1]) >= int(re.findall(r"\d+", gentle.get("bounds"))[1]):
        raise AssertionError("차량 관리 선택지가 첫 번째가 아니에요")
    device.capture(prefix + "04-demo", ["어떤 주행이 나를 닮았나요?", "떠나기 전에 챙기는 사람"])
    began = time.monotonic()
    device.tap(choice)
    device.wait("주행 2/3", timeout=25)
    device.capture(prefix + "04-live-progress", ["주행 2/3"])
    device.wait("이 주행이 매듭이 됐어요", timeout=40)
    device.wait("상대에게 어떻게 보일지 고르기")
    if time.monotonic() - began >= 60:
        raise AssertionError("합성 주행 공개에 1분 이상 걸렸어요")
    titles = [label for label in device.labels(device.tree()) if label.endswith("드라이버")]
    if len(titles) != 1:
        raise AssertionError("첫 시그니처 칭호가 하나가 아니에요: " + str(titles))
    title = titles[0]
    expected_title, title_trait = {
        "떠나기 전에 챙기는 사람": ("떠나기 전에 살피는 드라이버", "타이어 관리"),
        "편안하게 달리는 사람": ("함께 타면 편안한 드라이버", "부드러운 주행"),
        "다음 움직임을 알려 주는 사람": ("깜빡이를 잊지 않는 드라이버", "깜빡이 매너"),
    }[choice]
    if title != expected_title:
        raise AssertionError("선택한 주행의 칭호가 아니에요: " + title)
    tree = device.tree()
    care_traits = ["타이어 관리", "미리 채우는 습관", "배터리 아끼기", "소모품 점검", "실내 공기 챙김"]
    if not any(device.find(trait, tree) is not None for trait in care_traits):
        raise AssertionError("첫 시그니처 순간에 차량 관리 특성이 보이지 않아요")
    if device.find("내가 운전하지 않았어요", tree) is not None:
        raise AssertionError("합성 주행에 실제 운전 제외 동작이 보여요")
    device.capture(prefix + "05-first-signature", ["이 주행이 매듭이 됐어요", title, title_trait])
    device.tap("상대에게 어떻게 보일지 고르기")
    device.wait("공개 범위")
    device.capture(prefix + "05-private-preview", ["나만 간직해요", "상대에게 이렇게 보여요"])
    device.tap("시그니처 공개")
    device.wait("고른 특성이 상대에게 보여요.")
    device.capture(prefix + "05-public-preview", ["상대에게 이렇게 보여요", title])
    device.tap("내 프로필")
    device.tap("내 시그니처 보기")
    device.tap(title_trait, scroll=True)
    device.wait("시그니처 근거: 합성 주행")
    device.capture(prefix + "05-evidence-source", ["특성 상세", "시그니처 근거: 합성 주행"])
    device.tap("뒤로")
    device.tap("홈")
    device.wait("잘 맞는 점", exact=False)
    if device.find("내 시그니처 만들기", device.tree()) is not None:
        raise AssertionError("공개 후에도 시그니처 초대가 남아 있어요")
    assert_solo_surface(device)
    device.capture(prefix + "06-cast", ["별보는밤", "잘 맞는 점", "드라이버"])
    return title


def set_synthetic_home(device, server=False):
    # Server mode keeps the synthetic-source wording; SOLO uses the plain label.
    device.tap("내 프로필")
    device.tap("집 보호 구역", scroll=True)
    device.wait("지금 위치를 집으로 정하기")
    device.tap("합성 위치로 집 정하기" if server else "집 보호 시작하기", scroll=True)
    device.wait("집 근처에서는 쉬고 있어요")
    device.capture("home-zone-set", ["집 근처에서는 쉬고 있어요", "500m"])
    device.tap("홈")
    assert_home_rest(device)


def assert_home_rest(device):
    device.wait("쉬는 중")
    tree = device.tree()
    if any(device.find(label, tree) is not None for label in ["프로필 보기", "지금 연결할 수 있는 사람"]):
        raise AssertionError("집 보호 중 후보가 보여요")


def home_position(device, inside, external=False):
    if external:
        device.shell("am", "broadcast", "--user", "10", "-n", "dev.chayeon/.VehicleConnectionProofReceiver",
                     "--es", "operation", "synthetic-home-inside" if inside else "synthetic-home-outside")
    else:
        device.tap("시연 제어", description_only=True)
        device.tap("집 근처로 오기" if inside else "집에서 멀어지기", scroll=True)
        device.tap("홈")
    if not inside:
        device.wait("별보는밤", timeout=20)


def prove_home_zone(device, console, include_driving=True):
    set_synthetic_home(device)
    assert_home_rest(device)
    device.capture("home-zone-presence-guard", ["집 근처에서는 쉬고 있어요", "벗어나면 다시 찾을 수 있어요", "쉬는 중"])
    for driving in ((False, True) if include_driving else (False,)):
        home_position(device, False)
        device.card("별보는밤")
        device.wait("연결 요청 보내기")
        device.tap("연결 요청 보내기")
        device.wait("통화 중")
        if driving:
            console.gear("Drive")
            device.wait("주행 중", exact=False)
            device.shell("input", "keyevent", "KEYCODE_SLEEP")
            home_position(device, True, external=True)
            time.sleep(12)
            device.shell("input", "keyevent", "KEYCODE_WAKEUP")
            device.launch()
            device.wait("쉬는 중")
            assert_locked(device, hidden_peers=("별보는밤", "새벽드라이브"))
            device.capture("home-zone-arrival-driving", ["쉬는 중", "주차하면"])
            console.gear("Park")
        else:
            device.tap("시연 제어", description_only=True)
            device.tap("집 근처로 오기", scroll=True)
            device.shell("input", "keyevent", "KEYCODE_BACK")
        device.wait("서로 멀어져서 통화가 끝났어요")
        if device.find("집", device.tree(), exact=False) is not None:
            raise AssertionError("연결 종료 화면에 집 사유가 보여요")
        device.capture("home-zone-ended-" + ("driving" if driving else "parked"), ["서로 멀어져서 통화가 끝났어요"])
        device.tap("오늘의 인연으로")
        assert_home_rest(device)
    home_position(device, False)
    device.capture("home-zone-return", ["별보는밤", "발견 중"])


def prove_platform_home(device, console):
    stop = threading.Event()
    geo_ok = []
    position = ["127", "37"]
    def feed():
        while not stop.is_set():
            result = device.run("emu", "geo", "fix", *position, check=False)
            geo_ok.append("OK" in result)
            stop.wait(1)
    worker = threading.Thread(target=feed, daemon=True)
    worker.start()
    try:
        # Seed the provider before requesting a fix, so the emulator's default position cannot win.
        time.sleep(2)
        device.tap("내 프로필")
        device.tap("집 보호 구역", scroll=True)
        device.tap("지금 위치를 집으로 정하기", scroll=True)
        device.permissions()
        time.sleep(10)
        tree = device.tree()
        actual = device.find("집 근처에서는 쉬고 있어요", tree) is not None
        if not actual:
            device.tap("지금 위치를 집으로 정하기", scroll=True)
            time.sleep(10)
            actual = device.find("집 근처에서는 쉬고 있어요", device.tree()) is not None
        packages = device.shell("dumpsys", "package", "dev.chayeon")
        location_dump = device.shell("dumpsys", "location")
        proof = {
            "normalInstall": True,
            "geoCommandAccepted": any(geo_ok),
            "finePermissionGranted": "android.permission.ACCESS_FINE_LOCATION: granted=true" in packages,
            "providerNames": sorted(set(re.findall(r"(?:^|\n)\s*(gps|network|fused|passive) provider", location_dump))),
            "deviceHomeSet": actual,
            "geoFeedStartedBeforeSet": True,
        }
        (device.output / "location-proof.json").write_text(json.dumps(proof, ensure_ascii=False, indent=2), encoding="utf-8")
        if actual:
            device.capture("home-zone-device-position", ["집 근처에서는 쉬고 있어요"])
            console.gear("Drive")
            assert_locked(device)
            device.shell("input", "keyevent", "KEYCODE_SLEEP")
            position[:] = ["127.03", "37.03"]
            print("RUN platform home: screen off beyond the maximum exit delay", flush=True)
            time.sleep(95)
            device.shell("input", "keyevent", "KEYCODE_WAKEUP")
            device.launch()
            assert_locked(device)
            console.gear("Park")
            device.wait("집 보호 구역 밖이에요", timeout=8)
            position[:] = ["127", "37"]
            console.gear("Drive")
            device.shell("input", "keyevent", "KEYCODE_SLEEP")
            time.sleep(12)
            device.shell("input", "keyevent", "KEYCODE_WAKEUP")
            device.launch()
            device.wait("쉬는 중", timeout=15)
            assert_locked(device)
            device.capture("home-zone-device-driving", ["쉬는 중"])
            console.gear("Park")
            device.wait("집 근처에서는 쉬고 있어요")
            proof["outsideAndDrivingArrival"] = True
            proof["screenOffExitAndArrival"] = True
        else:
            device.wait("위치를 정하지 못했어요", exact=False)
            device.capture("home-zone-device-unavailable", ["위치를 정하지 못했어요"])
            proof["limitation"] = "이 이미지에서 일반 설치와 위치 권한 허용 후 geo fix를 입력해도 사용할 수 있는 기기 위치를 받지 못했어요. 합성 위치와 호스트의 가짜 위치 테스트를 사용해요."
        (device.output / "location-proof.json").write_text(json.dumps(proof, ensure_ascii=False, indent=2), encoding="utf-8")
        print("PASS platform location probe: " + ("fresh device fix and driving transition" if actual else "unavailable on this image; synthetic path verified"), flush=True)
    finally:
        stop.set()
        worker.join(timeout=5)
        console.gear("Park")


def home_record_count(device):
    result = device.shell("run-as", "dev.chayeon", "--user", "10", "ls", "databases")
    if "drive-signature.db" not in result:
        return 0
    import sqlite3
    import tempfile
    with tempfile.TemporaryDirectory(prefix="chayeon-private-proof-") as folder:
        path = Path(folder) / "private.db"
        path.write_bytes(device.run("exec-out", "run-as", "dev.chayeon", "--user", "10", "cat", "databases/drive-signature.db", binary=True))
        with sqlite3.connect(path) as db:
            return db.execute("SELECT COUNT(*) FROM home_zone").fetchone()[0]


def run(with_real_drive=False, skip_build=False, apk=None, serial=None):
    device = Device(serial=serial, output="build/e2e/release" if apk else "build/e2e")
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
        assert_solo_surface(device)
        device.capture("07-candidate", ["별보는밤", "함께하면 잘 맞는 점", "시그니처 매듭", "둘 다 함께 타는 공기까지 신경 써요"])
        if any("한 분은 편안하게 달리고" in label for label in device.labels(device.tree())):
            raise AssertionError("공유한 실내 공기 특성이 서로 다른 특성으로도 표시돼요")
        device.tap("연결 요청 보내기")
        console.gear("Drive")
        device.wait("통화 끊기")
        device.wait("통화 중")
        assert_locked(device)
        device.capture("07-formed-while-driving", ["주행 중", "통화 끊기"])
        console.gear("Park")
        device.wait("통화 중")
        assert_demo_icon(device)
        assert_no_arrival_banner(device)
        assert_solo_surface(device)
        device.capture("08-connected", ["별보는밤", "통화 중", "음소거", "통화 끊기"])
        device.wait("이야깃거리")
        assert_no_arrival_banner(device)
        device.capture("09-call-topics", ["이야깃거리", "통화 중", "둘 다 함께 타는 공기까지 신경 써요"])
        console.gear("Drive")
        device.wait("통화 중")
        device.tap("음소거")
        device.wait("음소거 중")
        device.capture("10-drive-muted", ["음소거 중", "음소거 해제", "통화 끊기"])
        assert_locked(device)
        device.tap("음소거 해제")
        device.wait("통화 중")
        device.capture("10-drive-zero-locked", ["주행 중", "통화 끊기"])
        assert_locked(device)
        console.gear("Neutral")
        device.capture("11-neutral-locked", ["주행 중", "통화 끊기"])
        assert_locked(device)
        console.gear("Drive", speed=40)
        device.capture("12-drive-moving-locked", ["주행 중", "통화 끊기", "새벽드라이브", "별보는밤"])
        assert_locked(device)
        console.gear("Park")
        device.wait("통화 중")
        device.capture("13-park-resumed", ["통화 중", "이야깃거리"])
        if debug_proof:
            device.shell("am", "broadcast", "--user", "10", "-n", "dev.chayeon/.VehicleConnectionProofReceiver", "--es", "operation", "disconnect")
            device.wait("주행 상태를 확인하고 있어요")
            device.capture("14-car-disconnected", ["주행 상태를 확인하고 있어요", "통화 끊기"])
            assert_locked(device)
            device.shell("am", "broadcast", "--user", "10", "-n", "dev.chayeon/.VehicleConnectionProofReceiver", "--es", "operation", "reconnect")
            device.wait("통화 중")
        device.tap("시연 제어", description_only=True)
        device.tap("서로 멀어져요")
        device.shell("input", "keyevent", "KEYCODE_BACK")
        device.wait("연결 상태를 확인하고 있어요", timeout=8)
        assert_no_arrival_banner(device)
        device.capture("15-checking", ["연결 상태를 확인하고 있어요"])
        device.wait("서로 멀어져서 통화가 끝났어요")
        device.capture("16-distance-ended", ["서로 멀어져서 통화가 끝났어요"])
        device.tap("기록 보기")
        device.wait("애프터 신청")
        device.capture("17-encounter", ["별보는밤", "멀어짐", "애프터 신청"])
        device.tap("애프터 신청")
        enter_instagram_contact(device)
        device.tap("애프터 신청 보내기")
        device.wait("이어진 인연 보기")
        device.tap("이어진 인연 보기")
        device.wait("@byeol-night.drive")
        device.wait("인스타그램 QR 코드")
        assert_no_arrival_banner(device)
        device.capture("18-after-contact", ["인스타그램", "@byeol-night.drive", "인스타그램 QR 코드", "관계 끝내기"])
        console.gear("Drive")
        assert_contacts_hidden(device, ("byeol-night.drive",))
        device.capture("18-after-restricted", ["주차하면"])
        console.gear("Park")
        device.wait("@byeol-night.drive")
        prove_home_zone(device, console, include_driving=debug_proof)
        device.tap("인연")
        device.tap("별보는밤", exact=False, scroll=True)
        device.tap("차단·신고", scroll=True)
        device.tap("별보는밤 님 차단")
        device.confirm("별보는밤 님 차단")
        device.wait("차단했어요")
        device.capture("19-blocked", ["차단했어요"])
        device.tap("뒤로")
        device.wait("이 인연은 더 이상 보이지 않아요")
        device.capture("20-after-closed", ["이 인연은 더 이상 보이지 않아요"])
        assert_contacts_hidden(device, ("byeol-night.drive",))
        device.tap("내 프로필")
        device.tap("홈")
        device.wait("잘 맞는 점", exact=False)
        tree = device.tree()
        if device.find("별보는밤", tree) is not None:
            raise AssertionError("차단한 상대가 발견 화면에 남아 있어요")
        device.capture("21-peer-removed", ["잘 맞는 점"])
        if with_real_drive:
            before = device.trip_summary() if debug_proof else None
            print("RUN platform drive: 210 seconds", flush=True)
            if dev.drive("care", "http://127.0.0.1:8088", device.serial, duration_seconds=210):
                raise AssertionError("실제 플랫폼 주행 입력에 실패했어요")
            device.wait("주행 후 요약")
            device.capture("22-vehicle-recap", ["주행 후 요약", "내 주행이 매듭이 됐어요", "내가 운전하지 않았어요"])
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
        if debug_proof:
            prove_platform_home(device, console)
        print("PASS distinct fresh-install scenarios: " + first_title + " / " + second_title, flush=True)
        return 0


def enter_instagram_contact(device):
    value = secrets.token_hex(12)
    device.tap("인스타그램 아이디 · 선택", scroll=True, description_only=True)
    device.shell("input", "text", value)
    device.shell("input", "keyevent", "KEYCODE_BACK")
    device.wait("한 번 공유한 아이디는 되돌릴 수 없어요.")
    return value


def assert_contacts_hidden(device, ids):
    labels = " ".join(device.labels(device.tree()))
    if "인스타그램 QR 코드" in labels or any(value in labels for value in ids):
        raise AssertionError("가려야 하는 연락처가 화면에 보여요")


def finish_session(device, debug_proof):
    device.shell("am", "force-stop", "--user", "10", "dev.chayeon")
    device.launch()
    device.wait("내 프로필")
    device.tap("내 프로필")
    device.wait("내 시그니처 보기")
    assert_solo_surface(device)
    device.capture("23-profile-restored", ["새벽드라이브", "내 시그니처 보기"])
    device.tap("인연")
    assert_contacts_hidden(device, ("byeol-night.drive",))
    if device.find("별보는밤") is not None:
        raise AssertionError("차단한 상대가 인연 목록에 다시 보여요")
    device.capture("24-records-restored", ["인연"])
    device.tap("내 프로필")
    device.tap("기록과 계정 정리", scroll=True)
    device.tap("로그아웃", scroll=True)
    device.confirm("로그아웃")
    device.wait("이 계정으로 시작")
    device.capture("25-signed-out", ["혼자 둘러보기(가상 상대)", "이 계정으로 시작"])
    if debug_proof and device.trip_summary()["rows"] != 0:
        raise AssertionError("로그아웃 뒤 차량 주행 지표가 남아 있어요")
    if debug_proof and home_record_count(device) != 0:
        raise AssertionError("로그아웃 뒤 집 기록이 남아 있어요")
    print("PASS clean install → signature → relationship → driving lock → after → block → restart → sign out", flush=True)
    return 0

#!/usr/bin/env python3
"""One emulator account and one real HTTP peer, with no actor-id shortcuts."""

import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import uuid
from urllib.request import Request, urlopen

try:
    from . import dev
    from .e2e import Console, Device, assert_locked
except ImportError:
    import dev
    from e2e import Console, Device, assert_locked


class Peer:
    def __init__(self, port):
        self.base = f"http://127.0.0.1:{port}"
        self.token = None

    def call(self, route, **body):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if body:
            body = {"meta": {"commandId": str(uuid.uuid4())}, **body}
        request = Request(self.base + route, data=json.dumps(body).encode() if body else None, headers=headers)
        with urlopen(request, timeout=30) as response:
            result = json.load(response)["result"]
        if result["type"] != "success":
            raise AssertionError("HTTP 명령 실패: " + route)
        return result["value"]

    def sync(self):
        return self.call("/v1/sync?since=0")


def enter_ui(device, room):
    device.launch()
    device.wait("두 대로 연결(서버)")
    device.tap("두 대로 연결(서버)")
    tree = device.tree()
    fields = [n for n in tree.iter("node") if n.get("class") == "android.widget.EditText"]
    if len(fields) != 1:
        raise AssertionError("서버 방 코드 입력 칸을 찾지 못했어요")
    import re
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", fields[0].get("bounds")))
    device.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    device.shell("input", "text", room)
    device.shell("input", "keyevent", "KEYCODE_BACK")
    device.tap("이 계정으로 시작", scroll=True)
    device.wait("저장하고 다음")
    device.tap("저장하고 다음", scroll=True)
    device.wait("차량 데이터·위치·공개 범위")
    for label in ["성인이고, 직접 참여해요", "차량 데이터 사용에 동의해요", "위치 사용에 동의해요", "프로필 공개에 동의해요"]:
        device.tap(label)
        device.permissions()
    device.tap("선택한 설정으로 시작")
    device.wait("내 프로필")
    device.tap("발견 켜기")


def run(port=18765):
    device = Device(output="build/e2e/server")
    if dev.gradle(["-Pchayeon.serverOnly=true", ":server:installDist"], android=False):
        return 1
    if dev.gradle([":app:assembleDebug", "-Pchayeon.demo=true", f"-Pchayeon.serverUrl=http://10.0.2.2:{port}"]):
        return 1
    env = dev.execution_environment()
    env.update(PORT=str(port), HOST="0.0.0.0")
    log = (device.output / "server.log").open("w", encoding="utf-8")
    server = subprocess.Popen([str(dev.ROOT / "server/build/install/server/bin/server")], env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(100):
            try:
                with urlopen(f"http://127.0.0.1:{port}/health", timeout=1):
                    break
            except OSError:
                if server.poll() is not None:
                    raise AssertionError("검증 서버가 시작되지 않았어요")
                time.sleep(0.2)
        else:
            raise AssertionError("검증 서버가 준비되지 않았어요")
        room = "proof-" + secrets.token_hex(4)
        peer = Peer(port)
        session = peer.call("/v1/session/enter", mode="SERVER_DEMO", persona="ACCOUNT_B", roomCode=room, clientSecret=secrets.token_urlsafe(32))
        peer.token = session["accessToken"]
        peer.call("/v1/profile/save", draft={"nickname": "이어가는별", "avatar": "STAR", "interests": ["COFFEE"]})
        for consent in ["ADULT_ATTESTATION", "VEHICLE_DATA", "LOCATION_USE", "PUBLIC_PROFILE"]:
            peer.call("/v1/profile/consent", scope=consent, action="GRANT", documentVersion=session["policy"]["consentDocumentVersion"])
        peer.call("/v1/discovery/set", mode="ON")
        peer.call("/v1/presence/synthetic", presence="NEARBY")
        with Console(device) as console:
            device.run("uninstall", "dev.chayeon", check=False)
            device.run("install", "--user", "10", str(dev.ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
            enter_ui(device, room)
            device.card("이어가는별")
            device.wait("연결 요청 보내기")
            device.capture("01-http-candidate", ["이어가는별", "가상 프로필"])
            device.tap("연결 요청 보내기")
            for _ in range(30):
                requests = peer.sync()["connectionRequests"]
                request = next((r for r in requests if r["direction"] == "INCOMING" and r["status"] == "PENDING"
                                and r["peer"]["nickname"] == "새벽드라이브"), None)
                if request:
                    break
                time.sleep(0.5)
            else:
                raise AssertionError("HTTP 상대에게 요청이 도착하지 않았어요")
            accepted = peer.call("/v1/connection/respond", requestId=request["id"], decision="ACCEPT")
            if accepted["peer"]["nickname"] != "새벽드라이브":
                raise AssertionError("앱 계정의 요청을 수락하지 않았어요")
            device.wait("주고받은 인사")
            device.tap("안녕하세요")
            device.wait("보냈어요")
            snapshot = peer.sync()
            if not any(m["author"] == "PEER" and m["code"] == "HELLO" for m in snapshot["greetings"]):
                raise AssertionError("앱의 인사가 HTTP 상대에게 도착하지 않았어요")
            connection = next(c for c in snapshot["connections"] if c["status"] == "CONNECTED")
            peer.call("/v1/connection/greeting", connectionId=connection["id"], clientMessageId=str(uuid.uuid4()), code="NICE_TO_MEET_YOU")
            device.wait("받았어요")
            device.capture("02-http-greetings", ["이어가는별 님과 이어져 있어요", "보냈어요", "받았어요"])
            console.gear("Drive")
            peer.call("/v1/connection/greeting", connectionId=connection["id"], clientMessageId=str(uuid.uuid4()), code="TAKE_CARE")
            time.sleep(2)
            assert_locked(device)
            device.capture("03-http-restricted", ["주행 중", "나가기"])
            notification = device.shell("dumpsys", "notification", "--noredact")
            if "주차하면 확인할 수 있어요" not in notification or "category=msg" not in notification:
                raise AssertionError("내용 없는 메시지 알림을 확인하지 못했어요")
            (device.output / "notification-proof.json").write_text(json.dumps({"category": "msg", "body": "주차하면 확인할 수 있어요"}, ensure_ascii=False), encoding="utf-8")
            console.gear("Park")
            device.wait("주고받은 인사")
            device.tap("나가기")
            device.wait("기록 보기")
            device.capture("04-http-ended", ["기록 보기"])
            if len(peer.sync()["encounters"]) != 1:
                raise AssertionError("HTTP 상대의 만남 기록이 한 건이 아니에요")
            device.tap("기록 보기")
            device.wait("애프터 신청")
            device.capture("05-http-encounter", ["이어가는별", "애프터 신청"])
        print("PASS real HTTP: request, accept, greeting both ways, restricted notification, leave, encounter", flush=True)
        return 0
    finally:
        server.terminate()
        try:
            server.wait(timeout=10)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait(timeout=5)
        log.close()

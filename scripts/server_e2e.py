#!/usr/bin/env python3
"""One emulator account and one real HTTP peer, with no actor-id shortcuts."""

import json
import os
from pathlib import Path
import secrets
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen

try:
    from . import dev
    from .e2e import Console, Device, assert_locked, set_synthetic_home, home_position, assert_home_rest, home_record_count
except ImportError:
    import dev
    from e2e import Console, Device, assert_locked, set_synthetic_home, home_position, assert_home_rest, home_record_count


class Peer:
    def __init__(self, port):
        self.base = f"http://127.0.0.1:{port}"
        self.token = None
        self.stop = threading.Event()
        self.failure = None
        self.worker = None

    def start_heartbeat(self):
        def renew():
            while not self.stop.wait(20):
                try:
                    self.call("/v1/presence/synthetic", presence="NEARBY")
                except Exception as failure:
                    self.failure = failure
                    return
        self.worker = threading.Thread(target=renew, daemon=True)
        self.worker.start()

    def close(self):
        self.stop.set()
        if self.worker:
            self.worker.join(timeout=35)

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
        if self.failure:
            raise AssertionError("HTTP 상대의 heartbeat가 실패했어요") from self.failure
        return self.call("/v1/sync?since=0")


class AppTransport:
    """Forward real HTTP unchanged; retain only route, presence and acknowledgement for proof."""
    def __init__(self, port):
        self.started = []
        self.completed = []
        self.lock = threading.Lock()
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def forward(self):
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                presence = json.loads(body).get("presence") if self.path == "/v1/presence/synthetic" else None
                with owner.lock:
                    owner.started.append((time.monotonic(), self.path))
                request = Request(f"http://127.0.0.1:{port}" + self.path, data=body if self.command == "POST" else None,
                                  headers={key: value for key, value in self.headers.items() if key.lower() not in {"host", "connection", "content-length"}},
                                  method=self.command)
                try:
                    response = urlopen(request, timeout=35)
                except HTTPError as response_error:
                    response = response_error
                with response:
                    payload = response.read()
                    status = response.status
                accepted = json.loads(payload).get("result", {}).get("type") == "success"
                with owner.lock:
                    owner.completed.append((time.monotonic(), self.path, presence, accepted))
                try:
                    self.send_response(status)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(payload)))
                    self.end_headers()
                    self.wfile.write(payload)
                except (BrokenPipeError, ConnectionResetError):
                    pass

            do_GET = forward
            do_POST = forward

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()

    @property
    def port(self):
        return self.server.server_address[1]

    def count(self):
        with self.lock:
            return len(self.started)

    def acknowledged(self, presence, since):
        with self.lock:
            return any(at >= since and value == presence and accepted for at, _, value, accepted in self.completed)

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(timeout=5)


def prove_lease_and_offline_restore(device, console, peer, transport, device_port):
    set_synthetic_home(device, server=True)
    home_position(device, False)
    device.card("이어가는별")
    device.tap("연결 요청 보내기")
    deadline = time.monotonic() + 20
    while True:
        pending = next((r for r in peer.sync()["connectionRequests"] if r["status"] == "PENDING" and r["direction"] == "INCOMING"
                        and r["peer"]["nickname"] == "새벽드라이브"), None)
        if pending:
            break
        if time.monotonic() >= deadline:
            raise AssertionError("lease 검증의 연결 요청이 도착하지 않았어요")
        time.sleep(.25)
    connection_id = peer.call("/v1/connection/respond", requestId=pending["id"], decision="ACCEPT")["connectionId"]
    device.wait("주고받은 인사")
    began = time.monotonic()
    print("RUN server lease: connected beyond 70 seconds on heartbeats", flush=True)
    while time.monotonic() - began < 75:
        connection = next(c for c in peer.sync()["connections"] if c["id"] == connection_id)
        if connection["status"] != "CONNECTED":
            raise AssertionError("heartbeat 중 연결이 유지되지 않았어요")
        time.sleep(1)
    live_seconds = time.monotonic() - began
    device.shell("am", "force-stop", "--user", "10", "dev.chayeon")
    stopped = time.monotonic()
    if device.shell("pidof", "dev.chayeon", check=False).strip():
        raise AssertionError("앱 프로세스가 종료되지 않았어요")
    request_count = transport.count()
    print("RUN server lease: no app traffic after force-stop; await DISTANCE", flush=True)
    while time.monotonic() - stopped < 80:
        connection = next(c for c in peer.sync()["connections"] if c["id"] == connection_id)
        if transport.count() != request_count:
            raise AssertionError("종료된 앱에서 추가 HTTP 요청이 왔어요")
        if connection["status"] == "ENDED":
            if connection["endReason"] != "DISTANCE":
                raise AssertionError("앱 종료가 거리 이탈로 끝나지 않았어요")
            break
        time.sleep(.25)
    else:
        raise AssertionError("lease와 거리 유예 뒤에도 연결이 남아 있어요")
    death_to_distance = time.monotonic() - stopped
    device.run("reverse", "--remove", f"tcp:{device_port}")
    try:
        device.launch()
        device.wait("내 프로필", timeout=15)
        device.tap("내 프로필")
        device.tap("집 보호 구역", scroll=True)
        device.wait("위치를 확인할 때까지 쉬고 있어요", timeout=15)
        device.capture("home-offline-restore", ["집 보호 구역", "위치를 확인할 때까지 쉬고 있어요", "집 지우기"])
        requests_after_stop = transport.count() - request_count
        if requests_after_stop != 0:
            raise AssertionError("reverse 제거 뒤에도 앱의 통신이 연결돼 있어요")
    finally:
        restored = time.monotonic()
        device.run("reverse", f"tcp:{device_port}", f"tcp:{transport.port}")
    deadline = time.monotonic() + 45
    while not transport.acknowledged("AWAY", restored):
        if time.monotonic() >= deadline:
            raise AssertionError("통신 복구 뒤 AWAY가 적용되지 않았어요")
        time.sleep(.25)
    if any(c["profile"]["nickname"] == "새벽드라이브" for c in peer.sync()["discovery"]["candidates"]):
        raise AssertionError("보호 중인 앱 계정이 후보에 보여요")
    proof = {"connectedSeconds": live_seconds, "deathToDistanceSeconds": death_to_distance,
             "appRequestsAfterStop": requests_after_stop,
             "offlineSettingsReachable": True, "awayAcknowledgedAfterReconnect": True}
    (device.output / "lease-offline-proof.json").write_text(json.dumps(proof, indent=2), encoding="utf-8")
    print("PASS server lease and offline restore: heartbeat survival, traffic-free expiry, reachable settings, AWAY acknowledgement", flush=True)
    home_position(device, False)


def normalized_connection(value):
    if isinstance(value, dict):
        return {key: normalized_connection(item) for key, item in value.items()
                if key not in {"id", "version"} and not key.endswith(("Id", "EpochMillis"))}
    if isinstance(value, list):
        return [normalized_connection(item) for item in value]
    return value


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


def prove_server_home(device, console, peer):
    set_synthetic_home(device, server=True)
    observations = []
    for mode in ("ordinary", "parked", "driving"):
        if mode == "ordinary":
            device.tap("시연 제어", description_only=True)
            device.tap("집에서 멀어지기", scroll=True)
            device.tap("오늘의 인연")
        else:
            device.tap("시연 제어", description_only=True)
            device.tap("가까이 있어요")
            device.tap("집에서 멀어지기", scroll=True)
            device.tap("오늘의 인연")
        device.card("이어가는별")
        device.tap("연결 요청 보내기")
        deadline = time.monotonic() + 20
        request = None
        while time.monotonic() < deadline:
            request = next((r for r in peer.sync()["connectionRequests"] if r["status"] == "PENDING" and r["direction"] == "INCOMING"
                            and r["peer"]["nickname"] == "새벽드라이브"), None)
            if request:
                break
            time.sleep(.25)
        if request is None:
            raise AssertionError("집 보호 검증의 요청이 도착하지 않았어요")
        accepted = peer.call("/v1/connection/respond", requestId=request["id"], decision="ACCEPT")
        device.wait("주고받은 인사")
        connection_id = accepted["connectionId"]
        if mode == "driving":
            console.gear("Drive")
            device.wait("주행 중", exact=False)
            home_position(device, True, external=True)
        else:
            device.tap("시연 제어", description_only=True)
            device.tap("서로 멀어져요" if mode == "ordinary" else "집 근처로 오기", scroll=True)
        deadline = time.monotonic() + 25
        states = []
        views = []
        while time.monotonic() < deadline:
            snapshot = peer.sync()
            connection = next(c for c in snapshot["connections"] if c["id"] == connection_id)
            if not states and connection["status"] == "CONNECTED":
                # The broadcast finishes its local update before the HTTP command is acknowledged.
                time.sleep(.1)
                continue
            state = (connection["status"], connection.get("endReason"))
            if not states or state != states[-1]:
                states.append(state)
                views.append(normalized_connection(connection))
            if any(c["profile"]["nickname"] == "새벽드라이브" for c in snapshot["discovery"]["candidates"]):
                raise AssertionError("쉬는 앱 계정이 HTTP 후보에 남아 있어요")
            if any(value in json.dumps(snapshot).lower() for value in ["homezone", "home_zone", "latitude", "longitude", '"radius"']):
                raise AssertionError("HTTP 상대에게 집 정보가 보여요")
            if connection["status"] == "ENDED":
                if connection["endReason"] != "DISTANCE":
                    raise AssertionError("보통 이탈과 종료 이유가 달라요")
                break
            time.sleep(.25)
        else:
            raise AssertionError("집 근처에서 HTTP 연결이 끝나지 않았어요")
        if states[-1] != ("ENDED", "DISTANCE") or ("CHECKING", None) not in states:
            raise AssertionError("거리 이탈의 확인 중 → 종료 경로를 거치지 않았어요")
        observations.append({"mode": mode, "states": states, "connectionViews": views})
        if mode == "driving":
            assert_locked(device, hidden_peers=("이어가는별", "새벽드라이브"))
            device.capture("home-http-driving", ["쉬는 중", "주차하면"])
            console.gear("Park")
        else:
            device.shell("input", "keyevent", "KEYCODE_BACK")
        device.wait("멀어져서 연결이 끝났어요")
        device.tap("오늘의 인연으로")
        if mode != "ordinary":
            assert_home_rest(device)
    if observations[0]["states"] != observations[1]["states"] or observations[1]["states"] != observations[2]["states"]:
        raise AssertionError("일반 이탈과 집 보호 이탈의 HTTP 상태 순서가 달라요")
    if observations[0]["connectionViews"] != observations[1]["connectionViews"] or observations[1]["connectionViews"] != observations[2]["connectionViews"]:
        raise AssertionError("식별자와 시각을 제외한 HTTP 연결 내용이 일반 이탈과 달라요")
    device.tap("시연 제어", description_only=True)
    device.tap("집에서 멀어지기", scroll=True)
    device.tap("오늘의 인연")
    device.card("이어가는별", navigate=False)
    deadline = time.monotonic() + 15
    while not any(c["profile"]["nickname"] == "새벽드라이브" for c in peer.sync()["discovery"]["candidates"]):
        if time.monotonic() >= deadline:
            raise AssertionError("구역 밖으로 나온 앱 계정이 HTTP 후보로 돌아오지 않았어요")
        time.sleep(.25)
    device.capture("home-http-return", ["이어가는별", "발견 중"])
    (device.output / "home-peer-proof.json").write_text(json.dumps(observations, ensure_ascii=False, indent=2), encoding="utf-8")
    device.tap("내 프로필")
    device.tap("기록과 계정 정리", scroll=True)
    device.tap("로그아웃", scroll=True)
    device.confirm("로그아웃")
    device.wait("이 계정으로 시작")
    if home_record_count(device):
        raise AssertionError("서버 모드 로그아웃 뒤 집 기록이 남아 있어요")
    device.capture("home-http-cleanup", ["이 계정으로 시작"])


def run(port=18765, serial=None):
    device = Device(serial=serial, output="build/e2e/server")
    if dev.gradle(["-Pchayeon.serverOnly=true", ":server:installDist"], android=False):
        return 1
    if dev.gradle([":app:assembleDebug", "-Pchayeon.demo=true", f"-Pchayeon.serverUrl=http://127.0.0.1:{port}"]):
        return 1
    env = dev.execution_environment()
    env.update(PORT=str(port), HOST="0.0.0.0")
    log = (device.output / "server.log").open("w", encoding="utf-8")
    server = subprocess.Popen([str(dev.ROOT / "server/build/install/server/bin/server")], env=env, stdout=log, stderr=subprocess.STDOUT)
    peer = None
    transport = None
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
        peer.start_heartbeat()
        transport = AppTransport(port)
        device.run("reverse", f"tcp:{port}", f"tcp:{transport.port}")
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
            prove_lease_and_offline_restore(device, console, peer, transport, port)
            prove_server_home(device, console, peer)
        print("PASS real HTTP: request, accept, greeting both ways, restricted notification, leave, encounter", flush=True)
        return 0
    finally:
        if peer:
            peer.close()
        if transport:
            device.run("reverse", "--remove", f"tcp:{port}", check=False)
            transport.close()
        server.terminate()
        try:
            server.wait(timeout=10)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait(timeout=5)
        log.close()

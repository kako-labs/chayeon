#!/usr/bin/env python3
"""One emulator account and one real HTTP peer, with no actor-id shortcuts."""

import json
import base64
import hashlib
import math
import select
import socket
import struct
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
    from .e2e import Console, Device, assert_locked, set_synthetic_home, home_position, assert_home_rest, home_record_count, enter_instagram_contact, assert_contacts_hidden, configure_auto_connect, enter_nickname
except ImportError:
    import dev
    from e2e import Console, Device, assert_locked, set_synthetic_home, home_position, assert_home_rest, home_record_count, enter_instagram_contact, assert_contacts_hidden, configure_auto_connect, enter_nickname


class Peer:
    def __init__(self, port):
        self.base = f"http://127.0.0.1:{port}"
        self.token = None
        self.stop = threading.Event()
        self.failure = None
        self.worker = None
        self.response_guard = None

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
            envelope = json.load(response)
            if self.response_guard:
                self.response_guard(envelope)
            result = envelope["result"]
        if result["type"] != "success":
            raise AssertionError("HTTP 명령 실패: " + route)
        return result["value"]

    def sync(self):
        if self.failure:
            raise AssertionError("HTTP 상대의 heartbeat가 실패했어요") from self.failure
        return self.call("/v1/sync?since=0")


class VoicePeer:
    """Bounded, unrecorded PCM proof using only the standard library."""
    def __init__(self, peer, connection_id):
        from urllib.parse import urlsplit
        address = urlsplit(peer.base)
        self.socket = socket.create_connection((address.hostname, address.port), timeout=5)
        if address.scheme == "https":
            import ssl
            self.socket = ssl.create_default_context().wrap_socket(self.socket, server_hostname=address.hostname)
        self.socket.settimeout(35)
        self.lock = threading.Lock()
        self.stop = threading.Event()
        self.closed = threading.Event()
        self.received = 0
        self.failure = None
        self.sender = None
        self.reader = None
        key = base64.b64encode(secrets.token_bytes(16)).decode()
        request = (f"GET /v1/voice/{connection_id} HTTP/1.1\r\nHost: {address.netloc}\r\n"
                   f"Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n"
                   f"Sec-WebSocket-Key: {key}\r\nAuthorization: Bearer {peer.token}\r\n\r\n")
        self.socket.sendall(request.encode())
        response = bytearray()
        while not response.endswith(b"\r\n\r\n") and len(response) < 8192:
            part = self.socket.recv(1)
            if not part:
                break
            response.extend(part)
        if not bytes(response).startswith(b"HTTP/1.1 101 "):
            self.socket.close()
            raise PermissionError("음성 연결이 허용되지 않았어요")
        expected = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest())
        if expected not in response:
            self.socket.close()
            raise AssertionError("음성 WebSocket 응답이 올바르지 않아요")

    def send(self, opcode, payload):
        mask = secrets.token_bytes(4)
        size = len(payload)
        header = bytes([0x80 | opcode, 0x80 | size]) if size < 126 else bytes([0x80 | opcode, 0x80 | 126]) + struct.pack("!H", size)
        data = bytes(value ^ mask[index % 4] for index, value in enumerate(payload))
        with self.lock:
            self.socket.sendall(header + mask + data)

    def exact(self, count):
        result = bytearray()
        while len(result) < count:
            part = self.socket.recv(count - len(result))
            if not part:
                raise EOFError()
            result.extend(part)
        return bytes(result)

    def start(self):
        def read():
            try:
                while not self.stop.is_set():
                    first, second = self.exact(2)
                    length = second & 127
                    if length == 126:
                        length = struct.unpack("!H", self.exact(2))[0]
                    elif length == 127:
                        length = struct.unpack("!Q", self.exact(8))[0]
                    if length > 640 or second & 128:
                        raise AssertionError("음성 프레임 크기 또는 형식이 올바르지 않아요")
                    payload = self.exact(length)
                    opcode = first & 15
                    if opcode == 8:
                        self.closed.set()
                        return
                    if opcode == 9:
                        self.send(10, payload)
                    elif opcode == 2:
                        if length != 640:
                            raise AssertionError("20 ms 음성 프레임이 아니에요")
                        self.received += 1
                    elif opcode != 10:
                        raise AssertionError("음성이 아닌 프레임이 왔어요")
            except (EOFError, ConnectionError, OSError):
                self.closed.set()
            except Exception as failure:
                self.failure = failure
                self.closed.set()

        def sine():
            index = 0
            deadline = time.monotonic()
            try:
                while not self.stop.is_set() and not self.closed.is_set():
                    frame = struct.pack("<320h", *(int(3000 * math.sin(2 * math.pi * 440 * (index + sample) / 16000)) for sample in range(320)))
                    self.send(2, frame)
                    index += 320
                    deadline += .02
                    self.stop.wait(max(0, deadline - time.monotonic()))
                    if time.monotonic() - deadline > .1:
                        deadline = time.monotonic()
            except (ConnectionError, OSError):
                self.closed.set()

        self.reader = threading.Thread(target=read, daemon=True)
        self.sender = threading.Thread(target=sine, daemon=True)
        self.reader.start()
        self.sender.start()
        return self

    def assert_capture(self, device):
        deadline = time.monotonic() + 8
        while self.received == 0 and time.monotonic() < deadline and not self.closed.is_set():
            time.sleep(.1)
        if self.failure:
            raise self.failure
        if not self.received:
            tree = device.tree()
            content = " ".join(n.get("text", "") + " " + n.get("content-desc", "") for n in tree.iter("node"))
            if "이 차에서는 마이크를 쓸 수 없어요" not in content and "마이크를 허용하면" not in content:
                raise AssertionError("마이크 제한 안내도 수신 음성 프레임도 없어요")
        if self.closed.is_set():
            raise AssertionError("활성 연결의 음성 소켓이 끝났어요")

    def assert_closed(self):
        if not self.closed.wait(3):
            raise AssertionError("연결 종료 뒤 음성 소켓이 남아 있어요")
        if self.failure:
            raise self.failure

    def close(self):
        self.stop.set()
        try:
            self.send(8, struct.pack("!H", 1000))
        except (ConnectionError, OSError):
            pass
        try:
            self.socket.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.socket.close()
        for thread in (self.sender, self.reader):
            if thread:
                thread.join(timeout=6)


def reject_third_voice(peer, connection_id):
    from urllib.parse import urlsplit
    stranger = Peer(urlsplit(peer.base).port)
    session = stranger.call("/v1/session/enter", mode="SERVER_DEMO",
                           roomCode="outsider-" + secrets.token_hex(4), clientSecret=secrets.token_urlsafe(32))
    stranger.token = session["accessToken"]
    try:
        unexpected = VoicePeer(stranger, connection_id)
    except PermissionError:
        return
    else:
        unexpected.close()
        raise AssertionError("세 번째 계정이 음성 연결에 들어왔어요")


class AppTransport:
    """Forward real HTTP unchanged; retain only route, presence and acknowledgement for proof."""
    def __init__(self, port):
        self.started = []
        self.completed = []
        self.private_contact_values = ()
        self.contact_leak = False
        self.inspected_responses = 0
        self.lock = threading.Lock()
        self.sync_allowed = threading.Event()
        self.sync_allowed.set()
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def forward(self):
                if self.headers.get("Upgrade", "").lower() == "websocket":
                    self.tunnel_voice()
                    return
                if self.path.startswith("/v1/sync") and not owner.sync_allowed.wait(30):
                    self.send_error(503)
                    return
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
                if self.path.startswith("/v1/sync") and not owner.sync_allowed.wait(30):
                    self.send_error(503)
                    return
                accepted = json.loads(payload).get("result", {}).get("type") == "success"
                with owner.lock:
                    if owner.private_contact_values:
                        owner.inspected_responses += 1
                        if any(value.encode() in payload for value in owner.private_contact_values):
                            owner.contact_leak = True
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

            def tunnel_voice(self):
                # Forward authenticated WebSocket bytes without inspecting or retaining audio.
                with socket.create_connection(("127.0.0.1", port), timeout=5) as upstream:
                    lines = [f"GET {self.path} HTTP/1.1", f"Host: 127.0.0.1:{port}"]
                    lines.extend(f"{key}: {value}" for key, value in self.headers.items() if key.lower() != "host")
                    upstream.sendall(("\r\n".join(lines) + "\r\n\r\n").encode())
                    self.close_connection = True
                    while True:
                        ready, _, _ = select.select([self.connection, upstream], [], [], 35)
                        if not ready:
                            return
                        for source in ready:
                            try:
                                data = source.recv(4096)
                                if not data:
                                    return
                                (upstream if source is self.connection else self.connection).sendall(data)
                            except (ConnectionError, OSError):
                                return

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
                        and r["peer"]["nickname"] == "DawnDriver"), None)
        if pending:
            break
        if time.monotonic() >= deadline:
            raise AssertionError("lease 검증의 연결 요청이 도착하지 않았어요")
        time.sleep(.25)
    connection_id = peer.call("/v1/connection/respond", requestId=pending["id"], decision="ACCEPT")["connectionId"]
    device.wait("통화 중")
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
    if any(c["profile"]["nickname"] == "DawnDriver" for c in peer.sync()["discovery"]["candidates"]):
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


def enter_ui(device):
    device.launch()
    device.wait("두 대로 연결(서버)")
    device.tap("두 대로 연결(서버)")
    device.tap("시작하기", scroll=True)
    device.wait("저장하고 다음")
    enter_nickname(device)
    device.tap("저장하고 다음", scroll=True)
    device.wait("차량 데이터·위치·공개 범위")
    for label in ["성인이고, 직접 참여해요", "차량 데이터 사용에 동의해요", "위치 사용에 동의해요", "프로필 공개에 동의해요"]:
        device.tap(label)
        device.permissions()
    device.tap("마이크 · 선택", scroll=True)
    device.permissions()
    device.tap("선택한 설정으로 시작", scroll=True)
    device.wait("내 프로필")
    device.tap("발견 켜기")


def prove_server_home(device, console, peer):
    set_synthetic_home(device, server=True)
    observations = []
    for mode in ("ordinary", "parked", "driving"):
        if mode == "ordinary":
            device.tap("시연 제어", description_only=True)
            device.tap("집에서 멀어지기", scroll=True)
            device.tap("홈")
        else:
            device.tap("시연 제어", description_only=True)
            device.tap("가까이 있어요")
            device.tap("집에서 멀어지기", scroll=True)
            device.tap("홈")
        device.card("이어가는별")
        device.tap("연결 요청 보내기")
        deadline = time.monotonic() + 20
        request = None
        while time.monotonic() < deadline:
            request = next((r for r in peer.sync()["connectionRequests"] if r["status"] == "PENDING" and r["direction"] == "INCOMING"
                            and r["peer"]["nickname"] == "DawnDriver"), None)
            if request:
                break
            time.sleep(.25)
        if request is None:
            raise AssertionError("집 보호 검증의 요청이 도착하지 않았어요")
        accepted = peer.call("/v1/connection/respond", requestId=request["id"], decision="ACCEPT")
        device.wait("통화 중")
        connection_id = accepted["connectionId"]
        voice = VoicePeer(peer, connection_id).start()
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
            if any(c["profile"]["nickname"] == "DawnDriver" for c in snapshot["discovery"]["candidates"]):
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
        voice.assert_closed()
        voice.close()
        try:
            late = VoicePeer(peer, connection_id)
        except PermissionError:
            pass
        else:
            late.close()
            raise AssertionError("끝난 연결에 다시 음성으로 들어왔어요")
        observations.append({"mode": mode, "states": states, "connectionViews": views})
        if mode == "driving":
            assert_locked(device, hidden_peers=("이어가는별", "DawnDriver"))
            device.capture("home-http-driving", ["쉬는 중", "주차하면"])
            console.gear("Park")
        else:
            device.shell("input", "keyevent", "KEYCODE_BACK")
        device.wait("서로 멀어져서 통화가 끝났어요")
        device.tap("오늘의 인연으로")
        if mode != "ordinary":
            assert_home_rest(device)
    if observations[0]["states"] != observations[1]["states"] or observations[1]["states"] != observations[2]["states"]:
        raise AssertionError("일반 이탈과 집 보호 이탈의 HTTP 상태 순서가 달라요")
    if observations[0]["connectionViews"] != observations[1]["connectionViews"] or observations[1]["connectionViews"] != observations[2]["connectionViews"]:
        raise AssertionError("식별자와 시각을 제외한 HTTP 연결 내용이 일반 이탈과 달라요")
    device.tap("시연 제어", description_only=True)
    device.tap("집에서 멀어지기", scroll=True)
    device.tap("홈")
    device.card("이어가는별", navigate=False)
    deadline = time.monotonic() + 15
    while not any(c["profile"]["nickname"] == "DawnDriver" for c in peer.sync()["discovery"]["candidates"]):
        if time.monotonic() >= deadline:
            raise AssertionError("구역 밖으로 나온 앱 계정이 HTTP 후보로 돌아오지 않았어요")
        time.sleep(.25)
    device.capture("home-http-return", ["이어가는별", "발견 중"])
    (device.output / "home-peer-proof.json").write_text(json.dumps(observations, ensure_ascii=False, indent=2), encoding="utf-8")
    device.tap("내 프로필")
    device.tap("기록과 계정 정리", scroll=True)
    device.tap("로그아웃", scroll=True)
    device.confirm("로그아웃")
    device.wait("두 대로 연결(서버)")
    if home_record_count(device):
        raise AssertionError("서버 모드 로그아웃 뒤 집 기록이 남아 있어요")
    device.capture("home-http-cleanup", ["두 대로 연결(서버)"])


def prove_after_exchange(device, console, peer, transport):
    device.tap("애프터 신청")
    own_id = enter_instagram_contact(device)
    peer_id = secrets.token_hex(12)
    transport.private_contact_values = (own_id, peer_id)
    observed = []

    def guard(payload):
        observed.append(True)
        if any(value in json.dumps(payload) for value in (own_id, peer_id)):
            raise AssertionError("수락 전에 HTTP 응답에 연락처가 나타났어요")

    peer.response_guard = guard
    device.tap("애프터 신청 보내기")
    deadline = time.monotonic() + 20
    while True:
        snapshot = peer.sync()
        pending = next((item for item in snapshot["afterRequests"]
                        if item["direction"] == "INCOMING" and item["status"] == "PENDING"
                        and item["peer"]["nickname"] == "DawnDriver"), None)
        if pending:
            break
        if time.monotonic() >= deadline:
            raise AssertionError("HTTP 상대에게 애프터 신청이 도착하지 않았어요")
        time.sleep(.25)
    assert_contacts_hidden(device, (peer_id,))
    if transport.contact_leak or not transport.inspected_responses:
        raise AssertionError("수락 전 앱 응답의 연락처 비공개를 확인하지 못했어요")
    peer.response_guard = None
    transport.private_contact_values = ()
    accepted = peer.call("/v1/after/respond", requestId=pending["id"], decision="ACCEPT",
                         contacts={"instagramId": peer_id})
    relationship = next(item for item in peer.sync()["afterConnections"] if item["id"] == accepted["afterConnectionId"])
    if relationship["sharedContacts"]["instagramId"] != own_id or relationship["ownSharedContacts"]["instagramId"] != peer_id:
        raise AssertionError("수락 뒤 양측 아이디가 함께 나타나지 않았어요")
    device.wait("이어진 인연 보기")
    device.tap("이어진 인연 보기")
    device.wait("@" + peer_id)
    device.wait("인스타그램 QR 코드")
    device.capture("06-http-contact-exchange", ["인스타그램", "@" + peer_id, "인스타그램 QR 코드"])
    console.gear("Drive")
    device.wait("주차하면", exact=False)
    assert_contacts_hidden(device, (own_id, peer_id))
    console.gear("Park")
    device.wait("@" + peer_id)
    peer.call("/v1/after/end", afterConnectionId=relationship["id"])
    device.wait("관계가 끝났어요")
    assert_contacts_hidden(device, (own_id, peer_id))
    closed = next(item for item in peer.sync()["afterConnections"] if item["id"] == relationship["id"])
    if closed.get("sharedContacts") or closed.get("ownSharedContacts"):
        raise AssertionError("관계 종료 뒤 서버가 연락처를 반환해요")
    (device.output / "contact-exchange-proof.json").write_text(json.dumps({
        "peerResponsesInspectedBeforeAccept": len(observed),
        "appResponsesInspectedBeforeAccept": transport.inspected_responses,
        "hiddenBeforeAccept": True, "mutualExchange": True,
        "hiddenWhileDriving": True, "hiddenAfterEnd": True,
    }, indent=2), encoding="utf-8")


def run(port=18765, serial=None):
    device = Device(serial=serial, output="build/e2e/server")
    if dev.gradle(["-Pchayeon.serverOnly=true", ":server:installDist"], android=False):
        return 1
    if dev.gradle([":app:assembleDebug", "-Pchayeon.demo=true", f"-Pchayeon.serverUrl=http://127.0.0.1:{port}"]):
        return 1
    env = dev.execution_environment()
    env.update(PORT=str(port), HOST="0.0.0.0", CHAYEON_ALLOWED_ROOMS="*", CHAYEON_NEW_ACCOUNTS_PER_HOUR="1000")
    log = (device.output / "server.log").open("w", encoding="utf-8")
    server = subprocess.Popen([str(dev.ROOT / "server/build/install/server/bin/server")], env=env, stdout=log, stderr=subprocess.STDOUT)
    peer = None
    transport = None
    voice = None
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
        room = "default"
        peer = Peer(port)
        session = peer.call("/v1/session/enter", mode="SERVER_DEMO",  roomCode=room, clientSecret=secrets.token_urlsafe(32))
        peer.token = session["accessToken"]
        peer.call("/v1/profile/save", draft={"nickname": "이어가는별", "avatar": "STAR", "interests": ["COFFEE"]})
        for consent in ["ADULT_ATTESTATION", "VEHICLE_DATA", "LOCATION_USE", "PUBLIC_PROFILE"]:
            peer.call("/v1/profile/consent", scope=consent, action="GRANT", documentVersion=session["policy"]["consentDocumentVersion"])
        peer.call("/v1/profile/signature", signature={
            "id": "http-driving-profile", "version": 1, "title": "깜빡이를 잊지 않는 드라이버",
            "traits": [{"trait": "SIGNAL_MANNERS", "level": "SIGNATURE", "score": 100}],
            "knotSeed": {"value": 48, "algorithmVersion": 2},
            "source": "SYNTHETIC", "evidenceBucket": "ESTABLISHED",
        })
        peer.call("/v1/discovery/set", mode="ON")
        peer.call("/v1/presence/synthetic", presence="AWAY")
        transport = AppTransport(port)
        device.run("reverse", f"tcp:{port}", f"tcp:{transport.port}")
        with Console(device) as console:
            device.run("uninstall", "dev.chayeon", check=False)
            device.run("install", "--user", "10", str(dev.ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
            enter_ui(device)
            configure_auto_connect(device, minimum=100)
            transport.sync_allowed.clear()
            try:
                console.gear("Drive")
                device.wait("주행 중", exact=False)
                peer.call("/v1/presence/synthetic", presence="NEARBY")
                peer.start_heartbeat()
                candidate = next((item for item in peer.sync()["discovery"]["candidates"]
                                  if item["profile"]["nickname"] == "DawnDriver"), None)
                if candidate is None:
                    raise AssertionError("HTTP 상대가 앱 계정을 발견하지 못했어요")
                requested = peer.call("/v1/connection/request", candidateId=candidate["id"])
            finally:
                transport.sync_allowed.set()
            deadline = time.monotonic() + 30
            while True:
                accepted = next((item for item in peer.sync()["connectionRequests"] if item["id"] == requested["id"]), None)
                if accepted and accepted["status"] == "ACCEPTED":
                    break
                if time.monotonic() >= deadline:
                    raise AssertionError("AUTO 조건에 맞는 주행 중 요청이 자동 수락되지 않았어요")
                time.sleep(.25)
            if accepted["direction"] != "OUTGOING" or not accepted.get("connectionId"):
                raise AssertionError("HTTP 발신 요청의 자동 수락으로 통화가 만들어지지 않았어요")
            device.wait("통화 중")
            device.capture("01-http-auto-accepted", ["이어가는별", "깜빡이 매너 100", "통화 중"])
            (device.output / "auto-connect-proof.json").write_text(json.dumps({
                "requester": "http-peer", "receiverMode": "AUTO", "driving": True,
                "minimumSignalMannersScore": 100, "acceptedWithoutTap": True,
            }, indent=2), encoding="utf-8")
            device.wait("통화 중")
            voice = VoicePeer(peer, accepted["connectionId"]).start()
            reject_third_voice(peer, accepted["connectionId"])
            voice.assert_capture(device)
            device.capture("02-http-call", ["이어가는별", "통화 중", "음소거", "통화 끊기"])
            console.gear("Drive")
            device.wait("통화 중")
            assert_locked(device)
            device.tap("음소거")
            device.wait("음소거 중")
            device.tap("음소거 해제")
            device.wait("통화 중")
            device.capture("03-http-restricted", ["통화 중", "음소거", "통화 끊기"])
            notification = device.shell("dumpsys", "notification", "--noredact")
            if "통화 끊기" not in notification or "category=call" not in notification:
                raise AssertionError("통화 끊기 동작이 있는 통화 알림을 확인하지 못했어요")
            (device.output / "notification-proof.json").write_text(json.dumps({"category": "call", "hangUpAction": True}, ensure_ascii=False), encoding="utf-8")
            console.gear("Park")
            device.wait("통화 중")
            device.tap("통화 끊기")
            voice.assert_closed()
            voice.close()
            voice = None
            device.wait("기록 보기")
            device.capture("04-http-ended", ["기록 보기"])
            if len(peer.sync()["encounters"]) != 1:
                raise AssertionError("HTTP 상대의 만남 기록이 한 건이 아니에요")
            device.tap("기록 보기")
            device.wait("애프터 신청")
            device.capture("05-http-encounter", ["이어가는별", "애프터 신청"])
            prove_after_exchange(device, console, peer, transport)
            prove_lease_and_offline_restore(device, console, peer, transport, port)
            prove_server_home(device, console, peer)
        print("PASS HTTP + voice: driving AUTO acceptance without taps, PCM relay, third-account rejection, driving mute, call termination, distance close, encounter, mutual contact exchange", flush=True)
        return 0
    finally:
        if voice:
            voice.close()
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

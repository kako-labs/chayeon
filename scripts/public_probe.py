"""Two-account check against a deployed judging server: HTTPS commands and WSS voice in both directions.

Usage: python3 scripts/public_probe.py <host>   (for example judging.example.org)

Creates two SERVER_DEMO accounts, connects them, relays 100 PCM frames each way over the voice WebSocket,
confirms that a third account is refused, then deletes all three accounts. Requests use the app's
User-Agent because some edge proxies reject the default Python one. No contact IDs, tokens or audio are
printed or stored.
"""
import base64, hashlib, json, math, secrets, socket, ssl, struct, sys, threading, time, uuid
from urllib.request import Request, urlopen

HOST = sys.argv[1] if len(sys.argv) > 1 else ""
BASE = f"https://{HOST}"


class Peer:
    def __init__(self):
        self.token = None

    def call(self, route, post=False, **body):
        headers = {"Content-Type": "application/json", "User-Agent": "ktor-client"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if body or post:
            body = {"meta": {"commandId": str(uuid.uuid4())}, **body}
        request = Request(BASE + route, data=json.dumps(body).encode() if body else None, headers=headers)
        with urlopen(request, timeout=30) as response:
            result = json.load(response)["result"]
        if result["type"] != "success":
            raise AssertionError(f"{route}: {result}")
        return result["value"]

    def sync(self):
        return self.call("/v1/sync?since=0")


class Voice:
    def __init__(self, peer, connection_id):
        raw = socket.create_connection((HOST, 443), timeout=10)
        self.sock = ssl.create_default_context().wrap_socket(raw, server_hostname=HOST)
        key = base64.b64encode(secrets.token_bytes(16)).decode()
        self.sock.sendall((f"GET /v1/voice/{connection_id} HTTP/1.1\r\nHost: {HOST}\r\nUpgrade: websocket\r\n"
                           f"Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: {key}\r\n"
                           f"Authorization: Bearer {peer.token}\r\nUser-Agent: ktor-client\r\n\r\n").encode())
        head = b""
        while b"\r\n\r\n" not in head:
            head += self.sock.recv(1)
        if not head.startswith(b"HTTP/1.1 101"):
            raise AssertionError("upgrade refused: " + head.split(b"\r\n")[0].decode())
        self.received = 0
        self.lock = threading.Lock()
        threading.Thread(target=self.read, daemon=True).start()

    def exact(self, n):
        out = b""
        while len(out) < n:
            part = self.sock.recv(n - len(out))
            if not part:
                raise EOFError
            out += part
        return out

    def read(self):
        try:
            while True:
                first, second = self.exact(2)
                length = second & 127
                if length == 126:
                    length = struct.unpack("!H", self.exact(2))[0]
                payload = self.exact(length)
                if first & 0x0F == 2 and len(payload) == 640:
                    self.received += 1
        except Exception:
            pass

    def send_frame(self, payload):
        mask = secrets.token_bytes(4)
        data = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        with self.lock:
            self.sock.sendall(bytes([0x82, 0x80 | 126]) + struct.pack("!H", len(payload)) + mask + data)


def main():
    if not HOST:
        raise SystemExit("usage: python3 scripts/public_probe.py <host>")
    a, b = Peer(), Peer()
    for peer, nick, interest in ((a, "점검하나", "JAZZ"), (b, "점검둘", "COFFEE")):
        session = peer.call("/v1/session/enter", mode="SERVER_DEMO", clientSecret=secrets.token_urlsafe(32))
        peer.token = session["accessToken"]
        peer.call("/v1/profile/save", draft={"nickname": nick, "avatar": "STAR", "interests": [interest]})
        for scope in ["ADULT_ATTESTATION", "VEHICLE_DATA", "LOCATION_USE", "PUBLIC_PROFILE"]:
            peer.call("/v1/profile/consent", scope=scope, action="GRANT", documentVersion=session["policy"]["consentDocumentVersion"])
        peer.call("/v1/discovery/set", mode="ON")
        peer.call("/v1/presence/synthetic", presence="NEARBY")
    candidate = next(c for c in a.sync()["discovery"]["candidates"] if c["profile"]["nickname"] == "점검둘")
    a.call("/v1/connection/request", candidateId=candidate["id"])
    pending = next(r for r in b.sync()["connectionRequests"] if r["status"] == "PENDING" and r["direction"] == "INCOMING")
    connection_id = b.call("/v1/connection/respond", requestId=pending["id"], decision="ACCEPT")["connectionId"]
    print("connected through", BASE)
    va, vb = Voice(a, connection_id), Voice(b, connection_id)
    frame = b"".join(struct.pack("<h", int(6000 * math.sin(2 * math.pi * 440 * i / 16000))) for i in range(320))
    for _ in range(100):
        va.send_frame(frame)
        vb.send_frame(frame)
        time.sleep(0.02)
    time.sleep(1.0)
    print(f"voice frames: A->B {vb.received}/100, B->A {va.received}/100")
    outsider = Peer()
    outsider.token = outsider.call("/v1/session/enter", mode="SERVER_DEMO", clientSecret=secrets.token_urlsafe(32))["accessToken"]
    try:
        Voice(outsider, connection_id)
        print("outsider voice: ADMITTED (unexpected)")
    except AssertionError as error:
        print("outsider voice:", error)
    for peer in (a, b, outsider):
        peer.call("/v1/account/delete", post=True)
    print("probe accounts deleted")


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Exercise HTTP commands and authenticated WebSocket upgrades through Caddy.

Run via verify.sh, which starts the isolated server with CHAYEON_ALLOWED_ROOMS='*'.
"""
import sys
import time
from pathlib import Path
from urllib.request import urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from server_e2e import Peer, VoicePeer


def run(base):
    for attempt in range(120):
        try:
            with urlopen(base + "/health", timeout=2) as response:
                assert response.status == 200
            break
        except OSError:
            if attempt == 119:
                raise
            time.sleep(1)
    import secrets
    peers = []
    for nickname in ("ProofOne", "ProofTwo"):
        peer = Peer(0)
        peer.base = base
        session = peer.call("/v1/session/enter", mode="SERVER_DEMO", clientSecret=secrets.token_urlsafe(32))
        peer.token = session["accessToken"]
        peer.call("/v1/profile/save", draft={"nickname": nickname, "avatar": "DAWN", "interests": ["COFFEE"]})
        for scope in ("ADULT_ATTESTATION", "VEHICLE_DATA", "LOCATION_USE", "PUBLIC_PROFILE"):
            peer.call("/v1/profile/consent", scope=scope, action="GRANT", documentVersion=session["policy"]["consentDocumentVersion"])
        peer.call("/v1/presence/synthetic", presence="NEARBY")
        peer.call("/v1/discovery/set", mode="ON")
        peers.append(peer)
    first, second = peers
    candidate = next(item for item in first.sync()["discovery"]["candidates"] if item["profile"]["nickname"] == "ProofTwo")
    request = first.call("/v1/connection/request", candidateId=candidate["id"])
    accepted = second.call("/v1/connection/respond", requestId=request["id"], decision="ACCEPT")
    sockets = []
    try:
        for peer in peers:
            sockets.append(VoicePeer(peer, accepted["connectionId"]))
        print("PASS compose health, HTTP commands, two authenticated WebSocket 101 upgrades")
    finally:
        for channel in sockets:
            channel.close()
        for peer in peers:
            peer.call("/v1/account/delete", meta={"commandId": secrets.token_hex(16)})


if __name__ == "__main__":
    run(sys.argv[1])

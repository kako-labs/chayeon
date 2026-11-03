"""Exercise a running local server without writing bearer credentials or response bodies."""

import json
import sys
import urllib.request
import uuid


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:19876"
    room = "http-" + uuid.uuid4().hex[:12]

    def command(path, session=None, **fields):
        body = {"meta": {"commandId": str(uuid.uuid4())}, **fields}
        headers = {"Content-Type": "application/json"}
        if session:
            headers["Authorization"] = "Bearer " + session["accessToken"]
        request = urllib.request.Request(base + "/v1/" + path, json.dumps(body).encode(), headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)["result"]
        assert result["type"] == "success", "Command did not succeed: " + path
        return result["value"]

    def snapshot(session):
        request = urllib.request.Request(base + "/v1/sync?since=0", headers={"Authorization": "Bearer " + session["accessToken"]})
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)["result"]
        assert result["type"] == "success"
        return result["value"]

    def prepare(persona):
        session = command("session/enter", mode="SERVER_DEMO", persona=persona, roomCode=room)
        profile = command("profile/save", session, draft={"nickname": "가상프로필", "avatar": "DAWN", "interests": ["JAZZ"]})
        for scope in ["ADULT_ATTESTATION", "VEHICLE_DATA", "LOCATION_USE", "PUBLIC_PROFILE"]:
            command("profile/consent", session, scope=scope, action="GRANT", documentVersion=session["policy"]["consentDocumentVersion"])
        command("discovery/set", session, mode="ON")
        return session, profile

    first, _ = prepare("ACCOUNT_A")
    second, second_profile = prepare("ACCOUNT_B")
    request = command("connection/request", first, candidateId=second_profile["id"])
    accepted = command("connection/respond", second, requestId=request["id"], decision="ACCEPT")
    connection_id = accepted["connectionId"]
    command("connection/greeting", first, connectionId=connection_id, clientMessageId="http-greeting", code="HELLO")
    received = snapshot(second)
    assert any(item["connectionId"] == connection_id and item["author"] == "PEER" for item in received["greetings"])
    command("connection/leave", first, connectionId=connection_id)
    own = snapshot(first)
    peer = snapshot(second)
    assert len(own["encounters"]) == len(peer["encounters"]) == 1
    assert own["encounters"][0]["endReason"] == "SELF_LEFT"
    assert peer["encounters"][0]["endReason"] == "PEER_LEFT"
    command("encounters/delete", first, encounterId=own["encounters"][0]["id"])
    assert not snapshot(first)["encounters"]
    assert len(snapshot(second)["encounters"]) == 1
    command("session/sign-out", first)
    command("session/sign-out", second)
    print("PASS: two accounts, request, accept, peer greeting, leave, one encounter per side, private deletion, sign-out")


if __name__ == "__main__":
    main()

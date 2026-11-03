"""Exercise a running local server without writing bearer credentials or response bodies."""

import json
import secrets
import sys
import urllib.error
import urllib.request
import uuid


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:19876"
    room = "http-" + uuid.uuid4().hex[:12]
    credentials = {persona: secrets.token_urlsafe(32) for persona in ["ACCOUNT_A", "ACCOUNT_B"]}

    def command(path, session=None, expected_error=None, **fields):
        body = {"meta": {"commandId": str(uuid.uuid4())}, **fields}
        headers = {"Content-Type": "application/json"}
        if session:
            headers["Authorization"] = "Bearer " + session["accessToken"]
        request = urllib.request.Request(base + "/v1/" + path, json.dumps(body).encode(), headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.load(response)["result"]
        except urllib.error.HTTPError as response:
            result = json.load(response)["result"]
        if expected_error:
            assert result == {"type": "failure", "error": {"type": expected_error}}, "Unexpected rejection: " + path
            return None
        assert result["type"] == "success", "Command did not succeed: " + path
        return result["value"]

    def snapshot(session):
        request = urllib.request.Request(base + "/v1/sync?since=0", headers={"Authorization": "Bearer " + session["accessToken"]})
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)["result"]
        assert result["type"] == "success"
        return result["value"]

    def enter(persona):
        return command("session/enter", mode="SERVER_DEMO", persona=persona, roomCode=room, clientSecret=credentials[persona])

    def prepare(persona):
        session = enter(persona)
        profile = command("profile/save", session, draft={"nickname": "가상프로필", "avatar": "DAWN", "interests": ["JAZZ"]})
        for scope in ["ADULT_ATTESTATION", "VEHICLE_DATA", "LOCATION_USE", "PUBLIC_PROFILE"]:
            command("profile/consent", session, scope=scope, action="GRANT", documentVersion=session["policy"]["consentDocumentVersion"])
        command("discovery/set", session, mode="ON")
        return session, profile

    first, first_profile = prepare("ACCOUNT_A")
    second, second_profile = prepare("ACCOUNT_B")
    request = command("connection/request", first, candidateId=second_profile["id"])
    for credential in [None, secrets.token_urlsafe(32)]:
        command("session/enter", expected_error="not_authorized", mode="SERVER_DEMO", persona="ACCOUNT_B",
                roomCode=room, clientSecret=credential)
    assert snapshot(second)["connectionRequests"][0]["status"] == "PENDING"
    command("connection/respond", {"accessToken": "invalid"}, expected_error="not_authorized",
            requestId=request["id"], decision="ACCEPT")
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
    after = command("after/request", second, encounterId=peer["encounters"][0]["id"])
    connected = command("after/respond", first, requestId=after["id"], decision="ACCEPT")["afterConnectionId"]
    command("after/message", first, afterConnectionId=connected, clientMessageId="delete-me", content={"type": "text", "text": "다시 만나서 반가워요"})
    command("after/message", second, afterConnectionId=connected, clientMessageId="keep-me", content={"type": "text", "text": "저도 반가워요"})
    end_body = {"meta": {"commandId": str(uuid.uuid4())}, "afterConnectionId": connected}
    command("after/end", second, **end_body)
    before_replay = command("after/end", second, **end_body)
    command("safety/block", first, peerProfileId=second_profile["id"])
    assert command("after/end", second, **end_body)["availablePresets"] == before_replay["availablePresets"]
    before = snapshot(second)
    command("profile/save", first, draft={"nickname": "새로운가상상대", "avatar": "STAR", "interests": ["TRAVEL"]})
    after_edit = snapshot(second)
    assert {k: v for k, v in before.items() if k != "serverTimeEpochMillis"} == {k: v for k, v in after_edit.items() if k != "serverTimeEpochMillis"}
    sign_out_body = {"meta": {"commandId": str(uuid.uuid4())}}
    command("session/sign-out", first, **sign_out_body)
    recovered = enter("ACCOUNT_A")
    assert recovered["accountId"] == first["accountId"]
    command("session/sign-out", recovered, **sign_out_body)
    assert snapshot(recovered)["profile"]["id"] == first_profile["id"]
    command("account/delete", recovered)
    remaining = snapshot(second)
    assert remaining["afterConnections"][0]["closeReason"] == "ACCOUNT_DELETED"
    assert all(message["author"] == "SELF" for message in remaining["afterMessages"] + remaining["greetings"])
    recovered_second = enter("ACCOUNT_B")
    assert recovered_second["accountId"] == second["accountId"]
    command("session/sign-out", recovered_second)
    print("PASS: two accounts, consent, takeover rejection, forged accept rejection, greetings, private encounter deletion, after messages, replay privacy, hidden activity, session recovery, deletion purge")


if __name__ == "__main__":
    main()

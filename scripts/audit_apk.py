#!/usr/bin/env python3
"""Verify the install signature and inspect actual release DEX class definitions."""

import json
import hashlib
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile

try:
    from . import dev
except ImportError:
    import dev


def defined_classes(data):
    string_count, string_offset = struct.unpack_from("<II", data, 56)
    strings = []
    for index in range(string_count):
        offset = struct.unpack_from("<I", data, string_offset + index * 4)[0]
        while data[offset] & 128:
            offset += 1
        offset += 1
        strings.append(data[offset:data.index(0, offset)].decode("utf-8", errors="replace"))
    type_count, type_offset = struct.unpack_from("<II", data, 64)
    types = [strings[struct.unpack_from("<I", data, type_offset + index * 4)[0]] for index in range(type_count)]
    class_count, class_offset = struct.unpack_from("<II", data, 96)
    return [types[struct.unpack_from("<I", data, class_offset + index * 32)[0]] for index in range(class_count)]


def validate_network_security(tree):
    """Check compiled aapt2 XML structure, including the global cleartext policy."""
    roots = []
    stack = []
    for line in tree.splitlines():
        indent = len(line) - len(line.lstrip())
        content = line.strip()
        element = re.fullmatch(r"E: ([\w-]+)(?: \(line=\d+\))?", content)
        if element:
            while stack and stack[-1][0] >= indent:
                stack.pop()
            node = {"name": element.group(1), "attributes": {}, "children": [], "text": []}
            (stack[-1][1]["children"] if stack else roots).append(node)
            stack.append((indent, node))
        elif content.startswith("A:") and stack:
            name, separator, value = content[2:].strip().partition("=")
            if not separator or name in stack[-1][1]["attributes"]:
                raise AssertionError("네트워크 보안 속성을 확인해 주세요")
            stack[-1][1]["attributes"][name] = value
        elif content.startswith("T:") and stack:
            value = content[2:].strip()
            if len(value) >= 2 and value[0] in {"'", '"'} and value[-1] == value[0]:
                value = value[1:-1]
            stack[-1][1]["text"].append(value)
        elif content:
            raise AssertionError("네트워크 보안 XML 형식을 확인해 주세요")
    valid = len(roots) == 1 and roots[0]["name"] == "network-security-config"
    children = roots[0]["children"] if valid else []
    base = [node for node in children if node["name"] == "base-config"]
    configurations = [node for node in children if node["name"] == "domain-config"]
    valid = (valid and len(children) == 2 and len(base) == len(configurations) == 1
             and base[0]["attributes"] == {"cleartextTrafficPermitted": "false"}
             and not base[0]["children"]
             and configurations[0]["attributes"] == {"cleartextTrafficPermitted": "true"})
    domains = configurations[0]["children"] if configurations else []
    valid = (valid and len(domains) == 3
             and all(node["name"] == "domain" and node["attributes"] == {"includeSubdomains": "false"}
                     and not node["children"] and len(node["text"]) == 1 for node in domains)
             and {node["text"][0] for node in domains if node["text"]} == {"localhost", "127.0.0.1", "::1"})
    if not valid:
        raise AssertionError("릴리스 cleartext 예외가 localhost 범위를 벗어나요")


def audit(apk):
    apk = Path(apk).resolve()
    tools = "build-tools/" + dev.properties(dev.ROOT / "config/android-test.properties")["buildTools"]
    signer = dev.sdk_tool(tools, "apksigner")
    aapt = dev.sdk_tool(tools, "aapt2")
    environment = dev.execution_environment()
    if dev.java_home():
        environment["PATH"] = str(dev.java_home() / "bin") + os.pathsep + environment.get("PATH", "")
    subprocess.run([signer, "verify", "--verbose", str(apk)], env=environment, check=True)
    classes = []
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                classes.extend(defined_classes(archive.read(name)))
    forbidden = [name for name in classes if name.startswith(("Lorg/junit/", "Lorg/robolectric/", "Landroidx/test/", "Landroidx/compose/ui/test/", "Lcom/github/takahirom/roborazzi/"))
                 or any(value in name for value in ["VehicleProbeService", "VehicleProofInstrumentation", "VehicleConnectionProofReceiver", "EmptySignatureRepository", "EmptySessionRepository"])]
    if forbidden:
        raise AssertionError("릴리스에 테스트 또는 probe 클래스가 있어요: " + str(forbidden))
    manifest = subprocess.check_output([aapt, "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
    if "CAR_DRIVING_STATE" in manifest or "android:debuggable" in manifest:
        raise AssertionError("릴리스 manifest를 확인해 주세요")
    resources = subprocess.check_output([aapt, "dump", "resources", str(apk)], text=True)
    path = re.search(r"xml/network_security_config\s+\(\) \(file\) (\S+)", resources)
    if path is None:
        raise AssertionError("네트워크 보안 설정이 없어요")
    security = subprocess.check_output([aapt, "dump", "xmltree", str(apk), "--file", path.group(1)], text=True)
    validate_network_security(security)
    result = {"bytes": apk.stat().st_size, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "definedClasses": len(classes), "testOrProbeClasses": forbidden,
              "signatureVerified": True, "debuggable": False, "carDrivingStatePermission": False, "cleartext": "localhost-only"}
    target = dev.ROOT / "build/release-audit.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print("PASS release audit: signed, no test/probe classes, non-debuggable, cleartext localhost only", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(audit(sys.argv[1] if len(sys.argv) > 1 else dev.ROOT / "apk/chayeon-release.apk"))

"""Selection and command routing proofs. These tests never invoke a real adb."""

import os
import subprocess
import unittest
from unittest.mock import patch

import dev
from e2e import Device


class EmulatorSelectionTest(unittest.TestCase):
    def setUp(self):
        environment = patch.dict(os.environ, {}, clear=True)
        environment.start()
        self.addCleanup(environment.stop)
        sdk = patch.object(dev, "sdk_tool", return_value="adb")
        sdk.start()
        self.addCleanup(sdk.stop)

    def adb(self, names):
        def response(command, **kwargs):
            if command == ["adb", "devices"]:
                output = "List of devices attached\n" + "".join(serial + "\tdevice\n" for serial in names)
            else:
                self.assertEqual(command[1], "-s")
                self.assertEqual(command[3:], ["emu", "avd", "name"])
                output = names[command[2]] + "\nOK\n"
            return subprocess.CompletedProcess(command, 0, stdout=output)
        return patch.object(subprocess, "run", side_effect=response)

    def test_single_matching_emulator_is_selected(self):
        with self.adb({"emulator-5510": "Pleos_Connect_v2_0"}):
            self.assertEqual("emulator-5510", dev.emulator_serial())

    def test_single_emulator_of_another_project_is_never_selected(self):
        with self.adb({"emulator-5510": "another-product"}), self.assertRaisesRegex(RuntimeError, "--serial"):
            dev.emulator_serial()

    def test_exactly_one_matching_avd_is_selected_among_multiple_emulators(self):
        with self.adb({"emulator-5510": "another-product", "emulator-5530": "Pleos_Connect_v2_0"}):
            self.assertEqual("emulator-5530", dev.emulator_serial())

    def test_no_match_or_multiple_matches_fail(self):
        for names in [{}, {"emulator-5510": "other", "emulator-5530": "another"},
                      {"emulator-5510": "Pleos_Connect_v2_0", "emulator-5530": "Pleos_Connect_v2_0"}]:
            with self.subTest(names=names), self.adb(names), self.assertRaisesRegex(RuntimeError, "--serial"):
                dev.emulator_serial()

    def test_override_and_environment_never_probe_another_avd(self):
        names = {"emulator-5510": "another-product", "emulator-5530": "Pleos_Connect_v2_0"}
        for environment, override in [({}, "emulator-5530"), ({"CHAYEON_EMULATOR_SERIAL": "emulator-5530"}, None),
                                      ({"ANDROID_SERIAL": "emulator-5530"}, None)]:
            with self.subTest(environment=environment), patch.dict(os.environ, environment), self.adb(names) as run:
                self.assertEqual("emulator-5530", dev.emulator_serial(override))
                self.assertEqual(1, run.call_count)

    def test_disappeared_override_never_falls_back(self):
        with self.adb({"emulator-5510": "Pleos_Connect_v2_0"}) as run:
            with self.assertRaisesRegex(RuntimeError, "실행 중이지"):
                dev.emulator_serial("emulator-5530")
            self.assertEqual(1, run.call_count)

    def test_name_probe_failure_does_not_guess(self):
        with patch.object(subprocess, "run", side_effect=[
            subprocess.CompletedProcess([], 0, stdout="emulator-5510\tdevice\nemulator-5530\tdevice\n"),
            subprocess.TimeoutExpired("adb", 5),
        ]), self.assertRaisesRegex(RuntimeError, "이름을 확인하지 못했어요"):
            dev.emulator_serial()

    def test_every_device_command_including_port_forwarding_is_scoped(self):
        device = Device.__new__(Device)
        device.adb, device.serial = "adb", "emulator-5530"
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 0, stdout="OK")) as run:
            for command in [("forward", "tcp:8088", "tcp:8088"), ("reverse", "tcp:18765", "tcp:18765"),
                            ("install", "app.apk"), ("uninstall", "dev.chayeon"), ("shell", "getprop", "sys.boot_completed")]:
                device.run(*command)
                self.assertEqual(["adb", "-s", device.serial, *command], run.call_args.args[0])


if __name__ == "__main__":
    unittest.main()

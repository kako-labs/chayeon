"""Check the local server environment without starting Gradle, a server, or a device."""

import unittest
from unittest.mock import patch

import server_e2e


class ServerLaunchTest(unittest.TestCase):
    def test_local_e2e_server_overrides_production_room_allowlist(self):
        class LaunchCaptured(Exception):
            pass

        with patch.object(server_e2e, "Device"), \
                patch.object(server_e2e.dev, "gradle", return_value=0), \
                patch.object(server_e2e.dev, "execution_environment", return_value={"CHAYEON_ALLOWED_ROOMS": "default"}), \
                patch.object(server_e2e.subprocess, "Popen", side_effect=LaunchCaptured) as launch:
            with self.assertRaises(LaunchCaptured):
                server_e2e.run(port=18765, serial="test-device")
            self.assertEqual("*", launch.call_args.kwargs["env"]["CHAYEON_ALLOWED_ROOMS"])
            self.assertEqual("18765", launch.call_args.kwargs["env"]["PORT"])


if __name__ == "__main__":
    unittest.main()

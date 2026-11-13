import unittest

from audit_apk import validate_network_security


ACTUAL_TREE = """E: network-security-config (line=2)
    E: base-config (line=3)
      A: cleartextTrafficPermitted=false
    E: domain-config (line=4)
      A: cleartextTrafficPermitted=true
        E: domain (line=5)
          A: includeSubdomains=false
            T: 'localhost'
        E: domain (line=6)
          A: includeSubdomains=false
            T: '127.0.0.1'
        E: domain (line=7)
          A: includeSubdomains=false
            T: '::1'
"""


class NetworkSecurityAuditTest(unittest.TestCase):
    def test_actual_aapt2_single_quotes_and_double_quotes(self):
        validate_network_security(ACTUAL_TREE)
        validate_network_security(ACTUAL_TREE.replace("'", '"'))

    def test_rejects_non_loopback_and_subdomains(self):
        for tree in (ACTUAL_TREE.replace("localhost", "example.org"),
                     ACTUAL_TREE.replace("includeSubdomains=false", "includeSubdomains=true", 1)):
            with self.assertRaises(AssertionError):
                validate_network_security(tree)

    def test_rejects_global_cleartext_even_with_false_domain_attribute(self):
        tree = ACTUAL_TREE.replace("cleartextTrafficPermitted=false", "cleartextTrafficPermitted=true", 1)
        tree = tree.replace("A: cleartextTrafficPermitted=true\n        E:",
                            "A: cleartextTrafficPermitted=false\n        E:")
        with self.assertRaises(AssertionError):
            validate_network_security(tree)

    def test_rejects_missing_base_and_extra_domain_configuration(self):
        for tree in (ACTUAL_TREE.replace("    E: base-config (line=3)\n      A: cleartextTrafficPermitted=false\n", ""),
                     ACTUAL_TREE + "    E: domain-config (line=8)\n      A: cleartextTrafficPermitted=true\n"):
            with self.assertRaises(AssertionError):
                validate_network_security(tree)

"""UI v2 carousel selection against representative accessibility trees."""

import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET

from e2e import Device


class CarouselNavigationTest(unittest.TestCase):
    def device(self, names):
        device = Device.__new__(Device)
        index = [0]
        def tree():
            root = ET.Element("hierarchy")
            ET.SubElement(root, "node", text=f"오늘의 인연  {index[0] + 1} / {len(names)}")
            ET.SubElement(root, "node", text=names[index[0]], **{"content-desc": "프로필 보기"})
            return root
        def tap(label, **kwargs):
            if label == "다음 사람 보기":
                index[0] = (index[0] + 1) % len(names)
        device.tree = tree
        device.tap = Mock(side_effect=tap)
        device.shell = Mock()
        return device

    def test_later_peer_opens_accessible_profile_action(self):
        device = self.device(["첫 인연", "다음 인연", "별보는밤"])
        device.card("별보는밤")
        self.assertEqual(["다음 사람 보기", "다음 사람 보기", "프로필 보기"],
                         [call.args[0] for call in device.tap.call_args_list])
        device.tap.assert_called_with("프로필 보기", description_only=True, scroll=True)

    def test_inspection_does_not_open_the_profile(self):
        device = self.device(["첫 인연", "별보는밤"])
        device.card("별보는밤", navigate=False)
        self.assertEqual(["다음 사람 보기"], [call.args[0] for call in device.tap.call_args_list])

    def test_missing_peer_fails_after_one_carousel_cycle(self):
        device = self.device(["첫 인연", "다음 인연"])
        with self.assertRaisesRegex(AssertionError, "카드를 찾지 못했어요"):
            device.card("별보는밤")
        self.assertEqual(2, device.tap.call_count)


class ReadOnlyRevealTest(unittest.TestCase):
    def test_revealing_delete_action_never_activates_it(self):
        device = Device.__new__(Device)
        target = ET.Element("node", text="집 지우기", clickable="true")
        device.find = Mock(side_effect=[None, target])
        device.shell = Mock()
        device.tap = Mock()
        with patch("e2e.time.sleep"):
            self.assertIs(target, device.reveal("집 지우기"))
        self.assertTrue(all(call.args[:2] == ("input", "swipe") for call in device.shell.call_args_list))
        device.tap.assert_not_called()

    def test_missing_action_fails_without_tapping(self):
        device = Device.__new__(Device)
        device.find = Mock(return_value=None)
        device.shell = Mock()
        with patch("e2e.time.sleep"), self.assertRaisesRegex(AssertionError, "스크롤해도 찾지 못했어요"):
            device.reveal("집 지우기")
        self.assertTrue(all(call.args[:2] == ("input", "swipe") for call in device.shell.call_args_list))


if __name__ == "__main__":
    unittest.main()

"""Film the approved story (screen and app audio) on the dedicated release device, without app changes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import statistics
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'scripts'))
from e2e import Console, Device, enter_instagram_contact, enter_nickname

SERIAL = 'emulator-5570'
EVENTS = []
RECORDER = None
OUTPUT = None


def log(kind, **data):
    if RECORDER is not None and RECORDER.poll() is not None:
        raise RuntimeError('Recorder stopped unexpectedly')
    event = {'t': time.time_ns() / 1_000_000, 'kind': kind, **data}
    EVENTS.append(event)
    if OUTPUT:
        (OUTPUT / 'events.json').write_text(json.dumps(EVENTS, ensure_ascii=False, indent=2))
    print(json.dumps(event, ensure_ascii=False), flush=True)


def mark(name):
    log('mark', name=name)
    return time.monotonic()


def hold_until(start, seconds):
    while time.monotonic() - start < seconds:
        if RECORDER is not None and RECORDER.poll() is not None:
            raise RuntimeError('Recorder stopped during a hold')
        time.sleep(min(0.5, max(0, seconds - (time.monotonic() - start))))


class FilmDevice(Device):
    label = ''

    def shell(self, *args, **kwargs):
        if args[:2] == ('input', 'tap'):
            log('tap', text=self.label, x=int(args[2]), y=int(args[3]))
        elif args[:2] in [('input', 'swipe'), ('input', 'keyevent')]:
            log('input', args=list(args))
        return super().shell(*args, **kwargs)

    def tap(self, label, **kwargs):
        self.label = label
        return super().tap(label, **kwargs)

    def card(self, title, **kwargs):
        self.label = title + ' 프로필 보기'
        return super().card(title, **kwargs)


class FilmConsole(Console):
    def request(self, method, route, **parameters):
        if route != '/browser/session':
            log('console', method=method, route=route, **parameters)
        return super().request(method, route, **parameters)


def measure_clock(device):
    samples = []
    for _ in range(7):
        before = time.time_ns() / 1_000_000
        remote = float(device.shell('date', '+%s%3N').strip())
        after = time.time_ns() / 1_000_000
        samples.append({'offsetMs': remote - (before + after) / 2, 'roundTripMs': after - before})
    log('clock', emulatorMinusHostMs=statistics.median(s['offsetMs'] for s in samples), samples=samples)


def prepare(device):
    apk = ROOT / 'apk/chayeon-release.apk'
    device.run('uninstall', 'dev.chayeon', check=False)
    device.run('install', '--user', '10', str(apk))
    log('release', sha256=hashlib.sha256(apk.read_bytes()).hexdigest())
    device.launch()
    device.wait('저장하고 다음')
    enter_nickname(device)
    device.tap('저장하고 다음', scroll=True)
    device.wait('차량 데이터·위치·공개 범위')
    device.tap('성인이고, 직접 참여해요')
    device.tap('차량 데이터 사용에 동의해요')
    device.permissions()
    device.tap('위치 사용에 동의해요')
    device.tap('프로필 공개에 동의해요')
    device.permissions()
    device.tap('선택한 설정으로 시작')
    device.wait('내 시그니처 만들기')
    device.tap('발견 꺼짐', last=True)
    device.wait('받은 통화 요청 보기', timeout=30)
    device.capture('ready', ['시그니처 만들러 가기'])


def story(d, c):
    # Parked: the drive profile is born from three trips and its scores are published from 60.
    mark('parking')
    time.sleep(2)
    d.tap('시그니처 만들러 가기', scroll=True)
    d.tap('다음 움직임을 알려 주는 사람')
    mark('signature-wait')
    time.sleep(5)
    mark('signature-cut-start')
    d.wait('이 주행이 매듭이 됐어요', timeout=50)
    mark('signature-ready')
    time.sleep(6)
    d.tap('상대에게 어떻게 보일지 고르기')
    d.wait('공개 범위')
    time.sleep(1.5)
    d.tap('시그니처 공개')
    d.wait('고른 특성이 상대에게 보여요.')
    time.sleep(3)
    mark('my-profile')
    d.tap('내 프로필')
    d.wait('내 시그니처 보기')
    time.sleep(4)
    d.tap('내 시그니처 보기')
    d.tap('깜빡이 매너', scroll=True)
    d.wait('/ 100')
    time.sleep(6)
    # Meeting: scores on the card and the profile, then a call request answered by the partner.
    mark('discovery')
    d.tap('뒤로')
    d.tap('홈')
    d.wait('근처에 인연이 있어요')
    time.sleep(3)
    d.card('별보는밤')
    d.wait('통화 요청하기')
    mark('peer-profile')
    time.sleep(7)
    mark('call')
    d.tap('통화 요청하기')
    d.wait('통화 중', timeout=40)
    mark('call-connected')
    time.sleep(12)
    # On the road: the call stays, the screen keeps only mute and hang up.
    start = mark('road')
    c.gear('Drive')
    for speed in [10, 20, 30, 40]:
        c.gear('Drive', speed=speed)
    d.wait('주행 중', exact=False)
    hold_until(start, 18)
    mark('road-zero')
    c.gear('Drive', speed=0)
    time.sleep(6)
    mark('road-park')
    c.gear('Park')
    d.wait('이야깃거리')
    time.sleep(5)
    # Apart: the call ends by itself, then the after request exchanges messenger IDs.
    mark('panel-cut-start')
    d.tap('시연 제어', description_only=True)
    d.tap('서로 멀어져요')
    d.shell('input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(0.5)
    mark('distance')
    d.wait('연결 상태를 확인하고 있어요', timeout=8)
    d.wait('서로 멀어져서\n통화가 끝났어요')
    time.sleep(3)
    mark('after')
    d.tap('애프터 신청하기')
    d.wait('애프터 신청 보내기')
    time.sleep(2)
    enter_instagram_contact(d)
    d.tap('애프터 신청 보내기')
    d.wait('이어진 인연 보기')
    d.tap('이어진 인연 보기')
    d.wait('인스타그램 QR 코드')
    mark('exchanged')
    time.sleep(6)
    # Ten minutes later: conditions for connecting while driving, then an automatic call on the road.
    mark('cooldown-cut-start')
    hold_until(time.monotonic(), 640)
    story_auto(d, c)


def reset_for_auto(d):
    """Off camera: bring the demo partners back near and return the driving connect setting to its default."""
    d.tap('시연 제어', description_only=True)
    d.tap('가까이 있어요', scroll=True)
    d.shell('input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(2)
    d.wait('내 프로필')
    open_my_profile(d)
    d.tap('설정')
    d.tap('주행 중 연결', scroll=True)
    d.wait('달리는 동안 어떻게 이어질까요?')
    if d.find('80점 이상', d.tree()) is not None:
        for value in (75, 70, 65, 60):
            d.tap('깜빡이 매너 최소 점수 낮추기', description_only=True, scroll=True)
            d.wait(f'{value}점 이상')
        d.tap('깜빡이 매너 조건 사용', scroll=True)
    d.tap('끄기', scroll=True)
    d.tap('뒤로')
    d.tap('홈')
    d.wait('근처에 인연이 있어요')


def story_auto(d, c):
    # Ten minutes later: conditions for connecting while driving, then an automatic call on the road.
    mark('auto-settings')
    open_my_profile(d)
    d.tap('설정')
    d.tap('주행 중 연결', scroll=True)
    d.wait('달리는 동안 어떻게 이어질까요?')
    time.sleep(1.5)
    d.tap('조건에 맞으면 자동 연결', scroll=True)
    # Jazz's own request expires during the wait and Star's call ended today, so both are skipped for the day;
    # the gentle-braking condition is met by Slow afternoon, who accepts.
    d.tap('여유로운 제동 조건 사용', scroll=True)
    d.wait('60점 이상')
    for value in (65, 70, 75, 80, 85, 90):
        d.tap('여유로운 제동 최소 점수 높이기', description_only=True, scroll=True)
        d.wait(f'{value}점 이상')
    time.sleep(2.5)
    d.tap('뒤로')
    d.tap('홈')
    d.wait('근처에 인연이 있어요')
    time.sleep(1)
    began = mark('auto-drive')
    c.gear('Drive')
    for speed in [10, 20, 30, 40]:
        c.gear('Drive', speed=speed)
    d.wait('통화 끊기', timeout=60)
    mark('auto-connected')
    time.sleep(14)
    d.tap('통화 끊기')
    time.sleep(3)
    mark('drive-cut-start')
    hold_until(began, 215)
    mark('drive-cut-end')
    c.gear('Drive', speed=0)
    c.gear('Park')
    try:
        d.wait('주행 후 요약', timeout=15)
    except AssertionError:
        # The ended call screen can come first after parking; leave it and the recap follows.
        d.tap('홈으로')
        d.wait('주행 후 요약', timeout=30)
    mark('recap')
    time.sleep(7)
    d.tap('뒤로')
    story_home(d, c)


def open_my_profile(d):
    time.sleep(1)
    if d.find('홈으로', d.tree()) is not None:  # an ended-call screen has no tab bar
        d.tap('홈으로')
        d.wait('근처에 인연이 있어요')
    if d.find('설정', d.tree()) is None:  # the selected tab is not clickable, so only switch when elsewhere
        d.tap('내 프로필')


def story_home(d, c):
    # Home: discovery rests near home.
    mark('home')
    open_my_profile(d)
    d.tap('집 보호 구역', scroll=True)
    d.tap('집 보호 시작하기', scroll=True)
    d.wait('집 근처에서는 쉬고 있어요')
    d.tap('홈')
    d.wait('쉬는 중')
    mark('resting')
    time.sleep(5)
    mark('end')


def main():
    global OUTPUT, RECORDER
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--part', choices=['all', 'auto', 'home'], default='all')
    args = parser.parse_args()
    OUTPUT = args.output.expanduser().resolve()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    os.environ['CHAYEON_EMULATOR_SERIAL'] = SERIAL
    d = FilmDevice(serial=SERIAL, output=str(OUTPUT / 'checks'))
    if d.run('emu', 'avd', 'name').split()[0] != 'Pleos_Connect_v2_0':
        raise RuntimeError('Unexpected AVD')
    if not args.prepare and (OUTPUT / 'screen.json').exists():
        raise RuntimeError('Use a fresh take folder to preserve existing recordings')
    with FilmConsole(d) as c:
        if args.prepare:
            prepare(d)
            return
        installed = d.shell('pm', 'path', '--user', '10', 'dev.chayeon').strip().removeprefix('package:')
        expected = hashlib.sha256((ROOT / 'apk/chayeon-release.apk').read_bytes()).hexdigest()
        actual = hashlib.sha256(d.run('exec-out', 'cat', installed, binary=True)).hexdigest()
        if actual != expected:
            raise RuntimeError('Installed APK differs from the release APK')
        log('release', sha256=actual, installedApkMatches=True, serial=SERIAL, user=10)
        if args.part == 'auto':
            reset_for_auto(d)
        measure_clock(d)
        recorder = subprocess.Popen(['node', str(Path(__file__).with_name('screen.js')), str(OUTPUT / 'screen.mp4'), str(OUTPUT / 'screen.json')])
        audio = subprocess.Popen(['node', str(Path(__file__).with_name('audio.js')), str(OUTPUT / 'audio.pcm'), str(OUTPUT / 'audio.json')])
        try:
            deadline = time.monotonic() + 20
            while not (OUTPUT / 'screen.json').exists():
                if recorder.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('Recorder did not receive its first frame')
                time.sleep(0.1)
            time.sleep(2)
            RECORDER = recorder
            {'auto': story_auto, 'home': story_home}.get(args.part, story)(d, c)
            measure_clock(d)
        finally:
            RECORDER = None
            audio.send_signal(signal.SIGTERM)
            audio.wait(timeout=45)
            recorder.send_signal(signal.SIGTERM)
            recorder.wait(timeout=45)
            if recorder.returncode:
                raise RuntimeError('Recorder failed')
            if audio.returncode:
                raise RuntimeError('Audio recorder failed')


if __name__ == '__main__':
    main()

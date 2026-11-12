"""Film the approved story on the dedicated release device, without app changes."""
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
from e2e import Console, Device

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
    device.wait('이 계정으로 시작')
    device.tap('혼자 둘러보기(가상 상대)')
    device.tap('이 계정으로 시작')
    device.wait('저장하고 다음')
    device.tap('저장하고 다음', scroll=True)
    for label in ['성인이고, 직접 참여해요', '차량 데이터 사용에 동의해요', '위치 사용에 동의해요', '프로필 공개에 동의해요']:
        device.tap(label)
        device.permissions()
    device.tap('선택한 설정으로 시작')
    device.wait('내 시그니처 만들기')
    device.tap('발견 켜기')
    device.wait('받은 연결 요청 보기', timeout=30)
    device.capture('ready', ['시그니처 만들러 가기'])


def story(d, c):
    mark('parking')
    time.sleep(1)
    d.tap('시그니처 만들러 가기')
    d.tap('다음 움직임을 알려 주는 사람')
    mark('signature-wait')
    time.sleep(5)
    mark('signature-cut-start')
    d.wait('이 주행이 매듭이 됐어요', timeout=50)
    mark('signature-ready')
    time.sleep(6)
    d.tap('상대에게 어떻게 보일지 고르기')
    d.tap('시그니처 공개')
    d.wait('고른 특성이 상대에게 보여요.')
    time.sleep(2)
    mark('discovery')
    d.tap('오늘의 인연')
    time.sleep(1)
    d.card('별보는밤')
    d.wait('연결 요청 보내기')
    mark('peer-profile')
    time.sleep(8)
    mark('greeting')
    d.tap('연결 요청 보내기')
    d.wait('주고받은 인사')
    time.sleep(2)
    d.tap('안녕하세요', exact=False)
    d.wait('보냈어요', exact=False)
    time.sleep(3)
    start = mark('road')
    time.sleep(1)
    c.gear('Drive')
    for speed in [10, 20, 30, 40]:
        c.gear('Drive', speed=speed)
    hold_until(start, 16)
    mark('road-zero')
    c.gear('Drive', speed=0)
    time.sleep(7)
    mark('road-park')
    c.gear('Park')
    d.wait('주고받은 인사')
    hold_until(start, 33)
    mark('panel-cut-start')
    d.tap('시연 제어', description_only=True)
    d.tap('서로 멀어져요')
    d.shell('input', 'keyevent', 'KEYCODE_BACK')
    time.sleep(0.5)
    mark('distance')
    d.wait('연결 상태를 확인하고 있어요', timeout=8)
    d.wait('멀어져서 연결이 끝났어요')
    time.sleep(2)
    d.tap('기록 보기')
    d.wait('애프터 신청')
    mark('encounter')
    time.sleep(4)
    mark('after')
    d.tap('애프터 신청')
    d.tap('애프터 신청 보내기')
    d.wait('이어진 인연 보기')
    d.tap('이어진 인연 보기')
    d.wait('대화 중인 인연')
    d.tap('안녕하세요')
    d.wait('다시 이야기하게 되어 반가워요. 야경 보는 걸 좋아하세요?')
    time.sleep(3)
    mark('home-drive')
    c.gear('Drive')
    began = time.monotonic()
    c.gear('Drive', speed=40)
    time.sleep(3)
    mark('drive-cut-start')
    hold_until(began, 205)
    mark('drive-cut-end')
    c.gear('Drive', speed=0)
    c.gear('Park')
    d.wait('주행 후 요약', timeout=40)
    mark('recap')
    time.sleep(6)
    mark('home')
    d.tap('뒤로')
    d.tap('내 프로필')
    d.tap('집 보호 구역', scroll=True)
    d.tap('집 보호 시작하기', scroll=True)
    d.wait('집 근처에서는 쉬고 있어요')
    d.tap('오늘의 인연')
    d.wait('쉬는 중')
    mark('resting')
    time.sleep(5)
    mark('end')


def main():
    global OUTPUT, RECORDER
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    parser.add_argument('--prepare', action='store_true')
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
        measure_clock(d)
        recorder = subprocess.Popen(['node', str(Path(__file__).with_name('screen.js')), str(OUTPUT / 'screen.mp4'), str(OUTPUT / 'screen.json')])
        try:
            deadline = time.monotonic() + 20
            while not (OUTPUT / 'screen.json').exists():
                if recorder.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('Recorder did not receive its first frame')
                time.sleep(0.1)
            time.sleep(2)
            RECORDER = recorder
            story(d, c)
            measure_clock(d)
        finally:
            RECORDER = None
            recorder.send_signal(signal.SIGTERM)
            recorder.wait(timeout=45)
            if recorder.returncode:
                raise RuntimeError('Recorder failed')


if __name__ == '__main__':
    main()

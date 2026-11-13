import recording from '../data/story.json';

export const FPS = 30;
export const TITLE_FRAMES = 105;
export const ENDING_FRAMES = 180;
export type Scene = {id: string; from: number; duration: number; chapter: string; caption: string; cut?: string; highlight?: [number, number, number, number]};
const M = recording.marks as Record<string, number>;
const f = (seconds: number) => Math.round(seconds * FPS);
// The first recorded tap with this text at or after `after` (seconds); later takes re-film earlier attempts.
const tap = (text: string, after = 0) => {
  const found = recording.taps.find(t => t.text === text && t.sec >= after);
  if (!found) throw new Error(`Missing recorded tap: ${text}`);
  return found.sec;
};
const shot = (id: string, begin: number, end: number, chapter: string, caption: string, cut?: string): Scene => {
  if (!Number.isFinite(begin) || !Number.isFinite(end) || end <= begin) throw new Error(`Invalid interval: ${id}`);
  return {id, from: f(begin), duration: f(end) - f(begin), chapter, caption, cut};
};

// Playback is always 1x. Every skipped source interval is labelled on the band (cut), so nothing is hidden.
// The voice lines land at call +0 s, +20 s and +40 s; the auto-connect announcement precedes the first line.
export const CHAPTERS = ['주차', '만남', '통화', '달리며 통화', '멀어짐', '애프터', '자동 연결', '집으로', '집'];
const PROFILE = '운전 습관과 차량 관리가 점수가 되어 나를 보여 줘요.';
const SHORT = '잠시 뒤';
export const SCENES: Scene[] = [
  shot('parking', tap('시그니처 만들러 가기') - 1.6, M['signature-cut-start'], '주차', PROFILE),
  shot('signature', M['signature-ready'] - 1, tap('상대에게 어떻게 보일지 고르기') - 1, '주차', PROFILE, '30초 뒤'),
  shot('privacy', tap('상대에게 어떻게 보일지 고르기') - 1, M['my-profile'], '주차', '60점이 넘는 특성만 골라 상대에게 보여요.'),
  shot('my-profile', M['my-profile'], tap('내 프로필') + 5, '주차', '점수마다 어떤 주행에서 나왔는지 근거를 볼 수 있어요.'),
  shot('evidence', tap('깜빡이 매너') + 0.4, tap('깜빡이 매너') + 6.4, '주차', '점수마다 어떤 주행에서 나왔는지 근거를 볼 수 있어요.', SHORT),
  shot('discovery', tap('홈') + 2.9, M['peer-profile'], '만남', '근처에 주차한 사람을 운전 프로필로 만나요.', SHORT),
  shot('profile', M['peer-profile'], M.call + 0.9, '만남', '공개한 점수와 함께하면 잘 맞는 점이 보여요.'),
  shot('call', M.call + 0.9, M['call-connected'] + 1.6, '통화', '상대가 요청을 받으면 바로 목소리로 이어져요.'),
  shot('road', M.road, M.road + 10.6, '달리며 통화', '달리는 동안에도 통화는 이어지고, 음소거와 끊기만 남아요.', SHORT),
  shot('park-again', M['road-zero'] - 0.5, M['road-zero'] + 10.5, '달리며 통화', '멈춰 있어도 D면 주행 중이에요. 주차하면 다시 펼쳐져요.', SHORT),
  shot('distance', M.distance + 1, M.distance + 11, '멀어짐', '서로 멀어지면 통화가 저절로 끝나요.', '서로 멀어진 뒤'),
  shot('after', tap('애프터 신청하기') - 1.1, tap('애프터 신청 보내기') + 1.5, '애프터', '다시 이어지고 싶으면 애프터를 신청해요.', SHORT),
  shot('exchanged', tap('이어진 인연 보기') - 0.6, M['cooldown-cut-start'] - 1, '애프터', '둘 다 수락하면 그때 서로의 아이디가 보여요.', SHORT),
  shot('auto-settings', tap('주행 중 연결', M['auto-settings']) - 0.5, tap('여유로운 제동 조건 사용', M['auto-settings']) + 0.8, '자동 연결', '조건을 정해 두면 달리는 중에도 맞는 사람과 이어져요.', '10분 뒤'),
  shot('auto-minimum', M['auto-drive'] - 13.6, M['auto-drive'], '자동 연결', '조건을 정해 두면 달리는 중에도 맞는 사람과 이어져요.', SHORT),
  // Ends before the partner's scripted step away (12 s into the call); the drive continues behind the cut.
  shot('auto-drive', M['auto-drive'], M['auto-connected'] + 6, '자동 연결', '조건에 맞으면 자동으로 이어지고, 이름을 한 번 알려 줘요.'),
  shot('recap', M['drive-cut-end'] + 1.4, M.recap + 7, '집으로', '실제 차량 신호로 달린 주행도 프로필에 쌓여요.', '3분 뒤'),
  shot('home', M.end - 13.4, M.end, '집', '집 근처에서는 발견을 쉬어요.', SHORT),
];

// Source seconds of the demo partner's voice lines, measured from the take audio (prepare.py loudness).
// Only these are silenced in the delivered video; the Pleos connection announcement and car sounds stay.
export const PARTNER_VOICE: [number, number][] = [[113.6, 116.0], [133.6, 137.0], [153.7, 156.5], [1000.45, 1002.3], [1013.3, 1015.3]];

export const PLACED = SCENES.map((scene, index) => ({...scene, start: TITLE_FRAMES + SCENES.slice(0, index).reduce((sum, previous) => sum + previous.duration, 0)}));
export const MAIN_END = TITLE_FRAMES + SCENES.reduce((sum, scene) => sum + scene.duration, 0);
export const TOTAL_FRAMES = MAIN_END + ENDING_FRAMES;

import recording from '../data/story.json';

export const FPS = 30;
export const TITLE_FRAMES = 105;
export const ENDING_FRAMES = 180;
export type Scene = {id: string; from: number; duration: number; chapter: string; caption: string; cut?: string; highlight?: [number, number, number, number]};
const M = recording.marks as Record<string, number>;
const f = (seconds: number) => Math.round(seconds * FPS);
const tap = (text: string) => {
  const found = recording.taps.find(t => t.text === text);
  if (!found) throw new Error(`Missing recorded tap: ${text}`);
  return found.sec;
};
const shot = (id: string, begin: number, end: number, chapter: string, caption: string, cut?: string): Scene => {
  if (!Number.isFinite(begin) || !Number.isFinite(end) || end <= begin) throw new Error(`Invalid interval: ${id}`);
  return {id, from: f(begin), duration: f(end) - f(begin), chapter, caption, cut};
};

// Source intervals are contiguous except the three explicitly labelled cuts. Playback is always 1x.
export const SCENES: Scene[] = [
  shot('parking', M.parking, M['signature-cut-start'], '주차', '주행 습관과 차량 관리가 나만의 매듭과 칭호가 됩니다.'),
  shot('signature', M['signature-ready'] - 1, tap('상대에게 어떻게 보일지 고르기') - 1, '주차', '주행 습관과 차량 관리가 나만의 매듭과 칭호가 됩니다.', '30초 뒤'),
  shot('privacy', tap('상대에게 어떻게 보일지 고르기') - 1, M.discovery, '주차', '무엇을 보여줄지는 내가 고르고, 수치와 순위는 보이지 않아요.'),
  shot('discovery', M.discovery, M['peer-profile'], '발견', '근처에서 주차한 사람을 시그니처로 만납니다.'),
  shot('profile', M['peer-profile'], M.greeting, '발견', '운전하는 모습이 서로 어떻게 잘 맞는지 보여 줍니다.'),
  shot('greeting', M.greeting, M.road, '인사', '양쪽이 수락해야만 이어지고, 준비된 문장으로 짧게 인사합니다.'),
  shot('road', M.road, M['road-zero'], '다시 길 위로', '달리는 동안은 화면을 잠그고 나가기만 남깁니다.'),
  shot('park-again', M['road-zero'], M['panel-cut-start'], '다시 길 위로', '멈춰 있어도 D면 잠겨 있고, 주차하면 그대로 이어집니다.'),
  shot('distance', M.distance + 1, M.encounter, '멀어짐', '서로 멀어지면 잠시 확인한 뒤 연결이 저절로 끝납니다.', '서로 멀어진 뒤'),
  shot('encounter', M.encounter, M.after, '멀어짐', '기록에는 시각과 기간만 남고, 위치와 경로는 남지 않습니다.'),
  shot('after', M.after, M['home-drive'], '애프터', '다시 이어지고 싶을 때만 애프터를 보냅니다.'),
  shot('home-drive', M['home-drive'], M['drive-cut-start'], '집으로', '오늘 달린 길도 시그니처에 더해집니다.'),
  shot('recap', M['drive-cut-end'], M.home, '집으로', '오늘 달린 길도 시그니처에 더해집니다.', '3분 뒤'),
  shot('home', M.home, M.end, '집', '집 근처에서는 발견을 쉬고, 상대에게는 멀어진 것과 똑같이 보입니다.'),
];

export const PLACED = SCENES.map((scene, index) => ({...scene, start: TITLE_FRAMES + SCENES.slice(0, index).reduce((sum, previous) => sum + previous.duration, 0)}));
export const MAIN_END = TITLE_FRAMES + SCENES.reduce((sum, scene) => sum + scene.duration, 0);
export const TOTAL_FRAMES = MAIN_END + ENDING_FRAMES;

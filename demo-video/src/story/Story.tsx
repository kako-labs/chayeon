import {AbsoluteFill, Img, OffthreadVideo, Sequence, staticFile, useCurrentFrame} from 'remotion';
import {FRAME, THEME} from '../config/theme';
import recording from '../data/story.json';
import {CHAPTERS, FPS, MAIN_END, PLACED, TITLE_FRAMES, type Scene} from './scenes';

const fontFace = `@font-face{font-family:'Noto Sans KR';src:url('${staticFile('fonts/NotoSansKR-VF.ttf')}');font-weight:100 900;}`;
const Brand = () => <div style={{display: 'flex', alignItems: 'center', gap: 30}}>
  <Img src={staticFile('story/app-icon.jpg')} style={{width: 140, height: 140, borderRadius: 32}} />
  <div style={{textAlign: 'left'}}><div style={{fontSize: 88, fontWeight: 900}}>차연</div>
    <div style={{fontSize: 34, color: THEME.secondary}}>길 위에서 시작되는 인연</div></div>
</div>;

const Shot = ({scene}: {scene: Scene}) => {
  const frame = useCurrentFrame();
  const sourceTime = (scene.from + frame) / FPS;
  const taps = recording.taps.filter(t => sourceTime >= t.sec && sourceTime < t.sec + 0.7);
  return <AbsoluteFill>
    <div style={{position: 'absolute', left: FRAME.x, top: FRAME.y, width: FRAME.w, height: FRAME.h}}>
      <OffthreadVideo src={staticFile('rec/story.mp4')} trimBefore={scene.from} playbackRate={1} muted style={{width: FRAME.w, height: FRAME.h, objectFit: 'contain'}} />
      {taps.map((tap, i) => <div key={i} style={{position: 'absolute', left: tap.x * FRAME.w / 2560 - 21, top: tap.y * FRAME.h / 1440 - 21, width: 42, height: 42, borderRadius: '50%', border: `3px solid ${THEME.accent}`, background: 'rgba(255,154,166,0.15)', opacity: 1 - (sourceTime - tap.sec) / 0.7}} />)}
      {scene.highlight ? <div style={{position: 'absolute', left: scene.highlight[0] * FRAME.w / 2560, top: scene.highlight[1] * FRAME.h / 1440, width: scene.highlight[2] * FRAME.w / 2560, height: scene.highlight[3] * FRAME.h / 1440, border: `2px solid ${THEME.accent}`, borderRadius: 6}} /> : null}
    </div>
    <div style={{position: 'absolute', left: FRAME.x, top: 920, width: FRAME.w}}>
      <div style={{display: 'flex', gap: 25, alignItems: 'center', fontSize: 21, height: 36}}>
        {CHAPTERS.map(chapter => <span key={chapter} style={{color: chapter === scene.chapter ? THEME.accent : THEME.secondary, opacity: chapter === scene.chapter ? 1 : 0.5, fontWeight: chapter === scene.chapter ? 800 : 500}}>{chapter}</span>)}
        {scene.cut ? <span style={{marginLeft: 'auto', border: `1px solid ${THEME.border}`, borderRadius: 18, padding: '4px 16px', color: THEME.accent, fontWeight: 700}}>{scene.cut}</span> : null}
      </div>
      <div style={{fontSize: 40, fontWeight: 800, marginTop: 10, lineHeight: 1.45, whiteSpace: 'nowrap'}}>{scene.caption}</div>
    </div>
  </AbsoluteFill>;
};

export const Story = () => {
  const frame = useCurrentFrame();
  return <AbsoluteFill style={{background: THEME.bg, color: THEME.text, fontFamily: THEME.font}}>
    <style>{fontFace}</style>
    {frame < TITLE_FRAMES ? <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', opacity: Math.min(1, frame / 12, (TITLE_FRAMES - frame) / 10)}}><Brand /></AbsoluteFill> : null}
    {PLACED.map(scene => <Sequence key={scene.id} from={scene.start} durationInFrames={scene.duration}><Shot scene={scene} /></Sequence>)}
    {frame >= MAIN_END ? <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center'}}>
      <Brand />
      <div style={{fontSize: 24, color: THEME.accent, marginTop: 65}}>영상에 나온 차량 기능</div>
      <div style={{fontSize: 34, marginTop: 16}}>기어 · 속도 · 주행 신호 · 위치 · Pleos 음성</div>
      <div style={{position: 'absolute', bottom: 90, fontSize: 25, lineHeight: 1.8, textAlign: 'center', color: THEME.secondary}}>
        Pleos Connect 에뮬레이터에서 녹화했어요.<br />
        주행은 Virtual Console 신호이고, 상대는 앱이 만든 가상 계정이에요.<br />
        실차에서는 확인하지 않았어요.
      </div>
    </AbsoluteFill> : null}
  </AbsoluteFill>;
};

import {Composition} from 'remotion';
import {Story} from './story/Story';
import {FPS, TOTAL_FRAMES} from './story/scenes';

export const Root = () => <Composition id="ChayeonStory" component={Story} durationInFrames={TOTAL_FRAMES} fps={FPS} width={1920} height={1080} />;

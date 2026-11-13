// Records the emulator's audio output over gRPC until SIGTERM, keeping each packet's capture time.
// usage: node audio.js <out.pcm> <meta.json>
// Output: 48 kHz stereo signed 16-bit little-endian packets appended to <out.pcm>, and in <meta.json> the
// byte offset and capture time (epoch ms) of every packet, so the track can be placed on the video clock.
const fs = require('fs');
const path = require('path');
const grpc = require('@grpc/grpc-js');
const loader = require('@grpc/proto-loader');

const [out, metaPath] = process.argv.slice(2);
const lib = path.join(process.env.HOME, 'Library/Android/sdk/emulator/lib');
const def = loader.loadSync(path.join(lib, 'emulator_controller.proto'), { includeDirs: [lib], keepCase: true, longs: Number, enums: String });
const pkg = grpc.loadPackageDefinition(def).android.emulation.control;
const client = new pkg.EmulatorController('localhost:8590', grpc.credentials.createInsecure());

const RATE = 48000;
const sink = fs.createWriteStream(out);
const packets = [];
let bytes = 0;
let stopping = false;
const call = client.streamAudio({ format: 'AUD_FMT_S16', channels: 'Stereo', samplingRate: RATE, mode: 'MODE_REAL_TIME' });
call.on('data', (packet) => {
  if (!packet.audio || packet.audio.length === 0) return;
  packets.push({ offset: bytes, length: packet.audio.length, atMs: packet.timestamp / 1000 });
  bytes += packet.audio.length;
  sink.write(packet.audio);
});
call.on('error', (e) => { if (e.code !== grpc.status.CANCELLED) { console.error('audio error', e.message); process.exitCode = 1; stop(); } });

const stop = () => {
  if (stopping) return;
  stopping = true;
  call.cancel();
  sink.end(() => {
    fs.writeFileSync(metaPath, JSON.stringify({ rate: RATE, channels: 2, sampleFormat: 's16le', bytes, packets }, null, 1));
    process.exit(process.exitCode || 0);
  });
};
process.on('SIGTERM', stop);
process.on('SIGINT', stop);

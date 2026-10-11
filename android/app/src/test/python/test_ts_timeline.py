import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from nanzhufeng_probe.ts_timeline import normalize_ts_discontinuities


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'local media tools required')
class TsTimelineTest(unittest.TestCase):
    def test_declared_discontinuity_preserves_all_packets_on_one_continuous_timeline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            clip = root / 'clip.ts'
            subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'testsrc2=size=160x90:rate=25:duration=2',
                            '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=44100:duration=2',
                            '-c:v', 'libx264', '-bf', '2', '-c:a', 'aac', '-f', 'mpegts', str(clip)], check=True)
            source = root / 'source.segments'
            source.write_bytes(clip.read_bytes() * 2)
            before = self.packets(source)
            self.assertTrue(any(b['dts_time'] < a['dts_time'] for a, b in zip(before[0], before[0][1:])))
            target = Path(normalize_ts_discontinuities(str(source), 1, lambda: False))
            # A non-picture AVC delimiter drains the last real access unit in Android.
            # It must not introduce a new decoded frame.
            self.assertEqual(source.stat().st_size + 188, target.stat().st_size)
            counters = {}
            data = target.read_bytes()
            for start in range(0, len(data), 188):
                packet = data[start:start + 188]
                pid = ((packet[1] & 31) << 8) | packet[2]
                if ((packet[3] >> 4) & 3) not in (1, 3) or pid == 8191:
                    continue
                counter = packet[3] & 15
                if pid in counters:
                    self.assertEqual((counters[pid] + 1) % 16, counter)
                counters[pid] = counter
            after = self.packets(target)
            for track in before:
                media = after[track][:len(before[track])]
                self.assertEqual([p['data_hash'] for p in before[track]], [p['data_hash'] for p in media])
                self.assertEqual(len(before[track]) + (1 if track == 0 else 0), len(after[track]))
                deltas = [float(b['dts_time']) - float(a['dts_time']) for a, b in zip(media, media[1:])]
                # TS keeps the shared clock; AAC encoder padding can overlap by
                # one frame at a boundary and is handled by the AAC sample clock.
                self.assertTrue(all(delta > (0 if track == 0 else -0.025) for delta in deltas))
            video_times = [float(p['dts_time']) for p in after[0][:len(before[0])]]
            self.assertAlmostEqual(3.96, video_times[-1] - video_times[0], places=2)
            original_pixels = self.video_pixels(source)
            self.assertEqual(100 * 160 * 90 * 3 // 2, len(original_pixels))
            self.assertEqual(original_pixels, self.video_pixels(target))
            # Two declared 2-second clips must survive. A third, byte-identical
            # append left by an overlapping resume is transport noise, not a clip.
            duplicated = root / 'overlapping-resume.segments'
            duplicated.write_bytes(clip.read_bytes() * 3)
            recovered = normalize_ts_discontinuities(str(duplicated), 1, lambda: False,
                                                    expected_duration=4.0, expected_boundaries=[2.0])
            self.assertEqual(original_pixels, self.video_pixels(recovered))
            self.assertEqual(clip.read_bytes() * 3, duplicated.read_bytes())
            unproven = bytearray(duplicated.read_bytes())
            unproven[len(clip.read_bytes()) * 2 + 1000] ^= 1
            bad = root / 'non-identical-repeat.segments'
            bad.write_bytes(unproven)
            with self.assertRaisesRegex(ValueError, '可证明的重复分片'):
                normalize_ts_discontinuities(str(bad), 1, lambda: False,
                                            expected_duration=4.0, expected_boundaries=[2.0])
            self.assertEqual(unproven, bad.read_bytes())
            self.assertEqual(clip.read_bytes() * 2, source.read_bytes())
            damaged = bytearray(source.read_bytes())
            media_packets = [offset for offset in range(0, len(damaged), 188)
                             if ((damaged[offset + 1] & 31) << 8 | damaged[offset + 2]) == 256
                             and ((damaged[offset + 3] >> 4) & 3) in (1, 3)]
            counter_offset = media_packets[10] + 3
            damaged[counter_offset] = (damaged[counter_offset] & 0xf0) | ((damaged[counter_offset] + 4) & 15)
            broken = root / 'missing-packet.segments'
            broken.write_bytes(damaged)
            with self.assertRaisesRegex(ValueError, '非声明切换'):
                normalize_ts_discontinuities(str(broken), 1, lambda: False)
            self.assertEqual(damaged, broken.read_bytes())
            with self.assertRaisesRegex(ValueError, '切换与媒体时钟不一致'):
                normalize_ts_discontinuities(str(source), 2, lambda: False)
            self.assertEqual(clip.read_bytes() * 2, source.read_bytes())

    def test_continuous_ts_drains_last_picture_without_changing_source_or_pixels(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'continuous.ts'
            subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'testsrc2=size=160x90:rate=10:duration=3',
                            '-c:v', 'libx264', '-bf', '2', '-f', 'mpegts', str(source)], check=True)
            original = source.read_bytes()
            target = Path(normalize_ts_discontinuities(str(source), 0, lambda: False))
            self.assertNotEqual(source, target)
            self.assertEqual(original, source.read_bytes())
            self.assertEqual(len(original) + 188, target.stat().st_size)
            self.assertEqual(self.video_pixels(source), self.video_pixels(target))
            self.assertEqual(30, len(self.packets(target)[0]) - 1)

    def test_cancel_keeps_source_and_removes_only_this_attempt_partial(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.ts'
            source.write_bytes(b'original evidence')
            with self.assertRaises(InterruptedError):
                normalize_ts_discontinuities(str(source), 1, lambda: True)
            self.assertEqual(b'original evidence', source.read_bytes())
            self.assertFalse(Path(str(source) + '.timeline.ts.part').exists())

    def test_unexpected_clock_jump_is_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'bad.ts'
            source.write_bytes(b'bad transport stream')
            with self.assertRaises(ValueError):
                normalize_ts_discontinuities(str(source), 1, lambda: False)
            self.assertFalse(Path(str(source) + '.timeline.ts').exists())

    @staticmethod
    def video_pixels(path):
        return subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-map', '0:v:0', '-fps_mode', 'passthrough',
            '-pix_fmt', 'yuv420p', '-f', 'rawvideo', '-'], check=True, capture_output=True).stdout

    @staticmethod
    def packets(path):
        result = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_packets', '-show_data_hash', 'sha256',
            '-show_entries', 'packet=stream_index,dts_time,data_hash', '-of', 'json', str(path)]))
        tracks = {}
        for packet in result['packets']:
            tracks.setdefault(packet['stream_index'], []).append(packet)
        return tracks

"""Real yt-dlp native fragment downloads from locally generated, non-user media."""
import functools
import http.server
import json
import shutil
import subprocess
import tempfile
import threading
import time
import hashlib
import unittest
from unittest import mock
from types import SimpleNamespace
import nanzhufeng_probe.segment_transfer as transfer_module
from pathlib import Path

from yt_dlp import YoutubeDL
from yt_dlp.utils import DownloadError
from nanzhufeng_probe.segment_transfer import download_segments, TransferCancelled, _hls_variant


class Observer:
    def __init__(self, cancel_after=None, connections=1):
        self.updates = []
        self.cancel_after = cancel_after
        self.network_bytes = 0
        self.connection_count = connections
        self.http_status = 0

    def connections(self):
        return self.connection_count

    def httpFailure(self, status):
        self.http_status = status

    def network(self, total):
        self.network_bytes = total

    def cancelled(self):
        return self.cancel_after is not None and len(self.updates) >= self.cancel_after

    def progress(self, downloaded, total):
        self.updates.append((downloaded, total))


@unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "Requires local FFmpeg fixture tools")
class SegmentTransferTest(unittest.TestCase):
    def test_master_variant_selects_bounded_self_contained_track(self):
        manifest = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360
low.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
high.m3u8
"""
        self.assertEqual("https://example.test/low.m3u8",
                         _hls_variant(manifest, "https://example.test/master.m3u8", 360))
        self.assertEqual("https://example.test/high.m3u8",
                         _hls_variant(manifest, "https://example.test/master.m3u8", 720))
        with self.assertRaisesRegex(ValueError, "独立音轨"):
            _hls_variant("#EXT-X-STREAM-INF:BANDWIDTH=5,AUDIO=\"audio\"\nvideo.m3u8",
                         "https://example.test/master.m3u8", 720)

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        fixture = self
        self.fragment_delay = 0
        self.active_fragments = 0
        self.peak_fragments = 0
        self.fragment_lock = threading.Lock()
        self.fragment_requests = []
        class Handler(http.server.SimpleHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def do_GET(self):
                if self.path.endswith(".ts"):
                    fixture.fragment_requests.append(self.path)
                delayed = (fixture.fragment_delay and self.path.endswith(".ts")
                           and (fixture.root / Path(self.path).name).is_file())
                if delayed:
                    with fixture.fragment_lock:
                        fixture.active_fragments += 1
                        fixture.peak_fragments = max(fixture.peak_fragments, fixture.active_fragments)
                    time.sleep(fixture.fragment_delay)
                try:
                    super().do_GET()
                finally:
                    if delayed:
                        with fixture.fragment_lock:
                            fixture.active_fragments -= 1
        handler = functools.partial(Handler, directory=self.tmp.name)
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_server)
        self.base = f"http://127.0.0.1:{self.server.server_port}/"

    def test_slow_fragment_reconnects_once_without_losing_complete_media(self):
        subprocess.run(["ffmpeg", "-v", "error", "-f", "lavfi", "-i", "testsrc2=s=640x360:r=25:d=8",
                        "-f", "lavfi", "-i", "sine=frequency=440:duration=8", "-c:v", "libx264",
                        "-preset", "ultrafast", "-crf", "18", "-g", "250", "-c:a", "aac", "-f", "hls",
                        "-hls_list_size", "0", "-hls_time", "8", str(self.root / "index.m3u8")],
                       check=True, capture_output=True, timeout=30)
        fragment = next(self.root.glob("*.ts"))
        self.assertGreater(fragment.stat().st_size, 512 * 1024)
        ticks = iter(range(0, 100000, 3))
        observer = Observer()
        spec = json.dumps({"id": "slow-fixture", "url": self.base + "index.m3u8", "ext": "mp4",
                           "protocol": "m3u8_native", "http_headers": {}})
        with mock.patch.object(transfer_module, "time", SimpleNamespace(monotonic=lambda: next(ticks))):
            raw = download_segments(spec, str(self.root / "slow-recovery"), observer)
        self.assertEqual(2, len(self.fragment_requests), "A severe trickle needs exactly one reconnect")
        self.assertEqual(hashlib.sha256(fragment.read_bytes()).digest(), hashlib.sha256(self.transport_payload(raw).read_bytes()).digest())
        self.assertEqual({"video", "audio"}, self.probe_tracks(raw))

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def generate(self, segment_type="mpegts", duration=3):
        subprocess.run(["ffmpeg", "-v", "error", "-f", "lavfi", "-i", f"color=c=blue:s=160x90:r=10:d={duration}",
                        "-f", "lavfi", "-i", f"sine=frequency=440:duration={duration}", "-c:v", "libx264", "-g", "10",
                        "-c:a", "aac", "-f", "hls", "-hls_list_size", "0", "-hls_time", "1", "-hls_segment_type", segment_type,
                        str(self.root / "index.m3u8")], check=True, timeout=30, capture_output=True)
        return json.dumps({"id": "fixture", "url": self.base + "index.m3u8", "ext": "mp4",
                           "protocol": "m3u8_native", "http_headers": {}})

    @staticmethod
    def transport_payload(path):
        # Android's normalized TS includes a non-picture drain delimiter.
        # Byte transport and strict decoding use the preserved original payload;
        # timeline tests independently compare every decoded pixel after draining.
        return Path(str(path).removesuffix(".timeline.ts"))

    def probe_tracks(self, path):
        result = subprocess.run(["ffprobe", "-v", "error", "-show_streams", "-of", "json", path],
                                check=True, capture_output=True, text=True, timeout=10)
        return {s["codec_type"] for s in json.loads(result.stdout)["streams"]}

    def test_hls_ts_and_fmp4_have_actual_audio_video_and_progress(self):
        for kind in ("mpegts", "fmp4"):
            with self.subTest(container=kind):
                spec = self.generate(kind)
                observer = Observer()
                raw = download_segments(spec, str(self.root / f"out-{kind}"), observer)
                self.assertEqual({"video", "audio"}, self.probe_tracks(raw))
                self.assertGreater(self.transport_payload(raw).stat().st_size, 1000)
                self.assertTrue(any(done > 0 for done, _ in observer.updates))
                self.assertGreaterEqual(observer.network_bytes, self.transport_payload(raw).stat().st_size)

    def test_hls_master_downloads_selected_variant(self):
        spec = json.loads(self.generate())
        (self.root / "master.m3u8").write_text(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=320x180\nindex.m3u8\n")
        spec["url"] = self.base + "master.m3u8"
        spec["max_height"] = 720
        raw = download_segments(json.dumps(spec), str(self.root / "master-out"), Observer())
        self.assertEqual({"video", "audio"}, self.probe_tracks(raw))

    def test_network_notifications_are_bounded_without_losing_actual_bytes(self):
        spec = self.generate()
        null_packets = (b"\x47\x1f\xff\x10" + b"\xff" * 184) * 16000
        for i in range(3):
            with (self.root / f"index{i}.ts").open("ab") as stream:
                stream.write(null_packets)
        class SlowBridgeObserver(Observer):
            def __init__(self):
                super().__init__(connections=6)
                self.notifications = []
            def network(self, total):
                self.notifications.append(total)
                time.sleep(.005)
                super().network(total)
        observer = SlowBridgeObserver()
        started = time.monotonic()
        output = download_segments(spec, str(self.root / "bounded-events"), observer)
        elapsed = time.monotonic() - started
        self.assertLessEqual(len(observer.notifications), 8 + int(elapsed * 4),
                             "cross-runtime network notifications must not flood the consumer")
        self.assertGreaterEqual(observer.network_bytes, self.transport_payload(output).stat().st_size)
        self.assertEqual(sorted(observer.notifications), observer.notifications)
        self.assertEqual({"video", "audio"}, self.probe_tracks(output))

    def test_bounded_parallel_fragments_preserve_byte_order_and_full_decode(self):
        spec = self.generate(duration=6)
        self.fragment_delay = 0.2
        started = time.monotonic()
        sequential = download_segments(spec, str(self.root / "sequential"), Observer())
        sequential_seconds = time.monotonic() - started
        self.peak_fragments = 0
        started = time.monotonic()
        parallel = download_segments(spec, str(self.root / "parallel"), Observer(connections=6))
        parallel_seconds = time.monotonic() - started
        self.assertGreaterEqual(self.peak_fragments, 2, "fragment budget must reach the real HTTP transport")
        self.assertLessEqual(self.peak_fragments, 6)
        self.assertEqual(hashlib.sha256(Path(sequential).read_bytes()).digest(),
                         hashlib.sha256(Path(parallel).read_bytes()).digest())
        subprocess.run(["ffmpeg", "-v", "error", "-xerror", "-i", str(self.transport_payload(parallel)),
                        "-map", "0:v", "-map", "0:a", "-f", "null", "-"],
                       check=True, capture_output=True, timeout=10)
        print(f"fragment fixture: sequential={sequential_seconds:.3f}s parallel={parallel_seconds:.3f}s peak={self.peak_fragments}")

    def test_declared_hls_discontinuity_rebases_both_copies_without_losing_frames(self):
        spec = self.generate()
        playlist = self.root / "index.m3u8"
        lines = playlist.read_text().splitlines()
        fragments = lines[lines.index(next(line for line in lines if line.startswith("#EXTINF"))):-1]
        playlist.write_text("\n".join(lines[:-1] + ["#EXT-X-DISCONTINUITY"] + fragments + ["#EXT-X-ENDLIST", ""]))
        output = download_segments(spec, str(self.root / "discontinuous"), Observer(connections=3))
        self.assertTrue(output.endswith(".segments.timeline.ts"))
        self.assertTrue(Path(output.removesuffix(".timeline.ts")).exists())
        probe = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-select_streams", "v", "-show_frames",
            "-show_entries", "frame=pts_time", "-of", "json", output], stderr=subprocess.DEVNULL))
        times = [float(frame["pts_time"]) for frame in probe["frames"]]
        self.assertEqual(60, len(times))
        self.assertAlmostEqual(5.9, times[-1] - times[0], places=2)
        self.assertTrue(all(b > a for a, b in zip(times, times[1:])))

    def test_missing_fragment_is_failure_not_partial_success(self):
        spec = self.generate()
        missing_bytes = (self.root / "index1.ts").read_bytes()
        (self.root / "index1.ts").unlink()
        self.fragment_delay = 0.3
        observer = Observer(connections=3)
        with self.assertRaisesRegex(DownloadError, "404"):
            download_segments(spec, str(self.root / "missing"), observer)
        self.assertEqual(404, observer.http_status)
        self.assertFalse((self.root / "missing.hls-plan.json").exists())
        self.assertFalse((self.root / "missing").exists())
        (self.root / "index1.ts").write_bytes(missing_bytes)
        self.fragment_delay = 0
        recovered = download_segments(spec, str(self.root / "missing"), Observer(connections=6))
        self.assertEqual(b"".join((self.root / f"index{i}.ts").read_bytes() for i in range(3)),
                         self.transport_payload(recovered).read_bytes())
        subprocess.run(["ffmpeg", "-v", "error", "-xerror", "-i", str(self.transport_payload(recovered)),
                        "-map", "0:v", "-map", "0:a", "-f", "null", "-"],
                       check=True, capture_output=True, timeout=10)

    def test_worker_progress_cannot_advance_resume_past_committed_fragment(self):
        spec = self.generate()
        fragments = [(self.root / f"index{i}.ts").read_bytes() for i in range(3)]
        (self.root / "index1.ts").unlink()
        self.fragment_delay = 0.3
        original_append = transfer_module.HlsFD._append_fragment

        def ahead_of_assembly(downloader, context, content):
            # Native parallel progress writes this shared counter independently
            # of the assembler. Make the out-of-order completion deterministic.
            context["fragment_index"] = context["total_frags"]
            return original_append(downloader, context, content)

        with mock.patch.object(transfer_module.HlsFD, "_append_fragment", ahead_of_assembly):
            with self.assertRaisesRegex(DownloadError, "404"):
                download_segments(spec, str(self.root / "checkpoint-race"), Observer(connections=3))
        (self.root / "index1.ts").write_bytes(fragments[1])
        self.fragment_delay = 0
        recovered = download_segments(spec, str(self.root / "checkpoint-race"), Observer(connections=6))
        self.assertEqual(hashlib.sha256(b"".join(fragments)).hexdigest(),
                         hashlib.sha256(self.transport_payload(recovered).read_bytes()).hexdigest())
        subprocess.run(["ffmpeg", "-v", "error", "-xerror", "-i", str(self.transport_payload(recovered)),
                        "-map", "0:v", "-map", "0:a", "-f", "null", "-"],
                       check=True, capture_output=True, timeout=10)

    def test_cancel_before_download_writes_no_output(self):
        spec = self.generate()
        with self.assertRaises(TransferCancelled):
            download_segments(spec, str(self.root / "cancelled"), Observer(cancel_after=0))
        self.assertEqual([], list(self.root.glob("cancelled*")))

    def test_cancelled_fragments_resume_to_complete_media(self):
        spec = self.generate()
        target = str(self.root / "resume")
        with self.assertRaises(TransferCancelled):
            download_segments(spec, target, Observer(cancel_after=2, connections=3))
        completed = download_segments(spec, target, Observer(connections=3))
        self.assertEqual({"video", "audio"}, self.probe_tracks(completed))

    def test_overlapping_resume_waits_for_cancelled_writer_before_touching_same_cache(self):
        spec = self.generate()
        target = str(self.root / "exclusive-resume")
        reached = threading.Event()
        release = threading.Event()
        second_finished = threading.Event()
        outcomes = {}
        first_fragment_size = (self.root / "index0.ts").stat().st_size

        class PausingObserver(Observer):
            stop = False
            def cancelled(self):
                return self.stop
            def progress(self, downloaded, total):
                if downloaded >= first_fragment_size and not reached.is_set():
                    reached.set()
                    release.wait(5)
                super().progress(downloaded, total)

        observer = PausingObserver()
        def run(name, progress_observer):
            try:
                outcomes[name] = download_segments(spec, target, progress_observer)
            except BaseException as error:
                outcomes[name] = error
            finally:
                if name == "second":
                    second_finished.set()

        first = threading.Thread(target=run, args=("first", observer))
        second = threading.Thread(target=run, args=("second", Observer()))
        first.start()
        try:
            self.assertTrue(reached.wait(3))
            second.start()
            raced = second_finished.wait(0.75)
        finally:
            observer.stop = True
            release.set()
            first.join(5)
            if second.ident is not None:
                second.join(5)
        self.assertFalse(raced, "replacement must not mutate a still-owned fragment destination")
        self.assertIsInstance(outcomes.get("first"), TransferCancelled)
        self.assertIsInstance(outcomes.get("second"), str)
        count = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-select_streams", "v", "-count_frames",
            "-show_entries", "stream=nb_read_frames", "-of", "json", outcomes["second"]]))
        self.assertEqual(30, int(count["streams"][0]["nb_read_frames"]))

    def test_pause_resumes_original_manifest_even_when_endpoint_returns_a_new_plan(self):
        spec = self.generate()
        target = str(self.root / "pinned")
        with self.assertRaises(TransferCancelled):
            download_segments(spec, target, Observer(cancel_after=2))
        manifest = self.root / "index.m3u8"
        original = manifest.read_text()
        self.assertIn("index1.ts", original)
        manifest.write_text(original.replace("index1.ts", "missing-new-plan.ts"))

        completed = download_segments(spec, target, Observer())

        self.assertEqual({"video", "audio"}, self.probe_tracks(completed))

    def test_expired_pinned_fragment_allows_a_fresh_plan_on_explicit_retry(self):
        spec = self.generate()
        target = str(self.root / "expired")
        with self.assertRaises(TransferCancelled):
            download_segments(spec, target, Observer(cancel_after=2))
        original_second = (self.root / "index1.ts").read_bytes()
        (self.root / "index1.ts").unlink()
        with self.assertRaisesRegex(DownloadError, "404"):
            download_segments(spec, target, Observer())
        manifest = self.root / "index.m3u8"
        (self.root / "replacement.ts").write_bytes(original_second)
        manifest.write_text(manifest.read_text().replace("index1.ts", "replacement.ts"))

        completed = download_segments(spec, target, Observer())

        self.assertEqual({"video", "audio"}, self.probe_tracks(completed))

    def test_changed_source_never_reuses_previous_source_pinned_plan(self):
        spec = self.generate()
        target = str(self.root / "new-source")
        with self.assertRaises(TransferCancelled):
            download_segments(spec, target, Observer(cancel_after=2))
        manifest = (self.root / "index.m3u8").read_text()
        (self.root / "different.m3u8").write_text(manifest.replace("index1.ts", "missing.ts"))
        changed = json.loads(spec)
        changed["url"] = self.base + "different.m3u8"

        with self.assertRaisesRegex(DownloadError, "404"):
            download_segments(json.dumps(changed), target, Observer())

    def test_changed_plan_removes_only_stale_fragments_for_same_target(self):
        spec = self.generate()
        target = str(self.root / "changed")
        old_prefix = self.root / ("changed." + "0" * 24 + ".segments")
        stale = [Path(str(old_prefix) + suffix) for suffix in
                 ("", ".part", ".ytdl", ".part-Frag108.part", ".part-Frag109")]
        for path in stale:
            path.write_bytes(b"old signed URL")
        unrelated = self.root / ("another." + "0" * 24 + ".segments.part")
        unrelated.write_bytes(b"keep")

        completed = download_segments(spec, target, Observer())

        self.assertEqual({"video", "audio"}, self.probe_tracks(completed))
        self.assertTrue(all(not path.exists() for path in stale))
        self.assertEqual(b"keep", unrelated.read_bytes())

    def test_dash_downloads_each_selected_track(self):
        subprocess.run(["ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=blue:s=160x90:r=10:d=2",
                        "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-c:v", "libx264", "-g", "10",
                        "-c:a", "aac", "-f", "dash", "-seg_duration", "1", str(self.root / "index.mpd")],
                       check=True, timeout=30, capture_output=True)
        with YoutubeDL({"quiet": True, "no_warnings": True}) as engine:
            info = engine.extract_info(self.base + "index.mpd", download=False)
        tracks = set()
        for index, fmt in enumerate(info["formats"]):
            fmt.update(id="fixture")
            raw = download_segments(json.dumps(fmt), str(self.root / f"dash-{index}"), Observer())
            tracks.update(self.probe_tracks(raw))
        self.assertEqual({"audio", "video"}, tracks)


if __name__ == "__main__":
    unittest.main()

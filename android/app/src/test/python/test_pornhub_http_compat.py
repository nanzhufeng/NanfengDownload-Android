import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

from yt_dlp import YoutubeDL
from yt_dlp.networking import Request

from nanzhufeng_probe.youtube_probe import extract_single
from nanzhufeng_probe.pornhub_http import PornhubCompatibleYoutubeDL, youtube_dl_for_transfer


class PornhubHttpCompatTest(unittest.TestCase):
    def setUp(self):
        class FixtureHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                uses_identity = self.headers.get("Accept-Encoding") == "identity"
                status = 200 if uses_identity == (self.path == "/pornhub") else 410
                self.send_response(status)
                self.send_header("Content-Type", "text/plain")
                self.end_headers()
                self.wfile.write(b"fixture")

            def log_message(self, _format, *_args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), FixtureHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def _extract_with_transport_fixture(self, source_url, fixture_path, platform):
        probe_url = f"http://127.0.0.1:{self.server.server_port}/{fixture_path}"

        def fake_extractor(engine, _url, download=False):
            self.assertFalse(download)
            with engine.urlopen(Request(probe_url)) as response:
                self.assertEqual(200, response.status)
                self.assertEqual(b"fixture", response.read())
            return {
                "id": "sample123",
                "title": "Synthetic transport fixture",
                "extractor_key": "PornHub" if platform == "pornhub" else "Generic",
                "webpage_url": source_url,
                "formats": [{
                    "url": "https://media.example.test/video.mp4",
                    "ext": "mp4",
                    "width": 1280,
                    "height": 720,
                    "vcodec": "avc1",
                    "acodec": "mp4a",
                }],
            }

        with patch.object(YoutubeDL, "extract_info", fake_extractor):
            return json.loads(extract_single(source_url))

    def test_pornhub_single_uses_compatible_http_transport(self):
        result = self._extract_with_transport_fixture(
            "https://www.pornhub.com/view_video.php?viewkey=sample123",
            "pornhub",
            "pornhub",
        )
        self.assertEqual("pornhub", result["platform"])
        self.assertEqual("sample123", result["id"])

    def test_other_web_sources_keep_the_existing_transport(self):
        result = self._extract_with_transport_fixture(
            "https://example.test/watch/fixture",
            "generic",
            "generic",
        )
        self.assertEqual("generic", result["platform"])

    def test_official_source_and_cdn_use_compatible_transfer_transport(self):
        spec = {
            "source_page_url": "https://www.pornhub.com/view_video.php?viewkey=sample123",
            "url": "https://ev-h.phncdn.com/path/index.m3u8",
        }
        with youtube_dl_for_transfer(spec, {"http_headers": {"Referer": spec["source_page_url"]}}) as engine:
            self.assertIsInstance(engine, PornhubCompatibleYoutubeDL)
            self.assertEqual("identity", engine.params["http_headers"]["Accept-Encoding"])

    def test_generic_or_spoofed_transfer_keeps_original_transport(self):
        for spec in (
            {"url": "https://ev-h.phncdn.com/path/index.m3u8"},
            {"source_page_url": "https://pornhub.com.evil.test/view_video.php?viewkey=x",
             "url": "https://ev-h.phncdn.com/path/index.m3u8"},
            {"source_page_url": "https://www.pornhub.com/view_video.php?viewkey=x",
             "url": "https://evil.test/path/index.m3u8"},
        ):
            with youtube_dl_for_transfer(spec, {}) as engine:
                self.assertIs(type(engine), YoutubeDL)

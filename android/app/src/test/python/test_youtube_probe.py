import unittest

from nanzhufeng_probe.youtube_probe import _media_result, _select_streams


class StreamSelectionTest(unittest.TestCase):
    def setUp(self):
        self.formats = [
            self._video("360", 640, 360, 600),
            self._video("720", 1280, 720, 1200),
            self._video("1080", 1920, 1080, 2400),
            self._video("2160", 3840, 2160, 6000),
            {
                "format_id": "audio",
                "url": "https://media.test/audio",
                "ext": "m4a",
                "vcodec": "none",
                "acodec": "mp4a",
                "abr": 192,
            },
        ]

    def test_720_preset_caps_short_edge(self):
        video, audio = _select_streams(self.formats, "UP_TO_720P")
        self.assertEqual("720", video["format_id"])
        self.assertEqual("audio", audio["format_id"])

    def test_360_preset_caps_short_edge(self):
        video, audio = _select_streams(self.formats, "UP_TO_360P")
        self.assertEqual("360", video["format_id"])
        self.assertEqual("audio", audio["format_id"])

    def test_1080_preset_selects_1080_stream(self):
        video, _ = _select_streams(self.formats, "UP_TO_1080P")
        self.assertEqual("1080", video["format_id"])

    def test_best_preset_has_no_resolution_cap(self):
        video, _ = _select_streams(self.formats, "BEST")
        self.assertEqual("2160", video["format_id"])

    def test_audio_preset_returns_audio_as_primary_stream(self):
        primary, secondary = _select_streams(self.formats, "AUDIO_MP3")
        self.assertEqual("audio", primary["format_id"])
        self.assertIsNone(secondary)

    def test_media_result_keeps_selected_format_headers_for_googlevideo(self):
        self.formats[0]["http_headers"] = {
            "User-Agent": "yt-dlp-player-agent",
            "Sec-Fetch-Mode": "navigate",
        }
        self.formats[-1]["http_headers"] = {
            "User-Agent": "yt-dlp-player-agent",
            "Accept-Language": "en-us,en;q=0.5",
        }
        result = _media_result(
            {
                "id": "sample",
                "title": "Sample",
                "formats": self.formats,
                "http_headers": {"Accept": "*/*"},
            },
            resolution="UP_TO_720P",
        )

        self.assertEqual("yt-dlp-player-agent", result["headers"]["User-Agent"])
        self.assertEqual("navigate", result["headers"]["Sec-Fetch-Mode"])
        self.assertEqual("en-us,en;q=0.5", result["headers"]["Accept-Language"])
        self.assertEqual("*/*", result["headers"]["Accept"])

    @staticmethod
    def _video(format_id, width, height, tbr):
        return {
            "format_id": format_id,
            "url": f"https://media.test/{format_id}",
            "ext": "mp4",
            "width": width,
            "height": height,
            "vcodec": "avc1",
            "acodec": "none",
            "tbr": tbr,
        }


if __name__ == "__main__":
    unittest.main()

import unittest

from nanzhufeng_probe.youtube_probe import _extractor_args_for, _media_result, _select_streams


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

    def test_360_preset_uses_smallest_valid_rendition_when_platform_has_no_360p(self):
        formats = [
            self._progressive("540", 960, 540, 900),
            self._progressive("720", 1280, 720, 1200),
        ]

        video, audio = _select_streams(formats, "UP_TO_360P")

        self.assertEqual("540", video["format_id"])
        self.assertIsNone(audio)

    def test_360_preset_keeps_fitting_separate_video_and_audio_over_540p_fallback(self):
        formats = [
            self._video("360-video", 640, 360, 600),
            self._progressive("540-progressive", 960, 540, 900),
            {
                "format_id": "audio",
                "url": "https://media.test/audio",
                "ext": "m4a",
                "vcodec": "none",
                "acodec": "mp4a",
                "abr": 128,
            },
        ]

        video, audio = _select_streams(formats, "UP_TO_360P")

        self.assertEqual("360-video", video["format_id"])
        self.assertEqual("audio", audio["format_id"])

    def test_hls_format_is_never_selected_as_a_download_file(self):
        formats = [
            {
                **self._progressive("hls", 1280, 720, 1800),
                "protocol": "m3u8_native",
            },
            self._progressive("direct", 640, 360, 600),
        ]

        video, audio = _select_streams(formats, "UP_TO_720P")

        self.assertEqual("direct", video["format_id"])
        self.assertIsNone(audio)

    def test_1080_preset_selects_1080_stream(self):
        video, _ = _select_streams(self.formats, "UP_TO_1080P")
        self.assertEqual("1080", video["format_id"])

    def test_best_preset_has_no_resolution_cap(self):
        video, _ = _select_streams(self.formats, "BEST")
        self.assertEqual("2160", video["format_id"])

    def test_youtube_uses_web_embedded_client_to_avoid_gvs_po_token_requirement(self):
        self.assertEqual(
            {"youtube": {"player_client": ["web_embedded"]}},
            _extractor_args_for("www.youtube.com"),
        )
        self.assertEqual(
            {"youtube": {"player_client": ["web_embedded"]}},
            _extractor_args_for("youtu.be"),
        )
        self.assertIsNone(_extractor_args_for("www.tiktok.com"))

    def test_audio_preset_returns_audio_as_primary_stream(self):
        primary, secondary = _select_streams(self.formats, "AUDIO_MP3")
        self.assertEqual("audio", primary["format_id"])
        self.assertIsNone(secondary)

    def test_audio_preset_prefers_stereo_m4a_over_higher_bitrate_surround_audio(self):
        formats = [
            {
                "format_id": "stereo",
                "url": "https://media.test/stereo",
                "ext": "m4a",
                "vcodec": "none",
                "acodec": "mp4a.40.2",
                "abr": 129,
                "audio_channels": 2,
            },
            {
                "format_id": "surround",
                "url": "https://media.test/surround",
                "ext": "m4a",
                "vcodec": "none",
                "acodec": "mp4a.40.2",
                "abr": 388,
                "audio_channels": 6,
            },
        ]

        primary, secondary = _select_streams(formats, "AUDIO_MP3")

        self.assertEqual("stereo", primary["format_id"])
        self.assertIsNone(secondary)

    def test_audio_preset_uses_720p_progressive_when_audio_and_360p_are_missing(self):
        formats = [
            self._video("720-video-only", 1280, 720, 1200),
            self._progressive("720-progressive", 1280, 720, 1450),
            self._progressive("1080-progressive", 1920, 1080, 2600),
        ]

        primary, secondary = _select_streams(formats, "AUDIO_MP3")

        self.assertEqual("720-progressive", primary["format_id"])
        self.assertIsNone(secondary)

    def test_audio_preset_never_uses_video_only_stream_as_conversion_source(self):
        formats = [
            self._video("360-video-only", 640, 360, 600),
            self._video("720-video-only", 1280, 720, 1200),
        ]

        primary, secondary = _select_streams(formats, "AUDIO_MP3")

        self.assertIsNone(primary)
        self.assertIsNone(secondary)

    def test_audio_preset_explains_when_resource_has_no_audio_track(self):
        with self.assertRaisesRegex(
            ValueError,
            "只有纯视频画面.*请改为下载视频",
        ):
            _media_result(
                {
                    "id": "silent",
                    "title": "Silent",
                    "formats": [self._video("720-video-only", 1280, 720, 1200)],
                },
                resolution="AUDIO_MP3",
            )

    def test_media_result_marks_video_fallback_used_for_audio_conversion(self):
        result = _media_result(
            {
                "id": "sample",
                "title": "Sample",
                "formats": [self._progressive("720-progressive", 1280, 720, 1450)],
            },
            resolution="AUDIO_MP3",
        )

        self.assertTrue(result["audio_from_video_source"])
        self.assertEqual("https://media.test/720-progressive", result["video_url"])
        self.assertEqual("", result["audio_url"])

    def test_media_result_keeps_selected_format_headers_for_googlevideo(self):
        self.formats[1]["http_headers"] = {
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

    @staticmethod
    def _progressive(format_id, width, height, tbr):
        return {
            "format_id": format_id,
            "url": f"https://media.test/{format_id}",
            "ext": "mp4",
            "width": width,
            "height": height,
            "vcodec": "avc1",
            "acodec": "mp4a",
            "tbr": tbr,
        }


if __name__ == "__main__":
    unittest.main()

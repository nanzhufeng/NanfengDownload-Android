import unittest

from nanzhufeng_probe.youtube_probe import (
    _media_result,
    _select_audio,
    _select_streams,
    _source_result,
)


class SelectAudioTest(unittest.TestCase):
    def test_prefers_mp4_compatible_audio_over_higher_bitrate_webm(self):
        formats = [
            {
                "url": "https://example.com/audio.webm",
                "ext": "webm",
                "acodec": "opus",
                "vcodec": "none",
                "abr": 160,
            },
            {
                "url": "https://example.com/audio.m4a",
                "ext": "m4a",
                "acodec": "mp4a.40.2",
                "vcodec": "none",
                "abr": 128,
            },
        ]

        selected = _select_audio(formats)

        self.assertEqual("m4a", selected["ext"])

    def test_prefers_higher_resolution_separate_video(self):
        progressive = {
            "url": "https://example.com/progressive.mp4",
            "ext": "mp4",
            "vcodec": "avc1",
            "acodec": "mp4a.40.2",
            "height": 360,
            "tbr": 500,
        }
        video = {
            "url": "https://example.com/video.mp4",
            "ext": "mp4",
            "vcodec": "avc1",
            "acodec": "none",
            "height": 720,
            "tbr": 1500,
        }
        audio = {
            "url": "https://example.com/audio.m4a",
            "ext": "m4a",
            "vcodec": "none",
            "acodec": "mp4a.40.2",
            "abr": 128,
        }

        chosen_video, chosen_audio = _select_streams([progressive, video, audio])

        self.assertEqual(video, chosen_video)
        self.assertEqual(audio, chosen_audio)

    def test_progressive_tiktok_mp4_becomes_single_media_result(self):
        info = {
            "id": "7512345678901234567",
            "title": "TikTok sample",
            "uploader": "creator",
            "uploader_id": "creator",
            "webpage_url": "https://www.tiktok.com/@creator/video/7512345678901234567",
            "extractor_key": "TikTok",
            "formats": [
                {
                    "url": "https://example.com/video.mp4",
                    "ext": "mp4",
                    "vcodec": "h264",
                    "acodec": "aac",
                    "height": 720,
                },
            ],
            "http_headers": {"Referer": "https://www.tiktok.com/"},
        }

        result = _media_result(info)

        self.assertEqual("tiktok", result["platform"])
        self.assertEqual("creator", result["creator_id"])
        self.assertEqual(info["webpage_url"], result["webpage_url"])
        self.assertEqual("https://example.com/video.mp4", result["video_url"])
        self.assertEqual("", result["audio_url"])

    def test_media_result_includes_cookie_header_when_provided(self):
        info = {
            "id": "7512345678901234567",
            "extractor_key": "TikTok",
            "formats": [
                {
                    "url": "https://example.com/video.mp4",
                    "ext": "mp4",
                    "vcodec": "h264",
                    "acodec": "aac",
                    "height": 720,
                },
            ],
            "http_headers": {"Referer": "https://www.tiktok.com/"},
        }

        result = _media_result(
            info,
            cookie_header="ttwid=abc; tt_csrf_token=def",
        )

        self.assertEqual(
            "ttwid=abc; tt_csrf_token=def",
            result["headers"]["Cookie"],
        )

    def test_tiktok_playlist_is_resolved_as_creator(self):
        result = _source_result(
            {
                "_type": "playlist",
                "webpage_url": "https://www.tiktok.com/@creator",
                "entries": [{"id": "1"}],
            },
        )

        self.assertEqual("creator", result["kind"])
        self.assertEqual("https://www.tiktok.com/@creator", result["url"])

    def test_vertical_tiktok_uses_short_edge_for_720p_limit(self):
        vertical = {
            "url": "https://example.com/vertical.mp4",
            "ext": "mp4",
            "vcodec": "h264",
            "acodec": "aac",
            "width": 576,
            "height": 1024,
            "tbr": 1376,
        }

        chosen_video, chosen_audio = _select_streams([vertical])

        self.assertEqual(vertical, chosen_video)
        self.assertIsNone(chosen_audio)


if __name__ == "__main__":
    unittest.main()

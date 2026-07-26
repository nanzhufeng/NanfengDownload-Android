import json
import unittest
from unittest.mock import patch

from nanzhufeng_probe.youtube_probe import (
    _bilibili_fallback_info,
    _media_result,
    _request_headers,
    _resolve_known_short_link,
    _xiaohongshu_info,
    _xiaohongshu_note_state,
)


class BilibiliFallbackTest(unittest.TestCase):
    def test_public_playurl_fallback_builds_separate_video_and_audio(self):
        initial_state = {
            "video": {
                "bvid": "BV1bK411W797",
                "avid": 498159642,
                "viewInfo": {
                    "bvid": "BV1bK411W797",
                    "aid": 498159642,
                    "title": "示例视频",
                    "pic": "https://image.test/cover.jpg",
                    "pubdate": 1589601697,
                    "owner": {"mid": 150259984, "name": "示例作者"},
                    "pages": [{"page": 1, "cid": 191557669, "part": "第一集"}],
                },
            }
        }
        page_html = (
            "<script>window.__INITIAL_STATE__="
            + json.dumps(initial_state)
            + ";</script>"
        ).replace("window.__INITIAL_STATE__", "__INITIAL_STATE__")
        playurl = {
            "code": 0,
            "data": {
                "dash": {
                    "video": [
                        {
                            "id": 64,
                            "baseUrl": "https://media.test/video.m4s",
                            "width": 1280,
                            "height": 720,
                            "bandwidth": 1200000,
                            "codecs": "avc1",
                        }
                    ],
                    "audio": [
                        {
                            "id": 30280,
                            "baseUrl": "https://media.test/audio.m4s",
                            "bandwidth": 128000,
                            "codecs": "mp4a",
                        }
                    ],
                }
            },
        }

        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            side_effect=[
                (page_html, "https://www.bilibili.com/video/BV1bK411W797"),
                (json.dumps(playurl), "https://api.bilibili.com/x/player/playurl"),
            ],
        ):
            info = _bilibili_fallback_info(
                "https://www.bilibili.com/video/BV1bK411W797",
                "SESSDATA=account",
            )
        result = _media_result(info, resolution="UP_TO_720P")

        self.assertEqual("bilibili", result["platform"])
        self.assertEqual("BV1bK411W797", result["id"])
        self.assertEqual("https://media.test/video.m4s", result["video_url"])
        self.assertEqual("https://media.test/audio.m4s", result["audio_url"])
        self.assertEqual("SESSDATA=account", result["headers"]["Cookie"])


class XiaohongshuStateTest(unittest.TestCase):
    def test_current_xhslink_cn_short_link_uses_xiaohongshu_session_and_official_redirect(self):
        self.assertEqual(
            "https://www.xiaohongshu.com/",
            _request_headers("https://xhslink.cn/o/7i6agytmp2s")["Referer"],
        )
        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=(
                "",
                "https://www.xiaohongshu.com/explore/current-note?xsec_token=fresh",
            ),
        ):
            resolved = _resolve_known_short_link(
                "https://xhslink.cn/o/7i6agytmp2s",
                "web_session=account",
            )

        self.assertEqual(
            "https://www.xiaohongshu.com/explore/current-note?xsec_token=fresh",
            resolved,
        )

    def test_current_rednote_note_data_is_parsed_as_progressive_video(self):
        note = {
            "type": "video",
            "noteId": "69ce30d3000000002100791c",
            "title": "森林素材",
            "time": 1775120595000,
            "user": {"userId": "creator-one", "nickName": "素材作者"},
            "imageList": [{"url": "https://image.test/cover.jpg"}],
            "video": {
                "media": {
                    "stream": {
                        "h264": [
                            {
                                "qualityType": "HD",
                                "masterUrl": "http://media.test/xhs.mp4",
                                "width": 1280,
                                "height": 720,
                                "avgBitrate": 1200000,
                                "videoCodec": "avc1",
                                "audioCodec": "mp4a",
                            }
                        ]
                    }
                }
            },
        }
        html = (
            "<script>window.__INITIAL_STATE__="
            + json.dumps({"noteData": {"data": {"noteData": note}}})
            + "</script>"
        )

        parsed = _xiaohongshu_note_state(html)
        self.assertEqual(note["noteId"], parsed["noteId"])

        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=(
                html,
                "https://www.rednote.com/explore/69ce30d3000000002100791c",
            ),
        ):
            info = _xiaohongshu_info(
                "https://www.rednote.com/explore/69ce30d3000000002100791c",
                "web_session=account",
            )
        result = _media_result(info, resolution="UP_TO_720P")

        self.assertEqual("xiaohongshu", result["platform"])
        self.assertEqual(note["noteId"], result["id"])
        self.assertEqual("https://media.test/xhs.mp4", result["video_url"])
        self.assertEqual("", result["audio_url"])
        self.assertEqual("web_session=account", result["headers"]["Cookie"])

        audio_result = _media_result(info, resolution="AUDIO_MP3")
        self.assertEqual("https://media.test/xhs.mp4", audio_result["video_url"])
        self.assertEqual("", audio_result["audio_url"])

    def test_image_only_note_has_explicit_failure(self):
        note = {
            "type": "normal",
            "noteId": "image-note",
            "video": {"media": {"stream": {}}},
        }
        html = (
            "<script>window.__INITIAL_STATE__="
            + json.dumps({"noteData": {"data": {"noteData": note}}})
            + "</script>"
        )
        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=(html, "https://www.xiaohongshu.com/explore/image-note"),
        ):
            with self.assertRaisesRegex(ValueError, "image-only note"):
                _xiaohongshu_info(
                    "https://www.xiaohongshu.com/explore/image-note"
                )


if __name__ == "__main__":
    unittest.main()

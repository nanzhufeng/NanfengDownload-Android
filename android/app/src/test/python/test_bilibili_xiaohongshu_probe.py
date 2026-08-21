import json
import unittest
from unittest.mock import patch

from nanzhufeng_probe.youtube_probe import (
    _bilibili_fallback_info,
    _media_result,
    _request_headers,
    _resolve_known_short_link,
    _xiaohongshu_image_urls,
    _xiaohongshu_info,
    _xiaohongshu_note_state,
    extract_single,
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
        self.assertNotIn("Cookie", result["headers"])


class XiaohongshuStateTest(unittest.TestCase):
    def test_verified_origin_video_is_selected_before_watermarked_rendition(self):
        info = {
            "extractor_key": "Xiaohongshu",
            "id": "origin-video",
            "title": "原始视频",
            "formats": [
                {
                    "format_id": "HD",
                    "url": "https://sns-video.xhscdn.com/watermarked.mp4",
                    "ext": "mp4",
                    "width": 1280,
                    "height": 720,
                    "vcodec": "avc1",
                    "acodec": "mp4a",
                },
                {
                    "format_id": "direct",
                    "url": "https://sns-video-bd.xhscdn.com/origin-video-key",
                    "ext": "mp4",
                },
            ],
        }

        result = _media_result(info, resolution="UP_TO_720P")

        self.assertEqual(
            "https://sns-video-bd.xhscdn.com/origin-video-key",
            result["video_url"],
        )

    def test_image_collection_refuses_unverified_scene_urls(self):
        note = {
            "imageList": [
                {
                    "urlDefault": "https://sns-img.xhscdn.com/fallback.jpg",
                    "infoList": [
                        {
                            "imageScene": "CRD_WM_WEBP",
                            "url": "https://sns-img.xhscdn.com/watermarked.webp",
                        },
                        {"imageScene": "WB_DFT", "url": "https://sns-img.xhscdn.com/share.jpg"},
                    ],
                }
            ]
        }

        self.assertEqual(
            [],
            _xiaohongshu_image_urls(note),
        )

    def test_image_collection_uses_the_verified_original_image_endpoint(self):
        note = {
            "imageList": [
                {
                    "urlDefault": (
                        "https://sns-webpic-qc.xhscdn.com/12345/"
                        "abc123/image-key!nd_dft_wlteh_webp_3_0.jpg"
                    )
                }
            ]
        }

        self.assertEqual(
            ["https://ci.xiaohongshu.com/image-key?imageView2/2/w/format/png"],
            _xiaohongshu_image_urls(note),
        )

    def test_image_collection_refuses_when_only_watermark_scene_is_available(self):
        note = {
            "imageList": [
                {
                    "infoList": [
                        {
                            "imageScene": "CRD_WM_WEBP",
                            "url": "https://sns-img.xhscdn.com/watermarked.webp",
                        }
                    ]
                }
            ]
        }

        self.assertEqual([], _xiaohongshu_image_urls(note))

    def test_static_video_refuses_share_rendition_without_origin_key(self):
        note = {
            "type": "video",
            "noteId": "watermarked-video",
            "video": {
                "media": {
                    "stream": {
                        "h264": [{"masterUrl": "https://sns-video.xhscdn.com/share.mp4"}]
                    }
                }
            },
        }
        html = "<script>window.__INITIAL_STATE__=" + json.dumps(
            {"noteData": {"data": {"noteData": note}}}
        ) + "</script>"

        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=(html, "https://www.xiaohongshu.com/explore/watermarked-video"),
        ):
            with self.assertRaisesRegex(ValueError, "未下载带水印版本"):
                _xiaohongshu_info("https://www.xiaohongshu.com/explore/watermarked-video")

    def test_official_xiaohongshu_http_cdn_media_is_upgraded_to_https(self):
        info = {
            "extractor_key": "Xiaohongshu",
            "id": "current-video",
            "title": "当前公开视频",
            "formats": [
                {
                    "url": "http://sns-video-v6.xhscdn.com/stream/video.mp4?sign=current",
                    "ext": "mp4",
                    "width": 1280,
                    "height": 720,
                    "vcodec": "avc1",
                    "acodec": "mp4a",
                }
            ],
        }

        result = _media_result(info)

        self.assertEqual(
            "https://sns-video-v6.xhscdn.com/stream/video.mp4?sign=current",
            result["video_url"],
        )

    def test_current_xiaohongshu_uses_public_note_state_before_yt_dlp_fallback(self):
        info = {
            "extractor_key": "Xiaohongshu",
            "id": "current-video",
            "title": "当前公开视频",
            "webpage_url": "https://www.xiaohongshu.com/explore/current-video",
            "formats": [
                {
                    "format_id": "direct",
                    "url": "https://media.test/current.mp4",
                    "ext": "mp4",
                    "width": 1280,
                    "height": 720,
                    "vcodec": "avc1",
                    "acodec": "mp4a",
                    "tbr": 1000,
                }
            ],
        }

        with patch(
            "nanzhufeng_probe.youtube_probe._xiaohongshu_info",
            return_value=info,
        ) as static_info:
            with patch("nanzhufeng_probe.youtube_probe.YoutubeDL") as ydl:
                result = json.loads(
                    extract_single("https://www.xiaohongshu.com/explore/current-video")
                )

        static_info.assert_called_once()
        ydl.assert_not_called()
        self.assertEqual("xiaohongshu", result["platform"])
        self.assertEqual("https://media.test/current.mp4", result["video_url"])

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
                "consumer": {"originVideoKey": "original-video-key"},
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
        self.assertEqual(
            "https://sns-video-bd.xhscdn.com/original-video-key",
            result["video_url"],
        )
        self.assertEqual("", result["audio_url"])
        self.assertNotIn("Cookie", result["headers"])

        audio_result = _media_result(info, resolution="AUDIO_MP3")
        self.assertEqual("https://media.test/xhs.mp4", audio_result["video_url"])
        self.assertEqual("", audio_result["audio_url"])

    def test_image_only_note_becomes_a_downloadable_image_collection(self):
        note = {
            "type": "normal",
            "noteId": "image-note",
            "title": "图文素材",
            "user": {"userId": "creator-image", "nickName": "图文作者"},
            "imageList": [
                {
                    "urlDefault": (
                        "https://sns-webpic-qc.xhscdn.com/12345/"
                        "alpha/image-one!nd_dft_wlteh_webp_3_0.jpg"
                    )
                },
                {
                    "url": (
                        "https://sns-webpic-qc.xhscdn.com/12345/"
                        "beta/image-two!nd_dft_wlteh_webp_3_0.jpg"
                    )
                },
            ],
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
            info = _xiaohongshu_info("https://www.xiaohongshu.com/explore/image-note")
        result = _media_result(info)

        self.assertEqual("image-note", result["id"])
        self.assertEqual(
            [
                "https://ci.xiaohongshu.com/image-one?imageView2/2/w/format/png",
                "https://ci.xiaohongshu.com/image-two?imageView2/2/w/format/png",
            ],
            result["image_urls"],
        )


class DouyinShortLinkTest(unittest.TestCase):
    def test_douyin_short_link_is_resolved_to_canonical_video_before_ytdlp(self):
        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=(
                "",
                "https://www.iesdouyin.com/share/video/7669248142533973995/?region=CN",
            ),
        ) as fetch:
            resolved = _resolve_known_short_link(
                "https://v.douyin.com/R37NZ1wqjiM/",
                "sessionid=account",
            )

        self.assertEqual(
            "https://www.douyin.com/video/7669248142533973995",
            resolved,
        )
        fetch.assert_called_once_with(
            "https://v.douyin.com/R37NZ1wqjiM/",
            "sessionid=account",
            max_bytes=2 * 1024 * 1024,
        )

    def test_douyin_short_link_rejects_non_official_redirect(self):
        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=("", "https://attacker.example/video/7669248142533973995"),
        ):
            with self.assertRaisesRegex(ValueError, "非官方域名"):
                _resolve_known_short_link("https://v.douyin.com/current/", "")

    def test_douyin_official_new_share_route_is_not_rejected_by_local_path_rule(self):
        final_url = "https://www.iesdouyin.com/share/discover?modal_id=current"
        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=("", final_url),
        ):
            self.assertEqual(
                final_url,
                _resolve_known_short_link("https://v.douyin.com/current/", ""),
            )


class TikTokShortLinkTest(unittest.TestCase):
    def test_current_tiktok_share_route_is_resolved_to_canonical_video_before_ytdlp(self):
        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=(
                "",
                "https://www.tiktok.com/@sevenjoy_aigc/video/7673462406240079118?_r=1&_t=fresh",
            ),
        ) as fetch:
            resolved = _resolve_known_short_link(
                "https://www.tiktok.com/t/ZTDPkLsKp/",
                "sessionid=account",
            )

        self.assertEqual(
            "https://www.tiktok.com/@sevenjoy_aigc/video/7673462406240079118",
            resolved,
        )
        fetch.assert_called_once_with(
            "https://www.tiktok.com/t/ZTDPkLsKp/",
            "sessionid=account",
            max_bytes=2 * 1024 * 1024,
        )

    def test_tiktok_share_route_rejects_non_official_redirect(self):
        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=("", "https://attacker.example/video/7673462406240079118"),
        ):
            with self.assertRaisesRegex(ValueError, "非官方域名"):
                _resolve_known_short_link("https://www.tiktok.com/t/current/", "")

    def test_legacy_tiktok_share_hosts_use_the_same_official_canonicalization(self):
        for source in (
            "https://vm.tiktok.com/ZM6H123abc/",
            "https://vt.tiktok.com/ZM6H123abc/",
        ):
            with self.subTest(source=source):
                with patch(
                    "nanzhufeng_probe.youtube_probe._fetch",
                    return_value=(
                        "",
                        "https://www.tiktok.com/@creator/video/7673462406240079118?_t=fresh",
                    ),
                ):
                    resolved = _resolve_known_short_link(source)

                self.assertEqual(
                    "https://www.tiktok.com/@creator/video/7673462406240079118",
                    resolved,
                )

    def test_new_tiktok_official_share_route_is_left_for_the_maintained_extractor(self):
        final_url = "https://www.tiktok.com/foryou?share_item_id=current"
        with patch(
            "nanzhufeng_probe.youtube_probe._fetch",
            return_value=("", final_url),
        ):
            self.assertEqual(
                final_url,
                _resolve_known_short_link("https://www.tiktok.com/t/current/"),
            )


if __name__ == "__main__":
    unittest.main()

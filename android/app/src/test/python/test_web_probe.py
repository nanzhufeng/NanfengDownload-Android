import json
import unittest
from unittest.mock import MagicMock, patch

from nanzhufeng_probe.youtube_probe import extract_single


class WebProbeTest(unittest.TestCase):
    def test_generic_filename_is_not_a_unique_work_identity(self):
        info = self.direct_info()
        info.update(id="index", extractor_key="Generic")
        first = self.extract(info, "https://example.test/one/index.m3u8")
        second = self.extract(info, "https://example.test/two/index.m3u8")
        third = self.extract(info, "https://example.test:8443/one/index.m3u8")
        self.assertEqual(3, len({first["id"], second["id"], third["id"]}))
        self.assertEqual(first["legacy_id"], second["legacy_id"])

    def test_generic_identity_stays_bound_to_input_url_when_reported_basename_changes(self):
        info = self.direct_info()
        info.update(id="index", extractor_key="Generic")
        first = self.extract(info, "https://example.test/work/42")
        info["id"] = "renamed"
        second = self.extract(info, "https://example.test/work/42")
        self.assertEqual(first["id"], second["id"])

    def test_named_extractor_still_uses_its_stable_work_id(self):
        info = self.direct_info()
        info.update(id="42", extractor_key="Vimeo")
        first = self.extract(info, "https://vimeo.com/42?player=one")
        second = self.extract(info, "https://vimeo.com/42?player=two")
        self.assertEqual(first["id"], second["id"])

    def test_legacy_segment_plan_preserves_only_the_previous_task_identity(self):
        info = self.direct_info()
        info.update(id="index", extractor_key="Generic")
        info["formats"][0].update(url="https://media.example.test/index.m3u8", protocol="m3u8_native")
        result = self.extract(info, "https://example.test/one/index.m3u8")
        current = json.loads(result["video_download_spec"])
        previous = json.loads(result["legacy_video_download_spec"])
        self.assertEqual(result["id"], current.pop("id"))
        self.assertEqual(result["legacy_id"], previous.pop("id"))
        self.assertEqual(current, previous)

    def direct_info(self):
        return {"id": "sample123", "title": "Synthetic fixture", "extractor_key": "PornHub",
                "formats": [{"url": "https://media.example.test/video.mp4", "ext": "mp4",
                             "width": 1280, "height": 720, "vcodec": "avc1", "acodec": "mp4a"}]}

    def extract(self, info, url="https://cn.pornhub.com/view_video.php?viewkey=sample123"):
        engine = MagicMock()
        engine.extract_info.return_value = info
        engine.cookiejar.get_cookie_header.return_value = ""
        with patch("nanzhufeng_probe.youtube_probe.youtube_dl_for_source") as factory:
            factory.return_value.__enter__.return_value = engine
            return json.loads(extract_single(url))

    def test_pornhub_hls_only_work_keeps_title_and_segment_protocol(self):
        result = self.extract({
            "id": "sample123", "title": "Synthetic media fixture", "extractor_key": "PornHub",
            "webpage_url": "https://cn.pornhub.com/view_video.php?viewkey=sample123",
            "formats": [{"format_id": "hls-720", "url": "https://media.example.test/index.m3u8",
                         "protocol": "m3u8_native", "ext": "mp4", "width": 1280, "height": 720,
                         "vcodec": "avc1", "acodec": "mp4a"}],
        })
        self.assertEqual("Synthetic media fixture", result["title"])
        self.assertEqual("pornhub", result["platform"])
        self.assertEqual("sample123", result["id"])
        self.assertEqual("m3u8_native", json.loads(result["video_download_spec"])["protocol"])
        self.assertEqual(
            "https://cn.pornhub.com/view_video.php?viewkey=sample123",
            json.loads(result["video_download_spec"])["source_page_url"],
        )

    def test_direct_mp4_keeps_http_route(self):
        result = self.extract(self.direct_info())
        self.assertEqual("https://media.example.test/video.mp4", result["video_url"])
        self.assertEqual("", result["video_download_spec"])

    def test_wrong_work_id_is_rejected(self):
        info = self.direct_info()
        info["id"] = "another-work"
        with self.assertRaisesRegex(ValueError, "目标作品"):
            self.extract(info)

    def test_cookie_and_authorization_headers_cannot_escape_to_media(self):
        info = self.direct_info()
        info["http_headers"] = {"Cookie": "synthetic=fixture", "Authorization": "test-only",
                                "Referer": "https://cn.pornhub.com/"}
        result = self.extract(info)
        self.assertEqual({"Referer": "https://cn.pornhub.com/"}, result["headers"])

    def test_drm_only_source_is_not_a_download_candidate(self):
        info = self.direct_info()
        info["formats"][0]["has_drm"] = True
        with self.assertRaisesRegex(ValueError, "没有找到"):
            self.extract(info)

    def test_audio_and_video_keep_their_own_request_headers(self):
        info = self.direct_info()
        info["formats"] = [
            {"url": "https://video.example.test/v.m3u8", "ext": "mp4", "protocol": "m3u8_native",
             "width": 1280, "height": 720, "vcodec": "avc1", "acodec": "none",
             "http_headers": {"Referer": "https://video.example.test/"}},
            {"url": "https://audio.example.test/a.m3u8", "ext": "m4a", "protocol": "m3u8_native",
             "vcodec": "none", "acodec": "mp4a", "http_headers": {"Referer": "https://audio.example.test/"}},
        ]
        result = self.extract(info)
        self.assertEqual("https://video.example.test/", result["headers"]["Referer"])
        self.assertEqual("https://audio.example.test/", result["audio_headers"]["Referer"])
        self.assertTrue(result["audio_download_spec"])

    def test_generic_identity_scopes_same_media_id_to_source_host(self):
        info = self.direct_info()
        info["extractor_key"] = "Generic"
        first = self.extract(info, "https://first.example.test/watch/1")
        second = self.extract(info, "https://second.example.test/watch/1")
        self.assertEqual("generic", first["platform"])
        self.assertNotEqual(first["id"], second["id"])

    def test_direct_media_without_explicit_formats_is_usable(self):
        url = "https://media.example.test/sample.mp4"
        result = self.extract({"id": "sample", "title": "Synthetic direct media", "ext": "mp4",
                               "url": url, "extractor_key": "Generic"}, url)
        self.assertEqual(url, result["video_url"])
        self.assertEqual("generic", result["platform"])

    def test_direct_media_with_none_codecs_from_real_generic_extractor_is_usable(self):
        url = "http://media.example.test/sample.mp4"
        result = self.extract({"id": "sample", "title": "Synthetic direct media", "ext": "mp4",
                               "extractor_key": "Generic", "formats": [
                                   {"url": url, "ext": "mp4", "protocol": "http",
                                    "vcodec": None, "acodec": None},
                               ]}, url)
        self.assertEqual(url, result["video_url"])

    def test_audio_direct_source_uses_mp3_task_semantics(self):
        url = "https://media.example.test/sample.mp3"
        result = self.extract({"id": "sample", "title": "Synthetic audio", "ext": "mp3",
                               "url": url, "extractor_key": "Generic"}, url)
        self.assertTrue(result["audio_only"])
        self.assertEqual(url, result["video_url"])

    def test_audio_direct_with_none_codecs_is_still_audio_only(self):
        url = "https://media.example.test/sample.mp3"
        result = self.extract({"id": "sample", "title": "Synthetic audio", "extractor_key": "Generic",
                               "formats": [{"url": url, "ext": "mp3", "protocol": "https",
                                            "vcodec": None, "acodec": None}]}, url)
        self.assertTrue(result["audio_only"])
        self.assertEqual(url, result["video_url"])


if __name__ == "__main__":
    unittest.main()

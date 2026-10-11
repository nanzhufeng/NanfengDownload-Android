import json
import unittest
from unittest.mock import MagicMock, patch
from nanzhufeng_probe.youtube_probe import read_single, extract_single, resolve_source

class ReadPipelineTest(unittest.TestCase):
    def info(self, page, platform="Youtube"):
        return {"id":"fixture123", "title":"Synthetic metadata", "webpage_url":page, "extractor_key":platform,
          "formats":[{"url":"https://media.test/video.mp4", "ext":"mp4", "width":1280, "height":720,
                      "vcodec":"avc1", "acodec":"mp4a"}]}

    def test_discovery_keeps_supported_default_clients_without_unnecessary_manifests(self):
        page="https://www.youtube.com/watch?v=fixture123"
        with patch("nanzhufeng_probe.youtube_probe.youtube_dl_for_source") as factory:
            engine=MagicMock(); engine.extract_info.return_value=self.info(page)
            engine.cookiejar.get_cookie_header.return_value=""; factory.return_value.__enter__.return_value=engine
            result=json.loads(read_single(page)); options=factory.call_args.args[1]
        self.assertEqual("fixture123", result["id"])
        self.assertEqual("https://media.test/video.mp4", result["video_url"])
        self.assertNotIn("player_client", options["extractor_args"]["youtube"])
        self.assertEqual(["hls","dash"], options["extractor_args"]["youtube"]["skip"])

    def test_download_extraction_keeps_the_full_manifest_and_retry_options(self):
        page="https://www.youtube.com/watch?v=fixture123"
        with patch("nanzhufeng_probe.youtube_probe.youtube_dl_for_source") as factory:
            engine=MagicMock(); engine.extract_info.return_value=self.info(page)
            engine.cookiejar.get_cookie_header.return_value=""; factory.return_value.__enter__.return_value=engine
            extract_single(page); options=factory.call_args.args[1]
        self.assertNotIn("extractor_args",options)
        self.assertEqual(20, options["socket_timeout"])
        self.assertEqual(1, options["retries"])

    def test_complete_generic_resolution_exposes_the_same_source_snapshot(self):
        page="https://fixture.test/video.mp4"
        with patch("nanzhufeng_probe.youtube_probe.YoutubeDL") as factory:
            engine=MagicMock(); engine.extract_info.return_value=self.info(page,"Generic")
            factory.return_value.__enter__.return_value=engine
            result=json.loads(resolve_source(page))
        self.assertEqual("single",result["kind"])
        self.assertEqual(page,result["media"]["webpage_url"])
        self.assertEqual("generic",result["media"]["platform"])

    def test_redirected_generic_resolution_does_not_borrow_a_snapshot_for_another_url(self):
        page="https://fixture.test/video.mp4"
        with patch("nanzhufeng_probe.youtube_probe.YoutubeDL") as factory:
            engine=MagicMock(); engine.extract_info.return_value=self.info("https://other.test/video.mp4","Generic")
            factory.return_value.__enter__.return_value=engine
            result=json.loads(resolve_source(page))
        self.assertNotIn("media",result)

    def test_bilibili_reads_small_official_apis_and_preserves_the_selected_part(self):
        page="https://www.bilibili.com/video/BVfixture123/?p=2"
        view={"code":0,"data":{"bvid":"BVfixture123","aid":123,"state":0,"videos":2,"title":"Fixture title",
              "owner":{"name":"Fixture author","mid":42},"pages":[{"page":1,"cid":101,"part":"First"},{"page":2,"cid":102,"part":"Second"}]}}
        play={"code":0,"data":{"dash":{"video":[{"baseUrl":"https://upos.bilivideo.com/video.m4s","width":854,"height":480,"codecs":"avc1"}],
              "audio":[{"baseUrl":"https://upos.bilivideo.com/audio.m4s","codecs":"mp4a"}]}}}
        with patch("nanzhufeng_probe.youtube_probe._fetch", side_effect=[(json.dumps(view),"https://api.bilibili.com/x/web-interface/view"),(json.dumps(play),"https://api.bilibili.com/x/player/playurl")]) as fetch:
            result=json.loads(read_single(page))
        self.assertEqual("BVfixture123_p2",result["id"])
        self.assertEqual("Fixture title - P2 Second",result["title"])
        self.assertIn("x/web-interface/view",fetch.call_args_list[0].args[0])
        self.assertEqual(1,fetch.call_count)
        self.assertEqual("",result["video_url"])
        self.assertEqual("",fetch.call_args_list[0].args[1])

    def test_bilibili_api_cannot_substitute_another_work_or_part(self):
        page="https://www.bilibili.com/video/BVfixture123/"
        view={"code":0,"data":{"bvid":"BVother123","aid":123,"state":0,"videos":1,"pages":[{"page":1,"cid":101}]}}
        with patch("nanzhufeng_probe.youtube_probe._fetch", return_value=(json.dumps(view),"https://api.bilibili.com/x/web-interface/view")) as fetch:
            with self.assertRaisesRegex(ValueError,"目标"):
                read_single(page)
            self.assertEqual(1,fetch.call_count)
        view["data"]["bvid"]="BVfixture123"
        with patch("nanzhufeng_probe.youtube_probe._fetch", return_value=(json.dumps(view),"https://api.bilibili.com/x/web-interface/view")) as fetch:
            with self.assertRaisesRegex(ValueError,"分P"):
                read_single(page+"?p=2")
            self.assertEqual(1,fetch.call_count)

    def test_unavailable_bilibili_api_retains_the_scoped_full_extractor(self):
        from urllib.error import HTTPError
        page="https://www.bilibili.com/video/BVfixture123/"
        with patch("nanzhufeng_probe.youtube_probe._fetch",side_effect=HTTPError("https://api.bilibili.com/",412,"fixture",{},None)), patch("nanzhufeng_probe.youtube_probe._bilibili_fallback_info",return_value=self.info(page,"Bilibili")) as fallback:
            read_single(page,cookie_header="fixture-session=demo")
            fallback.assert_called_once_with(page,"fixture-session=demo")

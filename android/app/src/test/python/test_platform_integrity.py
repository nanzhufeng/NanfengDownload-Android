import json
import unittest
from unittest.mock import patch
from nanzhufeng_probe.youtube_probe import _bilibili_fallback_info, _bilibili_page_state, _media_result, resolve_source

class PlatformIntegrityTest(unittest.TestCase):
    def page(self, bvid='BV1bK411W797'):
        return '__INITIAL_STATE__=' + json.dumps({'videoData': {'bvid':bvid,'aid':498159642,'title':'fixture','pages':[{'page':1,'cid':123}]}})

    def test_missing_bilibili_part_never_downloads_first_part(self):
        with self.assertRaisesRegex(ValueError, '分P'):
            _bilibili_page_state(self.page(), 2)

    def test_bilibili_redirect_cannot_replace_requested_work(self):
        with patch('nanzhufeng_probe.youtube_probe._fetch', return_value=(self.page(), 'https://www.bilibili.com/video/BV1bK411W797')) as fetch:
            with self.assertRaisesRegex(ValueError, '目标'):
                _bilibili_fallback_info('https://www.bilibili.com/video/BV1zv5561EtZ')
            self.assertEqual(1, fetch.call_count)

    def test_separate_audio_headers_never_replace_video_headers(self):
        formats=[{'url':'https://video.example/video.mp4','ext':'mp4','vcodec':'avc1','acodec':'none','height':720,'width':1280,'http_headers':{'User-Agent':'video','Referer':'https://video.example/','Cookie':'secret'}}, {'url':'https://audio.example/audio.m4a','ext':'m4a','vcodec':'none','acodec':'mp4a','http_headers':{'User-Agent':'audio','Referer':'https://audio.example/','Authorization':'secret'}}]
        result=_media_result({'id':'fixture','extractor_key':'YouTube','http_headers':{'Accept':'*/*'},'formats':formats})
        self.assertEqual('video',result['headers']['User-Agent'])
        self.assertEqual('audio',result['audio_headers']['User-Agent'])
        self.assertNotIn('Cookie',result['headers'])
        self.assertNotIn('Authorization',result['audio_headers'])

    def test_tiktok_short_link_identity_does_not_extract_video_twice(self):
        canonical = 'https://www.tiktok.com/@fixture/video/1234567890'
        with patch('nanzhufeng_probe.youtube_probe._resolve_known_short_link', return_value=canonical), patch('nanzhufeng_probe.youtube_probe.youtube_dl_for_source', side_effect=AssertionError('identity resolution must not fetch media')):
            self.assertEqual({'kind':'single','url':canonical}, json.loads(resolve_source('https://vm.tiktok.com/fixture/')))

    def test_bilibili_sequential_durl_is_not_a_quality_ladder(self):
        api = {'code':0,'data':{'durl':[{'url':'https://media.test/part1.flv'},{'url':'https://media.test/part2.flv'}]}}
        with patch('nanzhufeng_probe.youtube_probe._fetch', side_effect=[(self.page(), 'https://www.bilibili.com/video/BV1bK411W797'),(json.dumps(api),'https://api.bilibili.com/')]):
            with self.assertRaisesRegex(ValueError, '多段'):
                _bilibili_fallback_info('https://www.bilibili.com/video/BV1bK411W797')

    def test_bilibili_base58_identity_is_case_sensitive(self):
        with patch('nanzhufeng_probe.youtube_probe._fetch', return_value=(self.page(), 'https://www.bilibili.com/video/BV1bK411W797')):
            with self.assertRaisesRegex(ValueError, '目标'):
                _bilibili_fallback_info('https://www.bilibili.com/video/BV1bk411W797')

    def test_bilibili_preview_or_drm_is_not_a_complete_download(self):
        for data, reason in [({'is_preview':1,'durl':[{'url':'https://media.test/preview.mp4'}]},'试看'),({'dash':{'drm_tech_type':1}},'DRM')]:
            with patch('nanzhufeng_probe.youtube_probe._fetch',side_effect=[(self.page(),'https://www.bilibili.com/video/BV1bK411W797'),(json.dumps({'code':0,'data':data}),'https://api.bilibili.com/')]):
                with self.assertRaisesRegex(ValueError,reason):
                    _bilibili_fallback_info('https://www.bilibili.com/video/BV1bK411W797')

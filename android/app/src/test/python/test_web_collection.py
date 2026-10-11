import unittest

from nanzhufeng_probe.web_collection import catalog_from_info, episode_links


class WebCollectionTest(unittest.TestCase):
    def test_catalog_preserves_only_individual_absolute_entries_and_pagination(self):
        source = "https://example.test/playlist/one"
        info = {"_type": "playlist", "id": "one", "extractor_key": "ExamplePlaylist",
                "title": "Collection", "entries": [
                    {"id": "1", "title": "First", "url": "https://example.test/watch/1"},
                    {"id": "1", "title": "Duplicate", "url": "https://example.test/watch/1"},
                    {"id": "nested", "_type": "playlist", "url": "https://example.test/other"},
                    {"id": "2", "title": "Second", "url": "https://example.test/watch/2"},
                    {"id": "3", "url": "https://example.test/watch/3"},
                ]}
        page = catalog_from_info(info, source, 1, 4)
        self.assertEqual(["First", "Second"], [x["title"] for x in page["entries"]])
        self.assertEqual(2, page["duplicate_count"])
        self.assertTrue(page["has_more"])
        self.assertEqual(5, page["next_start"])

    def test_different_hosts_with_same_item_id_remain_distinct(self):
        info = {"_type": "playlist", "entries": [
            {"id": "same", "url": "https://first.test/video"},
            {"id": "same", "url": "https://second.test/video"},
        ]}
        page = catalog_from_info(info, "https://example.test/playlist", 1, 50)
        self.assertEqual(2, len(page["entries"]))
        self.assertNotEqual(page["entries"][0]["id"], page["entries"][1]["id"])

    def test_embedded_videos_use_distinct_media_urls_not_shared_page(self):
        source = "https://example.test/collection.html"
        page = catalog_from_info({"_type": "playlist", "entries": [
            {"id": "first", "title": "First", "webpage_url": source,
             "url": "https://example.test/first.mp4"},
            {"id": "second", "title": "Second", "webpage_url": source,
             "url": "https://example.test/second.mp4"},
        ]}, source, 1, 50)
        self.assertEqual(["https://example.test/first.mp4", "https://example.test/second.mp4"],
                         [entry["webpage_url"] for entry in page["entries"]])

    def test_yinghua_episodes_order_and_work_scope(self):
        links = episode_links("https://www.yhdm.one/vod-detail/42.html", [
            ("/vod-play/42/ep10.html", "ten"),
            ("/vod-play/99/ep1.html", "other"),
            ("/vod-play/42/ep2.html", "two"),
            ("https://ads.test/vod-play/42/ep1.html", "advert"),
        ])
        self.assertEqual(["第2集", "第10集"], [title for _, title in links])

    def test_yinghua_mixed_works_are_not_guessed(self):
        with self.assertRaisesRegex(ValueError, "多个作品"):
            episode_links("https://www.yhdm.one/", [
                ("/vod-play/42/ep1.html", "one"),
                ("/vod-play/99/ep1.html", "other"),
            ])


if __name__ == "__main__":
    unittest.main()

import unittest

from nanzhufeng_probe.youtube_probe import (
    _creator_page_result,
    _creator_result,
    _expected_creator_hint,
    _normalize_collection_url,
)


class CreatorResultTest(unittest.TestCase):
    def test_deduplicates_and_rejects_foreign_creator(self):
        info = {
            "uploader": "target",
            "uploader_id": "target",
            "webpage_url": "https://www.tiktok.com/@target",
            "entries": [
                {
                    "id": "1",
                    "title": "first",
                    "uploader_id": "target",
                    "url": "https://www.tiktok.com/@target/video/1",
                },
                {
                    "id": "1",
                    "title": "duplicate",
                    "uploader_id": "target",
                    "url": "https://www.tiktok.com/@target/video/1",
                },
                {
                    "id": "2",
                    "title": "foreign",
                    "uploader_id": "other",
                    "url": "https://www.tiktok.com/@other/video/2",
                },
                {
                    "id": "3",
                    "title": "inherited",
                    "url": "https://www.tiktok.com/@target/video/3",
                },
            ],
        }

        result = _creator_result(info, "target")

        self.assertEqual(["1", "3"], [entry["id"] for entry in result["entries"]])
        self.assertEqual(1, result["duplicate_count"])
        self.assertEqual(1, result["foreign_count"])

    def test_accepts_tiktok_handle_and_channel_identity_shape(self):
        info = {
            "id": "encrypted-channel-id",
            "title": "scout2015",
            "webpage_url": "https://www.tiktok.com/@scout2015",
            "entries": [
                {
                    "id": "7662829257558592799",
                    "title": "sample",
                    "uploader": "scout2015",
                    "uploader_id": "53279706535428096",
                    "channel": "Scout, Suki & Stella",
                    "channel_id": "encrypted-channel-id",
                    "url": "https://www.tiktok.com/@scout2015/video/7662829257558592799",
                },
            ],
        }

        result = _creator_result(info, "scout2015")

        self.assertEqual(1, len(result["entries"]))
        self.assertEqual("encrypted-channel-id", result["creator_id"])
        self.assertEqual("encrypted-channel-id", result["entries"][0]["creator_id"])
        self.assertEqual(0, result["foreign_count"])

    def test_creator_page_keeps_page_size_and_exposes_next_start(self):
        info = {
            "title": "target",
            "entries": [
                {"id": "1", "uploader": "target", "url": "https://www.tiktok.com/@target/video/1"},
                {"id": "2", "uploader": "target", "url": "https://www.tiktok.com/@target/video/2"},
                {"id": "3", "uploader": "target", "url": "https://www.tiktok.com/@target/video/3"},
            ],
        }

        result = _creator_page_result(info, "target", start=1, page_size=2)

        self.assertEqual(["1", "2"], [entry["id"] for entry in result["entries"]])
        self.assertTrue(result["has_more"])
        self.assertEqual(3, result["next_start"])

    def test_youtube_channel_url_is_normalized_to_videos_tab(self):
        self.assertEqual(
            "https://www.youtube.com/@target/videos",
            _normalize_collection_url("https://www.youtube.com/@target"),
        )
        self.assertEqual(
            "uctarget",
            _expected_creator_hint("https://www.youtube.com/channel/UCtarget"),
        )

    def test_playlist_keeps_mixed_creators_under_collection_identity(self):
        info = {
            "id": "PL123",
            "title": "mixed playlist",
            "entries": [
                {
                    "id": "one",
                    "uploader": "creator-a",
                    "channel_id": "UC-A",
                    "url": "https://www.youtube.com/watch?v=one",
                },
                {
                    "id": "two",
                    "uploader": "creator-b",
                    "channel_id": "UC-B",
                    "url": "https://www.youtube.com/watch?v=two",
                },
            ],
        }

        result = _creator_result(info, "PL123", strict_owner=False)

        self.assertEqual(["one", "two"], [entry["id"] for entry in result["entries"]])
        self.assertEqual({"pl123"}, {entry["creator_id"] for entry in result["entries"]})

    def test_youtube_flat_entry_id_becomes_downloadable_watch_url(self):
        info = {
            "id": "UCtarget",
            "channel_id": "UCtarget",
            "webpage_url": "https://www.youtube.com/@target/videos",
            "entries": [
                {
                    "id": "abc123",
                    "channel_id": "UCtarget",
                    "url": "abc123",
                },
            ],
        }

        result = _creator_result(info, "UCtarget")

        self.assertEqual(
            "https://www.youtube.com/watch?v=abc123",
            result["entries"][0]["webpage_url"],
        )

    def test_youtube_channel_flat_entries_inherit_verified_root_owner(self):
        info = {
            "id": "UCtarget",
            "channel_id": "UCtarget",
            "uploader_id": "@target",
            "webpage_url": "https://www.youtube.com/channel/UCtarget/videos",
            "entries": [
                {
                    "id": "abc123",
                    "url": "https://www.youtube.com/watch?v=abc123",
                },
            ],
        }

        result = _creator_result(
            info,
            "UCtarget",
            allow_inherited_owner=True,
        )

        self.assertEqual(["abc123"], [entry["id"] for entry in result["entries"]])
        self.assertEqual("uctarget", result["entries"][0]["creator_id"])
        self.assertEqual(0, result["foreign_count"])


if __name__ == "__main__":
    unittest.main()

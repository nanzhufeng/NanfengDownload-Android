import unittest

from nanzhufeng_probe.youtube_probe import _creator_result


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


if __name__ == "__main__":
    unittest.main()

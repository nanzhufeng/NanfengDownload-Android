"""Exercise the declared Room 9→10 SQL against SQLite without a device."""

import json
import re
import sqlite3
import unittest
from contextlib import closing
from pathlib import Path


APP_ROOT = Path(__file__).resolve().parents[3]
SCHEMAS = APP_ROOT / "schemas/com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase"
MIGRATIONS = APP_ROOT / "src/main/java/com/nanzhufeng/videodownloader/data/database/NanzhufengDatabase.kt"


class GenericCaptureMigrationSqliteTest(unittest.TestCase):
    def test_old_media_row_survives_and_columns_match_exported_schema(self):
        previous = json.loads((SCHEMAS / "9.json").read_text())["database"]
        current = json.loads((SCHEMAS / "10.json").read_text())["database"]
        old_table = next(item for item in previous["entities"] if item["tableName"] == "media_items")
        new_table = next(item for item in current["entities"] if item["tableName"] == "media_items")
        migration_source = MIGRATIONS.read_text().split("val MIGRATION_9_10", 1)[1].split("val MIGRATION_10_11", 1)[0]
        statements = re.findall(r'database\.execSQL\("([^"]+)"\)', migration_source)
        self.assertEqual(3, len(statements))

        with closing(sqlite3.connect(":memory:")) as database:
            database.execute(old_table["createSql"].replace("${TABLE_NAME}", "media_items"))
            old_fields = old_table["fields"]
            values = {
                field["columnName"]: (
                    0 if field["affinity"] == "INTEGER" else
                    "old" if field["columnName"] == "mediaKey" else
                    "[]" if field["columnName"] == "capturedImageUrlsJson" else ""
                ) for field in old_fields
            }
            names = list(values)
            database.execute(
                f"INSERT INTO media_items ({','.join(names)}) VALUES ({','.join('?' for _ in names)})",
                tuple(values.values()),
            )
            for statement in statements:
                database.execute(statement)

            # ALTER TABLE appends columns; Room schema field order follows the
            # Kotlin entity and does not define SQLite column identity.
            actual = {row[1]: (row[2], bool(row[3]))
                      for row in database.execute("PRAGMA table_info(media_items)")}
            expected = {field["columnName"]: (field["affinity"], field["notNull"])
                        for field in new_table["fields"]}
            self.assertEqual(expected, actual)
            self.assertEqual(
                ("old", "", "", 0),
                database.execute(
                    "SELECT mediaKey, capturedVideoUrl, capturedVideoReferer, "
                    "capturedVideoSourceVersion FROM media_items"
                ).fetchone(),
            )


if __name__ == "__main__":
    unittest.main()

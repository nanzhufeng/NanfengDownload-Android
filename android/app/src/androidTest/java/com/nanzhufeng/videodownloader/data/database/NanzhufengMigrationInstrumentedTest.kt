package com.nanzhufeng.videodownloader.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NanzhufengMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NanzhufengDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migration1To2_preservesHistoryAndAddsProblemColumns() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO download_history(
                    taskId, platform, contentId, originalUrl, title, creator,
                    resolution, finalStatus, outputUri, fileSize, fileExists, completedAt
                ) VALUES(
                    'task-1', 'YOUTUBE', 'video-1', 'https://youtu.be/video-1',
                    '标题', '作者', 'UP_TO_720P', 'FAILED', NULL, 0, 0, 100
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            NanzhufengMigrations.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT taskId, failureType, errorSummary FROM download_history WHERE taskId = 'task-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("task-1", cursor.getString(0))
            assertEquals(null, cursor.getString(1))
            assertEquals(null, cursor.getString(2))
        }
    }

    @Test
    fun migration3To4PreservesTasksAndAddsPermanentThroughputReports() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO media_items VALUES (
                    'YOUTUBE:one', 'YOUTUBE', 'one', 'https://youtu.be/one', 'SINGLE_VIDEO',
                    '标题', '作者', 'creator', '20260717', '', 100
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO download_tasks VALUES (
                    'task-1', 'YOUTUBE:one', 1, 1, 'UP_TO_720P', 'DATE_AND_TITLE', NULL, NULL,
                    0, 0, 0, NULL, 'WAITING', NULL, NULL, 0, 100, 100
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            NanzhufengMigrations.MIGRATION_3_4,
        )

        database.query(
            "SELECT connectionMode, connectionCount FROM download_tasks WHERE taskId = 'task-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("UNKNOWN", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        database.query("SELECT COUNT(*) FROM download_throughput_reports").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
        const val TEST_DB = "migration-3-4-test"
    }
}

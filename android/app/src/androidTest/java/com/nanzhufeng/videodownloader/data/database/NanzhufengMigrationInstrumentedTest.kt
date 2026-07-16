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

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}

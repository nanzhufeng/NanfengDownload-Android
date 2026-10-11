package com.nanzhufeng.videodownloader.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class GenericCaptureMigrationTest {
    @Test
    fun migrationNineToTenAddsOnlyNullableSafeCaptureDefaults() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, arguments ->
            if (method.name == "execSQL") statements += arguments?.firstOrNull() as String
            null
        } as SupportSQLiteDatabase
        NanzhufengMigrations.MIGRATION_9_10.migrate(database)
        assertEquals(listOf(
            "ALTER TABLE media_items ADD COLUMN capturedVideoUrl TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE media_items ADD COLUMN capturedVideoReferer TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE media_items ADD COLUMN capturedVideoSourceVersion INTEGER NOT NULL DEFAULT 0",
        ), statements)
    }
}

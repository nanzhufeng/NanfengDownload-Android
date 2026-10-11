package com.nanzhufeng.videodownloader.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFavoriteMigrationTest {
    @Test fun realSqliteUpgradePreservesLegacyOutputsAndStoresFavoritesAcrossReopen() {
        val statements = mutableListOf<String>()
        val db = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") statements += args!!.first() as String
            null
        } as SupportSQLiteDatabase
        NanzhufengMigrations.MIGRATION_10_11.migrate(db)
        val sql = File.createTempFile("favorite-migration-", ".sql")
        try {
            sql.writeText(statements.joinToString(";\n"))
            val script = """
                import sqlite3, json, sys, tempfile, pathlib
                schema = json.loads(pathlib.Path(sys.argv[1]).read_text())['database']
                with tempfile.TemporaryDirectory() as tmp:
                    path = pathlib.Path(tmp) / 'fixture.db'
                    c = sqlite3.connect(path)
                    for t in schema['entities']:
                        c.execute(t['createSql'].replace('${'$'}{TABLE_NAME}', t['tableName']))
                        for idx in t['indices']: c.execute(idx['createSql'].replace('${'$'}{TABLE_NAME}', t['tableName']))
                    c.execute("INSERT INTO download_history(taskId,platform,contentId,originalUrl,title,creator,resolution,finalStatus,outputUri,fileSize,fileExists,completedAt,failureType,errorSummary,outputUrisJson,audioSegmentCount,capturedImageExpectedCount,capturedImageSourceVersion) VALUES ('legacy','YOUTUBE','v','https://example.com/v','Video','Author','UP_TO_720P','COMPLETED','content://media/external/video/media/9',35000,1,1234,NULL,NULL,'[]',1,0,0)")
                    old = c.execute('SELECT * FROM download_history').fetchall()
                    columns = [x[1] for x in c.execute('pragma table_info(download_history)')]
                    c.executescript(pathlib.Path(sys.argv[2]).read_text())
                    projection = ','.join('"'+x+'"' for x in columns)
                    assert c.execute('SELECT '+projection+' FROM download_history').fetchall() == old
                    assert c.execute('SELECT isFavorite FROM download_history').fetchone() == (0,)
                    c.execute("UPDATE download_history SET isFavorite=1 WHERE taskId='legacy'")
                    c.commit(); c.close(); c = sqlite3.connect(path)
                    assert c.execute('SELECT isFavorite,outputUri,fileSize FROM download_history').fetchone() == (1,'content://media/external/video/media/9',35000)
                    c.execute("UPDATE download_history SET isFavorite=0 WHERE taskId='legacy'")
                    assert c.execute('SELECT '+projection+' FROM download_history').fetchall() == old
                    c.execute("DELETE FROM download_history WHERE taskId='legacy'")
                    assert c.execute('SELECT COUNT(*) FROM download_history').fetchone() == (0,)
            """.trimIndent()
            val schema = File("schemas/com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase/10.json").absolutePath
            val process = ProcessBuilder("python3", "-c", script, schema, sql.absolutePath).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.waitFor())
        } finally { sql.delete() }
    }
}

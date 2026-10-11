"""Exercise production Room task lifecycle SQL against the exported SQLite schema."""
import json
import re
import sqlite3
import unittest
from pathlib import Path

APP = Path(__file__).resolve().parents[3]
DAO = APP / 'src/main/java/com/nanzhufeng/videodownloader/data/database/dao/DownloadDaos.kt'
SCHEMA = APP / 'schemas/com.nanzhufeng.videodownloader.data.database.NanzhufengDatabase/10.json'


class TaskNetworkLifecycleTest(unittest.TestCase):
    def setUp(self):
        table = next(e for e in json.loads(SCHEMA.read_text())['database']['entities']
                     if e['tableName'] == 'download_tasks')
        self.db = sqlite3.connect(':memory:')
        self.addCleanup(self.db.close)
        self.db.execute(table['createSql'].replace('${TABLE_NAME}', 'download_tasks'))
        self.states = ('WAITING', 'PARSING', 'DOWNLOADING', 'VALIDATING', 'WAITING_NETWORK',
                       'PAUSED', 'CANCELLED', 'COMPLETED', 'FAILED')
        for index, state in enumerate(self.states):
            values = {f['columnName']: 0 if f['affinity'] == 'INTEGER' else ''
                      for f in table['fields']}
            values.update(taskId=state, mediaKey='fixture', status=state, selected=1, sortOrder=index)
            names = list(values)
            self.db.execute(f"INSERT INTO download_tasks ({','.join(names)}) VALUES "
                            f"({','.join('?' for _ in names)})", tuple(values.values()))

    def query(self, method):
        queries = dict((name, sql) for sql, name in re.findall(
            r'@Query\(\s*"""(.*?)"""\s*,?\s*\)\s*suspend fun (\w+)',
            DAO.read_text(), re.S))
        self.assertTrue(method in queries, f'缺少公开持久状态操作 {method}')
        return queries[method]

    def statuses(self):
        return dict(self.db.execute('SELECT taskId,status FROM download_tasks'))

    def test_manual_pause_includes_validation_and_keeps_terminal_tasks(self):
        self.db.execute(self.query('pauseRunnableTasks'), {'updatedAt': 123})
        self.assertEqual({s: 'PAUSED' if s in self.states[:6] else s for s in self.states},
                         self.statuses())

    def test_network_loss_holds_active_tasks_without_overwriting_user_pause_or_terminal_state(self):
        self.db.execute("UPDATE download_tasks SET downloadedBytes=1234")
        self.db.execute(self.query('markActiveTasksWaitingForNetwork'), {'updatedAt': 456})
        expected = dict(zip(self.states, self.states))
        expected.update(PARSING='WAITING_NETWORK', DOWNLOADING='WAITING_NETWORK', VALIDATING='WAITING_NETWORK')
        self.assertEqual(expected, self.statuses())
        self.assertEqual((1234, 'NETWORK', 1), self.db.execute(
            "SELECT downloadedBytes,failureType,retryCount FROM download_tasks WHERE taskId='DOWNLOADING'").fetchone())

    def test_explicit_network_resume_changes_only_network_waiting(self):
        self.db.execute(self.query('resumeNetworkWaitingTasks'), {'updatedAt': 789})
        expected = dict(zip(self.states, self.states))
        expected['WAITING_NETWORK'] = 'WAITING'
        self.assertEqual(expected, self.statuses())


if __name__ == '__main__':
    unittest.main()

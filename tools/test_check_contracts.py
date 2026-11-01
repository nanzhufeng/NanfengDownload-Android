"""Contract-maintenance checks in temporary repositories; no app/device access."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from check_contracts import check


CONCEPTS = ('sources', 'tasks-output', 'transfer', 'playback', 'favorites',
            'background-island', 'feedback-island', 'release-verification')


class ContractChecksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write('source.kt', '// fixture\n')
        self.write('README.md', '# Project\n')
        self.write('docs/verification/evidence.md', '# Dated evidence\n')
        for directory in ('archive', 'superpowers/plans', 'superpowers/specs'):
            (self.root / 'docs' / directory).mkdir(parents=True)
        records = []
        for concept in CONCEPTS:
            document = f'docs/{concept}.md'
            self.write(document, '# Contract\n\n> 合同层级：现行合同。\n')
            records.append({'id': concept, 'document': document,
                            'sources': {'source.kt': hashlib.sha256(b'// fixture\n').hexdigest()},
                            'evidence': ['docs/verification/evidence.md']})
        self.write('docs/contracts/README.md', '\n'.join(
            f'[Contract](../{concept}.md)' for concept in CONCEPTS))
        self.registry = {
            'schema': 1, 'reviewedAt': '2026-10-18', 'index': 'docs/contracts/README.md',
            'contracts': records,
            'historicalDirectories': ['docs/archive', 'docs/superpowers/plans', 'docs/superpowers/specs'],
            'evidenceDirectories': ['docs/verification'],
            'documentRoles': {'README.md': 'entry'},
        }
        self.save_registry()

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def save_registry(self):
        self.write('docs/contracts/registry.json', json.dumps(self.registry))

    def result(self):
        return check(self.root, 'docs/contracts/registry.json')

    def test_valid_registered_project(self):
        self.assertEqual('PASS', self.result()['result'])

    def test_encoded_document_link_with_spaces_is_valid(self):
        self.write('docs/guide notes.md', '# Notes\n')
        self.registry['documentRoles']['docs/guide notes.md'] = 'architecture'
        self.save_registry()
        self.write('docs/sources.md', '# Contract\n\n> 合同层级：现行合同。\n\n'
                   '[Notes](<guide%20notes.md>)\n')
        self.assertEqual([], self.result()['errors'])

    def test_new_document_cannot_silently_bypass_classification(self):
        self.write('docs/second-favorites-contract.md', '# Another set of rules\n')
        self.assertEqual('FAIL', self.result()['result'])

    def test_readme_missing_preview_is_rejected(self):
        self.write('README.md', '# Project\n<img src="docs/missing-preview.png" width="240" />\n')
        self.assertEqual('FAIL', self.result()['result'])

    def test_source_change_requires_review(self):
        self.write('source.kt', '// changed behavior\n')
        self.assertEqual('FAIL', self.result()['result'])

    def test_duplicate_canonical_document_is_rejected(self):
        self.registry['contracts'][1]['document'] = 'docs/sources.md'
        self.save_registry()
        self.assertEqual('FAIL', self.result()['result'])

    def test_nested_history_requires_classification(self):
        self.write('docs/archive/older/handoff.md', '# Old rules\n')
        self.assertEqual('FAIL', self.result()['result'])

    def test_supporting_document_cannot_claim_canonical_role(self):
        self.registry['documentRoles']['docs/sources.md'] = 'architecture'
        self.save_registry()
        self.assertEqual('FAIL', self.result()['result'])

    def test_encoded_outside_link_is_rejected(self):
        self.write('README.md', '# Project\n[Outside](%2e%2e/outside.md)\n')
        self.assertEqual('FAIL', self.result()['result'])

    def test_historical_reference_cannot_look_current(self):
        self.registry['documentRoles']['README.md'] = 'historical-reference'
        self.save_registry()
        self.assertEqual('FAIL', self.result()['result'])

    def test_root_document_cannot_be_a_second_canonical_contract(self):
        self.write('README.md', '# Project\n\n> 合同层级：现行合同。\n')
        self.assertEqual('FAIL', self.result()['result'])


if __name__ == '__main__':
    unittest.main()

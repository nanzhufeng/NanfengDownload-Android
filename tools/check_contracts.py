#!/usr/bin/env python3
"""Read-only contract routing/drift check; never updates reviewed hashes."""
import argparse
import hashlib
import json
from pathlib import Path
import re


REQUIRED = {'sources', 'tasks-output', 'transfer', 'playback', 'favorites',
            'background-island', 'feedback-island', 'release-verification'}


def check(root, registry_path):
    errors = []
    root = root.resolve()

    def local(name):
        target = (root / name).resolve()
        if not target.is_relative_to(root) or not target.is_file():
            raise ValueError(f'Invalid local file: {name}')
        return target

    data = json.loads(local(registry_path).read_text())
    if data.get('schema') != 1:
        raise ValueError('Unsupported registry schema')
    index = local(data['index'])
    index_text = index.read_text()
    records = data['contracts']
    ids = [item['id'] for item in records]
    documents = [item['document'] for item in records]
    if len(ids) != len(set(ids)) or len(documents) != len(set(documents)):
        errors.append('Duplicate concept or canonical document')
    if not REQUIRED.issubset(ids):
        errors.append('Missing current contract concepts')
    inspected = {index}
    sources = set()
    for document in (root / 'docs').rglob('*.md'):
        if '> 合同层级：现行合同。' in document.read_text()[:500] and document.relative_to(root).as_posix() not in documents:
            errors.append(f'Unregistered current contract: {document.relative_to(root)}')
    for item in records:
        document = local(item['document'])
        inspected.add(document)
        if '> 合同层级：现行合同。' not in document.read_text()[:500]:
            errors.append(f'Missing canonical classification: {item["id"]}')
        relative = document.relative_to(index.parent).as_posix() if document.is_relative_to(index.parent) else None
        # Canonical documents are currently siblings/parents of the index.
        if relative is None:
            import os
            relative = Path(os.path.relpath(document, index.parent)).as_posix()
        if relative not in index_text:
            errors.append(f'Canonical document absent from index: {item["id"]}')
        if not item['sources'] or not item['evidence']:
            errors.append(f'Missing sources/evidence: {item["id"]}')
        for name, expected in item['sources'].items():
            source = local(name)
            sources.add(name)
            if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
                errors.append(f'Reviewed source drift: {item["id"]}: {name}')
        for name in item['evidence']:
            local(name)
    historical = 0
    if not {'docs/archive', 'docs/superpowers/plans', 'docs/superpowers/specs'}.issubset(data['historicalDirectories']):
        errors.append('Historical directories removed from audit coverage')
    for name in data['historicalDirectories']:
        directory = (root / name).resolve()
        if not directory.is_relative_to(root) or not directory.is_dir():
            raise ValueError(f'Invalid history directory: {name}')
        for document in directory.glob('*.md'):
            inspected.add(document)
            historical += 1
            if '> 合同层级：' not in document.read_text():
                errors.append(f'Unclassified historical contract: {document.relative_to(root)}')
    links = 0
    for document in inspected:
        for raw in re.findall(r'\[[^\]]+\]\(([^)]+)\)', document.read_text()):
            target = raw.split('#', 1)[0]
            if not target or re.match(r'^(?:https?://|mailto:)', target):
                continue
            path = (document.parent / target).resolve()
            if not path.is_relative_to(root) or not path.exists():
                errors.append(f'Broken/out-of-root link: {document.relative_to(root)}: {target}')
            links += 1
    return {'result': 'FAIL' if errors else 'PASS', 'contracts': len(records),
            'reviewedSources': len(sources), 'historicalDocuments': historical,
            'localLinks': links, 'errors': errors,
            'scope': 'routing/classification/source drift only; semantic and business validation are separate'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--registry', default='docs/contracts/registry.json')
    args = parser.parse_args()
    try:
        result = check(args.root, args.registry)
    except (OSError, KeyError, ValueError, TypeError) as error:
        parser.exit(1, f'FAIL: {error}\n')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if result['errors'] else 0


if __name__ == '__main__':
    raise SystemExit(main())

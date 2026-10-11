#!/usr/bin/env python3
"""Read-only check of tracked production sources and an already frozen APK.

This does not build, sign, install, publish, or prove media/user-path correctness.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify(root, release, apk, source_ref):
    artifact = json.loads((release / 'artifact.json').read_text())
    manifest = json.loads((release / 'source-manifest.json').read_text())
    files = manifest['files']
    assert files, 'Empty source manifest'
    assert artifact['version'] == manifest['version'], 'Version mismatch'
    summary = digest(json.dumps(files, sort_keys=True).encode())
    assert summary == manifest['productionSourcesSha256'] == artifact['productionSourcesSha256'], 'Source summary mismatch'
    paths = ['android/app/src/main', 'android/app/build.gradle.kts',
             'android/app/proguard-rules.pro', 'android/build.gradle.kts',
             'android/settings.gradle.kts', 'android/gradle.properties']
    tracked = subprocess.check_output(['git', 'ls-files', '-z', '--', *paths], cwd=root).decode().split('\0')
    assert set(files) == set(filter(None, tracked)), 'Tracked production file set mismatch'
    for name, expected in files.items():
        path = (root / name).resolve()
        assert path.is_relative_to(root.resolve()), f'Unsafe source path: {name}'
        assert digest(path.read_bytes()) == expected, f'Worktree source changed: {name}'
        committed = subprocess.check_output(['git', 'show', f'{source_ref}:{name}'], cwd=root)
        assert digest(committed) == expected, f'Committed source changed: {name}'
    assert apk.stat().st_size == artifact['bytes'], 'APK size mismatch'
    assert digest(apk.read_bytes()) == artifact['sha256'], 'APK hash mismatch'
    checked_archives = []
    with zipfile.ZipFile(apk) as package:
        for name in ('assets/chaquopy/app.imy', 'assets/chaquopy/requirements-common.imy'):
            with zipfile.ZipFile(io.BytesIO(package.read(name))) as archive:
                polluted = [entry.filename for entry in archive.infolist()
                            if not entry.is_dir() and '__pycache__' in entry.filename.split('/')]
                assert not polluted, f'Host bytecode in {name}: {polluted[:3]}'
            checked_archives.append(name)
    return {'result': 'PASS', 'version': artifact['version'], 'sourceRef': source_ref,
            'productionFiles': len(files), 'apkBytes': artifact['bytes'],
            'apkSha256': artifact['sha256'], 'archivesWithoutHostCache': checked_archives,
            'scope': 'source/bytes/archive only; signing and business evidence are separate'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('release', type=Path, help='Directory containing artifact/source-manifest JSON')
    parser.add_argument('apk', type=Path, help='Existing frozen APK; never rebuilt here')
    parser.add_argument('--source-ref', required=True, help='Verified production commit')
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        # Assertions are deliberately used as invariant checks; disallow -O.
        if not __debug__:
            raise ValueError('Run without Python optimization flags')
        result = verify(args.root, args.release, args.apk, args.source_ref)
    except (AssertionError, OSError, KeyError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        parser.exit(1, f'FAIL: {error}\n')
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

# -*- mode: python ; coding: utf-8 -*-
from pathlib import Path

from PyInstaller.utils.hooks import collect_all, collect_submodules


project_root = Path.cwd()
datas = [("app/assets", "app/assets")]
binaries = []
hiddenimports = []
hiddenimports += collect_submodules("playwright")

tmp_ret = collect_all("yt_dlp")
datas += tmp_ret[0]
binaries += tmp_ret[1]
hiddenimports += tmp_ret[2]

local_ffmpeg = project_root / "tools" / "ffmpeg"
if local_ffmpeg.exists():
    datas.append((str(local_ffmpeg), "tools/ffmpeg"))


a = Analysis(
    ["start.py"],
    pathex=[],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="南烛枫视频下载器",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon="app/assets/nanzhufeng-icon.icns" if Path("app/assets/nanzhufeng-icon.icns").exists() else None,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="南烛枫视频下载器",
)
app = BUNDLE(
    coll,
    name="南烛枫视频下载器.app",
    icon="app/assets/nanzhufeng-icon.icns" if Path("app/assets/nanzhufeng-icon.icns").exists() else None,
    bundle_identifier="com.nanzhufeng.video-downloader",
    info_plist={
        "NSHighResolutionCapable": "True",
        "NSRequiresAquaSystemAppearance": "False",
    },
)

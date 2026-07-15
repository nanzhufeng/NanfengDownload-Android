from __future__ import annotations

import importlib.util
import sys


REQUIRED_MODULES = {
    "PySide6": "PySide6",
    "yt_dlp": "yt-dlp",
    "playwright": "playwright",
}


def main() -> int:
    missing = [
        package_name
        for module_name, package_name in REQUIRED_MODULES.items()
        if importlib.util.find_spec(module_name) is None
    ]
    if missing:
        print("缺少运行依赖：")
        for package_name in missing:
            print(f"  - {package_name}")
        print()
        print("安装命令：")
        print("  python -m pip install -r requirements.txt")
        print()
        print("说明：安装依赖需要联网；安装完成后再次运行 .\\run.ps1。")
        return 1

    if len(sys.argv) >= 3 and sys.argv[1] == "--login-browser":
        from app.auth_profile import open_login_browser

        open_login_browser(sys.argv[2])
        return 0

    from app.main import main as run_app

    return run_app()


if __name__ == "__main__":
    raise SystemExit(main())

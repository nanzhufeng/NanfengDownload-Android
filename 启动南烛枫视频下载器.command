#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Starting Nanzhufeng Video Downloader..."

if ! command -v python3 >/dev/null 2>&1; then
  echo "未找到 python3。请先安装 Python 3.11 或更高版本。"
  echo "推荐安装方式：brew install python"
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi

if [ ! -d ".venv-mac" ]; then
  echo "首次运行，正在创建 Mac 运行环境..."
  python3 -m venv .venv-mac
fi

source ".venv-mac/bin/activate"
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "未检测到 ffmpeg。普通视频可尝试下载，但图文合成/音频处理需要 ffmpeg。"
  echo "推荐安装方式：brew install ffmpeg"
fi

python start.py

#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Building Nanzhufeng Video Downloader for macOS..."

if ! command -v python3 >/dev/null 2>&1; then
  echo "未找到 python3。请先安装 Python 3.11 或更高版本。"
  echo "推荐安装方式：brew install python"
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi

if [ ! -d ".venv-mac" ]; then
  python3 -m venv .venv-mac
fi

source ".venv-mac/bin/activate"
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m pip install pyinstaller

if [ ! -f "app/assets/nanzhufeng-icon.icns" ] && [ -f "app/assets/nanzhufeng-icon.png" ]; then
  if command -v sips >/dev/null 2>&1 && command -v iconutil >/dev/null 2>&1; then
    echo "正在生成 Mac app 图标..."
    rm -rf "app/assets/nanzhufeng-icon.iconset"
    mkdir -p "app/assets/nanzhufeng-icon.iconset"
    for size in 16 32 64 128 256 512; do
      sips -z "$size" "$size" "app/assets/nanzhufeng-icon.png" --out "app/assets/nanzhufeng-icon.iconset/icon_${size}x${size}.png" >/dev/null
      double=$((size * 2))
      sips -z "$double" "$double" "app/assets/nanzhufeng-icon.png" --out "app/assets/nanzhufeng-icon.iconset/icon_${size}x${size}@2x.png" >/dev/null
    done
    iconutil -c icns "app/assets/nanzhufeng-icon.iconset" -o "app/assets/nanzhufeng-icon.icns"
    rm -rf "app/assets/nanzhufeng-icon.iconset"
  fi
fi

python -m PyInstaller --noconfirm --clean "南烛枫视频下载器_mac.spec"

echo
echo "Mac 版已生成："
echo "dist/南烛枫视频下载器.app"
echo
echo "如果下载或图文合成需要 ffmpeg，请确保 Mac 已安装：brew install ffmpeg"
read -n 1 -s -r -p "按任意键退出..."

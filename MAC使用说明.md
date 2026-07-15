# 南烛枫视频下载器 Mac 版说明

## 结论

当前 Windows 电脑不能直接生成可运行的 macOS `.app`，Mac 版必须在 Mac 电脑上打包。本项目已准备好 Mac 启动和打包文件。

## 先运行测试版

1. 把整个 `CleanVideoDownloader` 文件夹复制到 Mac。
2. 打开终端，进入该文件夹。
3. 赋予脚本执行权限：

```bash
chmod +x 启动南烛枫视频下载器.command 打包Mac版.command
```

4. 双击或运行：

```bash
./启动南烛枫视频下载器.command
```

首次运行会自动创建 `.venv-mac` 并安装依赖。

## 打包 app

在 Mac 上运行：

```bash
./打包Mac版.command
```

生成位置：

```text
dist/南烛枫视频下载器.app
```

## 依赖说明

- 需要 Python 3.11 或更高版本。
- 需要 Chrome 或 Edge 用于软件内登录。
- 建议安装 FFmpeg：

```bash
brew install ffmpeg
```

FFmpeg 用于 YouTube 合并音视频、抖音图文作品合成 mp4、仅音频导出等功能。

## 已做的 Mac 适配

- 支持 macOS 登录数据目录：`~/Library/Application Support/NanzhufengVideoDownloader`。
- 支持 Mac 常见 Chrome/Edge 路径。
- 支持 Homebrew FFmpeg 路径：`/opt/homebrew/bin`、`/usr/local/bin`。
- 添加 Mac 启动脚本。
- 添加 Mac PyInstaller 打包 spec。

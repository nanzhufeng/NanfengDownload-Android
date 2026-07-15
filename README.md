# 南烛枫视频下载器

一个简化版抖音 / YouTube 视频下载桌面工具原型。

## 边界

- 支持公开视频，以及登录后你有权限访问的非会员内容。
- 不支持会员、付费、DRM、私密内容绕过。
- 不在软件里填写、保存账号密码。
- 登录态只通过浏览器 Cookie 或 `cookies.txt` 交给 `yt-dlp` 使用。

## 功能

- 粘贴单个或多个链接。
- 自动排队下载。
- 可选最佳画质、1080p、720p、仅音频 MP3。
- 可选 Chrome / Edge / Firefox 浏览器 Cookie。
- 可选手动 `cookies.txt` 文件。
- 显示进度、速度、剩余时间、状态。
- 自动查找随包 `JHlib/ffmpeg/ffmpeg.exe`。

## 运行前准备

当前项目依赖：

```powershell
python -m pip install -r requirements.txt
```

如果需要打包成 EXE，可以后续再加 `PyInstaller` 打包脚本。

## 启动

```powershell
python .\start.py
```

或运行：

```powershell
.\run.ps1
```

## 登录 Cookie 说明

推荐先用浏览器正常登录 YouTube / 抖音网页端，然后在软件里选择对应浏览器 Cookie。

不要把主账号用于高频批量下载。平台可能触发验证码、限流或封号。

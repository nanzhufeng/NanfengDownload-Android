"""Local-only manual fixture: rotate a playable video's URL without changing its work page."""

import argparse
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


def serve(video: Path, port: int) -> None:
    current_token = 1

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, _format, *_args):
            pass

        def do_POST(self):
            nonlocal current_token
            if self.path != "/rotate":
                self.send_error(404)
                return
            current_token += 1
            self.send_response(200)
            self.end_headers()
            self.wfile.write(str(current_token).encode())

        def do_HEAD(self):
            self.respond(False)

        def do_GET(self):
            self.respond(True)

        def respond(self, include_body: bool):
            parsed = urlsplit(self.path)
            if parsed.path == "/page.html":
                body = (
                    "<!doctype html><html><meta name='viewport' content='width=device-width'>"
                    "<title>播放源轮换测试作品</title><body>"
                    "<h1>播放源轮换测试作品</h1>"
                    "<button id='play' onclick='play()'>播放目标视频</button>"
                    "<script>function play(){let v=document.getElementById('target');"
                    "if(!v){v=document.createElement('video');v.id='target';v.controls=true;v.loop=true;"
                    "v.muted=true;v.style.width='90vw';document.body.appendChild(v)}"
                    "v.src='/'+'media'+'.mp4?token='+" + str(current_token) + ";v.play()}</script>"
                    "</body></html>"
                ).encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                if include_body:
                    self.wfile.write(body)
                return
            if parsed.path != "/media.mp4":
                self.send_error(404)
                return
            if parse_qs(parsed.query).get("token") != [str(current_token)]:
                self.send_error(403)
                return
            data = video.read_bytes()
            start, end = 0, len(data) - 1
            requested = self.headers.get("Range", "")
            if requested:
                match = re.fullmatch(r"bytes=(\d+)-(\d*)", requested)
                if not match:
                    self.send_error(416)
                    return
                start = int(match.group(1))
                end = min(int(match.group(2)), end) if match.group(2) else end
                if start > end:
                    self.send_error(416)
                    return
            self.send_response(206 if requested else 200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end - start + 1))
            if requested:
                self.send_header("Content-Range", f"bytes {start}-{end}/{len(data)}")
            self.end_headers()
            if include_body:
                self.wfile.write(data[start : end + 1])

    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--video", type=Path, required=True)
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    serve(args.video, args.port)

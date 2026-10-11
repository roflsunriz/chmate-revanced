"""Local-only 5ch-style board for post retry and NG checks on a device.

Run this on the host and use ``adb reverse tcp:80 tcp:8765``. Open
http://127.0.0.1/test/read.cgi/test/1234567890/ in ChMate.
"""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from os import environ
from time import monotonic


POSTS = []
THREAD_ID = "1234567890"
WAIT_SECONDS = int(environ.get("MOCK_WAIT_SECONDS", "2"))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return

    def send_bytes(self, body, content_type="text/plain; charset=Shift_JIS"):
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        print(f"GET {self.path}", flush=True)
        if self.path.endswith("subject.txt"):
            self.send_bytes(f"{THREAD_ID}.dat<>Local retry test (30)\n".encode("ascii"))
        elif "/dat/" in self.path:
            content = (
                b"Tester<>sage<>2026/10/11(Sun) 20:00:00 ID:local<>Neutral opening post<>Local retry test\n"
                b"Target<>sage<>2026/10/11(Sun) 20:01:00 ID:middle<>middle_target_20261011<>\n"
                b"Responder<>sage<>2026/10/11(Sun) 20:02:00 ID:other<>&gt;&gt;2 linked reply<>\n"
            )
            for number in range(4, 31):
                content += (
                    f"User{number}<>sage<>2026/10/11(Sun) 20:03:00 ID:u{number}<>"
                    f"neutral response {number}<>\n"
                ).encode("ascii")
            self.send_bytes(content)
        elif self.path.endswith("SETTING.TXT"):
            self.send_bytes(b"BBS_TITLE=Local retry test\nBBS_NONAME_NAME=Tester\n")
        else:
            self.send_bytes(
                b"<html><head><title>Local retry test</title></head><body>Local retry test thread</body></html>",
                "text/html; charset=Shift_JIS",
            )

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        POSTS.append(monotonic())
        print(f"POST count={len(POSTS)} path={self.path} body_bytes={length}", flush=True)
        if len(POSTS) == 1:
            body = (
                "<html><head><title>ＥＲＲＯＲ</title></head><body>"
                f"頻繁に投稿しすぎです。再投稿まで{WAIT_SECONDS}秒待ってください"
                "</body></html>"
            ).encode("shift_jis")
        else:
            body = b"<html><head><title>ERROR</title></head><body>Local test completed</body></html>"
            print(f"RETRY_DELTA_SECONDS={POSTS[-1] - POSTS[-2]:.3f}", flush=True)
        self.send_bytes(body, "text/html; charset=Shift_JIS")


server = ThreadingHTTPServer(("127.0.0.1", 8765), Handler)
print("MOCK_BOARD_READY 127.0.0.1:8765", flush=True)
server.serve_forever()

"""Narrow yt-dlp HTTP compatibility for official Pornhub single-work pages.

On Android's embedded Python, yt-dlp's own urllib connection returns HTTP
410 for a public target page while Python's standard urllib connection on
the same device returns 200. Keep the alternate connection confined to this
extractor session; other platforms retain yt-dlp's default request handlers.
"""

from urllib.parse import parse_qs, urlsplit
from urllib.request import HTTPCookieProcessor, ProxyHandler, build_opener

from yt_dlp import YoutubeDL
from yt_dlp.networking._urllib import RedirectHandler, UrllibRH
from .android_network import proxy_options


def is_official_pornhub_video(url):
    parsed = urlsplit(url)
    host = (parsed.hostname or "").lower()
    return (
        parsed.scheme == "https"
        and (host == "pornhub.com" or host.endswith(".pornhub.com"))
        and parsed.path.lower() == "/view_video.php"
        and bool((parse_qs(parsed.query).get("viewkey") or [""])[0])
    )


class _StandardConnectionUrllibRH(UrllibRH):
    def _create_instance(self, proxies, cookiejar, legacy_ssl_support=None):
        # Keep yt-dlp's domain-scoped CookieJar and redirect credential guard,
        # changing only the connection handler that fails on Android.
        opener = build_opener(
            ProxyHandler(proxies),
            HTTPCookieProcessor(cookiejar),
            RedirectHandler(),
        )
        opener.addheaders = []
        return opener


class PornhubCompatibleYoutubeDL(YoutubeDL):
    def build_request_director(self, handlers, preferences=None):
        scoped = [
            _StandardConnectionUrllibRH if handler is UrllibRH else handler
            for handler in handlers
        ]
        director = super().build_request_director(scoped, preferences)
        director.preferences.add(
            lambda handler, _: 500 if isinstance(handler, _StandardConnectionUrllibRH) else 0
        )
        return director


def youtube_dl_for_source(url, options):
    options = proxy_options(url, options)
    if not is_official_pornhub_video(url):
        return YoutubeDL(options)
    scoped = dict(options)
    headers = dict(scoped.get("http_headers") or {})
    # The standard opener does not transparently decompress gzip responses.
    headers["Accept-Encoding"] = "identity"
    scoped["http_headers"] = headers
    return PornhubCompatibleYoutubeDL(scoped)


def transfer_youtube_dl_configuration(spec, options):
    """Use the same transport only for media yielded by an official video page."""
    page_url = str(spec.get("source_page_url") or "")
    options = proxy_options(str(spec.get("url") or page_url), options)
    media = urlsplit(str(spec.get("url") or ""))
    media_host = (media.hostname or "").lower()
    if not (
        is_official_pornhub_video(page_url)
        and media.scheme == "https"
        and (media_host == "phncdn.com" or media_host.endswith(".phncdn.com"))
    ):
        return YoutubeDL, options
    scoped = dict(options)
    headers = dict(scoped.get("http_headers") or {})
    headers["Accept-Encoding"] = "identity"
    scoped["http_headers"] = headers
    return PornhubCompatibleYoutubeDL, scoped


def youtube_dl_for_transfer(spec, options):
    engine_class, scoped_options = transfer_youtube_dl_configuration(spec, options)
    return engine_class(scoped_options)

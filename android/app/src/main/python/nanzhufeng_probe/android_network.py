"""Honor Android's configured HTTP proxy without changing process environment.

Desktop Python retains its existing platform proxy lookup. Android urllib has
no native ProxySelector integration, unlike OkHttp; select the current route
for each request and preserve HTTPS tunneling and origin certificate checks.
"""
from urllib.error import URLError
from urllib.request import ProxyHandler, build_opener


def _java_classes():
    try:
        from java import jclass
    except ImportError:
        return None
    return jclass("java.lang.System"), jclass("java.net.ProxySelector"), jclass("java.net.URI")


def android_http_proxy(url):
    classes = _java_classes()
    if classes is None:
        return None  # Host Python: preserve its existing environment/system routing.
    system, selectors, uri = classes
    if str(system.getProperty("java.vm.name")) != "Dalvik":
        return None
    stage = "selector"
    try:
        selector = selectors.getDefault()
        if selector is None:
            return ""
        stage = "select"
        proxies = selector.select(uri(url))
        stage = "routes"
        # Use java.util.List explicitly; Java collections are not Python iterables on every bridge runtime.
        for index in range(int(proxies.size())):
            proxy = proxies.get(index)
            kind = str(proxy.type().name())
            if kind == "DIRECT":
                return ""
            if kind != "HTTP":
                raise URLError("系统代理类型暂不受内嵌读取支持")
            address = proxy.address()
            host = str(address.getHostString())
            port = int(address.getPort())
            if not host or not 1 <= port <= 65535 or any(c in host for c in "/@\\\r\n"):
                raise URLError("系统代理地址无效")
            if ":" in host and not host.startswith("["):
                host = "[" + host + "]"
            return f"http://{host}:{port}"
        return ""
    except URLError:
        print(f"ReadPerformanceProxy phase={stage} type=URLError", flush=True)
        raise
    except Exception as error:
        print(f"ReadPerformanceProxy phase={stage} type={type(error).__name__}", flush=True)
        raise URLError(f"无法读取系统网络代理配置（{stage}:{type(error).__name__}）") from None


def proxy_options(url, options):
    selected = android_http_proxy(url)
    if selected is None:
        return options
    return dict(options, proxy=selected)


class _AndroidProxyHandler(ProxyHandler):
    def __init__(self):
        super().__init__({})

    def http_open(self, request):
        selected = android_http_proxy(request.full_url)
        if selected:
            return self.proxy_open(request, selected, request.type)
        return None

    https_open = http_open


def system_opener(*handlers):
    classes = _java_classes()
    if classes is not None and str(classes[0].getProperty("java.vm.name")) == "Dalvik":
        return build_opener(_AndroidProxyHandler(), *handlers)
    return build_opener(*handlers)

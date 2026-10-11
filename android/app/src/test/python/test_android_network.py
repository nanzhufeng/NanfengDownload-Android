import unittest
from unittest.mock import MagicMock, patch
from urllib.error import URLError
from nanzhufeng_probe.android_network import android_http_proxy, proxy_options, _AndroidProxyHandler


class AndroidNetworkTest(unittest.TestCase):
    def classes(self, kind="HTTP", host="proxy.test", port=8888):
        system=MagicMock();system.getProperty.return_value="Dalvik"
        selectors=MagicMock();uri=lambda value:value
        proxy=MagicMock();proxy.type.return_value.name.return_value=kind
        proxy.address.return_value.getHostString.return_value=host
        proxy.address.return_value.getPort.return_value=port
        routes=MagicMock();routes.size.return_value=1;routes.get.return_value=proxy
        selectors.getDefault.return_value.select.return_value=routes
        return system,selectors,uri

    def test_host_python_preserves_existing_options(self):
        options={"socket_timeout":20,"retries":1}
        with patch("nanzhufeng_probe.android_network._java_classes",return_value=None):
            self.assertIs(options,proxy_options("https://fixture.test/",options))

    def test_android_http_route_is_read_without_mutating_original_options(self):
        options={"socket_timeout":20,"retries":1}
        with patch("nanzhufeng_probe.android_network._java_classes",return_value=self.classes()):
            routed=proxy_options("https://fixture.test/",options)
        self.assertEqual("http://proxy.test:8888",routed["proxy"])
        self.assertNotIn("proxy",options);self.assertEqual(20,routed["socket_timeout"])

    def test_direct_route_does_not_inherit_a_stale_process_proxy(self):
        with patch("nanzhufeng_probe.android_network._java_classes",return_value=self.classes(kind="DIRECT")):
            self.assertEqual("",android_http_proxy("https://fixture.test/"))

    def test_ipv6_proxy_authority_is_correctly_bracketed(self):
        with patch("nanzhufeng_probe.android_network._java_classes",return_value=self.classes(host="::1")):
            self.assertEqual("http://[::1]:8888",android_http_proxy("https://fixture.test/"))

    def test_unsupported_or_invalid_proxy_does_not_silently_send_direct(self):
        for classes in [self.classes(kind="SOCKS"),self.classes(host="bad@host"),self.classes(port=0)]:
            with patch("nanzhufeng_probe.android_network._java_classes",return_value=classes),self.assertRaises(URLError):
                android_http_proxy("https://fixture.test/")

    def test_each_urllib_request_selects_its_own_destination_route(self):
        with patch("nanzhufeng_probe.android_network.android_http_proxy",side_effect=["http://proxy.test:8888",""]) as lookup:
            handler=_AndroidProxyHandler()
            with patch.object(handler,"proxy_open") as tunnel:
                request=MagicMock(full_url="https://first.test/",type="https")
                handler.https_open(request);tunnel.assert_called_once_with(request,"http://proxy.test:8888","https")
                handler.https_open(MagicMock(full_url="https://second.test/",type="https"))
                self.assertEqual(1,tunnel.call_count)
            self.assertEqual(["https://first.test/","https://second.test/"],[c.args[0] for c in lookup.call_args_list])

    def test_public_extractor_receives_the_actual_android_route(self):
        from nanzhufeng_probe.pornhub_http import youtube_dl_for_source
        with patch("nanzhufeng_probe.android_network.android_http_proxy",return_value="http://proxy.test:8888"), patch("nanzhufeng_probe.pornhub_http.YoutubeDL") as engine:
            youtube_dl_for_source("https://www.youtube.com/watch?v=fixture1234",{"quiet":True})
            self.assertEqual("http://proxy.test:8888",engine.call_args.args[0]["proxy"])

    def test_segment_transfer_receives_the_route_for_its_media_destination(self):
        from nanzhufeng_probe.pornhub_http import transfer_youtube_dl_configuration
        with patch("nanzhufeng_probe.android_network.android_http_proxy",return_value="http://proxy.test:8888") as route:
            _,options=transfer_youtube_dl_configuration({"url":"https://media.fixture.test/target.m3u8"},{"quiet":True})
            self.assertEqual("http://proxy.test:8888",options["proxy"])
            route.assert_called_once_with("https://media.fixture.test/target.m3u8")

    def test_selector_error_is_explicit_and_does_not_disclose_addresses(self):
        classes=self.classes();classes[1].getDefault.side_effect=RuntimeError("private proxy configuration")
        with patch("nanzhufeng_probe.android_network._java_classes",return_value=classes),self.assertRaises(URLError) as raised:
            android_http_proxy("https://fixture.test/")
        self.assertIn("selector:RuntimeError",str(raised.exception))
        self.assertNotIn("private proxy",str(raised.exception))

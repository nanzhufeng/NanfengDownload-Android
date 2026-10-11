(() => {
    if (window.top !== window || window.__nfReadTargetVideo) return;
    const targetId = () => (location.pathname.match(/^\/(?:share\/)?video\/(\d+)\/?$/) || [])[1];
    const supported = (value) => {
        try {
            const u = new URL(value);
            const hosts = ['douyin.com', 'iesdouyin.com', 'douyinvod.com', 'bytecdn.cn', 'byteimg.com', 'ibytedtos.com', 'amemv.com', 'snssdk.com'];
            return u.protocol === 'https:' && !u.username && !u.password && (!u.port || u.port === '443') &&
                hosts.some(h => u.hostname === h || u.hostname.endsWith('.' + h));
        } catch (_) { return false; }
    };
    const accept = (data) => {
        const id = targetId();
        if (!id || !data || (typeof data.status_code === 'number' && data.status_code !== 0)) return;
        const visited = new Set();
        let budget = 2000;
        const walk = (node, depth) => {
            if (!node || typeof node !== 'object' || depth > 12 || visited.has(node) || budget-- <= 0) return;
            visited.add(node);
            if (String(node.aweme_id || node.awemeId || '') === id) {
                const detail = node.video ? node : node.aweme?.detail;
                const video = detail?.video;
                const urls = video?.play_addr?.url_list || video?.playAddr?.urlList || [];
                const sources = Array.isArray(urls) ? urls.filter(supported) : [];
                if (sources.length) {
                    window.__nfTargetVideo = {
                        id, sources: [...new Set(sources)], title: detail.desc || '',
                        creator: detail.author?.nickname || '',
                        thumbnail: video.cover?.url_list?.[0] || video.cover?.urlList?.[0] || ''
                    };
                }
            }
            for (const child of Object.values(node)) walk(child, depth + 1);
        };
        walk(data, 0);
    };
    const isDetailResponse = (value) => {
        try {
            const u = new URL(value, location.href);
            const official = ['douyin.com', 'iesdouyin.com'].some(h => u.hostname === h || u.hostname.endsWith('.' + h));
            return official && u.protocol === 'https:' &&
                ['/web/api/v2/aweme/iteminfo/', '/aweme/v1/web/aweme/detail/', '/aweme/v1/aweme/detail/'].includes(u.pathname);
        } catch (_) { return false; }
    };
    const parse = (text) => {
        if (typeof text !== 'string' || text.length > 512000) return;
        try { accept(JSON.parse(text)); } catch (_) {}
    };
    if (typeof window.fetch === 'function') {
        const original = window.fetch;
        window.fetch = function (...args) {
            return original.apply(this, args).then(response => {
                if (response.ok && isDetailResponse(response.url)) {
                    response.clone().text().then(parse).catch(() => {});
                }
                return response;
            });
        };
    }
    if (window.XMLHttpRequest) {
        const open = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (...args) {
            this.addEventListener('load', () => {
                if (this.status !== 200 || !isDetailResponse(this.responseURL)) return;
                if (this.responseType === 'json') accept(this.response);
                else if (!this.responseType || this.responseType === 'text') parse(this.responseText);
            }, { once: true });
            return open.apply(this, args);
        };
    }
    window.__nfReadTargetVideo = () => {
        accept(window._ROUTER_DATA);
        const result = window.__nfTargetVideo;
        return result?.id === targetId() ? result : null;
    };
})();

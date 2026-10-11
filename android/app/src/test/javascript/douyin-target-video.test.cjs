const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync(require('node:path').resolve(__dirname, '../../main/assets/douyin-target-video.js'), 'utf8');
const id = '7684884186910329202';
function page(data, responseUrl = 'https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/') {
    const location = { pathname: '/share/video/' + id + '/', href: 'https://www.iesdouyin.com/share/video/' + id + '/' };
    const window = { _ROUTER_DATA: data, fetch: async () => ({ ok: true, url: responseUrl, clone: () => ({ text: async () => JSON.stringify(data) }) }) };
    window.top = window;
    vm.runInNewContext(code, { window, location, URL });
    return { window, location };
}
function detail(workId = id) {
    return { aweme_id: workId, desc: 'target', author: { nickname: 'creator' }, video: { play_addr: { url_list: ['https://v3-web.douyinvod.com/video/tos/target.mp4'] } } };
}
test('target SSR data returns before a video element exists', () => {
    const p = page({ loaderData: { page: { item_list: [detail()] } } });
    assert.equal(p.window.__nfReadTargetVideo().sources[0], 'https://v3-web.douyinvod.com/video/tos/target.mp4');
    assert.equal(p.window.__nfReadTargetVideo().title, 'target');
});
test('recommendations and changed routes never become the target', () => {
    const p = page({ item_list: [detail('7690158572814077184')] });
    assert.equal(p.window.__nfReadTargetVideo(), null);
    p.window._ROUTER_DATA = { item_list: [detail()] };
    assert.ok(p.window.__nfReadTargetVideo());
    p.location.pathname = '/video/7690158572814077184';
    assert.equal(p.window.__nfReadTargetVideo(), null);
});
test('only the official target detail response can supply a stream', async () => {
    const p = page(null);
    const response = await p.window.fetch('unused');
    assert.equal(response.ok, true);
    await Promise.resolve();
    assert.equal(p.window.__nfReadTargetVideo(), null);
    const valid = page({ item_list: [detail()] });
    valid.window._ROUTER_DATA = null;
    await valid.window.fetch('unused');
    await Promise.resolve();
    assert.ok(valid.window.__nfReadTargetVideo());
    const foreign = page({ item_list: [detail()] }, 'https://evil.example/web/api/v2/aweme/iteminfo/');
    foreign.window._ROUTER_DATA = null;
    await foreign.window.fetch('unused');
    await Promise.resolve();
    assert.equal(foreign.window.__nfReadTargetVideo(), null);
});
test('untrusted media and nonzero platform status are rejected', () => {
    const a = detail(); a.video.play_addr.url_list = ['https://evil.example/target.mp4'];
    assert.equal(page({ item_list: [a] }).window.__nfReadTargetVideo(), null);
    assert.equal(page({ status_code: 1, item_list: [detail()] }).window.__nfReadTargetVideo(), null);
});

# LAME 4.0 source and license notice

- Project: LAME
- Version: 4.0
- Upstream source: <https://sourceforge.net/projects/lame/files/lame/4.0/>
- Source archive SHA-256: `3df5124d5ad3a98312ffd7ba6a9b36230e4f8a3e66d3ce0f425e336c32d216eb`
- License: GNU Library General Public License version 2, as supplied in `COPYING`

The Android application builds LAME from the verified upstream source included
under `src/main/cpp/third_party/lame-4.0` and links it as the separate
`libmp3lame.so` shared library. The JNI-facing application bridge is built as
the separate `libnanzhufeng_mp3.so` shared library.

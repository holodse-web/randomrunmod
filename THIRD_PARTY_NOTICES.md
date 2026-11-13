# Third-party components / Сторонние компоненты

RandomRun's own code is covered by the `LICENSE` file. The components below are
**not** covered by it: they keep their own licenses, and nothing in RandomRun's
license limits the rights those licenses give you.

## webrtc-java 1.0.0 (bundled)

- What: Java bindings for WebRTC, used for voice chat and peer-to-peer battle
  connections. Bundled inside the mod JAR (`META-INF/jars/`) unmodified, from
  Maven Central: `dev.onvoid.webrtc:webrtc-java:1.0.0` and its platform jars
  `windows-x86_64`, `linux-x86_64`, `macos-x86_64`, `macos-aarch64`.
- License: Apache License 2.0. Copyright 2019-2026 Alex Andres.
- Source: https://github.com/devopvoid/webrtc-java (release 1.0.0).
- Full license texts: inside each bundled jar under `META-INF/LICENSE`,
  `META-INF/NOTICE` and `META-INF/licenses/`.

### Components inside the webrtc-java native libraries

The native library in each platform jar (`webrtc-java-<platform>.dll/.so/.dylib`)
contains WebRTC, statically linked together with the third-party code WebRTC
builds in:

| Component | License |
|---|---|
| WebRTC | BSD 3-Clause + WebRTC patent grant |
| libvpx, libyuv, libaom, dav1d, Opus, Abseil | BSD-style |
| OpenH264 | BSD 2-Clause |
| BoringSSL | OpenSSL / ISC-style |
| **FFmpeg (Chromium's copy, H.264 decoding only)** | **GNU LGPL 2.1 or later** |

The complete license texts of all of them are in
`META-INF/licenses/webrtc/LICENSE.md` of each platform jar.

### FFmpeg (LGPL 2.1+)

- The FFmpeg code is the one shipped with the WebRTC/Chromium source that
  webrtc-java 1.0.0 is built from. It is built without `--enable-gpl` and
  without `--enable-nonfree`.
- Corresponding source: the webrtc-java 1.0.0 source (link above) together with
  the WebRTC checkout it pins; FFmpeg is in `third_party/ffmpeg` of that checkout
  (Chromium's mirror: https://chromium.googlesource.com/chromium/third_party/ffmpeg).
  FFmpeg itself: https://ffmpeg.org.
- Your rights under the LGPL: you may replace the native library with your own
  build (it is a separate file inside the bundled platform jar), modify it, and
  reverse-engineer it to debug such modifications. RandomRun does not restrict
  any of this.
- Written offer: for at least three years after we last distribute this version,
  the RandomRun author will provide the complete corresponding source code of the
  LGPL components on request, for no more than the cost of providing it.
  Contact: see the mod page.

## Downloaded on request (not bundled)

- **Vosk** speech recognition (Apache License 2.0) and its language models are
  downloaded only when you turn on voice commands and press download in the
  settings, from `repo1.maven.org` and `alphacephei.com`.

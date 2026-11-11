<p align="center"><img src="docs/logo.png" alt="RandomRun" width="384"></p>

<p align="center"><a href="README.ru.md">Русская версия</a> · <a href="https://t.me/rrmnetwork">Telegram</a> · <a href="CHANGELOG.md">Changelog</a></p>

A Fabric client mod for Minecraft **26.3**, Java **25+**, mod version **27.1**. Obtain a random item as quickly as possible, alone or in a room with friends.

## Playing

- Solo runs: item targets, optional custom seed, timer, run history and personal records. Start with `/go` or the configured key.
- **Random Drop:** randomized block drops. These runs are excluded from the leaderboard.
- **RRM Upgrader:** bet up to 10 items of up to 3 types for a chance at more valuable items, stacks and bonus streaks up to x32. Open with **U** (configurable). The integrated server decides the outcome; room hosts enable it for everyone.
- Rooms for **2–6 players** with optional passwords, a shared target and synchronized start. Play in the host's **shared world** through built-in WebRTC (the host must keep it running), or in **separate worlds** with the same seed.
- A player who wins a room by outlasting everyone, without obtaining the item, does not enter the leaderboard.
- Online profiles, history and leaderboards. Register with your launcher nickname; old database records are not imported.
- Fair play: cheat clients, X-ray packs, minimaps and speedrun helpers are blocked before a run.
- Offline solo play without an account. Rooms and cloud data require sign-in.
- Friends with requests, live status and room invites; invited friends skip the room password.
- HUD editor, interface/audio settings, six color themes, English/Russian/Ukrainian translations.

**Change seed** in the pause menu creates a new solo world, preserving the target, Random Drop and Upgrader. Available after the initial loading delay, even before `/go`.

## Installation

1. Install Minecraft **26.3**, Fabric Loader **0.19.5+**, Java **25+**, and Fabric API for 26.3.
2. Put `RandomRun-26.3Fabric-27.1.jar` in `mods` and remove older RandomRun jars.
3. Use the same mod version on every player's client. WebRTC is bundled for Windows x64, Linux x64 and macOS (Intel and Apple Silicon); e4mc and a VPN are not required.

## Development

Requires Java 25, Node.js 22+ and npm.

```powershell
.\gradlew.bat build
.\gradlew.bat obfuscatedJar
cd server
npm ci
npm run dev
```

For a local Minecraft client set JVM property `-Drandomrun.api=http://127.0.0.1:8787`. The packaged API address is in `src/main/resources/randomrun-online.json`; remote overrides require HTTPS.

Jars: `build/libs` (plain), `build/obfuscated/release` (obfuscated). The crash mapping in `build/obfuscated` is private: do not distribute it. Build tasks do not publish releases.

The Upgrader catalog is regenerated with `python tools/upgrader/generate_values.py C:/path/to/minecraft-26.3-client.jar`. Its resource is `src/main/resources/assets/randomrun/upgrader_values.json`; audits go to `build/reports/upgrader`.

## Links and license

[Telegram](https://t.me/rrmnetwork). Copyright (c) 2026 Stanislav Kholod. All rights reserved. Source is published for reference; modification and redistribution of modified versions are not permitted. Modpack use is allowed with attribution. See [LICENSE](LICENSE).

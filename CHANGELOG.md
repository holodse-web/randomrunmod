# Changelog

## 27.2 — Minecraft 26.3

- First launch asks whether to play online or offline. Nothing is sent to the RandomRun servers until you agree; you can switch at any time in the mod menu.
- The login screen checks your nickname with the server only after you chose to play online.
- World auto-delete only removes worlds the mod created for a run; normal saves are never touched, whatever their folder is called.
- No more code obfuscation.
- Third-party notices (WebRTC, LGPL FFmpeg) are included in the jar: see THIRD_PARTY_NOTICES.md.
- Smoother floating item preview in menus; quieter victory sound.
- Fully compatible with 27.1 players: rooms, battles and the online service work across both versions.

## 27.1 — Minecraft 26.3

- Shared-world rooms use built-in WebRTC: e4mc and Radmin VPN are no longer required. Separate worlds with the same seed are also available.
- Friends with requests, live status and room invites.
- Reworked menus, loading screens, item roulette and HUD; six color themes.
- RRM Upgrader: refreshed interface, full-stack bets and bonus doubling streaks.
- Improved room synchronization, reconnect handling and rendering performance.
- Expanded fair-play checks. Random Drop runs and wins without obtaining the target are excluded from the leaderboard.
- New online service; accounts from the old database are not transferred.

## 27.0 — Minecraft 26.3

- Ported to Minecraft 26.3 with Java 25 and Fabric Loader 0.19.5+.
- Added RRM Upgrader for solo runs and rooms, with a configurable shortcut (U by default).
- Fixed private-room passwords, connection cleanup, version checks and item previews.
- Optimized HUD and interface rendering; fixed Java 25 release bytecode verification.
- Shared-world rooms in this release require e4mc.

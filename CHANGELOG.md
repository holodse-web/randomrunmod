# Changelog

## 27.0 — Minecraft 26.3

### New
- Port to Minecraft 26.3 (Java 25, Fabric Loader 0.19.5+, Fabric API 0.162.0+26.3): new rendering, SDL keyboard and mouse input, new world-creation screens.
- **RRM Upgrader**: chance wheel for solo runs and battles (host enables it per room). Up to 10 items and 3 item types per bet, chance shown in the wheel, result decided once and granted after the animation.
- Upgrader key is configurable in Settings → Controls (reset to U or unbind).
- Chance percentage changes color from red through yellow to green.

### Fixed
- Joining private rooms with the correct password: the password is now hashed once. Rooms created by older clients must be recreated by the host.
- Room event subscription path, partial events, and one-time room cleanup after a disconnect; a late network reply no longer clears a new room.
- Version check: separate 10-second limit, retry / back / offline choices, late replies from an old attempt no longer override a new one, `26.8-4` vs `26.9` comparison.
- Preview crashes before a world is loaded (`Components not bound yet`, decorated pot renderer).
- Achievement events and icons for 26.3; background lookup instead of scanning every JSON on the client thread.
- Online `/go` no longer prints a premature "run started" message.

### Performance
- HUD rainbow border, upgrader wheel, button and text-field borders are submitted as a single batch instead of hundreds of GUI elements.
- Large target icon is rendered from the model at its real size and cached.
- Recipe ingredients are expanded once per start; skin UUIDs and notification texts are cached.
- Inventory checks and anti-cheat thresholds unchanged.

### Project
- Source cleanup: comments and debug logging removed, only warnings, errors and key events are logged.
- Release builds are obfuscated with ProGuard (`gradlew obfuscatedJar`).
- Backend connection details moved out of the public repository.

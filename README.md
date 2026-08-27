# KeybindSync

A Fabric mod that lets you save and load named keybind profiles without restarting Minecraft. Switch between PvP, survival, and modpack layouts instantly.

## Features

- **Named profiles** — save your current keybinds under any name and load them back anytime
- **Preview before loading** — see exactly what will change before applying a profile
- **Side panel** — manage profiles directly from the vanilla Controls screen
- **Auto-save on disconnect** — automatically saves your keybinds when you leave a server
- **Auto-load on launch** — loads a profile every time Minecraft starts
- **Per-server switching** — automatically loads a different profile when joining specific servers

## Hotkeys

- **F8** — quick-save current keybinds to a profile called `quick`
- **F9** — quick-load the `quick` profile

All other management (save, load, delete, preview) is done through the side panel on the vanilla Controls screen.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the correct JAR for your Minecraft version from [Modrinth](https://modrinth.com/mod/keybindsync)
4. Drop it into your `mods` folder

## Supported Versions

| Minecraft | JAR |
|-----------|-----|
| 1.20 – 1.20.1 | `keybindsync-1.20.1-x.x.x.jar` |
| 1.20.2 – 1.20.6 | `keybindsync-1.20.4-x.x.x.jar` |
| 1.21 – 1.21.8 | `keybindsync-1.21.1-x.x.x.jar` |
| 1.21.9 – 1.21.11 | `keybindsync-1.21-x.x.x.jar` |

## Building

Requires Java 21+. Each Minecraft version is its own subproject with its own
Gradle wrapper, so build them one at a time:

```bash
cd mc1_20_1    && ./gradlew build   # 1.20 – 1.20.1
cd mc1_20_4    && ./gradlew build   # 1.20.2 – 1.20.6
cd mc1_21      && ./gradlew build   # 1.21 – 1.21.8
cd fabric-1.21 && ./gradlew build   # 1.21.9 – 1.21.11
```

On Windows use `gradlew.bat` in place of `./gradlew`. Each build writes its JAR
to that subproject's `build/libs/`.

## License

MIT — see [LICENSE](LICENSE).

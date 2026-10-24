# CrystalRush (Minecraft 1.21.11 • Fabric)

**CrystalRush** is a configurable, state-driven End Crystal combat utility for **Minecraft Java Edition 1.21.11** using the **Fabric** toolchain and **Mod Menu**.

## Key Features
- **Hold-to-Activate Workflow**: Runs only while the configured activation key is held down during active gameplay. Automatically pauses or cancels if chat, inventory, or any GUI screen is opened, or if the game window loses focus.
- **Automatic Crystal Slot Detection & Safe Restoration**: Searches the hotbar (`0..8`) for `Items.END_CRYSTAL`, preferring your currently held slot. When the activation key is released, CrystalRush restores your previous hotbar slot *only* if you did not manually switch slots in the meantime.
- **Server-Confirmed State Machine**: Transitions cleanly through `IDLE`, `CHECKING_INVENTORY`, `SELECTING_CRYSTAL`, `CHECKING_TARGET`, `WAITING_FOR_GAME_STATE`, `ACTION_COMPLETED`, `CANCELLED`, and `RESTORING_SLOT`. Never assumes client interactions succeeded before observing the updated world state.
- **Mod Menu Dark/Cyan Configuration UI**: Persists settings atomically to `.minecraft/config/crystalrush.json` with full input validation and a one-click **Restore Default Settings** button.
- **Client + Optional Server Companion Architecture**:
  - **Client-Only Mode (Default)**: Works on any vanilla or Fabric 1.21.11 server using standard Minecraft interaction packets (`interactBlock` / `attackEntity` / `UpdateSelectedSlotC2SPacket`).
  - **Optional Server Companion Mode**: When installed on a Fabric 1.21.11 server, administrators can configure `config/crystalrush-server.json` to broadcast server policy (`allowCrystalRush`, `minimumServerActionIntervalTicks`) and validate optional C2S verification payloads.

## Verified Dependency Versions (Minecraft 1.21.11)
| Dependency | Version | Required / Optional |
| :--- | :--- | :--- |
| **Minecraft Java Edition** | `1.21.11` | Required |
| **Java Runtime** | `Java 21` (`>=21`) | Required |
| **Fabric Loader** | `0.19.3` (`>=0.18.1`) | Required |
| **Fabric API** | `0.141.4+1.21.11` | Required |
| **Yarn Mappings** | `1.21.11+build.6` | Compile-time (Remapped to Intermediary) |
| **Mod Menu** | `17.0.1` (`>=17.0.0`) | Optional (Client GUI Integration) |

## Building from Source
1. Ensure **JDK 21** is installed (`java -version`).
2. Run the Gradle build and unit test suite:
   ```bash
   ./gradlew clean build
   ```
3.  The compiled, remapped mod JAR will be output to:
      - build/libs/crystalrush-1.0.0+1.21.11.jar
      - build/libs/crystalrush-1.0.0+1.21.11-sources.jar

Installation Instructions

Client Installation

1.  Install Fabric Loader (>=0.18.1) for Minecraft 1.21.11.
2.  Place fabric-api-0.141.4+1.21.11.jar and crystalrush-1.0.0+1.21.11.jar into
    your .minecraft/mods/ folder.
3.  (Optional) Place modmenu-17.0.1.jar into .minecraft/mods/ to access the
    graphical configuration screen from the Mods menu.

Optional Dedicated Server Installation

1.  Install Fabric Loader and Fabric API on your Minecraft 1.21.11 server.
2.  Copy the same crystalrush-1.0.0+1.21.11.jar into the server's mods/
    directory.
3.  Start the server once to generate config/crystalrush-server.json, where you
    can configure allowCrystalRush and minimumServerActionIntervalTicks.

Troubleshooting

  - Mod Menu button not visible: Confirm modmenu-17.0.1.jar is installed in
    .minecraft/mods/. Even without Mod Menu, you can edit
    .minecraft/config/crystalrush.json directly and rebind the key in
    Minecraft's standard Options → Controls → Key Binds → CrystalRush category.
  - No End Crystals found message: Ensure End Crystals are present in your
    hotbar (slots 1–9) or offhand, and that Automatic Crystal Slot Detection is
    set to ON.
  - Corrupt config file: If crystalrush.json is manually edited with invalid
    JSON syntax, CrystalRush automatically backs it up to crystalrush.json.bak
    and restores safe default settings on startup.

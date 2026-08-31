# Create Aeronautics: Flinging Rope

A throwable rope coil for Minecraft **1.21.1 / NeoForge**, built as an addon for the
[Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) ecosystem.

## What it does

Throw the **Rope Coil** and the knot flies off, trailing rope behind it. The knot
hooks onto whatever surface it hits — a wall, a ceiling, the hull of an airship —
and from then on you are tethered to it: the rope has a real length, and if you go
past it, it yanks you back.

- **Right-click** — throw the knot. Right-click again while it's hooked to pay out
  more rope; right-click mid-flight to recall it.
- **Sneak (coil in hand)** — winch: the rope shortens and reels you towards the knot.
- **Sneak + right-click the knot** — release the rope.
- **Any other player can right-click the knot to grab the rope.** The tether jumps
  to them — throw a rope out of a hovering airship, let your teammate grab it, then
  winch them up. Helicopter rescue, Create-style.

## Versions

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.228+ |
| Java | 21 |

**v1** (this version) uses self-contained vanilla physics — no hard dependencies, works
standalone. **v2** (planned) integrates with the Sable physics engine used by
Create: Aeronautics / Create: Simulated, so ropes can tie onto moving physics
contraptions (airships) and pull on them for real. The dependency coordinates are
already staged in `build.gradle` / `gradle.properties`.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`.

## Development

```
./gradlew runClient
```

## Credits

The tether mechanics are adapted from the Plunger Launcher in
[Create: Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project)
(code under MIT), reworked for vanilla physics. No assets from the Simulated
Project are used.

## License

MIT — see [LICENSE](LICENSE).
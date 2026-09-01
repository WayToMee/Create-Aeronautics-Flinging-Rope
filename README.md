# Create Aeronautics: Flinging Rope

A throwable rope coil for Minecraft **1.21.1 (NeoForge)** built **entirely on the
Create: Aeronautics stack**:

- the rope is a real physics object simulated by [Sable](https://github.com/ryanhcode/sable)'s
  Rapier rope pipeline (`RopePhysicsObject`),
- server/client strand management, point-snapshot networking and interpolation follow
  [Create: Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project)'s
  rope-strand systems (MIT),
- rendering uses Simulated's own rope models and textures
  (`simulated:block/rope/rope`, `simulated:block/rope/knot`) at runtime.

**Hard dependencies, no fallbacks**: `simulated`, `sable` and `create` are required —
without the Aeronautics stack the mod does not load.

## What it does

The rope is a *free* rope: it never hooks onto world blocks. It flies, whips, falls and
drapes over terrain under real rope physics. A **rope hook** fitted onto the far end
latches onto Aeronautics contraptions (Sable sub-levels — ships) on contact.

| Action | Effect |
|---|---|
| Right-click (coil in hand) | Fling the rope out along your view |
| Right-click again | Pay out more rope from the coil |
| Sneak + right-click (coil in hand) | Wind the rope back in; fully wound = back in the coil |
| Sneak + right-click near a loose rope start (coil in hand) | Pick a dropped rope back up |
| Sneak + right-click, **empty hand**, near the far end | Grab on / let go (helicopter pickup) |
| Right-click with a **rope hook** near a loose far end | Fit the hook onto the rope |
| Fitted hook touches a ship | The hook latches on — the rope tows or hangs from the contraption |
| Grab a latched end (empty hand, sneak + right-click) | Pull the hook off the ship |

Letting go of the coil (or dying) drops the rope where it is — it keeps simulating and
can be picked back up. A rope latched to a ship keeps hanging from it and never despawns;
other abandoned ropes despawn after two minutes. If the ship is disassembled, the latch
lets go and the rope falls free. Winding the rope fully in pops a fitted hook off as an
item.

## Building

```
./gradlew build
```

Requires Java 21. Dependencies resolve from `maven.ryanhcode.dev` (Sable, Simulated)
and `maven.createmod.net` (Create, Flywheel, Ponder).

## Credits

- Rope physics: [Sable](https://github.com/ryanhcode/sable) by ryanhcode
- Strand system patterns and rope visuals: [Create: Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project)
  by the Simulated Team (code MIT; assets referenced at runtime, not redistributed)

## License

MIT — see [LICENSE](LICENSE).

package dev.waytomee.flingingrope.content;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side tracking of which rope knot belongs to which holder.
 * One active knot per player: throwing a new one discards the previous one.
 *
 * Modeled on LaunchedPlungerServerHandler from Create: Simulated (MIT),
 * simplified to a holder-keyed map since a rope has exactly one holder.
 */
public final class RopeKnotHandler {

    private static final Map<UUID, RopeKnotEntity> KNOTS = new HashMap<>();

    private RopeKnotHandler() {
    }

    public static void track(final RopeKnotEntity knot) {
        final Player holder = knot.getHolder();
        if (holder == null) {
            return;
        }
        // drop stale mappings for this knot under other holders (rope was grabbed over)
        KNOTS.entrySet().removeIf(entry -> entry.getValue() == knot && !entry.getKey().equals(holder.getUUID()));

        final RopeKnotEntity previous = KNOTS.put(holder.getUUID(), knot);
        if (previous != null && previous != knot && previous.isAlive()) {
            previous.discard();
        }
    }

    public static void untrack(final RopeKnotEntity knot) {
        KNOTS.values().removeIf(value -> value == knot);
    }

    public static RopeKnotEntity getKnot(final Player player) {
        final RopeKnotEntity knot = KNOTS.get(player.getUUID());
        if (knot == null || !knot.isAlive() || knot.level() != player.level()) {
            return null;
        }
        return knot;
    }
}
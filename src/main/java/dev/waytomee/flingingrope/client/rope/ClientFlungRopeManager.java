package dev.waytomee.flingingrope.client.rope;

import dev.waytomee.flingingrope.network.ClientboundFlungRopeDataPacket;
import dev.waytomee.flingingrope.network.ClientboundFlungRopeStopPacket;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.UUID;

/**
 * Holds every client-side flung rope, mirroring Create: Simulated's
 * {@code ClientLevelRopeManager} (MIT). Cleared when the player changes levels.
 */
public final class ClientFlungRopeManager {

    private static final Map<UUID, ClientFlungRope> ROPES = new Object2ObjectOpenHashMap<>();

    private ClientFlungRopeManager() {
    }

    public static Iterable<ClientFlungRope> getAllRopes() {
        return ROPES.values();
    }

    public static void handleData(final ClientboundFlungRopeDataPacket packet) {
        final ClientFlungRope rope = ROPES.computeIfAbsent(packet.uuid(), ClientFlungRope::new);
        rope.receive(packet.interpolationTick(), packet.points());
    }

    public static void handleStop(final ClientboundFlungRopeStopPacket packet) {
        if (packet.removed()) {
            ROPES.remove(packet.uuid());
            return;
        }

        final ClientFlungRope rope = ROPES.get(packet.uuid());
        if (rope != null) {
            rope.setStopped(true);
        }
    }

    public static void tickInterpolation(final double interpolationTick) {
        for (final ClientFlungRope rope : ROPES.values()) {
            rope.tickInterpolation(interpolationTick);
        }
    }

    public static void clear() {
        ROPES.clear();
    }
}
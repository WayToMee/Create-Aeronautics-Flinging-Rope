package dev.waytomee.flingingrope.content.rope;

import dev.ryanhcode.sable.api.sublevel.SubLevelTrackingPlugin;
import dev.waytomee.flingingrope.network.ClientboundFlungRopeDataPacket;
import dev.waytomee.flingingrope.network.ClientboundFlungRopeStopPacket;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Streams flung-rope poses to clients through Sable's tracking system, exactly like
 * Create: Simulated's {@code ServerRopeTrackingSystem} (MIT) does for its strands.
 */
public class FlungRopeTrackingPlugin implements SubLevelTrackingPlugin {

    private final ServerLevel level;

    public FlungRopeTrackingPlugin(final ServerLevel level) {
        this.level = level;
    }

    @Override
    public Iterable<UUID> neededPlayers() {
        final FlungRopeServerManager manager = FlungRopeServerManager.get(this.level);
        if (manager == null) return List.of();

        final Set<UUID> players = new ObjectOpenHashSet<>();

        for (final FlungRopeStrand strand : manager.getAllStrands()) {
            if (!strand.isActive()) continue;

            strand.updatePose();
            if (!strand.needsSync() && strand.networkingStopped) {
                continue;
            }

            for (final ServerPlayer player : this.trackingPlayers(strand)) {
                players.add(player.getUUID());
            }
        }

        return players;
    }

    @Override
    public void sendTrackingData(final int interpolationTick) {
        final FlungRopeServerManager manager = FlungRopeServerManager.get(this.level);
        if (manager == null) return;

        for (final FlungRopeStrand strand : manager.getAllStrands()) {
            if (!strand.isActive()) continue;

            if (strand.needsSync()) {
                strand.networkingStopped = false;

                final ClientboundFlungRopeDataPacket packet = new ClientboundFlungRopeDataPacket(
                        interpolationTick,
                        strand.getUUID(),
                        strand.hasEndHook(),
                        new ObjectArrayList<>(strand.getPoints())
                );

                for (final ServerPlayer player : this.trackingPlayers(strand)) {
                    PacketDistributor.sendToPlayer(player, packet);
                }

                strand.justSynced();
            } else if (!strand.networkingStopped) {
                strand.networkingStopped = true;

                final ClientboundFlungRopeStopPacket packet =
                        new ClientboundFlungRopeStopPacket(strand.getUUID(), false);

                for (final ServerPlayer player : this.trackingPlayers(strand)) {
                    PacketDistributor.sendToPlayer(player, packet);
                }
            }
        }
    }

    private List<ServerPlayer> trackingPlayers(final FlungRopeStrand strand) {
        final Vector3d first = strand.getPoints().getFirst();
        final ChunkPos chunk = new ChunkPos(BlockPos.containing(first.x, first.y, first.z));
        return this.level.getChunkSource().chunkMap.getPlayers(chunk, false);
    }
}
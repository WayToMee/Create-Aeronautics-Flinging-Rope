package dev.waytomee.flingingrope.content.rope;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.waytomee.flingingrope.index.FRItems;
import dev.waytomee.flingingrope.network.ClientboundFlungRopeStopPacket;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every {@link FlungRopeStrand} in a server level: adds them to Sable's physics system,
 * validates their holders, and re-applies hand attachments each physics tick.
 * Pattern adapted from Create: Simulated's {@code ServerLevelRopeManager} (MIT).
 */
public class FlungRopeServerManager {

    /** Free ropes despawn after two minutes. */
    private static final int FREE_DESPAWN_TICKS = 2 * 60 * 20;
    private static final double GRAB_RANGE = 3.0;

    private static final WorldAttached<FlungRopeServerManager> MANAGERS =
            new WorldAttached<>(FlungRopeServerManager::create);

    private final ServerLevel level;
    private final Map<UUID, FlungRopeStrand> strands = new Object2ObjectOpenHashMap<>();

    private FlungRopeServerManager(final ServerLevel level) {
        this.level = level;
    }

    @Nullable
    private static FlungRopeServerManager create(final LevelAccessor level) {
        if (!(level instanceof final ServerLevel serverLevel)) return null;
        return new FlungRopeServerManager(serverLevel);
    }

    @Nullable
    public static FlungRopeServerManager get(final Level level) {
        return MANAGERS.get(level);
    }

    public Iterable<FlungRopeStrand> getAllStrands() {
        return this.strands.values();
    }

    public void addStrand(final FlungRopeStrand strand) {
        this.strands.put(strand.getUUID(), strand);
    }

    @Nullable
    public FlungRopeStrand getByHolder(final UUID playerId) {
        for (final FlungRopeStrand strand : this.strands.values()) {
            if (playerId.equals(strand.getHolder())) {
                return strand;
            }
        }
        return null;
    }

    /**
     * Removes a strand entirely (wound back into the coil, or despawned).
     */
    public void removeStrand(final FlungRopeStrand strand) {
        final SubLevelPhysicsSystem system = this.physicsSystem();
        if (system != null && strand.isActive()) {
            system.removeObject(strand);
        }
        this.strands.remove(strand.getUUID());

        PacketDistributor.sendToPlayersTrackingChunk(this.level, this.chunkOf(strand),
                new ClientboundFlungRopeStopPacket(strand.getUUID(), true));
    }

    /**
     * Sable's rope attachments can be set but not cleared, so dropping an attachment means
     * rebuilding the physics object: copy the current pose into a fresh strand with the same
     * UUID (clients keep interpolating seamlessly) and swap it into the physics system.
     */
    public FlungRopeStrand rebuildStrand(final FlungRopeStrand old,
                                         @Nullable final UUID holder,
                                         @Nullable final UUID endHolder) {
        final SubLevelPhysicsSystem system = this.physicsSystem();

        if (old.isActive()) {
            old.updatePose();
            if (system != null) {
                system.removeObject(old);
            }
        }

        final List<Vector3d> points = new ObjectArrayList<>();
        for (final Vector3d point : old.getPoints()) {
            points.add(new Vector3d(point));
        }

        final FlungRopeStrand fresh = new FlungRopeStrand(old.getUUID(), points, holder);
        fresh.setEndHolder(endHolder);
        fresh.networkingStopped = old.networkingStopped;
        this.strands.put(fresh.getUUID(), fresh);
        return fresh;
    }

    /**
     * Called once per game tick: validates holders, feeds strands into the physics system,
     * pulls END grabbers along (helicopter pickup) and despawns abandoned ropes.
     */
    public void gameTick() {
        final SubLevelPhysicsSystem system = this.physicsSystem();
        if (system == null) return;

        for (final FlungRopeStrand strand : new ArrayList<>(this.strands.values())) {
            this.validateHolders(strand);

            if (strand.isFree() && ++strand.freeTicks > FREE_DESPAWN_TICKS) {
                this.removeStrand(strand);
                continue;
            }

            if (!strand.isActive()
                    && strand.isChunkLoadedEnough(this.level)
                    && system.getTicketManager().wouldBeLoaded(this.level, strand)) {
                system.addObject(strand);
            }

            if (strand.isActive()) {
                strand.updatePose();
                this.towEndHolder(strand);
            }
        }
    }

    /**
     * Called on every Sable physics tick (multiple per game tick).
     */
    public void physicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        for (final FlungRopeStrand strand : this.strands.values()) {
            if (!strand.isActive()) {
                continue;
            }

            if (!strand.isChunkLoadedEnough(this.level)) {
                physicsSystem.removeObject(strand);
                continue;
            }

            strand.prePhysicsTick(this.level);
        }
    }

    private void validateHolders(final FlungRopeStrand strand) {
        final UUID holderId = strand.getHolder();
        if (holderId != null) {
            final Player holder = this.level.getPlayerByUUID(holderId);
            if (holder == null || !holder.isAlive() || !isHoldingCoil(holder)) {
                // the rope is let go — it keeps flying free under physics
                this.rebuildStrand(strand, null, strand.getEndHolder());
                return;
            }
        }

        final UUID endHolderId = strand.getEndHolder();
        if (endHolderId != null) {
            final Player grabber = this.level.getPlayerByUUID(endHolderId);
            final boolean tooFar = grabber != null && this.distanceToEnd(strand, grabber) >
                    strand.getPoints().size() * FlungRopeStrand.SEGMENT_LENGTH + 8.0;
            if (grabber == null || !grabber.isAlive() || tooFar) {
                this.rebuildStrand(strand, strand.getHolder(), null);
            }
        }
    }

    /**
     * Helicopter pickup: the physics rope cannot push players, so when the grabbed end
     * segment is over-stretched, the grabber is towed towards the rope.
     */
    private void towEndHolder(final FlungRopeStrand strand) {
        final UUID endHolderId = strand.getEndHolder();
        if (endHolderId == null) return;

        final Player grabber = this.level.getPlayerByUUID(endHolderId);
        if (grabber == null) return;

        final List<Vector3d> points = strand.getPoints();
        if (points.size() < 2) return;

        final Vector3d anchor = points.get(points.size() - 2);
        final Vector3d hand = FlungRopeStrand.handPos(grabber);
        final double distance = hand.distance(anchor);

        if (distance > FlungRopeStrand.SEGMENT_LENGTH * 1.6) {
            final double strength = Math.min(0.35, (distance - FlungRopeStrand.SEGMENT_LENGTH * 1.6) * 0.2);
            final Vec3 pull = new Vec3(anchor.x - hand.x, anchor.y - hand.y, anchor.z - hand.z)
                    .normalize().scale(strength);

            grabber.setDeltaMovement(grabber.getDeltaMovement().add(pull));
            grabber.fallDistance = 0.0f;
            grabber.hurtMarked = true;
        }
    }

    /**
     * Toggles the END grab for a player with an empty hand.
     */
    public void toggleEndGrab(final ServerPlayer player) {
        // already grabbing something -> let go
        for (final FlungRopeStrand strand : this.strands.values()) {
            if (player.getUUID().equals(strand.getEndHolder())) {
                this.rebuildStrand(strand, strand.getHolder(), null);
                this.playRopeSound(player, 0.8f);
                return;
            }
        }

        // grab the nearest reachable rope end
        final Vector3d hand = FlungRopeStrand.handPos(player);
        FlungRopeStrand best = null;
        double bestDistance = GRAB_RANGE;

        for (final FlungRopeStrand strand : this.strands.values()) {
            if (strand.getEndHolder() != null) continue;
            if (player.getUUID().equals(strand.getHolder())) continue;
            if (strand.getPoints().isEmpty()) continue;

            final double distance = strand.getPoints().getLast().distance(hand);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = strand;
            }
        }

        if (best != null) {
            best.setEndHolder(player.getUUID());
            best.freeTicks = 0;
            best.wakeUp();
            this.playRopeSound(player, 1.1f);
        }
    }

    /**
     * Picks up a free rope by its START (sneak + use with a rope coil near the loose start).
     */
    @Nullable
    public FlungRopeStrand tryPickUpFreeStrand(final ServerPlayer player) {
        final Vector3d hand = FlungRopeStrand.handPos(player);
        FlungRopeStrand best = null;
        double bestDistance = GRAB_RANGE;

        for (final FlungRopeStrand strand : this.strands.values()) {
            if (strand.getHolder() != null) continue;
            if (strand.getPoints().isEmpty()) continue;

            final double distance = strand.getPoints().getFirst().distance(hand);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = strand;
            }
        }

        if (best == null) {
            return null;
        }

        final FlungRopeStrand picked = this.rebuildStrand(best, player.getUUID(), best.getEndHolder());
        picked.freeTicks = 0;
        this.playRopeSound(player, 1.0f);
        return picked;
    }

    public static boolean isHoldingCoil(final Player player) {
        return player.getMainHandItem().is(FRItems.ROPE_COIL.get())
                || player.getOffhandItem().is(FRItems.ROPE_COIL.get());
    }

    private double distanceToEnd(final FlungRopeStrand strand, final Player player) {
        return strand.getPoints().getLast().distance(FlungRopeStrand.handPos(player));
    }

    private ChunkPos chunkOf(final FlungRopeStrand strand) {
        final Vector3d first = strand.getPoints().getFirst();
        return new ChunkPos(BlockPos.containing(first.x, first.y, first.z));
    }

    private void playRopeSound(final ServerPlayer player, final float pitch) {
        this.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.6f, pitch);
    }

    @Nullable
    private SubLevelPhysicsSystem physicsSystem() {
        final var container = SubLevelContainer.getContainer(this.level);
        if (container == null) return null;
        return container.physicsSystem();
    }
}
package dev.waytomee.flingingrope.content.rope;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * validates their holders, latches fitted hooks onto sub-levels (ships) and re-applies hand
 * attachments each physics tick.
 * Pattern adapted from Create: Simulated's {@code ServerLevelRopeManager} (MIT).
 */
public class FlungRopeServerManager {

    /** Free ropes despawn after two minutes. */
    private static final int FREE_DESPAWN_TICKS = 2 * 60 * 20;
    private static final double GRAB_RANGE = 3.0;
    /** How close the hooked END must get to a ship block to latch on. */
    private static final double LATCH_RANGE = 0.75;
    /** Re-latch delay after the hook is pulled off a ship. */
    private static final int LATCH_COOLDOWN_TICKS = 40;

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
     * Removes a strand entirely (wound back into the coil, or despawned). A fitted hook
     * pops off and drops as an item at the rope's end.
     */
    public void removeStrand(final FlungRopeStrand strand) {
        final SubLevelPhysicsSystem system = this.physicsSystem();
        if (system != null && strand.isActive()) {
            system.removeObject(strand);
        }
        this.strands.remove(strand.getUUID());

        if (strand.hasEndHook() && !strand.getPoints().isEmpty()) {
            final Vector3d end = strand.getPoints().getLast();
            this.level.addFreshEntity(new ItemEntity(this.level, end.x, end.y, end.z,
                    new ItemStack(FRItems.HOOK.get())));
        }

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
                                         @Nullable final UUID endHolder,
                                         final boolean keepLatch) {
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
        fresh.setEndHook(old.hasEndHook());
        fresh.networkingStopped = old.networkingStopped;
        fresh.latchCooldown = old.latchCooldown;
        if (keepLatch && old.isLatched()) {
            fresh.latch(old.getLatchedSubLevelId(), old.getLatchLocalPos());
        }
        this.strands.put(fresh.getUUID(), fresh);
        return fresh;
    }

    /**
     * Called once per game tick: validates holders, feeds strands into the physics system,
     * pulls END grabbers along (helicopter pickup), latches fitted hooks onto ships and
     * despawns abandoned ropes.
     */
    public void gameTick() {
        final SubLevelPhysicsSystem system = this.physicsSystem();
        if (system == null) return;

        for (final FlungRopeStrand strand : new ArrayList<>(this.strands.values())) {
            this.validateHolders(strand);

            // a rebuild inside validateHolders replaces the map entry — pick the
            // replacement up on the next tick instead of re-adding the stale object
            if (this.strands.get(strand.getUUID()) != strand) {
                continue;
            }

            if (strand.latchCooldown > 0) {
                strand.latchCooldown--;
            }

            if (strand.isLatched() && !this.isLatchTargetAlive(strand)) {
                // the ship is gone — drop the latch (attachments can only be cleared by rebuild)
                this.rebuildStrand(strand, strand.getHolder(), strand.getEndHolder(), false);
                continue;
            }

            if (strand.isFree() && !strand.isLatched() && ++strand.freeTicks > FREE_DESPAWN_TICKS) {
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
                this.tryLatch(strand);
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

    private boolean isLatchTargetAlive(final FlungRopeStrand strand) {
        final var container = SubLevelContainer.getContainer(this.level);
        if (container == null) return false;

        final SubLevel subLevel = container.getSubLevel(strand.getLatchedSubLevelId());
        return subLevel != null && !subLevel.isRemoved();
    }

    /**
     * Latches a fitted hook onto the first sub-level (ship) whose blocks the rope's END is
     * touching: the END position is transformed into the ship's local plot space (the plot
     * lives in the far plotyard region of the same level, so its local coordinates are real
     * level coordinates) and checked against nearby blocks.
     */
    private void tryLatch(final FlungRopeStrand strand) {
        if (!strand.hasEndHook() || strand.isLatched()
                || strand.getEndHolder() != null || strand.latchCooldown > 0) {
            return;
        }
        if (strand.getPoints().isEmpty()) {
            return;
        }

        final var container = SubLevelContainer.getContainer(this.level);
        if (container == null) return;

        final Vector3d end = strand.getPoints().getLast();
        final BoundingBox3d query = new BoundingBox3d(
                end.x - LATCH_RANGE, end.y - LATCH_RANGE, end.z - LATCH_RANGE,
                end.x + LATCH_RANGE, end.y + LATCH_RANGE, end.z + LATCH_RANGE);

        for (final SubLevel subLevel : container.queryIntersecting(query)) {
            if (subLevel.isRemoved() || !(subLevel instanceof ServerSubLevel)) continue;

            final Vector3d local = subLevel.logicalPose()
                    .transformPositionInverse(end, new Vector3d());
            if (!this.touchesSubLevelBlock(local)) continue;

            strand.latch(subLevel.getUniqueId(), local);
            strand.freeTicks = 0;
            strand.forceResync();
            strand.wakeUp();
            this.level.playSound(null, end.x, end.y, end.z,
                    SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.8f, 1.5f);
            return;
        }
    }

    private boolean touchesSubLevelBlock(final Vector3d local) {
        final BlockPos center = BlockPos.containing(local.x, local.y, local.z);
        for (final BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (!this.level.getBlockState(pos).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The rope stays tied to its holder through item switches — letting go is explicit
     * (sneak + left-click with the coil, {@link #releaseHeldStrand}). Only death or
     * disconnect releases it here.
     */
    private void validateHolders(final FlungRopeStrand strand) {
        final UUID holderId = strand.getHolder();
        if (holderId != null) {
            final Player holder = this.level.getPlayerByUUID(holderId);
            if (holder == null || !holder.isAlive()) {
                // the holder is gone — the rope keeps flying free under physics
                this.rebuildStrand(strand, null, strand.getEndHolder(), true);
                return;
            }
        }

        final UUID endHolderId = strand.getEndHolder();
        if (endHolderId != null) {
            final Player grabber = this.level.getPlayerByUUID(endHolderId);
            final boolean tooFar = grabber != null && this.distanceToEnd(strand, grabber) >
                    strand.getPoints().size() * FlungRopeStrand.SEGMENT_LENGTH + 8.0;
            if (grabber == null || !grabber.isAlive() || tooFar) {
                this.rebuildStrand(strand, strand.getHolder(), null, true);
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
     * Explicitly lets go of the held rope (sneak + left-click with the coil in hand).
     * A latched hook stays latched — the rope keeps hanging from the ship.
     */
    public boolean releaseHeldStrand(final ServerPlayer player) {
        final FlungRopeStrand held = this.getByHolder(player.getUUID());
        if (held == null) {
            return false;
        }

        this.rebuildStrand(held, null, held.getEndHolder(), true);
        this.playRopeSound(player, 0.7f);
        return true;
    }

    /**
     * Fits a hook onto the loose far END of the nearest rope (right-click with a hook item).
     */
    public boolean tryAttachHook(final ServerPlayer player) {
        final Vector3d hand = FlungRopeStrand.handPos(player);
        FlungRopeStrand best = null;
        double bestDistance = GRAB_RANGE;

        for (final FlungRopeStrand strand : this.strands.values()) {
            if (strand.hasEndHook()) continue;
            if (strand.getEndHolder() != null) continue;
            if (strand.getPoints().isEmpty()) continue;

            final double distance = strand.getPoints().getLast().distance(hand);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = strand;
            }
        }

        if (best == null) {
            return false;
        }

        best.setEndHook(true);
        best.forceResync();
        best.wakeUp();
        this.playRopeSound(player, 1.3f);
        return true;
    }

    /**
     * Toggles the END grab for a player with an empty hand. Grabbing a latched end pulls
     * the hook off the ship (with a short re-latch cooldown).
     */
    public void toggleEndGrab(final ServerPlayer player) {
        // already grabbing something -> let go
        for (final FlungRopeStrand strand : this.strands.values()) {
            if (player.getUUID().equals(strand.getEndHolder())) {
                this.rebuildStrand(strand, strand.getHolder(), null, true);
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
            FlungRopeStrand target = best;
            if (best.isLatched()) {
                target = this.rebuildStrand(best, best.getHolder(), null, false);
                target.latchCooldown = LATCH_COOLDOWN_TICKS;
            }
            target.setEndHolder(player.getUUID());
            target.freeTicks = 0;
            target.wakeUp();
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

        final FlungRopeStrand picked = this.rebuildStrand(best, player.getUUID(), best.getEndHolder(), true);
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

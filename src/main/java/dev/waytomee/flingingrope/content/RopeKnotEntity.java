package dev.waytomee.flingingrope.content;

import dev.waytomee.flingingrope.index.FRItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The thrown end of a {@link RopeCoilItem}. Flies as a projectile, hooks onto the
 * first block face it hits and from then on keeps its holder tethered: whenever the
 * holder moves further away than the current rope length, they get pulled back in.
 *
 * The holder is not necessarily the thrower — any player can right-click the knot
 * (or the visible rope end) to take the rope over, which is what makes the
 * "helicopter pickup" scenario work: the pilot throws the knot down, the teammate
 * grabs it, the pilot winches them in.
 *
 * Adapted from the LaunchedPlungerEntity pattern in Create: Simulated (MIT).
 */
public class RopeKnotEntity extends ThrowableProjectile {

    public static final float MIN_ROPE_LENGTH = 2.0f;
    public static final float MAX_ROPE_LENGTH = 32.0f;
    public static final float MAX_FLIGHT_DISTANCE = 48.0f;
    public static final float WINCH_SPEED = 0.15f;
    private static final double PULL_STRENGTH = 0.15;
    private static final double MAX_PULL = 0.8;

    public static final EntityDataAccessor<Integer> HOLDER_ID =
            SynchedEntityData.defineId(RopeKnotEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> IS_HOOKED =
            SynchedEntityData.defineId(RopeKnotEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<BlockPos> HOOKED_POS =
            SynchedEntityData.defineId(RopeKnotEntity.class, EntityDataSerializers.BLOCK_POS);
    public static final EntityDataAccessor<Float> ROPE_LENGTH =
            SynchedEntityData.defineId(RopeKnotEntity.class, EntityDataSerializers.FLOAT);

    private UUID holderUUID;
    private Player cachedHolder;
    private boolean addedToHandler = false;

    public RopeKnotEntity(final EntityType<? extends RopeKnotEntity> entityType, final Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        builder.define(HOLDER_ID, -1);
        builder.define(IS_HOOKED, false);
        builder.define(HOOKED_POS, BlockPos.ZERO);
        builder.define(ROPE_LENGTH, 12.0f);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    public void tick() {
        super.tick();

        final Level level = this.level();
        if (level.isClientSide) {
            return;
        }

        final Player holder = this.getHolder();
        if (holder == null || !holder.isAlive() || holder.level() != level) {
            this.discard();
            return;
        }

        if (!this.addedToHandler) {
            RopeKnotHandler.track(this);
            this.addedToHandler = true;
        }

        final double distance = holder.position().distanceTo(this.position());

        if (!this.isHooked()) {
            // the rope simply ran out mid-flight
            if (this.tickCount > 2 && distance > MAX_FLIGHT_DISTANCE) {
                holder.displayClientMessage(Component.translatable("message.flinging_rope.snapped"), true);
                this.discard();
            }
            return;
        }

        // hooked: stay put and keep the holder tethered
        this.setDeltaMovement(Vec3.ZERO);

        final BlockPos hookedPos = this.entityData.get(HOOKED_POS);
        if (level.isLoaded(hookedPos) && level.getBlockState(hookedPos).isAir()) {
            this.unhook();
            return;
        }

        // winching: holder sneaks while holding the coil -> rope gets shorter
        if (holder.isShiftKeyDown() && holder.isHolding(FRItems.ROPE_COIL.get())) {
            this.setRopeLength(Math.max(MIN_ROPE_LENGTH, this.getRopeLength() - WINCH_SPEED));
        }

        this.applyRopeConstraint(holder, distance);
    }

    private void applyRopeConstraint(final Player holder, final double distance) {
        final double length = this.getRopeLength();
        if (distance <= length) {
            return;
        }

        final Vec3 toKnot = this.position().subtract(holder.position()
                .add(0.0, holder.getBbHeight() * 0.5, 0.0)).normalize();
        final double excess = distance - length;
        final Vec3 pull = toKnot.scale(Math.min(excess * PULL_STRENGTH, MAX_PULL));

        holder.setDeltaMovement(holder.getDeltaMovement()
                .multiply(0.95, 0.95, 0.95)
                .add(pull));
        holder.fallDistance = 0.0f;
        holder.hurtMarked = true; // force a velocity packet to the client
    }

    @Override
    protected void onHitBlock(final BlockHitResult hitResult) {
        super.onHitBlock(hitResult);

        final Level level = this.level();
        if (level.isClientSide) {
            return;
        }

        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);

        final Vec3 location = hitResult.getLocation()
                .add(Vec3.atLowerCornerOf(hitResult.getDirection().getNormal()).scale(0.05));
        this.setPos(location);

        this.entityData.set(IS_HOOKED, true);
        this.entityData.set(HOOKED_POS, hitResult.getBlockPos());

        final Player holder = this.getHolder();
        if (holder != null) {
            final float length = (float) Mth.clamp(
                    holder.position().distanceTo(this.position()) + 1.0,
                    MIN_ROPE_LENGTH, MAX_ROPE_LENGTH);
            this.setRopeLength(length);
        }

        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public void unhook() {
        this.entityData.set(IS_HOOKED, false);
        this.entityData.set(HOOKED_POS, BlockPos.ZERO);
        this.noPhysics = false;
        this.setNoGravity(false);
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand) {
        final Level level = this.level();
        if (!level.isClientSide) {
            final Player holder = this.getHolder();
            if (player == holder) {
                if (player.isShiftKeyDown()) {
                    player.displayClientMessage(Component.translatable("message.flinging_rope.released"), true);
                    this.discard();
                }
            } else {
                // another player grabs the rope
                this.setHolder(player);
                player.displayClientMessage(Component.translatable("message.flinging_rope.grabbed"), true);
                if (holder != null) {
                    holder.displayClientMessage(
                            Component.translatable("message.flinging_rope.taken", player.getDisplayName()), true);
                }
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.7f, 1.4f);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean skipAttackInteraction(final Entity attacker) {
        if (!this.level().isClientSide && attacker == this.getHolder()) {
            this.discard();
        }
        return true;
    }

    @Override
    protected boolean canHitEntity(final Entity entity) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 0.3f;
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!this.level().isClientSide) {
            RopeKnotHandler.untrack(this);
        }
        super.remove(reason);
    }

    // --- holder management -------------------------------------------------

    public Player getHolder() {
        if (this.cachedHolder != null && this.cachedHolder.isAlive() && !this.cachedHolder.isRemoved()) {
            return this.cachedHolder;
        }
        if (this.holderUUID != null && this.level() instanceof final ServerLevel serverLevel) {
            final Player player = serverLevel.getPlayerByUUID(this.holderUUID);
            if (player != null) {
                this.cachedHolder = player;
                this.entityData.set(HOLDER_ID, player.getId());
            }
            return player;
        }
        return null;
    }

    public void setHolder(final Player player) {
        this.cachedHolder = player;
        this.holderUUID = player == null ? null : player.getUUID();
        this.entityData.set(HOLDER_ID, player == null ? -1 : player.getId());
        if (player != null && !this.level().isClientSide) {
            RopeKnotHandler.track(this);
        }
    }

    /**
     * Client-side holder lookup via the synced entity id.
     */
    public Entity getHolderClient() {
        final int id = this.entityData.get(HOLDER_ID);
        return id == -1 ? null : this.level().getEntity(id);
    }

    // --- state accessors ----------------------------------------------------

    public boolean isHooked() {
        return this.entityData.get(IS_HOOKED);
    }

    public float getRopeLength() {
        return this.entityData.get(ROPE_LENGTH);
    }

    public void setRopeLength(final float length) {
        this.entityData.set(ROPE_LENGTH, Mth.clamp(length, MIN_ROPE_LENGTH, MAX_ROPE_LENGTH));
    }

    public void extendRope(final float amount) {
        this.setRopeLength(this.getRopeLength() + amount);
    }

    // --- persistence ----------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.holderUUID != null) {
            tag.putUUID("Holder", this.holderUUID);
        }
        tag.putBoolean("Hooked", this.isHooked());
        tag.put("HookedPos", NbtUtils.writeBlockPos(this.entityData.get(HOOKED_POS)));
        tag.putFloat("RopeLength", this.getRopeLength());
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Holder")) {
            this.holderUUID = tag.getUUID("Holder");
        }
        this.entityData.set(IS_HOOKED, tag.getBoolean("Hooked"));
        NbtUtils.readBlockPos(tag, "HookedPos").ifPresent(pos -> this.entityData.set(HOOKED_POS, pos));
        this.setRopeLength(tag.getFloat("RopeLength"));
        if (this.isHooked()) {
            this.noPhysics = true;
            this.setNoGravity(true);
        }
    }
}
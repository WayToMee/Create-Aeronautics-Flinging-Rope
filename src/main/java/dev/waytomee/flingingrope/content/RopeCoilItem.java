package dev.waytomee.flingingrope.content;

import dev.waytomee.flingingrope.content.rope.FlungRopeServerManager;
import dev.waytomee.flingingrope.content.rope.FlungRopeStrand;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * A coil of Aeronautics rope. The rope is a free strand simulated by Sable's rope physics —
 * it never hooks onto blocks; it just flies, falls and drapes.
 *
 *  - right-click                 -> fling the rope out ahead of you (or pay out more rope)
 *  - sneak + right-click         -> winch the rope back in (fully wound = gone)
 *  - sneak + right-click (near a loose rope start, no rope held) -> pick the rope back up
 *  - another player, empty hand, sneak + right-click near the far end -> grab on (helicopter pickup)
 */
public class RopeCoilItem extends Item {

    /** Points added per throw. */
    private static final int THROW_POINTS = 13;
    /** Segments paid out per extra right-click. */
    private static final int PAY_OUT_SEGMENTS = 2;
    /** Segments wound in per sneak-right-click. */
    private static final int WIND_IN_SEGMENTS = 2;
    private static final int USE_COOLDOWN_TICKS = 8;

    public RopeCoilItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);

        if (!(level instanceof final ServerLevel serverLevel) || !(player instanceof final ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        final FlungRopeServerManager manager = FlungRopeServerManager.get(serverLevel);
        if (manager == null) {
            return InteractionResultHolder.pass(stack);
        }

        final FlungRopeStrand held = manager.getByHolder(player.getUUID());

        if (player.isShiftKeyDown()) {
            if (held != null) {
                this.windIn(serverLevel, serverPlayer, manager, held);
            } else if (manager.tryPickUpFreeStrand(serverPlayer) == null) {
                return InteractionResultHolder.pass(stack);
            }
        } else {
            if (held == null) {
                this.fling(serverLevel, serverPlayer, manager);
            } else {
                this.payOut(serverPlayer, held);
            }
        }

        player.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * Flings the rope out along the player's look vector as a line of points; Sable's physics
     * takes over immediately, so it whips and falls like a real rope.
     */
    private void fling(final ServerLevel level, final ServerPlayer player, final FlungRopeServerManager manager) {
        final Vector3d hand = FlungRopeStrand.handPos(player);
        final Vec3 look = player.getLookAngle();

        final List<Vector3d> points = new ObjectArrayList<>();
        for (int i = 0; i < THROW_POINTS; i++) {
            points.add(new Vector3d(
                    hand.x + look.x * i * FlungRopeStrand.SEGMENT_LENGTH,
                    hand.y + look.y * i * FlungRopeStrand.SEGMENT_LENGTH,
                    hand.z + look.z * i * FlungRopeStrand.SEGMENT_LENGTH
            ));
        }

        final FlungRopeStrand strand = new FlungRopeStrand(UUID.randomUUID(), points, player.getUUID());
        manager.addStrand(strand);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    /**
     * Pays out more rope from the coil, following Simulated's winch math: new points are
     * inserted at the hand end.
     */
    private void payOut(final ServerPlayer player, final FlungRopeStrand strand) {
        if (strand.getPoints().size() + PAY_OUT_SEGMENTS > FlungRopeStrand.MAX_POINTS) {
            return;
        }

        final Vector3d hand = FlungRopeStrand.handPos(player);
        for (int i = 0; i < PAY_OUT_SEGMENTS; i++) {
            strand.addPoint(hand);
        }
        strand.wakeUp();

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.5f, 0.7f);
    }

    /**
     * Winds the rope back in; when only the minimum is left, the rope disappears back
     * into the coil.
     */
    private void windIn(final ServerLevel level, final ServerPlayer player,
                        final FlungRopeServerManager manager, final FlungRopeStrand strand) {
        for (int i = 0; i < WIND_IN_SEGMENTS; i++) {
            if (strand.getPoints().size() <= FlungRopeStrand.MIN_POINTS) {
                manager.removeStrand(strand);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
                return;
            }
            strand.removeFirstPoint();
        }
        strand.wakeUp();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.5f, 1.1f);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("item.flinging_rope.rope_coil.tooltip.fling"));
        tooltip.add(Component.translatable("item.flinging_rope.rope_coil.tooltip.wind"));
        tooltip.add(Component.translatable("item.flinging_rope.rope_coil.tooltip.grab"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
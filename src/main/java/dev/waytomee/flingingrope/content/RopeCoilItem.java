package dev.waytomee.flingingrope.content;

import dev.waytomee.flingingrope.index.FREntityTypes;
import net.minecraft.network.chat.Component;
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

import java.util.List;

/**
 * A coil of rope. Right-click to throw the knot; it hooks onto whatever surface it
 * hits and the rope stays tied to the thrower.
 *
 * While a knot is hooked:
 *  - right-click again          -> pay out one extra block of rope
 *  - sneak + right-click        -> release and coil the rope back
 *  - hold sneak (coil in hand)  -> winch yourself towards the knot
 *
 * While a knot is flying / not hooked, right-click recalls it.
 */
public class RopeCoilItem extends Item {

    private static final int THROW_COOLDOWN_TICKS = 10;
    private static final float THROW_POWER = 1.1f;

    public RopeCoilItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            final RopeKnotEntity existing = RopeKnotHandler.getKnot(player);

            if (existing != null) {
                if (player.isShiftKeyDown() || !existing.isHooked()) {
                    // release / recall
                    existing.discard();
                    player.displayClientMessage(Component.translatable("message.flinging_rope.recalled"), true);
                } else {
                    // pay out extra rope
                    existing.extendRope(1.0f);
                }
            } else {
                final RopeKnotEntity knot = new RopeKnotEntity(FREntityTypes.ROPE_KNOT.get(), level);
                knot.setPos(player.getEyePosition().add(player.getLookAngle().scale(0.3)));
                knot.setOwner(player);
                knot.setHolder(player);
                knot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, THROW_POWER, 0.5f);
                level.addFreshEntity(knot);

                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8f, 0.9f);
                player.getCooldowns().addCooldown(this, THROW_COOLDOWN_TICKS);
            }

            player.awardStat(Stats.ITEM_USED.get(this));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("item.flinging_rope.rope_coil.tooltip.throw"));
        tooltip.add(Component.translatable("item.flinging_rope.rope_coil.tooltip.winch"));
        tooltip.add(Component.translatable("item.flinging_rope.rope_coil.tooltip.grab"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
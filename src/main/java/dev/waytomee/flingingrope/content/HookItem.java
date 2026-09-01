package dev.waytomee.flingingrope.content;

import dev.waytomee.flingingrope.content.rope.FlungRopeServerManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * A rope hook. Right-click near the loose far END of a flung rope to fit the hook onto it.
 * The fitted hook is returned as an item when the rope is fully wound in or despawns.
 * (Latching fitted hooks onto Aeronautics handles is the next step.)
 */
public class HookItem extends Item {

    private static final int USE_COOLDOWN_TICKS = 8;

    public HookItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);

        if (!(level instanceof final ServerLevel serverLevel) || !(player instanceof final ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        final FlungRopeServerManager manager = FlungRopeServerManager.get(serverLevel);
        if (manager == null || !manager.tryAttachHook(serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        player.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ropes.hook.tooltip.attach"));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}

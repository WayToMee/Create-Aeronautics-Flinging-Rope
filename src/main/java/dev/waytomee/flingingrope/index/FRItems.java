package dev.waytomee.flingingrope.index;

import dev.waytomee.flingingrope.FlingingRope;
import dev.waytomee.flingingrope.content.HookItem;
import dev.waytomee.flingingrope.content.RopeCoilItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FRItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FlingingRope.MOD_ID);

    public static final DeferredItem<RopeCoilItem> ROPE_COIL = ITEMS.registerItem(
            "rope_coil",
            RopeCoilItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<HookItem> HOOK = ITEMS.registerItem(
            "hook",
            HookItem::new,
            new Item.Properties().stacksTo(16));

    private FRItems() {
    }
}

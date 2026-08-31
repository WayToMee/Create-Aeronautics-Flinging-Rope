package dev.waytomee.flingingrope;

import dev.waytomee.flingingrope.index.FREntityTypes;
import dev.waytomee.flingingrope.index.FRItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(FlingingRope.MOD_ID)
public final class FlingingRope {

    public static final String MOD_ID = "flinging_rope";

    public FlingingRope(final IEventBus modEventBus) {
        FRItems.ITEMS.register(modEventBus);
        FREntityTypes.ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FRItems.ROPE_COIL);
        }
    }
}
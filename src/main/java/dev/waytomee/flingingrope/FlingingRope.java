package dev.waytomee.flingingrope;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.waytomee.flingingrope.content.rope.FlungRopeServerManager;
import dev.waytomee.flingingrope.content.rope.FlungRopeTrackingPlugin;
import dev.waytomee.flingingrope.index.FRItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Create Aeronautics: Flinging Rope — a throwable rope coil running entirely on the
 * Aeronautics stack: Sable rope physics (Rapier) with Create: Simulated's strand
 * patterns and rope visuals. Hard dependency, no fallbacks.
 */
@Mod(FlingingRope.MOD_ID)
public final class FlingingRope {

    public static final String MOD_ID = "flinging_rope";

    public FlingingRope(final IEventBus modEventBus) {
        FRItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::addCreative);

        // Sable physics hooks — the same registration points Create: Simulated uses.
        SableEventPlatform.INSTANCE.onSubLevelContainerReady((level, container) -> {
            if (container instanceof final ServerSubLevelContainer serverContainer) {
                serverContainer.trackingSystem()
                        .addTrackingPlugin(new FlungRopeTrackingPlugin(serverContainer.getLevel()));
            }
        });

        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) -> {
            final FlungRopeServerManager manager = FlungRopeServerManager.get(physicsSystem.getLevel());
            if (manager != null) {
                manager.physicsTick(physicsSystem, timeStep);
            }
        });

        NeoForge.EVENT_BUS.addListener((final LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof final ServerLevel serverLevel) {
                final FlungRopeServerManager manager = FlungRopeServerManager.get(serverLevel);
                if (manager != null) {
                    manager.gameTick();
                }
            }
        });
    }

    private void addCreative(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FRItems.ROPE_COIL);
        }
    }
}
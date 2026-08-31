package dev.waytomee.flingingrope.client;

import dev.waytomee.flingingrope.FlingingRope;
import dev.waytomee.flingingrope.index.FREntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = FlingingRope.MOD_ID, value = Dist.CLIENT)
public final class FlingingRopeClient {

    private FlingingRopeClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FREntityTypes.ROPE_KNOT.get(), RopeKnotRenderer::new);
    }
}
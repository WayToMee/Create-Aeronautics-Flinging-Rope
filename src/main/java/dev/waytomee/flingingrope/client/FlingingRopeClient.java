package dev.waytomee.flingingrope.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.waytomee.flingingrope.FlingingRope;
import dev.waytomee.flingingrope.client.rope.ClientFlungRopeManager;
import dev.waytomee.flingingrope.client.rope.FlungRopeRenderer;
import dev.waytomee.flingingrope.network.ServerboundGrabRopePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = FlingingRope.MOD_ID, value = Dist.CLIENT)
public final class FlingingRopeClient {

    private FlingingRopeClient() {
    }

    @SubscribeEvent
    public static void renderRopes(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        final PoseStack ps = event.getPoseStack();
        final Vec3 cameraPos = event.getCamera().getPosition();

        FlungRopeRenderer.render(event.getPartialTick().getGameTimeDeltaPartialTick(false), ps, buffer, cameraPos);
        buffer.endBatch();
    }

    @SubscribeEvent
    public static void onRightClickEmpty(final PlayerInteractEvent.RightClickEmpty event) {
        final Player player = event.getEntity();
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            PacketDistributor.sendToServer(ServerboundGrabRopePacket.INSTANCE);
        }
    }

    @SubscribeEvent
    public static void onClientLevelChange(final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFlungRopeManager.clear();
    }
}
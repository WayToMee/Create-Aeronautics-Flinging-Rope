package dev.waytomee.flingingrope.network;

import dev.waytomee.flingingrope.FlingingRope;
import dev.waytomee.flingingrope.client.rope.ClientFlungRopeManager;
import dev.waytomee.flingingrope.content.rope.FlungRopeServerManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = FlingingRope.MOD_ID)
public final class FRNetwork {

    private FRNetwork() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                ClientboundFlungRopeDataPacket.TYPE,
                ClientboundFlungRopeDataPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() ->
                        ClientFlungRopeManager.handleData(packet)));

        registrar.playToClient(
                ClientboundFlungRopeStopPacket.TYPE,
                ClientboundFlungRopeStopPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() ->
                        ClientFlungRopeManager.handleStop(packet)));

        registrar.playToServer(
                ServerboundGrabRopePacket.TYPE,
                ServerboundGrabRopePacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof final ServerPlayer serverPlayer) {
                        final FlungRopeServerManager manager =
                                FlungRopeServerManager.get(serverPlayer.serverLevel());
                        if (manager != null) {
                            manager.toggleEndGrab(serverPlayer);
                        }
                    }
                }));
    }
}
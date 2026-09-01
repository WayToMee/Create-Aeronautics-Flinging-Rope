package dev.waytomee.flingingrope.network;

import dev.waytomee.flingingrope.FlingingRope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent when a sneaking player left-clicks air with the rope coil in hand,
 * explicitly letting go of the rope they are holding.
 */
public record ServerboundReleaseRopePacket() implements CustomPacketPayload {

    public static final ServerboundReleaseRopePacket INSTANCE = new ServerboundReleaseRopePacket();

    public static final Type<ServerboundReleaseRopePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlingingRope.MOD_ID, "release_rope"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundReleaseRopePacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

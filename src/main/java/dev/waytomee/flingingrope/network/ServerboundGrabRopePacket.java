package dev.waytomee.flingingrope.network;

import dev.waytomee.flingingrope.FlingingRope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent when a sneaking, empty-handed player right-clicks to grab (or let go of)
 * the free end of a nearby flung rope.
 */
public record ServerboundGrabRopePacket() implements CustomPacketPayload {

    public static final ServerboundGrabRopePacket INSTANCE = new ServerboundGrabRopePacket();

    public static final Type<ServerboundGrabRopePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlingingRope.MOD_ID, "grab_rope"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundGrabRopePacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
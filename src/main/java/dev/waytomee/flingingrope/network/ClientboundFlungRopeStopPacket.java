package dev.waytomee.flingingrope.network;

import dev.waytomee.flingingrope.FlingingRope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Tells the client that a rope stopped moving ({@code removed = false}) so interpolation can
 * freeze, or that it disappeared entirely ({@code removed = true}).
 */
public record ClientboundFlungRopeStopPacket(UUID uuid, boolean removed) implements CustomPacketPayload {

    public static final Type<ClientboundFlungRopeStopPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlingingRope.MOD_ID, "flung_rope_stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFlungRopeStopPacket> STREAM_CODEC =
            StreamCodec.of((buf, value) -> {
                buf.writeUUID(value.uuid());
                buf.writeBoolean(value.removed());
            }, buf -> new ClientboundFlungRopeStopPacket(buf.readUUID(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
package dev.waytomee.flingingrope.network;

import dev.ryanhcode.sable.util.SableBufferUtils;
import dev.waytomee.flingingrope.FlingingRope;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

/**
 * Rope pose snapshot for one flung rope. Mirrors Simulated's ClientboundRopeDataPacket,
 * carried over vanilla NeoForge networking instead of Veil.
 */
public record ClientboundFlungRopeDataPacket(int interpolationTick, UUID uuid,
                                             List<Vector3d> points) implements CustomPacketPayload {

    public static final Type<ClientboundFlungRopeDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlingingRope.MOD_ID, "flung_rope_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFlungRopeDataPacket> STREAM_CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), ClientboundFlungRopeDataPacket::read);

    private static ClientboundFlungRopeDataPacket read(final RegistryFriendlyByteBuf buf) {
        final int interpolationTick = buf.readInt();
        final UUID uuid = buf.readUUID();

        final int size = buf.readInt();
        final List<Vector3d> points = new ObjectArrayList<>(size);
        for (int i = 0; i < size; i++) {
            points.add(SableBufferUtils.read(buf, new Vector3d()));
        }

        return new ClientboundFlungRopeDataPacket(interpolationTick, uuid, points);
    }

    private void write(final RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.interpolationTick);
        buf.writeUUID(this.uuid);

        buf.writeInt(this.points.size());
        for (final Vector3dc point : this.points) {
            SableBufferUtils.write(buf, point);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
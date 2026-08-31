package dev.waytomee.flingingrope.content.rope;

import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * A free-flying rope simulated entirely by Sable's rope physics (Rapier), following the
 * strand pattern of Create: Simulated's {@code ServerRopeStrand} (MIT).
 *
 * Unlike Simulated's block-anchored strands, a flung rope is anchored to a *player's hand*
 * (the START attachment is re-applied every physics tick), and the END is either free or
 * grabbed by another player. It never attaches to blocks.
 */
public class FlungRopeStrand extends RopePhysicsObject {

    public static final double SEGMENT_LENGTH = 1.0;
    public static final double COLLISION_RADIUS = 0.125;
    public static final int MIN_POINTS = 2;
    public static final int MAX_POINTS = 33;

    private final UUID uuid;

    /**
     * The player holding the rope coil — the START of the rope follows their hand.
     * Null means the rope is lying free in the world.
     */
    @Nullable
    private UUID holder;

    /**
     * A second player who grabbed the far END of the rope (helicopter pickup).
     */
    @Nullable
    private UUID endHolder;

    /**
     * Game ticks this strand has spent with no holder at all.
     */
    public int freeTicks;

    /**
     * The list of points we last sent clients, to know if they moved enough to re-sync.
     */
    private final List<Vector3d> lastNetworkedPoints = new ObjectArrayList<>();
    public boolean networkingStopped;

    public FlungRopeStrand(final UUID uuid, final Collection<Vector3d> points, @Nullable final UUID holder) {
        super(points, COLLISION_RADIUS);
        this.uuid = uuid;
        this.holder = holder;
    }

    public UUID getUUID() {
        return this.uuid;
    }

    @Nullable
    public UUID getHolder() {
        return this.holder;
    }

    @Nullable
    public UUID getEndHolder() {
        return this.endHolder;
    }

    public void setEndHolder(@Nullable final UUID endHolder) {
        this.endHolder = endHolder;
    }

    public boolean isFree() {
        return this.holder == null && this.endHolder == null;
    }

    /**
     * Re-applies the hand attachments. Called on every physics tick (multiple per game tick),
     * mirroring how Simulated's winch keeps its attachment point current.
     */
    public void prePhysicsTick(final ServerLevel level) {
        if (this.holder != null) {
            final Player player = level.getPlayerByUUID(this.holder);
            if (player != null) {
                this.setAttachment(RopeHandle.AttachmentPoint.START, handPos(player), null);
            }
        }

        if (this.endHolder != null) {
            final Player player = level.getPlayerByUUID(this.endHolder);
            if (player != null) {
                this.setAttachment(RopeHandle.AttachmentPoint.END, handPos(player), null);
            }
        }
    }

    /**
     * World-space position of the player's rope hand.
     */
    public static Vector3d handPos(final Player player) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 look = player.getLookAngle();
        return new Vector3d(
                eye.x + look.x * 0.35,
                eye.y - 0.35,
                eye.z + look.z * 0.35
        );
    }

    /**
     * @return true if the chunk holding the first rope point is loaded enough to simulate
     */
    public boolean isChunkLoadedEnough(final ServerLevel level) {
        final Vector3d first = this.getPoints().getFirst();
        final BlockPos pos = BlockPos.containing(first.x, first.y, first.z);
        return PhysicsChunkTicketManager.isChunkLoadedEnough(level,
                pos.getX() >> SectionPos.SECTION_BITS, pos.getZ() >> SectionPos.SECTION_BITS);
    }

    public boolean needsSync() {
        if (this.lastNetworkedPoints.size() != this.points.size()) {
            return true;
        }

        final double threshold = Mth.square(1.0 / 16.0 * 0.1);
        for (int i = 0; i < this.points.size(); i++) {
            if (this.points.get(i).distanceSquared(this.lastNetworkedPoints.get(i)) > threshold) {
                return true;
            }
        }

        return false;
    }

    public void justSynced() {
        this.lastNetworkedPoints.clear();

        for (final Vector3d point : this.points) {
            this.lastNetworkedPoints.add(new Vector3d(point));
        }
    }
}
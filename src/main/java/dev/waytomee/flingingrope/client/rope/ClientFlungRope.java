package dev.waytomee.flingingrope.client.rope;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.util.Mth;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

/**
 * Client-side flung rope: interpolates the point snapshots streamed by the server.
 * Interpolation logic mirrors Create: Simulated's {@code ClientRopeStrand} (MIT).
 */
public class ClientFlungRope {

    private final UUID uuid;
    private final ObjectArrayList<ClientFlungRopePoint> points = new ObjectArrayList<>();
    private boolean stopped;

    public ClientFlungRope(final UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public ObjectArrayList<ClientFlungRopePoint> getPoints() {
        return this.points;
    }

    public void setStopped(final boolean stopped) {
        this.stopped = stopped;
    }

    public void receive(final int interpolationTick, final List<Vector3d> incomingPoints) {
        this.stopped = false;

        while (this.points.size() < incomingPoints.size()) {
            final Vector3dc position = incomingPoints.get(incomingPoints.size() - this.points.size() - 1);
            this.points.addFirst(new ClientFlungRopePoint(
                    new Vector3d(position), new Vector3d(position), new ObjectArrayList<>()));
        }

        while (this.points.size() > incomingPoints.size()) {
            this.points.removeFirst();
        }

        for (int i = 0; i < incomingPoints.size(); i++) {
            this.points.get(i).snapshots()
                    .add(new ClientFlungRopePoint.Snapshot(interpolationTick, incomingPoints.get(i)));
        }
    }

    protected void tickInterpolation(final double gameTick) {
        for (final ClientFlungRopePoint point : this.points) {
            final ObjectList<ClientFlungRopePoint.Snapshot> buffer = point.snapshots();

            point.previousPosition().set(point.position());

            while (!buffer.isEmpty() && buffer.getFirst().interpolationTick() < gameTick - 6) {
                buffer.removeFirst();
            }

            if (buffer.isEmpty()) {
                continue;
            }

            int beforeIndex = -1;
            ClientFlungRopePoint.Snapshot before = null;
            ClientFlungRopePoint.Snapshot after = null;

            for (int i = 0; i < buffer.size(); i++) {
                final ClientFlungRopePoint.Snapshot snapshot = buffer.get(i);
                if (gameTick == snapshot.interpolationTick()) {
                    point.position().set(snapshot.position());
                    continue;
                }

                if (snapshot.interpolationTick() < gameTick) {
                    beforeIndex = i;
                    before = snapshot;
                } else if (snapshot.interpolationTick() > gameTick) {
                    after = snapshot;
                    break;
                }
            }

            if (before == null || after == null) {
                if (before != null) {
                    point.position().set(before.position());

                    // dead reckon for a single tick max
                    final int beforeBeforeIndex = beforeIndex - 1;
                    if (beforeBeforeIndex >= 0 && !this.stopped) {
                        final ClientFlungRopePoint.Snapshot beforeBefore = buffer.get(beforeBeforeIndex);

                        final double deadReckoningTicks = Mth.clamp(gameTick - before.interpolationTick(), 0, 1);
                        final double fraction = deadReckoningTicks
                                / (before.interpolationTick() - beforeBefore.interpolationTick());

                        point.position().set(beforeBefore.position())
                                .lerp(before.position(), 1.0 + fraction);
                    }
                } else if (after != null) {
                    point.position().set(after.position());
                }
                // else: every snapshot matched gameTick exactly — position was
                // already set inside the scan loop, nothing to interpolate.
            } else {
                final double factor = (gameTick - before.interpolationTick())
                        / (after.interpolationTick() - before.interpolationTick());

                before.position().lerp(after.position(), factor, point.position());
            }
        }
    }
}
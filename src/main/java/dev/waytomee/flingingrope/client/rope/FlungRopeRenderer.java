package dev.waytomee.flingingrope.client.rope;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.util.SimMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * Renders flung ropes with Create: Simulated's own rope visuals — the
 * {@code simulated:block/rope/rope} and {@code simulated:block/rope/knot} partial
 * models — using the segment/orientation math of Simulated's RopeStrandRenderer (MIT).
 * Rendering is camera-relative since flung ropes have no owning block entity.
 * A fitted hook is drawn as an enlarged knot on the far end.
 */
public final class FlungRopeRenderer {

    private record RopeRenderPoint(Quaternionf orientation, Vector3d position) {
    }

    private FlungRopeRenderer() {
    }

    public static void render(final float partialTick, final PoseStack ps,
                              final MultiBufferSource buffer, final Vec3 cameraPos) {
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        final SuperByteBuffer middle = CachedBuffers.partialFacing(
                SimPartialModels.ROPE, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
        final SuperByteBuffer knot = CachedBuffers.partialFacing(
                SimPartialModels.ROPE_KNOT, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        for (final ClientFlungRope rope : ClientFlungRopeManager.getAllRopes()) {
            final List<ClientFlungRopePoint> points = rope.getPoints();

            if (points.size() <= 1) {
                continue;
            }

            final ObjectArrayList<RopeRenderPoint> renderPoints = buildRenderPoints(partialTick, points);

            if (renderPoints.isEmpty()) {
                continue;
            }

            ps.pushPose();
            for (int i = 1; i < renderPoints.size(); i++) {
                final RopeRenderPoint renderPoint0 = renderPoints.get(i - 1);
                final RopeRenderPoint renderPoint1 = renderPoints.get(i);
                final Vector3d renderPos = renderPoint0.position();
                final Quaternionf orientation = renderPoint0.orientation();

                final double length = renderPoint1.position().distance(renderPoint0.position());

                ps.pushPose();
                ps.translate(renderPos.x - cameraPos.x, renderPos.y - cameraPos.y, renderPos.z - cameraPos.z);
                ps.mulPose(orientation);
                ps.translate(-0.5, -0.5, -0.5);

                final BlockPos pos = BlockPos.containing(renderPos.x, renderPos.y, renderPos.z);
                final int worldLight = LevelRenderer.getLightColor(level, pos);

                if (i > 1) {
                    knot.light(worldLight)
                            .renderInto(ps, vb);
                }
                ps.translate(0.0, 0.5, 0.0);
                ps.scale(1.0f, (float) length, 1.0f);

                middle.light(worldLight)
                        .renderInto(ps, vb);
                ps.popPose();
            }

            if (rope.hasEndHook()) {
                final RopeRenderPoint last = renderPoints.getLast();
                final Vector3d endPos = last.position();

                ps.pushPose();
                ps.translate(endPos.x - cameraPos.x, endPos.y - cameraPos.y, endPos.z - cameraPos.z);
                ps.mulPose(last.orientation());
                ps.scale(1.8f, 1.8f, 1.8f);
                ps.translate(-0.5, -0.5, -0.5);

                final BlockPos hookPos = BlockPos.containing(endPos.x, endPos.y, endPos.z);
                knot.light(LevelRenderer.getLightColor(level, hookPos))
                        .renderInto(ps, vb);
                ps.popPose();
            }
            ps.popPose();
        }
    }

    private static ObjectArrayList<RopeRenderPoint> buildRenderPoints(final float partialTick,
                                                                      final List<ClientFlungRopePoint> inputPoints) {
        final ObjectArrayList<RopeRenderPoint> renderPoints = new ObjectArrayList<>();
        final ObjectArrayList<ClientFlungRopePoint> points = new ObjectArrayList<>(inputPoints);

        while (points.size() >= 2 && points.getFirst().position().distanceSquared(points.get(1).position()) < 1e-3) {
            points.removeFirst();
        }

        if (points.size() <= 1) {
            return new ObjectArrayList<>();
        }

        final Vector3dc pointZeroPosition = points.get(0).renderPos(partialTick, new Vector3d());
        final Vector3dc pointOnePosition = points.get(1).renderPos(partialTick, new Vector3d());

        final Vector3d normal = pointOnePosition.sub(pointZeroPosition, new Vector3d()).normalize();

        final Quaternionf runningRotation;
        if (normal.dot(OrientedBoundingBox3d.UP) < 0) {
            runningRotation = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, -1, 0), normal);
            runningRotation.rotateZ((float) Math.PI);
        } else {
            runningRotation = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 1, 0), normal);
        }

        renderPoints.add(new RopeRenderPoint(new Quaternionf(runningRotation), new Vector3d(pointZeroPosition)));

        final Vector3d runningNormal = new Vector3d();
        final Vector3d bPos = new Vector3d();
        final Vector3d aPos = new Vector3d();

        for (int i = 2; i < points.size(); i++) {
            final ClientFlungRopePoint pointA = points.get(i - 1);
            final ClientFlungRopePoint pointB = points.get(i);

            runningNormal.set(pointB.renderPos(partialTick, bPos))
                    .sub(pointA.renderPos(partialTick, aPos))
                    .normalize();

            if (runningNormal.dot(OrientedBoundingBox3d.UP) < -0.15) {
                runningRotation.set(SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, -1, 0), runningNormal));
                runningRotation.rotateZ((float) Math.PI);
            } else {
                runningRotation.set(SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 1, 0), runningNormal));
            }

            renderPoints.add(new RopeRenderPoint(new Quaternionf(runningRotation),
                    pointA.renderPos(partialTick, new Vector3d())));
            normal.set(runningNormal);
        }

        renderPoints.add(new RopeRenderPoint(new Quaternionf(runningRotation),
                points.getLast().renderPos(partialTick, new Vector3d())));
        return renderPoints;
    }
}

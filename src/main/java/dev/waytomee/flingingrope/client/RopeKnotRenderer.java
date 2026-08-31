package dev.waytomee.flingingrope.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.waytomee.flingingrope.content.RopeKnotEntity;
import dev.waytomee.flingingrope.index.FRItems;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders the knot as a small billboarded item and draws the rope back to the
 * holder as two crossed leash-style strips with catenary sag proportional to the
 * current slack.
 *
 * Rope geometry follows the vanilla leash renderer; the sag term and the
 * holder-attachment via Entity#getRopeHoldPosition mirror what Create: Simulated
 * does for its plunger tether (MIT), minus the Veil shader pipeline.
 */
public class RopeKnotRenderer extends EntityRenderer<RopeKnotEntity> {

    private static final int SEGMENTS = 24;
    private static final float KNOT_Y_OFFSET = 0.15f;

    private final ItemRenderer itemRenderer;

    public RopeKnotRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(final RopeKnotEntity entity, final float entityYaw, final float partialTick,
                       final PoseStack poseStack, final MultiBufferSource bufferSource, final int light) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, light);

        // the knot itself: the coil item as a small billboard
        poseStack.pushPose();
        poseStack.translate(0.0f, KNOT_Y_OFFSET, 0.0f);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.55f, 0.55f, 0.55f);
        this.itemRenderer.renderStatic(new ItemStack(FRItems.ROPE_COIL.get()), ItemDisplayContext.GROUND,
                light, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), entity.getId());
        poseStack.popPose();

        final Entity holder = entity.getHolderClient();
        if (holder != null) {
            this.renderRope(entity, partialTick, poseStack, bufferSource, holder);
        }
    }

    private void renderRope(final RopeKnotEntity knot, final float partialTick, final PoseStack poseStack,
                            final MultiBufferSource bufferSource, final Entity holder) {
        poseStack.pushPose();
        poseStack.translate(0.0f, KNOT_Y_OFFSET, 0.0f);

        final Vec3 holdPos = holder.getRopeHoldPosition(partialTick);
        final double knotX = Mth.lerp(partialTick, knot.xo, knot.getX());
        final double knotY = Mth.lerp(partialTick, knot.yo, knot.getY()) + KNOT_Y_OFFSET;
        final double knotZ = Mth.lerp(partialTick, knot.zo, knot.getZ());

        final float dx = (float) (holdPos.x - knotX);
        final float dy = (float) (holdPos.y - knotY);
        final float dz = (float) (holdPos.z - knotZ);

        final VertexConsumer consumer = bufferSource.getBuffer(RenderType.leash());
        final Matrix4f pose = poseStack.last().pose();

        // cross-section offsets, perpendicular to the rope in the horizontal plane
        final float horizontalSq = dx * dx + dz * dz;
        final float halfWidth = 0.03f;
        final float xOffset;
        final float zOffset;
        if (horizontalSq > 1.0E-6f) {
            final float inv = Mth.invSqrt(horizontalSq) * halfWidth;
            xOffset = dz * inv;
            zOffset = dx * inv;
        } else {
            xOffset = halfWidth;
            zOffset = 0.0f;
        }

        final BlockPos knotBlock = BlockPos.containing(knot.getEyePosition(partialTick));
        final BlockPos holderBlock = BlockPos.containing(holder.getEyePosition(partialTick));
        final int knotBlockLight = this.getBlockLightLevel(knot, knotBlock);
        final int holderBlockLight = holder.isOnFire() ? 15
                : holder.level().getBrightness(LightLayer.BLOCK, holderBlock);
        final int knotSkyLight = knot.level().getBrightness(LightLayer.SKY, knotBlock);
        final int holderSkyLight = holder.level().getBrightness(LightLayer.SKY, holderBlock);

        // sag: the more slack in the rope, the deeper it dips
        final float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        final float slack = Math.max(0.0f, knot.getRopeLength() - distance);
        final float sag = -Math.min(slack * 0.25f, 4.0f);

        for (int i = 0; i <= SEGMENTS; i++) {
            addVertexPair(consumer, pose, dx, dy, dz, knotBlockLight, holderBlockLight,
                    knotSkyLight, holderSkyLight, 0.025f, 0.025f, xOffset, zOffset, i, false, sag);
        }
        for (int i = SEGMENTS; i >= 0; i--) {
            addVertexPair(consumer, pose, dx, dy, dz, knotBlockLight, holderBlockLight,
                    knotSkyLight, holderSkyLight, 0.025f, 0.0f, xOffset, zOffset, i, true, sag);
        }

        poseStack.popPose();
    }

    private static void addVertexPair(final VertexConsumer consumer, final Matrix4f pose,
                                      final float dx, final float dy, final float dz,
                                      final int knotBlockLight, final int holderBlockLight,
                                      final int knotSkyLight, final int holderSkyLight,
                                      final float thickness, final float yOffset,
                                      final float xOffset, final float zOffset,
                                      final int index, final boolean reverse, final float sag) {
        final float f = (float) index / SEGMENTS;
        final int blockLight = (int) Mth.lerp(f, (float) knotBlockLight, (float) holderBlockLight);
        final int skyLight = (int) Mth.lerp(f, (float) knotSkyLight, (float) holderSkyLight);
        final int packedLight = LightTexture.pack(blockLight, skyLight);

        final float shade = (index % 2 == (reverse ? 1 : 0)) ? 0.7f : 1.0f;
        final float r = 0.54f * shade;
        final float g = 0.40f * shade;
        final float b = 0.25f * shade;

        final float x = dx * f;
        final float y = dy * f + sag * (4.0f * f * (1.0f - f));
        final float z = dz * f;

        consumer.addVertex(pose, x - xOffset, y + yOffset, z + zOffset)
                .setColor(r, g, b, 1.0f).setLight(packedLight);
        consumer.addVertex(pose, x + xOffset, y + thickness - yOffset, z - zOffset)
                .setColor(r, g, b, 1.0f).setLight(packedLight);
    }

    @Override
    public boolean shouldRender(final RopeKnotEntity entity, final Frustum frustum,
                                final double camX, final double camY, final double camZ) {
        // the rope can span far outside the knot's bounding box
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(final RopeKnotEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
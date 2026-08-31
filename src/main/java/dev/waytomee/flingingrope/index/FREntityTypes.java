package dev.waytomee.flingingrope.index;

import dev.waytomee.flingingrope.FlingingRope;
import dev.waytomee.flingingrope.content.RopeKnotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FREntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FlingingRope.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<RopeKnotEntity>> ROPE_KNOT =
            ENTITY_TYPES.register("rope_knot", () -> EntityType.Builder
                    .<RopeKnotEntity>of(RopeKnotEntity::new, MobCategory.MISC)
                    .sized(0.35f, 0.35f)
                    .eyeHeight(0.175f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("rope_knot"));

    private FREntityTypes() {
    }
}
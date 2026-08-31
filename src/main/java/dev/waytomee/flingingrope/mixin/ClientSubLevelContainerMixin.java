package dev.waytomee.flingingrope.mixin;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.waytomee.flingingrope.client.rope.ClientFlungRopeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ticks flung-rope interpolation off Sable's own interpolation clock — the same hook
 * Create: Simulated uses for its rope strands.
 */
@Mixin(ClientSubLevelContainer.class)
public abstract class ClientSubLevelContainerMixin {

    @Shadow
    @Final
    private ClientSableInterpolationState interpolation;

    @Inject(method = "tick", at = @At("TAIL"))
    private void flingingRope$tickRopeInterpolation(final CallbackInfo ci) {
        ClientFlungRopeManager.tickInterpolation(this.interpolation.getTickPointer());
    }
}
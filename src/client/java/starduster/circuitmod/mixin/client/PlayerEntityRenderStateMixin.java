package starduster.circuitmod.mixin.client;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starduster.circuitmod.entity.RocketEntity;

/**
 * Mixin to override player pose when riding a rocket entity.
 * This makes the player appear standing instead of sitting when riding the rocket.
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityRenderStateMixin {
    
    @Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
    private void circuitmod$overrideRocketRidingPose(CallbackInfoReturnable<EntityPose> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        
        // Check if the player is riding a rocket
        if (player.hasVehicle() && player.getVehicle() instanceof RocketEntity) {
            // Force standing pose when riding a rocket
            cir.setReturnValue(EntityPose.STANDING);
        }
    }
}

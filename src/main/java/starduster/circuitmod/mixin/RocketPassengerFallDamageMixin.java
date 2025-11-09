package starduster.circuitmod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.RocketEntity;

/**
 * Mixin to prevent fall damage for players riding rockets during controlled landing
 */
@Mixin(PlayerEntity.class)
public class RocketPassengerFallDamageMixin {
    
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void circuitmod$preventRocketPassengerFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        
        // Check if player is riding a rocket
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof RocketEntity rocket) {
            // Prevent fall damage if rocket is landing or came from Luna
            if (rocket.isLanding() || rocket.getCameFromLuna()) {
                Circuitmod.LOGGER.info("Preventing fall damage for player {} riding rocket during controlled landing (distance: {})", 
                    player.getName().getString(), fallDistance);
                cir.setReturnValue(false); // Cancel fall damage
            }
        }
    }
}

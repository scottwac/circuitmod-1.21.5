package starduster.circuitmod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starduster.circuitmod.entity.RocketEntity;

/**
 * Mixin to prevent fall damage for rockets and their passengers when landing on Luna
 */
@Mixin(LivingEntity.class)
public class RocketLandingMixin {
    
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void circuitmod$preventRocketFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        
        // Check if entity is in Luna dimension
        boolean isInLuna = entity.getWorld().getRegistryKey().getValue().equals(Identifier.of("circuitmod", "luna"));
        
        if (!isInLuna) {
            return;
        }
        
        // Prevent fall damage if riding a rocket
        Entity vehicle = entity.getVehicle();
        if (vehicle instanceof RocketEntity) {
            cir.setReturnValue(false); // No fall damage while riding rocket
            return;
        }
        
        // Prevent fall damage if the entity is a rocket
        if (entity instanceof RocketEntity) {
            cir.setReturnValue(false); // Rockets take no fall damage on Luna
            return;
        }
    }
}


package starduster.circuitmod.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starduster.circuitmod.item.ModItems;

@Mixin(LivingEntity.class)
public class PlayerFallDamageMixin {
    private static final double MOON_GRAVITY_MULTIPLIER = 0.15; // matches gravity scaling used in moon

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void preventFallDamageWhenHoldingPulseStick(double fallDistance, float damagePerDistance, net.minecraft.entity.damage.DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        
        // Check if the entity is a player
        if (entity instanceof PlayerEntity player) {
            // No teleport immunity checks; Slow Falling effect will handle safe descent

            // Check if player is holding pulse stick in either hand
            ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
            ItemStack offHand = player.getStackInHand(Hand.OFF_HAND);
            
            if (mainHand.getItem() == ModItems.PULSE_STICK || offHand.getItem() == ModItems.PULSE_STICK) {
                // Cancel fall damage by returning false (no damage taken)
                cir.setReturnValue(false);
            }
        }
    }

    // Reduce effective fall distance in the moon to reflect lower acceleration
    @ModifyVariable(method = "handleFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double circuitmod$scaleFallDistanceForMoon(double fallDistance) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.getWorld().getRegistryKey().getValue().equals(Identifier.of("circuitmod", "moon"))) {
            return fallDistance * Math.sqrt(MOON_GRAVITY_MULTIPLIER);
        }
        return fallDistance;
    }

    // Note: we only scale fallDistance; scaling damagePerDistance caused injection issues on 1.21.5

    // No RETURN hook needed
}
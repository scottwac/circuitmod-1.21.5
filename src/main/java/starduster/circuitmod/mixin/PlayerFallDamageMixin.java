package starduster.circuitmod.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starduster.circuitmod.item.ModItems;

@Mixin(LivingEntity.class)
public class PlayerFallDamageMixin {
    
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void preventFallDamageWhenHoldingPulseStick(double fallDistance, float damagePerDistance, net.minecraft.entity.damage.DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        
        // Check if the entity is a player
        if (entity instanceof PlayerEntity player) {
            // Check if player is holding pulse stick in either hand
            ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
            ItemStack offHand = player.getStackInHand(Hand.OFF_HAND);
            
            if (mainHand.getItem() == ModItems.PULSE_STICK || offHand.getItem() == ModItems.PULSE_STICK) {
                // Cancel fall damage by returning false (no damage taken)
                cir.setReturnValue(false);
            }
        }
    }
} 
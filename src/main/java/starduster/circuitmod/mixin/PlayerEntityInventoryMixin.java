package starduster.circuitmod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.entity.CustomPlayerInventory;

/**
 * Mixin to replace the PlayerEntity inventory with our CustomPlayerInventory
 * for CustomPlayerEntity instances.
 */
@Mixin(value = PlayerEntity.class, priority = 500)
public class PlayerEntityInventoryMixin {
    
    @Shadow @Final @Mutable
    PlayerInventory inventory;
    
    /**
     * Replace the inventory with CustomPlayerInventory for ALL player entities.
     * Inject after the inventory field is set but before PlayerScreenHandler is created.
     */
    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;inventory:Lnet/minecraft/entity/player/PlayerInventory;", shift = At.Shift.AFTER))
    private void circuitmod$replaceInventory(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        
        System.out.println("[CircuitMod] PlayerEntity init (FIELD injection) - Entity type: " + self.getClass().getName());
        System.out.println("[CircuitMod] Current inventory type: " + this.inventory.getClass().getName());
        
        // Only replace if not already a CustomPlayerInventory (prevent double replacement)
        if (!(this.inventory instanceof CustomPlayerInventory)) {
            System.out.println("[CircuitMod] Replacing with CustomPlayerInventory!");
            this.inventory = new CustomPlayerInventory(self);
            System.out.println("[CircuitMod] Inventory replaced. New inventory size: " + this.inventory.size());
        } else {
            System.out.println("[CircuitMod] Already CustomPlayerInventory, skipping replacement");
        }
    }
}

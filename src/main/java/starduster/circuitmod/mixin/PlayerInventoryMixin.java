package starduster.circuitmod.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to add oxygen tank slots directly to PlayerInventory without extending it.
 * This approach avoids inventory sync issues.
 */
@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    
    @Unique
    private DefaultedList<ItemStack> circuitmod$oxygenTanks = DefaultedList.ofSize(2, ItemStack.EMPTY);
    
    @Unique
    private static final int OXYGEN_TANK_1_SLOT = 41;
    @Unique
    private static final int OXYGEN_TANK_2_SLOT = 42;
    
    @Inject(method = "size", at = @At("RETURN"), cancellable = true)
    private void circuitmod$increaseSize(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + 2); // Add 2 oxygen slots
    }
    
    @Inject(method = "getStack(I)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void circuitmod$getOxygenStack(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == OXYGEN_TANK_1_SLOT) {
            cir.setReturnValue(this.circuitmod$oxygenTanks.get(0));
        } else if (slot == OXYGEN_TANK_2_SLOT) {
            cir.setReturnValue(this.circuitmod$oxygenTanks.get(1));
        }
    }
    
    @Inject(method = "setStack", at = @At("HEAD"), cancellable = true)
    private void circuitmod$setOxygenStack(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot == OXYGEN_TANK_1_SLOT) {
            this.circuitmod$oxygenTanks.set(0, stack);
            ci.cancel();
        } else if (slot == OXYGEN_TANK_2_SLOT) {
            this.circuitmod$oxygenTanks.set(1, stack);
            ci.cancel();
        }
    }
    
    @Inject(method = "removeStack(I)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void circuitmod$removeOxygenStack(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == OXYGEN_TANK_1_SLOT) {
            ItemStack result = this.circuitmod$oxygenTanks.get(0);
            this.circuitmod$oxygenTanks.set(0, ItemStack.EMPTY);
            cir.setReturnValue(result);
        } else if (slot == OXYGEN_TANK_2_SLOT) {
            ItemStack result = this.circuitmod$oxygenTanks.get(1);
            this.circuitmod$oxygenTanks.set(1, ItemStack.EMPTY);
            cir.setReturnValue(result);
        }
    }
}

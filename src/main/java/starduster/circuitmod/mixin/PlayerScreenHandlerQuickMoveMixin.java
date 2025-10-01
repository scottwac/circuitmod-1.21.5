package starduster.circuitmod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import starduster.circuitmod.entity.CustomPlayerInventory;

/**
 * Mixin to handle shift-clicking and regular clicking for oxygen tank slots.
 */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerQuickMoveMixin {
    
    /**
     * Handle shift-click (quick move) for oxygen tank slots.
     */
    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void circuitmod$handleOxygenSlotQuickMove(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        PlayerScreenHandler self = (PlayerScreenHandler) (Object) this;
        ScreenHandlerAccessor accessor = (ScreenHandlerAccessor) self;
        
        // Check if this is one of our oxygen tank slots (they're added at the end)
        // The oxygen tank slots are at indices 46 and 47 in the screen handler
        // (after the 46 vanilla slots: 0-35 inventory, 36-39 armor, 40 offhand, 41-45 crafting)
        if (slotIndex == 46 || slotIndex == 47) {
            Slot slot = self.slots.get(slotIndex);
            ItemStack stackInSlot = slot.getStack();
            
            if (!stackInSlot.isEmpty()) {
                // Try to move the item to the main inventory
                if (!accessor.invokeInsertItem(stackInSlot, 9, 45, true)) {
                    // If that fails, try the hotbar
                    if (!accessor.invokeInsertItem(stackInSlot, 0, 9, false)) {
                        cir.setReturnValue(ItemStack.EMPTY);
                        return;
                    }
                }
                
                if (stackInSlot.isEmpty()) {
                    slot.setStack(ItemStack.EMPTY);
                } else {
                    slot.markDirty();
                }
            }
            
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}

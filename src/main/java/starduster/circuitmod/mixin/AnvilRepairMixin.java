package starduster.circuitmod.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.item.ModItems;

@Mixin(AnvilScreenHandler.class)
public class AnvilRepairMixin {
    
    @Shadow @Final private Property levelCost;
    
    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void enablePulseStickRepair(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;
        
        ItemStack leftStack = handler.getSlot(0).getStack();
        ItemStack rightStack = handler.getSlot(1).getStack();
        
        // Check if we're trying to repair a pulse stick with diamond
        if (leftStack.getItem() == ModItems.PULSE_STICK && rightStack.getItem() == Items.DIAMOND) {
            if (leftStack.isDamaged()) {
                ItemStack resultStack = leftStack.copy();
                
                // Diamond fully repairs the pulse stick
                resultStack.setDamage(0);
                
                // Set the result and cost
                handler.getSlot(2).setStack(resultStack);
                this.levelCost.set(1); // Set XP cost to 1 level
                
                ci.cancel(); // Cancel the original method
            }
        }
    }
}

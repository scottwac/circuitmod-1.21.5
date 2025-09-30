package starduster.circuitmod.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin to expose protected methods in ScreenHandler.
 */
@Mixin(ScreenHandler.class)
public interface ScreenHandlerAccessor {
    
    @Invoker("addSlot")
    Slot invokeAddSlot(Slot slot);
    
    @Invoker("insertItem")
    boolean invokeInsertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast);
}

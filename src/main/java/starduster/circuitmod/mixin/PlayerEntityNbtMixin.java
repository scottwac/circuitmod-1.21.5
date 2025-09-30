package starduster.circuitmod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.entity.CustomPlayerInventory;

/**
 * Mixin to handle saving and loading custom oxygen tank slots in player NBT.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityNbtMixin {
    
    @Shadow
    public abstract PlayerInventory getInventory();
    
    /**
     * After vanilla writes the inventory to NBT, add our oxygen tank slots.
     */
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void circuitmod$writeOxygenTanks(NbtCompound nbt, CallbackInfo ci) {
        PlayerInventory inventory = this.getInventory();
        
        if (inventory instanceof CustomPlayerInventory customInv) {
            System.out.println("[CircuitMod] Writing oxygen tanks to player NBT");
            
            NbtList oxygenTanksNbt = new NbtList();
            
            for (int i = 0; i < 2; i++) {
                ItemStack stack = customInv.getOxygenTank(i);
                if (!stack.isEmpty()) {
                    NbtCompound stackNbt = new NbtCompound();
                    stackNbt.putByte("Slot", (byte) i);
                    PlayerEntity self = (PlayerEntity) (Object) this;
                    oxygenTanksNbt.add(stack.toNbt(self.getRegistryManager(), stackNbt));
                    System.out.println("[CircuitMod] Wrote oxygen tank " + (i+1) + ": " + stack);
                }
            }
            
            nbt.put("OxygenTanks", oxygenTanksNbt);
            System.out.println("[CircuitMod] Finished writing oxygen tanks");
        }
    }
    
    /**
     * After vanilla reads the inventory from NBT, load our oxygen tank slots.
     */
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void circuitmod$readOxygenTanks(NbtCompound nbt, CallbackInfo ci) {
        PlayerInventory inventory = this.getInventory();
        
        if (inventory instanceof CustomPlayerInventory customInv && nbt.contains("OxygenTanks")) {
            System.out.println("[CircuitMod] Reading oxygen tanks from player NBT");
            
            NbtList oxygenTanksNbt = nbt.getList("OxygenTanks").orElse(new NbtList());
            
            for (int i = 0; i < oxygenTanksNbt.size(); i++) {
                NbtCompound stackNbt = oxygenTanksNbt.getCompoundOrEmpty(i);
                int slot = stackNbt.getByte("Slot", (byte)0) & 255;
                
                PlayerEntity self = (PlayerEntity) (Object) this;
                ItemStack stack = ItemStack.fromNbt(self.getRegistryManager(), stackNbt).orElse(ItemStack.EMPTY);
                
                if (slot >= 0 && slot < 2) {
                    customInv.setOxygenTank(slot, stack);
                    System.out.println("[CircuitMod] Read oxygen tank " + (slot+1) + ": " + stack);
                }
            }
            
            System.out.println("[CircuitMod] Finished reading oxygen tanks");
        }
    }
}

package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.block.entity.XpGeneratorBlockEntity;

public class XpGeneratorScreenHandler extends ScreenHandler {
    private final XpGeneratorBlockEntity blockEntity;
    private final PropertyDelegate propertyDelegate;

    // Client constructor
    public XpGeneratorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null, new ArrayPropertyDelegate(6));
    }
    
    // Server constructor
    public XpGeneratorScreenHandler(int syncId, PlayerInventory playerInventory, XpGeneratorBlockEntity blockEntity, PropertyDelegate propertyDelegate) {
        super(ModScreenHandlers.XP_GENERATOR_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.propertyDelegate = propertyDelegate;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Add player inventory slots
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }
    
    private void addPlayerInventory(PlayerInventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
    }
    
    private void addPlayerHotbar(PlayerInventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        return blockEntity != null ? blockEntity.canPlayerUse(player) : true;
    }
    
    // Property getters for GUI
    public boolean isPowered() {
        return propertyDelegate.get(0) == 1;
    }
    
    public int getStoredXp() {
        return propertyDelegate.get(1);
    }
    
    public int getGenerationProgress() {
        return propertyDelegate.get(2);
    }
    
    public int getMaxProgress() {
        return propertyDelegate.get(3);
    }
    
    public int getMaxStoredXp() {
        return propertyDelegate.get(4);
    }
    
    public int getEnergyDemand() {
        return propertyDelegate.get(5);
    }
    
    public XpGeneratorBlockEntity getBlockEntity() {
        return blockEntity;
    }
    
    @Override
    public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int slot) {
        // XP Generator has no inventory to quick move, return empty stack
        return net.minecraft.item.ItemStack.EMPTY;
    }
} 
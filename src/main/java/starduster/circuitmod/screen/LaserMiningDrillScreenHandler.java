package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.block.entity.LaserMiningDrillBlockEntity;
import starduster.circuitmod.screen.ModScreenHandlers.LaserMiningDrillData;
import starduster.circuitmod.Circuitmod;

public class LaserMiningDrillScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final LaserMiningDrillBlockEntity blockEntity; // Reference to block entity for getting position
    
    // Property delegate indices
    private static final int ENERGY_RECEIVED_INDEX = 0;
    private static final int MINING_ENABLED_INDEX = 1;
    
    // Client constructor
    public LaserMiningDrillScreenHandler(int syncId, PlayerInventory playerInventory, LaserMiningDrillData data) {
        super(ModScreenHandlers.LASER_MINING_DRILL_SCREEN_HANDLER, syncId);
        
        // Get the block position from the data
        BlockPos pos = data.pos();
        // Look up the block entity in the client world
        LaserMiningDrillBlockEntity blockEntity = 
            (LaserMiningDrillBlockEntity) playerInventory.player.getWorld().getBlockEntity(pos);
        
        // Initialize fields
        this.inventory = blockEntity;
        this.propertyDelegate = blockEntity.getPropertyDelegate();
        this.blockEntity = blockEntity;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add laser mining drill inventory slots (3x4 grid = 12 slots) positioned on the far right
        int rows = 3;
        int columns = 4;
        int startX = 98; // Position on the right side, starting at x=97
        int startY = 18; // Starting at y=17
        
        // Add the laser mining drill inventory slots
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                this.addSlot(new Slot(inventory, column + row * columns, startX + column * 18, startY + row * 18));
            }
        }
        
        // Add player inventory slots (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        
        // Add player hotbar slots (1 row of 9)
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
        
        Circuitmod.LOGGER.info("[LASER-MINING-DRILL-HANDLER] Client constructor called - blockEntity at pos: {}", pos);
    }
    
    // Server constructor
    public LaserMiningDrillScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, LaserMiningDrillBlockEntity blockEntity) {
        super(ModScreenHandlers.LASER_MINING_DRILL_SCREEN_HANDLER, syncId);
        checkSize(inventory, 12);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.blockEntity = blockEntity;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add laser mining drill inventory slots (3x4 grid = 12 slots) positioned on the far right
        int rows = 3;
        int columns = 4;
        int startX = 98; // Position on the right side
        int startY = 18; // Starting position
        
        // Add the laser mining drill inventory slots
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                this.addSlot(new Slot(inventory, column + row * columns, startX + column * 18, startY + row * 18));
            }
        }
        
        // Add player inventory slots (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        
        // Add player hotbar slots (1 row of 9)
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
        
        Circuitmod.LOGGER.info("[LASER-MINING-DRILL-HANDLER] Server constructor called");
    }
    
    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            
            // If the slot is from the laser mining drill's inventory (first 12 slots)
            if (invSlot < 12) {
                // Try to move to player inventory
                if (!this.insertItem(originalStack, 12, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // If the slot is from player inventory, try to move to laser mining drill inventory
                if (!this.insertItem(originalStack, 0, 12, false)) {
                    return ItemStack.EMPTY;
                }
            }
            
            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        
        return newStack;
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
    
    public boolean isMiningEnabled() {
        return propertyDelegate.get(MINING_ENABLED_INDEX) == 1;
    }
    
    public int getEnergyReceived() {
        return propertyDelegate.get(ENERGY_RECEIVED_INDEX);
    }
    
    public BlockPos getBlockPos() {
        return blockEntity != null ? blockEntity.getPos() : BlockPos.ORIGIN;
    }
    
    /**
     * Called when mining enabled status is updated from the server
     */
    public void setMiningEnabledProperty(boolean enabled) {
        propertyDelegate.set(MINING_ENABLED_INDEX, enabled ? 1 : 0);
        Circuitmod.LOGGER.info("[LASER-MINING-DRILL-HANDLER] Mining enabled property set to: {}", enabled);
    }
    
    /**
     * Called when depth is updated from the server
     */
    public void setDepthProperty(int depth) {
        // The property delegate doesn't have depth property, but we can update the block entity directly
        if (blockEntity != null) {
            blockEntity.setMiningDepthFromNetwork(depth);
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL-HANDLER] Depth property set to: {}", depth);
        }
    }
    
    /**
     * Gets the current depth from the property delegate
     */
    public int getCurrentDepth() {
        return propertyDelegate.get(2); // CURRENT_DEPTH_INDEX
    }
    
    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }
} 
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
import starduster.circuitmod.block.entity.DrillBlockEntity;
import starduster.circuitmod.screen.ModScreenHandlers.DrillData;
import starduster.circuitmod.Circuitmod;

public class DrillScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final DrillBlockEntity blockEntity; // Reference to block entity for getting position
    
    // Property delegate indices
    private static final int ENERGY_RECEIVED_INDEX = 0;
    private static final int MINING_ENABLED_INDEX = 1;
    

    
    // Client constructor
    public DrillScreenHandler(int syncId, PlayerInventory playerInventory, DrillData data) {
        super(ModScreenHandlers.DRILL_SCREEN_HANDLER, syncId);
        
        // Get the block position from the data
        BlockPos pos = data.pos();
        // Look up the block entity in the client world
        DrillBlockEntity blockEntity = 
            (DrillBlockEntity) playerInventory.player.getWorld().getBlockEntity(pos);
        
        // Initialize fields
        this.inventory = blockEntity;
        this.propertyDelegate = blockEntity.getPropertyDelegate();
        this.blockEntity = blockEntity;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add drill inventory slots (3x4 grid = 12 slots) positioned on the far right
        int rows = 3;
        int columns = 4;
        int startX = 98; // Position on the right side, starting at x=97
        int startY = 18; // Starting at y=17
        
        // Add the drill inventory slots
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
        
        Circuitmod.LOGGER.info("[DRILL-HANDLER] Client constructor called - blockEntity at pos: {}", pos);
    }
    
    // Server constructor
    public DrillScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, DrillBlockEntity blockEntity) {
        super(ModScreenHandlers.DRILL_SCREEN_HANDLER, syncId);
        checkSize(inventory, 12);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.blockEntity = blockEntity;
        
        if (blockEntity != null) {
            Circuitmod.LOGGER.info("[DRILL-HANDLER] Server constructor called - blockEntity at pos: {}", blockEntity.getPos());
        } else {
            Circuitmod.LOGGER.warn("[DRILL-HANDLER] Server constructor called - blockEntity is null!");
        }
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add drill inventory slots (3x4 grid = 12 slots) positioned on the far right
        int rows = 3;
        int columns = 4;
        int startX = 98; // Position on the right side, starting at x=97
        int startY = 18; // Starting at y=17
        
        // Add the drill inventory slots
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
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
    
    // Handle shift-clicking items between inventories
    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            
            if (invSlot < this.inventory.size()) {
                // Transfer from drill inventory to player inventory
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Transfer from player inventory to drill inventory
                if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
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
    
    // Get energy received for display
    public int getEnergyReceived() {
        return this.propertyDelegate.get(ENERGY_RECEIVED_INDEX);
    }
    
    // Get mining enabled state
    public boolean isMiningEnabled() {
        return this.propertyDelegate.get(MINING_ENABLED_INDEX) == 1;
    }
    
    // Get block position (for client-side networking)
    public BlockPos getBlockPos() {
        if (blockEntity != null) {
            BlockPos pos = blockEntity.getPos();
            Circuitmod.LOGGER.info("[DRILL-HANDLER] getBlockPos() - blockEntity exists, pos: {}", pos);
            return pos;
        } else {
            Circuitmod.LOGGER.warn("[DRILL-HANDLER] getBlockPos() - blockEntity is null! Returning ORIGIN");
            return BlockPos.ORIGIN;
        }
    }
    
    // Update mining enabled status from client networking
    public void updateMiningEnabledFromNetwork(boolean enabled) {
        this.propertyDelegate.set(MINING_ENABLED_INDEX, enabled ? 1 : 0);
        Circuitmod.LOGGER.info("[DRILL-HANDLER] Updated mining enabled property to: {}", enabled);
    }
} 
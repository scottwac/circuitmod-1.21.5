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
import starduster.circuitmod.block.entity.ReactorBlockBlockEntity;
import starduster.circuitmod.screen.ModScreenHandlers.ReactorData;

public class ReactorScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final ReactorBlockBlockEntity blockEntity;
    
    // Property delegate indices
    private static final int ENERGY_PRODUCTION_INDEX = 0;
    private static final int ROD_COUNT_INDEX = 1;
    private static final int IS_ACTIVE_INDEX = 2;
    
    // Client constructor
    public ReactorScreenHandler(int syncId, PlayerInventory playerInventory, ReactorData data) {
        super(ModScreenHandlers.REACTOR_SCREEN_HANDLER, syncId);
        
        // Get the block position from the data
        BlockPos pos = data.pos();
        // Look up the block entity in the client world
        ReactorBlockBlockEntity blockEntity = 
            (ReactorBlockBlockEntity) playerInventory.player.getWorld().getBlockEntity(pos);
        
        // Initialize fields
        this.inventory = blockEntity;
        this.propertyDelegate = blockEntity.getPropertyDelegate();
        this.blockEntity = blockEntity;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add reactor inventory slots (3x3 grid = 9 slots) for uranium pellets
        int rows = 3;
        int columns = 3;
        int startX = 97; // Position on the right side, starting at x=97
        int startY = 17; // Starting at y=17
        
        // Add the reactor inventory slots
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
    
    // Server constructor
    public ReactorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, ReactorBlockBlockEntity blockEntity) {
        super(ModScreenHandlers.REACTOR_SCREEN_HANDLER, syncId);
        checkSize(inventory, 9);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.blockEntity = blockEntity;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add reactor inventory slots (3x3 grid = 9 slots) for uranium pellets
        int rows = 3;
        int columns = 3;
        int startX = 97; // Position on the right side, starting at x=97
        int startY = 17; // Starting at y=17
        
        // Add the reactor inventory slots
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
                // Transfer from reactor inventory to player inventory
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Transfer from player inventory to reactor inventory
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
    
    // Get energy production for display
    public int getEnergyProduction() {
        return this.propertyDelegate.get(ENERGY_PRODUCTION_INDEX);
    }
    
    // Get rod count for display
    public int getRodCount() {
        return this.propertyDelegate.get(ROD_COUNT_INDEX);
    }
    
    // Get active state for display
    public boolean isActive() {
        return this.propertyDelegate.get(IS_ACTIVE_INDEX) == 1;
    }
} 
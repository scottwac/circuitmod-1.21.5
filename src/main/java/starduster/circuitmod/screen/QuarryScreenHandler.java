package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class QuarryScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    
    // Property delegate indices
    private static final int ENERGY_RECEIVED_INDEX = 0;
    private static final int MINING_SPEED_INDEX = 1;
    
    // Direct mining speed tracking for more reliable updates
    private int cachedMiningSpeed = 0;
    
    // Client constructor
    public QuarryScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(27), new PropertyDelegate() {
            public int get(int index) { return 0; }
            public void set(int index, int value) {}
            public int size() { return 2; }
        });
    }
    
    // Server constructor
    public QuarryScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate) {
        super(ModScreenHandlers.QUARRY_SCREEN_HANDLER, syncId);
        checkSize(inventory, 27);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Initialize the cached value
        if (propertyDelegate.size() > MINING_SPEED_INDEX) {
            this.cachedMiningSpeed = propertyDelegate.get(MINING_SPEED_INDEX);
        }
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add quarry inventory slots (3x9 grid = 27 slots)
        int rows = 3;
        int columns = 9;
        
        // Add the quarry inventory slots
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                this.addSlot(new Slot(inventory, column + row * columns, 8 + column * 18, 18 + row * 18));
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
                // Transfer from quarry inventory to player inventory
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Transfer from player inventory to quarry inventory
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
    
    // Get mining speed for display
    public int getMiningSpeed() {
        // First try to get the latest value from the property delegate
        int miningSpeed = this.propertyDelegate.get(MINING_SPEED_INDEX);
        
        starduster.circuitmod.Circuitmod.LOGGER.info("[SCREEN-HANDLER] Getting mining speed from property delegate: " + 
            miningSpeed + " (cached: " + cachedMiningSpeed + ")");
        
        // Update our cached value if the property delegate has a non-zero value
        if (miningSpeed > 0) {
            starduster.circuitmod.Circuitmod.LOGGER.info("[SCREEN-HANDLER] Updating cached mining speed to: " + miningSpeed);
            cachedMiningSpeed = miningSpeed;
        } else if (cachedMiningSpeed > 0) {
            // If property delegate returns 0 but we have a cached value, use that
            // This helps maintain the display between updates
            starduster.circuitmod.Circuitmod.LOGGER.info("[SCREEN-HANDLER] Using cached mining speed: " + cachedMiningSpeed + 
                " (property delegate returned: " + miningSpeed + ")");
            return cachedMiningSpeed;
        }
        
        return miningSpeed;
    }
    
    /**
     * Update the mining speed directly
     * This can be called from the block entity on the client side
     * 
     * @param miningSpeed The mining speed in blocks per second
     */
    public void updateMiningSpeed(int miningSpeed) {
        this.cachedMiningSpeed = miningSpeed;
    }
    
    // Get energy received for display
    public int getEnergyReceived() {
        return this.propertyDelegate.get(ENERGY_RECEIVED_INDEX);
    }
} 
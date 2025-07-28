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
import starduster.circuitmod.block.entity.QuarryBlockEntity;
import starduster.circuitmod.screen.ModScreenHandlers.QuarryData;
import starduster.circuitmod.Circuitmod;

public class QuarryScreenHandler extends ScreenHandler {
    private Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final QuarryBlockEntity blockEntity; // Reference to block entity for getting position
    
    // Property delegate indices
    private static final int ENERGY_RECEIVED_INDEX = 0;
    private static final int MINING_ENABLED_INDEX = 1;
    
    private BlockPos pos; // Added for client-side constructor

    
    /**
     * Client-side constructor called when opening the screen
     */
    public QuarryScreenHandler(int syncId, PlayerInventory playerInventory, QuarryData data) {
        super(ModScreenHandlers.QUARRY_SCREEN_HANDLER, syncId);
        
        // Store the position for reference
        this.pos = data.pos(); 
        
        QuarryBlockEntity blockEntity =
            (QuarryBlockEntity) playerInventory.player.getWorld().getBlockEntity(pos);
        this.blockEntity = blockEntity;
        
        if (blockEntity != null) {
            this.inventory = blockEntity;
            this.propertyDelegate = blockEntity.getPropertyDelegate();
        } else {
            // Create a fallback inventory and property delegate
            this.inventory = new SimpleInventory(12);
            this.propertyDelegate = new PropertyDelegate() {
                @Override
                public int get(int index) {
                    return 0; // Default value for all properties
                }
                
                @Override
                public void set(int index, int value) {
                    // Do nothing for fallback
                }
                
                @Override
                public int size() {
                    return 3; // miningEnabled, width, length
                }
            };
        }
        
        // Add property delegate
        this.addProperties(this.propertyDelegate);
        
        // Add quarry inventory slots (4x3 grid = 12 slots) positioned in center-left area
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                int slotIndex = row * 4 + col;
                this.addSlot(new Slot(this.inventory, slotIndex, 98 + col * 18, 18 + row * 18));
            }
        }
        
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }
    
    /**
     * Server-side constructor
     */
    public QuarryScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, QuarryBlockEntity blockEntity) {
        super(ModScreenHandlers.QUARRY_SCREEN_HANDLER, syncId);
        
        // Store references
        this.inventory = inventory;
        this.blockEntity = blockEntity;
        this.propertyDelegate = propertyDelegate;
        this.pos = blockEntity != null ? blockEntity.getPos() : BlockPos.ORIGIN;
        
        // Add property delegate
        this.addProperties(propertyDelegate);
        
        // Add quarry inventory slots (4x3 grid = 12 slots) positioned in center-left area
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                int slotIndex = row * 4 + col;
                this.addSlot(new Slot(inventory, slotIndex, 98 + col * 18, 18 + row * 18));
            }
        }
        
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
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
            return pos;
        } else {
            return BlockPos.ORIGIN;
        }
    }
    
    // Update mining enabled status from client networking
    public void updateMiningEnabledFromNetwork(boolean enabled) {
        this.propertyDelegate.set(MINING_ENABLED_INDEX, enabled ? 1 : 0);
    }

    // Helper method to add player inventory slots
    private void addPlayerInventory(PlayerInventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
    }

    // Helper method to add player hotbar slots
    private void addPlayerHotbar(PlayerInventory playerInventory) {
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }
} 
package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.block.entity.ConstructorBlockEntity;
import starduster.circuitmod.screen.ModScreenHandlers.ConstructorData;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.item.BlueprintItem;

public class ConstructorScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final ConstructorBlockEntity blockEntity; // Reference to block entity for getting position
    
    // Property delegate indices
    private static final int IS_BUILDING_INDEX = 0;
    private static final int BUILD_PROGRESS_INDEX = 1;
    private static final int TOTAL_BUILD_BLOCKS_INDEX = 2;
    private static final int HAS_BLUEPRINT_INDEX = 3;
    
    // Client constructor
    public ConstructorScreenHandler(int syncId, PlayerInventory playerInventory, ConstructorData data) {
        super(ModScreenHandlers.CONSTRUCTOR_SCREEN_HANDLER, syncId);
        
        // Get the block position from the data
        BlockPos pos = data.pos();
        // Look up the block entity in the client world
        ConstructorBlockEntity blockEntity = 
            (ConstructorBlockEntity) playerInventory.player.getWorld().getBlockEntity(pos);
        
        // Initialize fields
        this.inventory = blockEntity;
        this.propertyDelegate = blockEntity.getPropertyDelegate();
        this.blockEntity = blockEntity;
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add constructor inventory slots
        // Slot 0: Blueprint slot (special position)
        this.addSlot(new Slot(inventory, 0, 8, 17) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.getItem() instanceof BlueprintItem;
            }
        });
        
        // Slots 1-12: Material inventory (3x4 grid) positioned on the right
        int rows = 3;
        int columns = 4;
        int startX = 97; // Position on the right side, starting at x=97
        int startY = 17; // Starting at y=17
        
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                this.addSlot(new Slot(inventory, 1 + column + row * columns, startX + column * 18, startY + row * 18));
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
        
        Circuitmod.LOGGER.info("[CONSTRUCTOR-HANDLER] Client constructor called - blockEntity at pos: {}", pos);
    }
    
    // Server constructor
    public ConstructorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, ConstructorBlockEntity blockEntity) {
        super(ModScreenHandlers.CONSTRUCTOR_SCREEN_HANDLER, syncId);
        checkSize(inventory, 13);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.blockEntity = blockEntity;
        
        if (blockEntity != null) {
            Circuitmod.LOGGER.info("[CONSTRUCTOR-HANDLER] Server constructor called - blockEntity at pos: {}", blockEntity.getPos());
        } else {
            Circuitmod.LOGGER.warn("[CONSTRUCTOR-HANDLER] Server constructor called - blockEntity is null!");
        }
        
        // Add property delegate for synchronization
        this.addProperties(propertyDelegate);
        
        // Make the inventory accessible to the player
        inventory.onOpen(playerInventory.player);
        
        // Add constructor inventory slots
        // Slot 0: Blueprint slot (special position)
        this.addSlot(new Slot(inventory, 0, 8, 17) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.getItem() instanceof BlueprintItem;
            }
        });
        
        // Slots 1-12: Material inventory (3x4 grid) positioned on the right
        int rows = 3;
        int columns = 4;
        int startX = 97; // Position on the right side, starting at x=97
        int startY = 17; // Starting at y=17
        
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                this.addSlot(new Slot(inventory, 1 + column + row * columns, startX + column * 18, startY + row * 18));
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
                // Transfer from constructor inventory to player inventory
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Transfer from player inventory to constructor inventory
                if (originalStack.getItem() instanceof BlueprintItem) {
                    // Blueprint items go to slot 0
                    if (!this.insertItem(originalStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Other items go to material slots (1-12)
                    if (!this.insertItem(originalStack, 1, this.inventory.size(), false)) {
                        return ItemStack.EMPTY;
                    }
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
    
    // Get building state for display
    public boolean isBuilding() {
        return this.propertyDelegate.get(IS_BUILDING_INDEX) == 1;
    }
    
    // Get build progress for display
    public int getBuildProgress() {
        return this.propertyDelegate.get(BUILD_PROGRESS_INDEX);
    }
    
    // Get total build blocks for display
    public int getTotalBuildBlocks() {
        return this.propertyDelegate.get(TOTAL_BUILD_BLOCKS_INDEX);
    }
    
    // Get has blueprint state for display
    public boolean hasBlueprint() {
        return this.propertyDelegate.get(HAS_BLUEPRINT_INDEX) == 1;
    }
    
    // Get block position (for client-side networking)
    public BlockPos getBlockPos() {
        return blockEntity != null ? blockEntity.getPos() : BlockPos.ORIGIN;
    }
    
    // Get the block entity (for direct access when needed)
    public ConstructorBlockEntity getBlockEntity() {
        return blockEntity;
    }
} 
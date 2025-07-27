package starduster.circuitmod.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.block.entity.SortingPipeBlockEntity;

public class SortingPipeScreenHandler extends ScreenHandler {
    private final SortingPipeBlockEntity blockEntity;
    private final Inventory filterInventory;
    
    // Constructor for server-side (with block entity)
    public SortingPipeScreenHandler(int syncId, PlayerInventory playerInventory, SortingPipeBlockEntity blockEntity) {
        super(ModScreenHandlers.SORTING_PIPE_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        
        // Handle client-side case where blockEntity is null
        if (blockEntity != null) {
            this.filterInventory = new SimpleInventory(blockEntity.getFilterInventory().toArray(new ItemStack[0])) {
                @Override
                public void markDirty() {
                    // Sync changes back to the block entity
                    for (int i = 0; i < 6; i++) {
                        blockEntity.setFilterStack(i, this.getStack(i));
                    }
                    blockEntity.markDirty();
                }
            };
        } else {
            // Client-side: create empty inventory
            this.filterInventory = new SimpleInventory(6);
        }
        
        // Add filter slots in compass layout:
        // Layout:      [NORTH]           [UP]
        //        [WEST]   X   [EAST]     [DOWN]  
        //             [SOUTH]
        
        int centerX = 80;
        int centerY = 30; // Center for compass directions
        
        // Compass directions in cross pattern
        // Slot 0: NORTH (above center)
        this.addSlot(new FilterSlot(filterInventory, 0, centerX, centerY - 18));
        
        // Slot 1: EAST (right of center)
        this.addSlot(new FilterSlot(filterInventory, 1, centerX + 18, centerY));
        
        // Slot 2: SOUTH (below center)
        this.addSlot(new FilterSlot(filterInventory, 2, centerX, centerY + 18));
        
        // Slot 3: WEST (left of center)
        this.addSlot(new FilterSlot(filterInventory, 3, centerX - 18, centerY));
        
        // Up/Down off to the side
        // Slot 4: UP (right side, upper)
        this.addSlot(new FilterSlot(filterInventory, 4, centerX + 45, centerY - 9));
        
        // Slot 5: DOWN (right side, lower)
        this.addSlot(new FilterSlot(filterInventory, 5, centerX + 45, centerY + 9));
        
        // Player inventory (standard layout matching crusher)
        // Main inventory (3x9 grid)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        
        // Hotbar (1x9 grid)
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
    
    // Constructor for client-side (without block entity)
    public SortingPipeScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        if (blockEntity != null) {
            BlockPos pos = blockEntity.getPos();
            return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
        }
        return true;
    }
    
    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            
            if (slotIndex < 6) {
                // Moving from filter slots to player inventory
                if (!this.insertItem(originalStack, 6, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory to filter slots
                if (!this.insertItem(originalStack, 0, 6, false)) {
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
    
    public BlockPos getBlockPos() {
        return blockEntity != null ? blockEntity.getPos() : BlockPos.ORIGIN;
    }
    
    // Custom slot class for filter slots
    private static class FilterSlot extends Slot {
        public FilterSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        
        @Override
        public int getMaxItemCount() {
            return 1; // Filter slots only hold 1 item for filtering
        }
    }
} 
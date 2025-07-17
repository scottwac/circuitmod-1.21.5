package starduster.circuitmod.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;

/**
 * Compressed storage for block data in blueprints.
 * Stores the block state and optional block entity data.
 */
public class CompressedBlockData {
    private final BlockState state;
    private final NbtCompound blockEntityData; // null if no block entity
    
    public CompressedBlockData(BlockState state, NbtCompound blockEntityData) {
        this.state = state;
        this.blockEntityData = blockEntityData;
    }
    
    public BlockState getState() {
        return state;
    }
    
    public NbtCompound getBlockEntityData() {
        return blockEntityData;
    }
    
    public boolean hasBlockEntity() {
        return blockEntityData != null;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CompressedBlockData other = (CompressedBlockData) obj;
        return state.equals(other.state) && 
               (blockEntityData == null ? other.blockEntityData == null : 
                blockEntityData.equals(other.blockEntityData));
    }
    
    @Override
    public int hashCode() {
        int result = state.hashCode();
        result = 31 * result + (blockEntityData != null ? blockEntityData.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("CompressedBlockData{state=%s, hasEntity=%b}", 
            state.getBlock().getName(), hasBlockEntity());
    }
} 
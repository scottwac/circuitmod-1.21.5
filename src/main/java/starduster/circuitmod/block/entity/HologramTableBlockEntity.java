package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import starduster.circuitmod.Circuitmod;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class HologramTableBlockEntity extends BlockEntity {
    // Cache for scanned blocks (client-side only)
    private List<BlockEntry> cachedBlocks = new ArrayList<>();
    private boolean needsRescan = true;
    // Chunk offset for chaining - (0,0) means render own chunk, (1,0) means render chunk east, etc.
    private int chunkOffsetX = 0;
    private int chunkOffsetZ = 0;
    
    public static class BlockEntry {
        public final BlockPos pos;
        public final BlockState state;
        
        public BlockEntry(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }
    
    public HologramTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLOGRAM_TABLE_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, HologramTableBlockEntity entity) {
        // The entity doesn't need server-side ticking, but we keep this for consistency
        // The rendering happens client-side only
        // Chaining checks happen via scheduledTick in the block
    }
    
    /**
     * Checks for adjacent hologram tables and sets up chaining
     * Called from scheduledTick when neighbors change
     */
    public void checkForChaining(World world, BlockPos pos) {
        // Only check if we don't already have an offset set
        if (chunkOffsetX != 0 || chunkOffsetZ != 0) {
            Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Table at {} already has offset ({}, {}), skipping chaining check", pos, chunkOffsetX, chunkOffsetZ);
            return;
        }
        
        int thisChunkX = getChunkX();
        int thisChunkZ = getChunkZ();
        Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Checking for chaining at {} (chunk {}, {})", pos, thisChunkX, thisChunkZ);
        
        // Check all horizontal directions for adjacent hologram tables
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
            
            Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Checking direction {} at neighbor pos {}", dir, neighborPos);
            
            if (neighborEntity instanceof HologramTableBlockEntity neighborTable) {
                Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Found neighbor table at {} (chunk {}, {})", 
                    neighborPos, neighborTable.getChunkX(), neighborTable.getChunkZ());
                
                // Get neighbor's chunk and target chunk
                int neighborChunkX = neighborTable.getChunkX();
                int neighborChunkZ = neighborTable.getChunkZ();
                int neighborTargetChunkX = neighborTable.getTargetChunkX();
                int neighborTargetChunkZ = neighborTable.getTargetChunkZ();
                
                Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Neighbor table at chunk ({}, {}) is rendering chunk ({}, {})", 
                    neighborChunkX, neighborChunkZ, neighborTargetChunkX, neighborTargetChunkZ);
                
                // Determine direction from neighbor's position to this table's position
                int offsetX = 0;
                int offsetZ = 0;
                
                switch (dir) {
                    case EAST:  // This table is east of neighbor
                        offsetX = 1;
                        break;
                    case WEST:  // This table is west of neighbor
                        offsetX = -1;
                        break;
                    case SOUTH: // This table is south of neighbor
                        offsetZ = 1;
                        break;
                    case NORTH: // This table is north of neighbor
                        offsetZ = -1;
                        break;
                    default:
                        break;
                }
                
                // If both tables are in the same chunk, use neighbor's base chunk to determine direction
                // Otherwise, use neighbor's target chunk
                int targetChunkX, targetChunkZ;
                if (neighborChunkX == thisChunkX && neighborChunkZ == thisChunkZ) {
                    // Same chunk - render chunk in direction from neighbor's base chunk
                    targetChunkX = neighborChunkX + offsetX;
                    targetChunkZ = neighborChunkZ + offsetZ;
                    Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Both tables in same chunk, using base chunk for direction");
                } else {
                    // Different chunks - render chunk in direction from neighbor's target chunk
                    targetChunkX = neighborTargetChunkX + offsetX;
                    targetChunkZ = neighborTargetChunkZ + offsetZ;
                }
                
                // Calculate offset from this table's chunk to target chunk
                int finalOffsetX = targetChunkX - thisChunkX;
                int finalOffsetZ = targetChunkZ - thisChunkZ;
                
                Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] This table is {} of neighbor. Setting table at {} (chunk {},{}) to render chunk ({}, {}) with offset ({}, {})", 
                    dir, pos, thisChunkX, thisChunkZ, targetChunkX, targetChunkZ, finalOffsetX, finalOffsetZ);
                
                // Only set offset if it's non-zero (don't render own chunk when chaining)
                if (finalOffsetX != 0 || finalOffsetZ != 0) {
                    setChunkOffset(finalOffsetX, finalOffsetZ);
                    markDirty();
                    // Trigger block update to sync to client
                    if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                        world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                        // Send update packet to sync block entity data
                        serverWorld.getChunkManager().markForUpdate(pos);
                    }
                    break; // Only chain to first found neighbor
                } else {
                    Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Calculated offset is (0, 0), skipping chaining for this direction");
                }
            } else {
                Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] No neighbor table found at {} (entity: {})", neighborPos, neighborEntity != null ? neighborEntity.getClass().getSimpleName() : "null");
            }
        }
        
        if (chunkOffsetX == 0 && chunkOffsetZ == 0) {
            Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] No chaining found for table at {}, will render own chunk ({}, {})", pos, thisChunkX, thisChunkZ);
        }
    }
    
    public void requestRescan() {
        needsRescan = true;
        cachedBlocks.clear();
    }
    
    public boolean needsRescan() {
        return needsRescan;
    }
    
    public void setCachedBlocks(List<BlockEntry> blocks) {
        cachedBlocks = blocks;
        needsRescan = false;
    }
    
    public List<BlockEntry> getCachedBlocks() {
        return cachedBlocks;
    }
    
    /**
     * Sets the chunk offset for chaining tables together
     */
    public void setChunkOffset(int offsetX, int offsetZ) {
        // Only clear cache if offset actually changed
        if (this.chunkOffsetX != offsetX || this.chunkOffsetZ != offsetZ) {
            this.chunkOffsetX = offsetX;
            this.chunkOffsetZ = offsetZ;
            this.needsRescan = true; // Rescan when offset changes
            this.cachedBlocks.clear(); // Clear cached blocks from old chunk
            Circuitmod.LOGGER.info("[HOLOGRAM-TABLE] Offset changed to ({}, {}), cleared cache", offsetX, offsetZ);
        }
    }
    
    /**
     * Gets the chunk offset X
     */
    public int getChunkOffsetX() {
        return chunkOffsetX;
    }
    
    /**
     * Gets the chunk offset Z
     */
    public int getChunkOffsetZ() {
        return chunkOffsetZ;
    }
    
    /**
     * Gets the target chunk X coordinate (own chunk + offset)
     */
    public int getTargetChunkX() {
        return getChunkX() + chunkOffsetX;
    }
    
    /**
     * Gets the target chunk Z coordinate (own chunk + offset)
     */
    public int getTargetChunkZ() {
        return getChunkZ() + chunkOffsetZ;
    }

    /**
     * Gets the surface Y coordinate for a given X, Z position
     */
    public int getSurfaceY(int x, int z) {
        if (world == null) {
            return pos.getY();
        }
        BlockPos checkPos = new BlockPos(x, 0, z);
        return world.getTopY(Heightmap.Type.WORLD_SURFACE, checkPos);
    }

    /**
     * Gets the chunk X coordinate for this block entity
     */
    public int getChunkX() {
        return pos.getX() >> 4; // Divide by 16
    }

    /**
     * Gets the chunk Z coordinate for this block entity
     */
    public int getChunkZ() {
        return pos.getZ() >> 4; // Divide by 16
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("chunkOffsetX", chunkOffsetX);
        nbt.putInt("chunkOffsetZ", chunkOffsetZ);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        chunkOffsetX = nbt.getInt("chunkOffsetX").orElse(0);
        chunkOffsetZ = nbt.getInt("chunkOffsetZ").orElse(0);
        // Clear cache when loading from NBT to ensure fresh scan
        if (chunkOffsetX != 0 || chunkOffsetZ != 0) {
            cachedBlocks.clear();
            needsRescan = true;
        }
    }
    
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
    
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt, registries);
        return nbt;
    }
}


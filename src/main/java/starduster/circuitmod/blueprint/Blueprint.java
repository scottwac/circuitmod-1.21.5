package starduster.circuitmod.blueprint;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

import java.util.*;

/**
 * Represents a blueprint of blocks that can be scanned and built by the Constructor.
 * Uses efficient storage to minimize memory usage and NBT size.
 */
public class Blueprint {
    private UUID id;
    private String name;
    private BlockPos dimensions; // width, height, length
    private BlockPos origin; // relative origin point (0,0,0)
    
    // Efficient storage - only store non-air blocks
    private Map<Long, CompressedBlockData> blocks; // Key: packed position (long)
    
    // Metadata
    private long createdTime;
    private int totalBlocks;
    private Set<Identifier> requiredBlocks; // Block IDs for material calculation
    
    // Default constructor for deserialization
    public Blueprint() {
        this.id = UUID.randomUUID();
        this.name = "Untitled Blueprint";
        this.dimensions = BlockPos.ORIGIN;
        this.origin = BlockPos.ORIGIN;
        this.blocks = new HashMap<>();
        this.createdTime = System.currentTimeMillis();
        this.totalBlocks = 0;
        this.requiredBlocks = new HashSet<>();
    }
    
    // Constructor for creating new blueprints
    public Blueprint(String name, BlockPos dimensions, BlockPos origin) {
        this();
        this.name = name;
        this.dimensions = dimensions;
        this.origin = origin;
    }
    
    /**
     * Packs a BlockPos into a long for efficient storage
     */
    private static long packPosition(BlockPos pos) {
        return ((long) pos.getX() & 0xFFFFL) | 
               (((long) pos.getY() & 0xFFFFL) << 16) | 
               (((long) pos.getZ() & 0xFFFFL) << 32);
    }
    
    /**
     * Unpacks a long back into a BlockPos
     */
    private static BlockPos unpackPosition(long packed) {
        int x = (int) (packed & 0xFFFFL);
        int y = (int) ((packed >> 16) & 0xFFFFL);
        int z = (int) ((packed >> 32) & 0xFFFFL);
        
        // Handle negative coordinates (sign extension)
        if (x >= 32768) x -= 65536;
        if (y >= 32768) y -= 65536;
        if (z >= 32768) z -= 65536;
        
        return new BlockPos(x, y, z);
    }
    
    /**
     * Adds a block to the blueprint at the specified relative position
     */
    public void addBlock(BlockPos relativePos, BlockState state, NbtCompound blockEntityData) {
        if (state.isAir()) {
            return; // Don't store air blocks
        }
        
        long packedPos = packPosition(relativePos);
        CompressedBlockData blockData = new CompressedBlockData(state, blockEntityData);
        
        blocks.put(packedPos, blockData);
        requiredBlocks.add(BlockStateSerializer.getBlockId(state));
        totalBlocks++;
        
        Circuitmod.LOGGER.debug("[BLUEPRINT] Added block {} at {}", state.getBlock().getName(), relativePos);
    }
    
    /**
     * Gets the block state at the specified relative position
     */
    public BlockState getBlockState(BlockPos relativePos) {
        long packedPos = packPosition(relativePos);
        CompressedBlockData blockData = blocks.get(packedPos);
        return blockData != null ? blockData.getState() : null;
    }
    
    /**
     * Gets the block entity data at the specified relative position
     */
    public NbtCompound getBlockEntityData(BlockPos relativePos) {
        long packedPos = packPosition(relativePos);
        CompressedBlockData blockData = blocks.get(packedPos);
        return blockData != null ? blockData.getBlockEntityData() : null;
    }
    
    /**
     * Removes a block from the blueprint
     */
    public void removeBlock(BlockPos relativePos) {
        long packedPos = packPosition(relativePos);
        CompressedBlockData removed = blocks.remove(packedPos);
        if (removed != null) {
            totalBlocks--;
            // Note: We don't remove from requiredBlocks as other blocks might still need it
        }
    }
    
    /**
     * Gets all block positions in the blueprint
     */
    public Set<BlockPos> getAllBlockPositions() {
        Set<BlockPos> positions = new HashSet<>();
        for (Long packedPos : blocks.keySet()) {
            positions.add(unpackPosition(packedPos));
        }
        return positions;
    }
    
    /**
     * Serializes the blueprint to NBT for storage
     */
    public NbtCompound writeToNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        
        // Basic metadata
        nbt.putString("id", id.toString());
        nbt.putString("name", name);
        nbt.putLong("created_time", createdTime);
        nbt.putInt("total_blocks", totalBlocks);
        
        // Dimensions and origin
        nbt.putInt("width", dimensions.getX());
        nbt.putInt("height", dimensions.getY());
        nbt.putInt("length", dimensions.getZ());
        nbt.putInt("origin_x", origin.getX());
        nbt.putInt("origin_y", origin.getY());
        nbt.putInt("origin_z", origin.getZ());
        
        // Required blocks list
        NbtList requiredBlocksList = new NbtList();
        for (Identifier blockId : requiredBlocks) {
            NbtCompound blockIdNbt = new NbtCompound();
            blockIdNbt.putString("block_id", blockId.toString());
            requiredBlocksList.add(blockIdNbt);
        }
        nbt.put("required_blocks", requiredBlocksList);
        
        // Block data
        NbtList blocksList = new NbtList();
        for (Map.Entry<Long, CompressedBlockData> entry : blocks.entrySet()) {
            NbtCompound blockNbt = new NbtCompound();
            BlockPos pos = unpackPosition(entry.getKey());
            
            blockNbt.putInt("x", pos.getX());
            blockNbt.putInt("y", pos.getY());
            blockNbt.putInt("z", pos.getZ());
            
            CompressedBlockData blockData = entry.getValue();
            blockNbt.put("state", BlockStateSerializer.serialize(blockData.getState(), registries));
            
            if (blockData.getBlockEntityData() != null) {
                blockNbt.put("block_entity", blockData.getBlockEntityData());
            }
            
            blocksList.add(blockNbt);
        }
        nbt.put("blocks", blocksList);
        
        Circuitmod.LOGGER.info("[BLUEPRINT] Serialized blueprint '{}' with {} blocks", name, totalBlocks);
        return nbt;
    }
    
    /**
     * Deserializes a blueprint from NBT
     */
    public static Blueprint readFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        Blueprint blueprint = new Blueprint();
        
        // Basic metadata
        String idString = nbt.getString("id", "");
        if (!idString.isEmpty()) {
            try {
                blueprint.id = UUID.fromString(idString);
            } catch (IllegalArgumentException e) {
                blueprint.id = UUID.randomUUID(); // Generate new UUID if parsing fails
            }
        }
        blueprint.name = nbt.getString("name", "Untitled Blueprint");
        blueprint.createdTime = nbt.getLong("created_time", System.currentTimeMillis());
        blueprint.totalBlocks = nbt.getInt("total_blocks", 0);
        
        // Dimensions and origin
        int width = nbt.getInt("width", 0);
        int height = nbt.getInt("height", 0);
        int length = nbt.getInt("length", 0);
        blueprint.dimensions = new BlockPos(width, height, length);
        
        int originX = nbt.getInt("origin_x", 0);
        int originY = nbt.getInt("origin_y", 0);
        int originZ = nbt.getInt("origin_z", 0);
        blueprint.origin = new BlockPos(originX, originY, originZ);
        
        // Required blocks
        blueprint.requiredBlocks = new HashSet<>();
        if (nbt.contains("required_blocks")) {
            NbtList requiredBlocksList = nbt.getList("required_blocks").orElse(new NbtList());
            for (int i = 0; i < requiredBlocksList.size(); i++) {
                NbtCompound blockIdNbt = requiredBlocksList.getCompound(i).orElse(new NbtCompound());
                String blockIdString = blockIdNbt.getString("block_id", "");
                if (!blockIdString.isEmpty()) {
                    Identifier blockId = Identifier.tryParse(blockIdString);
                    if (blockId != null) {
                        blueprint.requiredBlocks.add(blockId);
                    }
                }
            }
        }
        
        // Block data
        blueprint.blocks = new HashMap<>();
        if (nbt.contains("blocks")) {
            NbtList blocksList = nbt.getList("blocks").orElse(new NbtList());
            for (int i = 0; i < blocksList.size(); i++) {
                NbtCompound blockNbt = blocksList.getCompound(i).orElse(new NbtCompound());
                
                int x = blockNbt.getInt("x", 0);
                int y = blockNbt.getInt("y", 0);
                int z = blockNbt.getInt("z", 0);
                BlockPos pos = new BlockPos(x, y, z);
                
                BlockState state = BlockStateSerializer.deserialize(blockNbt.getCompound("state").orElse(new NbtCompound()), registries);
                NbtCompound blockEntityData = blockNbt.contains("block_entity") ? 
                    blockNbt.getCompound("block_entity").orElse(null) : null;
                
                if (state != null) {
                    long packedPos = packPosition(pos);
                    blueprint.blocks.put(packedPos, new CompressedBlockData(state, blockEntityData));
                }
            }
        }
        
        Circuitmod.LOGGER.info("[BLUEPRINT] Deserialized blueprint '{}' with {} blocks", blueprint.name, blueprint.totalBlocks);
        return blueprint;
    }
    
    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public BlockPos getDimensions() { return dimensions; }
    public void setDimensions(BlockPos dimensions) { this.dimensions = dimensions; }
    
    public BlockPos getOrigin() { return origin; }
    public void setOrigin(BlockPos origin) { this.origin = origin; }
    
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    
    public int getTotalBlocks() { return totalBlocks; }
    public Set<Identifier> getRequiredBlocks() { return requiredBlocks; }
    
    public int getWidth() { return dimensions.getX(); }
    public int getHeight() { return dimensions.getY(); }
    public int getLength() { return dimensions.getZ(); }
    
    /**
     * Checks if the blueprint is empty
     */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }
    
    /**
     * Gets the number of unique block types required
     */
    public int getUniqueBlockTypes() {
        return requiredBlocks.size();
    }
    
    /**
     * Gets a summary string for display
     */
    public String getSummary() {
        return String.format("%s (%dx%dx%d, %d blocks, %d types)", 
            name, getWidth(), getHeight(), getLength(), totalBlocks, getUniqueBlockTypes());
    }
} 
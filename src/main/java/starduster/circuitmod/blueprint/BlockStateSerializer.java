package starduster.circuitmod.blueprint;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import starduster.circuitmod.Circuitmod;

/**
 * Utility class for serializing and deserializing BlockState objects to/from NBT.
 * Handles block state properties and registry lookups.
 */
public class BlockStateSerializer {
    
    /**
     * Serializes a BlockState to NBT
     */
    public static NbtCompound serialize(BlockState state, RegistryWrapper.WrapperLookup registries) {
        try {
            return NbtHelper.fromBlockState(state);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[BLUEPRINT] Failed to serialize block state: {}", state, e);
            // Fallback: create a basic NBT compound with just the block ID
            NbtCompound fallback = new NbtCompound();
            fallback.putString("Name", getBlockId(state).toString());
            return fallback;
        }
    }
    
    /**
     * Deserializes a BlockState from NBT
     */
    public static BlockState deserialize(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        try {
            // Get the block registry from the wrapper
            return NbtHelper.toBlockState(registries.getOrThrow(RegistryKeys.BLOCK), nbt);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[BLUEPRINT] Failed to deserialize block state from NBT: {}", nbt, e);
            
            // Fallback: try to get just the block without properties
            String blockName = nbt.getString("Name", "");
            if (!blockName.isEmpty()) {
                try {
                    Identifier blockId = Identifier.tryParse(blockName);
                    if (blockId != null && Registries.BLOCK.containsId(blockId)) {
                        Block block = Registries.BLOCK.get(blockId);
                        return block.getDefaultState();
                    }
                } catch (Exception fallbackException) {
                    Circuitmod.LOGGER.error("[BLUEPRINT] Fallback deserialization failed for block: {}", blockName, fallbackException);
                }
            }
            
            // Final fallback: return null to indicate failure
            return null;
        }
    }
    
    /**
     * Gets the registry identifier for a block state
     */
    public static Identifier getBlockId(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock());
    }
    
    /**
     * Gets a human-readable name for a block state
     */
    public static String getBlockName(BlockState state) {
        Identifier id = getBlockId(state);
        return id.toString();
    }
    
    /**
     * Validates that a block state can be properly serialized and deserialized
     */
    public static boolean validateSerialization(BlockState state, RegistryWrapper.WrapperLookup registries) {
        try {
            NbtCompound serialized = serialize(state, registries);
            BlockState deserialized = deserialize(serialized, registries);
            return deserialized != null && deserialized.equals(state);
        } catch (Exception e) {
            Circuitmod.LOGGER.warn("[BLUEPRINT] Block state serialization validation failed for: {}", state, e);
            return false;
        }
    }
} 
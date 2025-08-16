package starduster.circuitmod.power;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global manager for energy networks that ensures consistency across chunk loads/unloads.
 * This prevents the desync issues that occur when chunks are unloaded and reloaded.
 */
public class EnergyNetworkManager {
    // Global registry of all energy networks by ID
    private static final Map<String, EnergyNetwork> networksById = new ConcurrentHashMap<>();
    
    // Mapping from block positions to their network IDs for quick lookup
    private static final Map<BlockPos, String> blockToNetwork = new ConcurrentHashMap<>();
    
    // Flag to control logging during startup
    private static boolean startupMode = true;
    
    /**
     * Sets startup mode to control logging
     */
    public static void setStartupMode(boolean mode) {
        startupMode = mode;
        // Also set it on all existing networks
        for (EnergyNetwork network : networksById.values()) {
            EnergyNetwork.setStartupMode(mode);
        }
    }
    
    /**
     * Registers a network globally. This should be called when a network is created.
     * 
     * @param network The network to register
     */
    public static void registerNetwork(EnergyNetwork network) {
        if (network == null) return;
        
        String networkId = network.getNetworkId();
        networksById.put(networkId, network);
        
        // Update block-to-network mapping for all blocks in this network
        for (BlockPos pos : network.getConnectedBlockPositions()) {
            blockToNetwork.put(pos, networkId);
        }
        
        if (!startupMode) {
            Circuitmod.LOGGER.debug("Registered energy network {} with {} blocks", networkId, network.getSize());
        }
    }
    
    /**
     * Unregisters a network globally. This should be called when a network is destroyed.
     * 
     * @param network The network to unregister
     */
    public static void unregisterNetwork(EnergyNetwork network) {
        if (network == null) return;
        
        String networkId = network.getNetworkId();
        EnergyNetwork removed = networksById.remove(networkId);
        
        if (removed != null) {
            // Remove all block mappings for this network
            for (BlockPos pos : removed.getConnectedBlockPositions()) {
                blockToNetwork.remove(pos);
            }
            
            if (!startupMode) {
                Circuitmod.LOGGER.debug("Unregistered energy network {} with {} blocks", networkId, removed.getSize());
            }
        }
    }
    
    /**
     * Gets a network by its ID. If the network doesn't exist, returns null.
     * 
     * @param networkId The ID of the network to retrieve
     * @return The network, or null if not found
     */
    public static EnergyNetwork getNetwork(String networkId) {
        return networksById.get(networkId);
    }
    
    /**
     * Gets the network ID for a specific block position.
     * 
     * @param pos The block position
     * @return The network ID, or null if the block is not in any network
     */
    public static String getNetworkIdForBlock(BlockPos pos) {
        return blockToNetwork.get(pos);
    }
    
    /**
     * Gets the network for a specific block position.
     * 
     * @param pos The block position
     * @return The network, or null if the block is not in any network
     */
    public static EnergyNetwork getNetworkForBlock(BlockPos pos) {
        String networkId = blockToNetwork.get(pos);
        return networkId != null ? networksById.get(networkId) : null;
    }
    
    /**
     * Creates a new network with the specified ID and registers it globally.
     * This ensures network consistency across chunk loads.
     * 
     * @param networkId The ID for the new network
     * @return The newly created and registered network
     */
    public static EnergyNetwork createNetwork(String networkId) {
        EnergyNetwork network = new EnergyNetwork(networkId);
        registerNetwork(network);
        return network;
    }
    
    /**
     * Creates a new network with a random ID and registers it globally.
     * 
     * @return The newly created and registered network
     */
    public static EnergyNetwork createNetwork() {
        EnergyNetwork network = new EnergyNetwork();
        registerNetwork(network);
        return network;
    }
    
    /**
     * Merges two networks, ensuring the result is properly registered globally.
     * 
     * @param primary The primary network that will remain
     * @param secondary The secondary network that will be merged into the primary
     */
    public static void mergeNetworks(EnergyNetwork primary, EnergyNetwork secondary) {
        if (primary == null || secondary == null || primary == secondary) return;
        
        // Unregister the secondary network first
        unregisterNetwork(secondary);
        
        // Perform the merge
        primary.mergeWith(secondary);
        
        // Update block mappings for the merged network
        for (BlockPos pos : primary.getConnectedBlockPositions()) {
            blockToNetwork.put(pos, primary.getNetworkId());
        }
        
        if (!startupMode) {
            Circuitmod.LOGGER.info("Merged networks: {} into {}, new size: {}", 
                secondary.getNetworkId(), primary.getNetworkId(), primary.getSize());
        }
    }
    
    /**
     * Removes a block from its network and updates global tracking.
     * 
     * @param pos The position of the block to remove
     * @return The network the block was removed from, or null if none
     */
    public static EnergyNetwork removeBlockFromNetwork(BlockPos pos) {
        String networkId = blockToNetwork.remove(pos);
        if (networkId == null) return null;
        
        EnergyNetwork network = networksById.get(networkId);
        if (network != null) {
            network.removeBlock(pos);
            
            // If the network is now empty, unregister it
            if (network.getSize() == 0) {
                unregisterNetwork(network);
            }
        }
        
        return network;
    }
    
    /**
     * Adds a block to a network and updates global tracking.
     * 
     * @param pos The position of the block
     * @param network The network to add the block to
     */
    public static void addBlockToNetwork(BlockPos pos, EnergyNetwork network) {
        if (network == null) return;
        
        network.addBlock(pos, null); // The block entity will set itself
        blockToNetwork.put(pos, network.getNetworkId());
        
        // Ensure the network is registered
        if (!networksById.containsKey(network.getNetworkId())) {
            registerNetwork(network);
        }
    }
    
    /**
     * Validates all networks and repairs any inconsistencies.
     * This should be called during world loading to recover from crashes.
     * 
     * @param world The world to validate networks in
     * @return The number of networks that were repaired
     */
    public static int validateAllNetworks(World world) {
        if (world == null) return 0;
        
        int repairedCount = 0;
        Set<String> networksToRemove = new HashSet<>();
        
        // Validate each network
        for (Map.Entry<String, EnergyNetwork> entry : networksById.entrySet()) {
            String networkId = entry.getKey();
            EnergyNetwork network = entry.getValue();
            
            if (network.validateAndRepair(world)) {
                repairedCount++;
            }
            
            // If network is now empty or inactive, mark for removal
            if (network.getSize() == 0 || !network.isActive()) {
                networksToRemove.add(networkId);
            }
        }
        
        // Remove invalid networks
        for (String networkId : networksToRemove) {
            EnergyNetwork network = networksById.remove(networkId);
            if (network != null) {
                // Remove all block mappings for this network
                for (BlockPos pos : network.getConnectedBlockPositions()) {
                    blockToNetwork.remove(pos);
                }
            }
        }
        
        if (!startupMode && repairedCount > 0) {
            Circuitmod.LOGGER.info("Validated energy networks: {} repaired, {} removed, {} remaining", 
                repairedCount, networksToRemove.size(), networksById.size());
        }
        
        return repairedCount;
    }
    
    /**
     * Performs a global network recovery operation.
     * This scans for orphaned blocks and attempts to reconnect them.
     * 
     * @param world The world to recover networks in
     * @return The number of blocks that were recovered
     */
    public static int performGlobalRecovery(World world) {
        if (world == null || world.isClient()) return 0;
        
        int recoveredCount = 0;
        Set<BlockPos> orphanedBlocks = new HashSet<>();
        
        // Find all power connectable blocks that aren't in any network
        for (int x = -3000; x <= 3000; x += 16) {
            for (int z = -3000; z <= 3000; z += 16) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                
                for (int y = world.getBottomY(); y < world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, new BlockPos(x, y, z)); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = world.getBlockEntity(pos);
                    
                    if (be instanceof IPowerConnectable) {
                        IPowerConnectable connectable = (IPowerConnectable) be;
                        if (connectable.getNetwork() == null) {
                            orphanedBlocks.add(pos);
                        }
                    }
                }
            }
        }
        
        // Attempt to reconnect orphaned blocks
        for (BlockPos pos : orphanedBlocks) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                
                // Try to find a network to join
                EnergyNetwork network = findAdjacentNetwork(world, pos);
                if (network != null) {
                    network.addBlock(pos, connectable);
                    blockToNetwork.put(pos, network.getNetworkId());
                    recoveredCount++;
                } else {
                    // Create a new network for this block
                    EnergyNetwork newNetwork = createNetwork();
                    newNetwork.addBlock(pos, connectable);
                    recoveredCount++;
                }
            }
        }
        
        if (!startupMode && recoveredCount > 0) {
            Circuitmod.LOGGER.info("Global network recovery: {} blocks reconnected", recoveredCount);
        }
        
        return recoveredCount;
    }
    
    /**
     * Finds an adjacent network for a block position.
     * 
     * @param world The world
     * @param pos The block position
     * @return An adjacent network, or null if none found
     */
    private static EnergyNetwork findAdjacentNetwork(World world, BlockPos pos) {
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                EnergyNetwork network = connectable.getNetwork();
                if (network != null) {
                    return network;
                }
            }
        }
        return null;
    }
    
    /**
     * Gets all registered networks.
     * 
     * @return A copy of all registered networks
     */
    public static Collection<EnergyNetwork> getAllNetworks() {
        return new ArrayList<>(networksById.values());
    }
    
    /**
     * Gets statistics about all networks.
     * 
     * @return A string with network statistics
     */
    public static String getNetworkStats() {
        int totalNetworks = networksById.size();
        int totalBlocks = blockToNetwork.size();
        
        return String.format("Energy Networks: %d networks, %d total blocks", totalNetworks, totalBlocks);
    }
    
    /**
     * Clears all networks. This should be called when the world is unloaded.
     */
    public static void clearAllNetworks() {
        networksById.clear();
        blockToNetwork.clear();
        Circuitmod.LOGGER.info("Cleared all energy networks");
    }
    
    /**
     * Gets the number of registered networks.
     * 
     * @return The number of networks
     */
    public static int getNetworkCount() {
        return networksById.size();
    }
    
    /**
     * Gets the total number of blocks in all networks.
     * 
     * @return The total number of blocks
     */
    public static int getTotalBlockCount() {
        return blockToNetwork.size();
    }
}

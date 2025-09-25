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
        for (EnergyNetwork existingNetwork : networksById.values()) {
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
    
    /**
     * Handles network conflicts by merging adjacent networks at a specific position.
     * This is useful when placing blocks that connect multiple existing networks.
     * 
     * @param world The world
     * @param pos The position where networks might conflict
     */
    public static void deduplicateNetworks(World world, BlockPos pos) {
        if (world == null || world.isClient()) return;
        
        Set<EnergyNetwork> adjacentNetworks = new HashSet<>();
        
        // Check all adjacent positions for existing networks
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            EnergyNetwork network = getNetworkForBlock(neighborPos);
            if (network != null && network.isActive()) {
                adjacentNetworks.add(network);
            }
        }
        
        if (adjacentNetworks.size() > 1) {
            // Multiple networks found - merge them
            EnergyNetwork primary = adjacentNetworks.iterator().next();
            Set<EnergyNetwork> networksToMerge = new HashSet<>(adjacentNetworks);
            networksToMerge.remove(primary); // Don't merge primary with itself
            
            if (!startupMode) {
                Circuitmod.LOGGER.info("Found {} adjacent networks at {}, merging into network {}", 
                    adjacentNetworks.size(), pos, primary.getNetworkId());
            }
            
            for (EnergyNetwork network : networksToMerge) {
                if (network != primary && network.isActive()) {
                    mergeNetworks(primary, network);
                }
            }
        }
    }
    
    /**
     * Saves all current networks to persistent storage.
     * This should be called when the world is being saved.
     * 
     * @param world The server world
     */
    public static void saveNetworksToPersistentStorage(ServerWorld world) {
        if (world == null) return;
        
        EnergyNetworkSaveData saveData = EnergyNetworkSaveData.get(world);
        
        // Clear existing saved networks
        saveData.getNetworks().clear();
        
        // Save all current active networks
        for (EnergyNetwork network : networksById.values()) {
            if (network.isActive() && network.getSize() > 0) {
                saveData.saveNetwork(network);
            }
        }
        
        if (!startupMode) {
            Circuitmod.LOGGER.info("Saved {} energy networks to persistent storage", networksById.size());
        }
    }
    
    /**
     * Loads networks from persistent storage.
     * This should be called when the world is loading.
     * 
     * @param world The server world
     */
    public static void loadNetworksFromPersistentStorage(ServerWorld world) {
        if (world == null) return;
        
        EnergyNetworkSaveData saveData = EnergyNetworkSaveData.get(world);
        Map<String, EnergyNetwork> savedNetworks = saveData.getNetworks();
        
        // Register all saved networks
        for (EnergyNetwork network : savedNetworks.values()) {
            if (network.isActive()) {
                registerNetwork(network);
            }
        }
        
        if (!startupMode) {
            Circuitmod.LOGGER.info("Loaded {} energy networks from persistent storage", savedNetworks.size());
        }
    }
    
    /**
     * Cleans up persistent storage by removing inactive or empty networks.
     * 
     * @param world The server world
     */
    public static void cleanupPersistentStorage(ServerWorld world) {
        if (world == null) return;
        
        EnergyNetworkSaveData saveData = EnergyNetworkSaveData.get(world);
        Map<String, EnergyNetwork> savedNetworks = saveData.getNetworks();
        Set<String> networksToRemove = new HashSet<>();
        
        // Find networks that should be removed
        for (Map.Entry<String, EnergyNetwork> entry : savedNetworks.entrySet()) {
            EnergyNetwork network = entry.getValue();
            if (!network.isActive() || network.getSize() == 0) {
                networksToRemove.add(entry.getKey());
            }
        }
        
        // Remove inactive networks
        for (String networkId : networksToRemove) {
            saveData.removeNetwork(networkId);
        }
        
        if (!startupMode && !networksToRemove.isEmpty()) {
            Circuitmod.LOGGER.info("Cleaned up {} inactive networks from persistent storage", networksToRemove.size());
        }
    }
    
    /**
     * Standardized method for block entities to find and join a network.
     * This replaces the duplicate logic found in each block entity.
     * 
     * @param world The world
     * @param pos The position of the block entity
     * @param connectable The block entity implementing IPowerConnectable
     * @return The network the block joined or created, or null if failed
     */
    public static EnergyNetwork findAndJoinNetwork(World world, BlockPos pos, IPowerConnectable connectable) {
        if (world == null || world.isClient() || connectable == null) return null;
        
        // During startup, be conservative
        if (startupMode && world.getTime() < 100) {
            return createMinimalNetwork(pos, connectable);
        }
        
        // First check if we need to deduplicate networks at this position
        deduplicateNetworks(world, pos);
        
        // Look for adjacent networks to join
        EnergyNetwork targetNetwork = null;
        Set<EnergyNetwork> adjacentNetworks = new HashSet<>();
        
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            
            // Skip if chunk is not loaded
            if (!world.isChunkLoaded(neighborPos.getX() >> 4, neighborPos.getZ() >> 4)) continue;
            
            BlockEntity be = world.getBlockEntity(neighborPos);
            if (be instanceof IPowerConnectable) {
                IPowerConnectable neighborConnectable = (IPowerConnectable) be;
                
                // Check if both blocks can connect in this direction
                if (neighborConnectable.canConnectPower(dir.getOpposite()) && 
                    connectable.canConnectPower(dir)) {
                    
                    EnergyNetwork neighborNetwork = neighborConnectable.getNetwork();
                    if (neighborNetwork != null && neighborNetwork.isActive()) {
                        adjacentNetworks.add(neighborNetwork);
                        if (targetNetwork == null) {
                            targetNetwork = neighborNetwork;
                        }
                    }
                }
            }
        }
        
        // Check current network state
        EnergyNetwork currentNetwork = connectable.getNetwork();
        
        // If the block already has the target network, don't do anything
        if (currentNetwork != null && currentNetwork == targetNetwork) {
            if (!startupMode) {
                Circuitmod.LOGGER.debug("Block at {} already has the correct network {}, no action needed", 
                    pos, targetNetwork.getNetworkId());
            }
            return targetNetwork;
        }
        
        // Remove from current network if we have one and it's different from target
        if (currentNetwork != null && currentNetwork != targetNetwork) {
            if (!startupMode) {
                Circuitmod.LOGGER.info("Block at {} has a different network (current: {}, target: {}), removing from current network", 
                    pos, currentNetwork != null ? currentNetwork.getNetworkId() : "null", 
                    targetNetwork != null ? targetNetwork.getNetworkId() : "null");
            }
            removeBlockFromNetwork(pos);
        }
        
        // Join existing network or create new one
        if (targetNetwork != null) {
            // Join the existing network
            addBlockToNetwork(pos, targetNetwork);
            
            if (!startupMode) {
                Circuitmod.LOGGER.debug("Block at {} joined existing network {}", pos, targetNetwork.getNetworkId());
            }
            
            // If there were multiple adjacent networks, they should have been merged by deduplicateNetworks
            return targetNetwork;
        } else {
            // Create a new network
            EnergyNetwork newNetwork = createNetwork();
            addBlockToNetwork(pos, newNetwork);
            
            // Try to connect adjacent unconnected blocks
            connectAdjacentBlocks(world, pos, newNetwork, connectable);
            
            if (!startupMode) {
                Circuitmod.LOGGER.debug("Block at {} created new network {}", pos, newNetwork.getNetworkId());
            }
            
            return newNetwork;
        }
    }
    
    /**
     * Creates a minimal network during startup to avoid complex operations.
     * 
     * @param pos The position of the block
     * @param connectable The block entity
     * @return A minimal network or null if failed
     */
    private static EnergyNetwork createMinimalNetwork(BlockPos pos, IPowerConnectable connectable) {
        try {
            EnergyNetwork existingNetwork = connectable.getNetwork();
            if (existingNetwork != null && existingNetwork.isActive()) {
                return existingNetwork;
            }
            
            EnergyNetwork newNetwork = createNetwork();
            addBlockToNetwork(pos, newNetwork);
            return newNetwork;
        } catch (Exception e) {
            if (!startupMode) {
                Circuitmod.LOGGER.warn("Failed to create minimal network during startup: {}", e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Connects adjacent unconnected blocks to the specified network.
     * 
     * @param world The world
     * @param centerPos The center position to check around
     * @param network The network to add blocks to
     * @param centerConnectable The center block entity
     */
    private static void connectAdjacentBlocks(World world, BlockPos centerPos, EnergyNetwork network, IPowerConnectable centerConnectable) {
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            BlockPos neighborPos = centerPos.offset(dir);
            
            // Skip if chunk is not loaded
            if (!world.isChunkLoaded(neighborPos.getX() >> 4, neighborPos.getZ() >> 4)) continue;
            
            BlockEntity be = world.getBlockEntity(neighborPos);
            if (be instanceof IPowerConnectable) {
                IPowerConnectable neighborConnectable = (IPowerConnectable) be;
                
                // Only connect if:
                // 1. The neighbor doesn't already have a network
                // 2. Both blocks can connect in this direction
                if (neighborConnectable.getNetwork() == null &&
                    neighborConnectable.canConnectPower(dir.getOpposite()) && 
                    centerConnectable.canConnectPower(dir)) {
                    
                    addBlockToNetwork(neighborPos, network);
                    
                    if (!startupMode) {
                        Circuitmod.LOGGER.debug("Connected adjacent block at {} to network {}", neighborPos, network.getNetworkId());
                    }
                }
            }
        }
    }
    
    /**
     * Handles block placement in the network system.
     * Call this when a power connectable block is placed.
     * 
     * @param world The world
     * @param pos The position where the block was placed
     * @param connectable The block entity
     */
    public static void onBlockPlaced(World world, BlockPos pos, IPowerConnectable connectable) {
        if (world == null || world.isClient()) return;
        
        // Delay network connection slightly to ensure all block entities are loaded
        if (world instanceof ServerWorld) {
            ((ServerWorld) world).scheduleBlockTick(pos, world.getBlockState(pos).getBlock(), 2);
        }
        
        // Immediate connection for simpler cases
        findAndJoinNetwork(world, pos, connectable);
    }
    
    /**
     * Handles block removal from the network system.
     * Call this when a power connectable block is broken.
     * 
     * @param world The world
     * @param pos The position where the block was removed
     */
    public static void onBlockRemoved(World world, BlockPos pos) {
        if (world == null || world.isClient()) return;
        
        EnergyNetwork removedFromNetwork = removeBlockFromNetwork(pos);
        
        if (removedFromNetwork != null && removedFromNetwork.isActive()) {
            // Check if the network needs to be split
            recheckNetworkConnectivity(world, removedFromNetwork, pos);
        }
    }
    
    /**
     * Rechecks network connectivity after a block is removed.
     * This may split the network if removing the block disconnected parts of it.
     * 
     * @param world The world
     * @param network The network to check
     * @param removedPos The position that was removed
     */
    private static void recheckNetworkConnectivity(World world, EnergyNetwork network, BlockPos removedPos) {
        // For now, we'll do a simple validation. In the future, this could be enhanced
        // to detect if the network should be split into multiple networks.
        network.validateAndRepair(world);
        
        if (!startupMode) {
            Circuitmod.LOGGER.debug("Rechecked connectivity for network {} after removing block at {}", 
                network.getNetworkId(), removedPos);
        }
    }
}

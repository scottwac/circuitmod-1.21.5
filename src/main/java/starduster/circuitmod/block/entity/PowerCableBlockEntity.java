package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IPowerConnectable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * BlockEntity for the power cable that connects energy producers and consumers.
 */
public class PowerCableBlockEntity extends BlockEntity implements IPowerConnectable {
    private EnergyNetwork network;
    
    // Add cooldown for network merge checks to prevent spam
    private int mergeCheckCooldown = 0;
    private static final int MERGE_CHECK_COOLDOWN_MAX = 20; // Only check every 20 ticks (1 second)
    
    private boolean needsNetworkRefresh = false;
    
    // Chunk loading management
    private Set<ChunkPos> loadedChunks = new HashSet<>();
    private static final int CHUNK_LOAD_RADIUS = 1; // Load chunks in a 1 chunk radius around the cable
    
    public PowerCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CABLE_BLOCK_ENTITY, pos, state);
    }
    
    /**
     * Updates network connections when the block is placed or neighbors change.
     */
    public void updateNetworkConnections() {
        if (world == null || world.isClient()) {
            return;
        }
        
        // Circuitmod.LOGGER.info("Cable at " + pos + " is updating network connections");
        
        // Check surrounding blocks and either join an existing network or create a new one
        if (network == null) {
            // No network yet, so create or join
            // Circuitmod.LOGGER.info("Cable has no network, trying to find or create one");
            joinExistingNetworkOrCreateNew();
        } else {
            // We have a network, check if we need to merge with other networks
            // Circuitmod.LOGGER.info("Cable already has a network with " + network.getSize() + " blocks, checking for merges");
            checkAndMergeWithNeighboringNetworks();
        }
        
        // Update chunk loading after network changes
        updateChunkLoading();
    }
    
    /**
     * Called when the cable is removed, handles network splitting.
     */
    public void onRemoved() {
        if (world == null || world.isClient() || network == null) {
            return;
        }
        
        Circuitmod.LOGGER.info("Cable at " + pos + " removed. Handling network changes...");
        
        // Store connected blocks before removing this one (for notification)
        Set<BlockPos> connectedPositions = new HashSet<>(network.getConnectedBlockPositions());
        
        // Remove this block from the network
        network.removeBlock(pos);
        
        // If there are still blocks in the network, choose one to rebuild from
        if (network.getSize() > 0) {
            // Find an adjacent block that's still in the network to rebuild from
            BlockPos rebuildFrom = null;
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                BlockEntity be = world.getBlockEntity(neighborPos);
                
                if (be instanceof IPowerConnectable) {
                    IPowerConnectable connectable = (IPowerConnectable) be;
                    if (connectable.getNetwork() == network) {
                        // Found a block still in the network, rebuild from it
                        rebuildFrom = neighborPos;
                        break;
                    }
                }
            }
            
                if (rebuildFrom != null) {
                    // Get the disconnected blocks after rebuilding
                    Set<BlockPos> disconnectedPositions = network.rebuild(world, rebuildFrom);
                    
                    // Create new networks for disconnected blocks that are still valid
                    createNetworksForDisconnectedBlocks(disconnectedPositions);
                    
                    Circuitmod.LOGGER.info("Network " + network.getNetworkId() + " rebuilt from " + rebuildFrom);
                    
                    // Update chunk loading after network rebuild
                    updateChunkLoading();
                } else {
                // No blocks adjacent to the removed cable are in the network
                // We need to scan the entire network to find a valid block for rebuilding
                boolean foundRebuildBlock = false;
                for (BlockPos blockPos : connectedPositions) {
                    if (blockPos.equals(pos)) continue; // Skip the removed cable
                    
                    BlockEntity be = world.getBlockEntity(blockPos);
                    if (be instanceof IPowerConnectable) {
                        // Rebuild from this block
                        Set<BlockPos> disconnectedPositions = network.rebuild(world, blockPos);
                        createNetworksForDisconnectedBlocks(disconnectedPositions);
                        foundRebuildBlock = true;
                        Circuitmod.LOGGER.info("Network " + network.getNetworkId() + " rebuilt from non-adjacent block " + blockPos);
                        
                        // Update chunk loading after network rebuild
                        updateChunkLoading();
                        break;
                    }
                }
                
                if (!foundRebuildBlock) {
                    // If we can't find any valid block for rebuilding, the network is invalid
                    // Clear it entirely and create new networks for all disconnected blocks
                    createNetworksForDisconnectedBlocks(connectedPositions);
                    Circuitmod.LOGGER.info("Network " + network.getNetworkId() + " completely dissolved");
                }
            }
        }
        
        // Release chunk loading when cable is removed
        releaseChunkLoading();
    }
    
    /**
     * Manages chunk loading to keep chunks with cables loaded.
     */
    public void updateChunkLoading() {
        if (world == null || world.isClient() || !(world instanceof ServerWorld)) {
            return;
        }
        
        ServerWorld serverWorld = (ServerWorld) world;
        Set<ChunkPos> newLoadedChunks = new HashSet<>();
        
        // Calculate chunks that should be loaded around this cable
        ChunkPos cableChunk = new ChunkPos(pos);
        for (int x = -CHUNK_LOAD_RADIUS; x <= CHUNK_LOAD_RADIUS; x++) {
            for (int z = -CHUNK_LOAD_RADIUS; z <= CHUNK_LOAD_RADIUS; z++) {
                ChunkPos chunkPos = new ChunkPos(cableChunk.x + x, cableChunk.z + z);
                newLoadedChunks.add(chunkPos);
                
                // Force load the chunk if it's not already loaded
                if (!serverWorld.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                    serverWorld.setChunkForced(chunkPos.x, chunkPos.z, true);
                    Circuitmod.LOGGER.info("Forced chunk loading for cable at {}: chunk ({}, {})", pos, chunkPos.x, chunkPos.z);
                }
            }
        }
        
        // Release chunks that are no longer needed
        Set<ChunkPos> chunksToRelease = new HashSet<>(loadedChunks);
        chunksToRelease.removeAll(newLoadedChunks);
        
        for (ChunkPos chunkPos : chunksToRelease) {
            // Only release if no other cables in the network are keeping it loaded
            if (!isChunkNeededByNetwork(chunkPos)) {
                serverWorld.setChunkForced(chunkPos.x, chunkPos.z, false);
                Circuitmod.LOGGER.info("Released forced chunk loading: chunk ({}, {})", chunkPos.x, chunkPos.z);
            }
        }
        
        // Update our loaded chunks set
        loadedChunks = newLoadedChunks;
    }
    
    /**
     * Releases all chunk loading when this cable is removed.
     */
    private void releaseChunkLoading() {
        if (world == null || world.isClient() || !(world instanceof ServerWorld)) {
            return;
        }
        
        ServerWorld serverWorld = (ServerWorld) world;
        
        for (ChunkPos chunkPos : loadedChunks) {
            // Only release if no other cables in the network are keeping it loaded
            if (!isChunkNeededByNetwork(chunkPos)) {
                serverWorld.setChunkForced(chunkPos.x, chunkPos.z, false);
                Circuitmod.LOGGER.debug("Released forced chunk loading on removal: chunk ({}, {})", chunkPos.x, chunkPos.z);
            }
        }
        
        loadedChunks.clear();
    }
    
    /**
     * Gets the number of chunks currently being kept loaded by this cable.
     */
    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }
    
    /**
     * Gets the set of chunks currently being kept loaded by this cable.
     */
    public Set<ChunkPos> getLoadedChunks() {
        return new HashSet<>(loadedChunks);
    }
    
    /**
     * Checks if a chunk is still needed by other cables in the same network.
     */
    private boolean isChunkNeededByNetwork(ChunkPos chunkPos) {
        if (network == null) {
            return false;
        }
        
        // Check if any other cables in the network are in this chunk
        for (BlockPos networkPos : network.getConnectedBlockPositions()) {
            if (networkPos.equals(pos)) {
                continue; // Skip this cable
            }
            
            ChunkPos networkChunk = new ChunkPos(networkPos);
            if (networkChunk.equals(chunkPos)) {
                // Another cable in the network is in this chunk, so it's still needed
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Joins an existing network from a neighbor or creates a new one.
     */
    private void joinExistingNetworkOrCreateNew() {
        // Look for adjacent networks
        EnergyNetwork existingNetwork = null;
        
        Circuitmod.LOGGER.info("Looking for adjacent networks to " + pos);
        
        // First, collect all connectable neighbors for later use
        Map<BlockPos, IPowerConnectable> connectableNeighbors = new HashMap<>();
        
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                Circuitmod.LOGGER.info("Found connectable at " + neighborPos + ": " + be.getClass().getSimpleName());
                
                // Store this connectable for later use
                connectableNeighbors.put(neighborPos, connectable);
                
                EnergyNetwork neighborNetwork = connectable.getNetwork();
                
                if (neighborNetwork != null) {
                    Circuitmod.LOGGER.info("Neighbor has a network with " + neighborNetwork.getSize() + " blocks");
                    if (existingNetwork == null) {
                        existingNetwork = neighborNetwork;
                    } else if (existingNetwork != neighborNetwork) {
                        // Check if the neighbor's network is already merged (prevent infinite loops)
                        String neighborNetworkId = neighborNetwork.getNetworkId();
                        if (neighborNetworkId.startsWith("MERGED-")) {
                            if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                                Circuitmod.LOGGER.info("Found neighbor with already merged network at " + neighborPos + 
                                                      " (Network ID: " + neighborNetworkId + "), skipping merge");
                            }
                            continue;
                        }
                        
                        // Found multiple networks, they need to be merged
                        if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                            Circuitmod.LOGGER.info("Found multiple networks, merging");
                        }
                        existingNetwork.mergeWith(neighborNetwork);
                    }
                } else {
                    if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                        Circuitmod.LOGGER.info("Neighbor has no network yet");
                    }
                }
            }
        }
        
        if (existingNetwork != null) {
            // Join existing network
            existingNetwork.addBlock(pos, this);
            this.network = existingNetwork;
            if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                Circuitmod.LOGGER.info("Cable at " + pos + " joined existing network with " + existingNetwork.getSize() + " blocks");
            }
            
            // Also add any neighbors that don't have a network yet
            for (Map.Entry<BlockPos, IPowerConnectable> entry : connectableNeighbors.entrySet()) {
                IPowerConnectable connectable = entry.getValue();
                if (connectable.getNetwork() == null) {
                    if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                        Circuitmod.LOGGER.info("Adding previously unconnected neighbor at " + entry.getKey() + " to existing network");
                    }
                    existingNetwork.addBlock(entry.getKey(), connectable);
                }
            }
        } else {
            // Create new network
            this.network = new EnergyNetwork();
            this.network.addBlock(pos, this);
            if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                Circuitmod.LOGGER.info("Cable at " + pos + " created new network");
            }
            
            // Add all the connectable neighbors to our new network
            for (Map.Entry<BlockPos, IPowerConnectable> entry : connectableNeighbors.entrySet()) {
                if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                    Circuitmod.LOGGER.info("Adding neighbor at " + entry.getKey() + " to new network");
                }
                this.network.addBlock(entry.getKey(), entry.getValue());
            }
        }
        
        // Update chunk loading after network changes
        updateChunkLoading();
    }
    
    /**
     * Checks adjacent blocks for networks that need to be merged with this one.
     */
    private void checkAndMergeWithNeighboringNetworks() {
        if (mergeCheckCooldown > 0) {
            mergeCheckCooldown--;
            return;
        }

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                EnergyNetwork neighborNetwork = connectable.getNetwork();
                
                if (neighborNetwork != null && neighborNetwork != network) {
                    // Check if the neighbor's network is already merged (prevent infinite loops)
                    String neighborNetworkId = neighborNetwork.getNetworkId();
                    if (neighborNetworkId.startsWith("MERGED-")) {
                        if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                            Circuitmod.LOGGER.debug("Cable at " + pos + " found neighbor with already merged network at " + neighborPos + 
                                                  " (Network ID: " + neighborNetworkId + "), skipping merge");
                        }
                        continue;
                    }
                    
                    // Found different network, merge them
                    network.mergeWith(neighborNetwork);
                    if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                        Circuitmod.LOGGER.debug("Merged networks at " + pos);
                    }
                }
            }
        }
        mergeCheckCooldown = MERGE_CHECK_COOLDOWN_MAX; // Reset cooldown
        
        // Update chunk loading after potential network merges
        updateChunkLoading();
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save network data if we have a network
        if (network != null) {
            NbtCompound networkNbt = new NbtCompound();
            network.writeToNbt(networkNbt);
            nbt.put("energy_network", networkNbt);
        }
        
        // Save chunk loading data
        if (!loadedChunks.isEmpty()) {
            NbtCompound chunksNbt = new NbtCompound();
            chunksNbt.putInt("count", loadedChunks.size());
            int i = 0;
            for (ChunkPos chunkPos : loadedChunks) {
                chunksNbt.putInt("x" + i, chunkPos.x);
                chunksNbt.putInt("z" + i, chunkPos.z);
                i++;
            }
            nbt.put("loaded_chunks", chunksNbt);
        }
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load network data
        if (nbt.contains("energy_network")) {
            // Create a new network and load its data
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        needsNetworkRefresh = true;
        
        // Load chunk loading data
        loadedChunks.clear();
        if (nbt.contains("loaded_chunks")) {
            NbtCompound chunksNbt = nbt.getCompound("loaded_chunks").orElse(new NbtCompound());
            int count = chunksNbt.getInt("count").orElse(0);
            for (int i = 0; i < count; i++) {
                int x = chunksNbt.getInt("x" + i).orElse(0);
                int z = chunksNbt.getInt("z" + i).orElse(0);
                loadedChunks.add(new ChunkPos(x, z));
            }
        }
        
        // Initialize chunk loading when loaded from NBT
        if (world != null && !world.isClient()) {
            // Schedule chunk loading update for next tick to ensure world is fully loaded
            world.getServer().execute(() -> {
                if (world != null && !world.isClient()) {
                    updateChunkLoading();
                }
            });
        }
    }
    
    // IPowerConnectable implementation
    
    @Override
    public boolean canConnectPower(Direction side) {
        return true; // Cables can connect from any side
    }
    
    @Override
    public EnergyNetwork getNetwork() {
        return network;
    }
    
    @Override
    public void setNetwork(EnergyNetwork network) {
        this.network = network;
    }
    
    /**
     * Called every tick to update the network.
     */
    public static void tick(World world, BlockPos pos, BlockState state, PowerCableBlockEntity blockEntity) {
        if (world.isClient()) {
            return;
        }
        if (blockEntity.needsNetworkRefresh) {
            blockEntity.updateNetworkConnections();
            blockEntity.needsNetworkRefresh = false;
        }
        
        // Check if we have a network
        if (blockEntity.network != null) {
            // Process network energy transfers once per tick
            blockEntity.network.tick();
            
            // Check for network integrity every tick
            validateNetworkIntegrity(world, pos, blockEntity);
            
            // Check for neighbors that aren't in a network every 2 ticks (high frequency)
            if (world.getTime() % 10 == 0) {
                checkForUnconnectedNeighbors(world, pos, blockEntity);
            }
            
            // Periodically verify all network connections and remove invalid ones
            if (world.getTime() % 20 == 0) {
                validateNetworkConnections(world, blockEntity);
            }
            
            // Update chunk loading every 100 ticks (5 seconds) to ensure chunks stay loaded
            if (world.getTime() % 100 == 0) {
                blockEntity.updateChunkLoading();
            }
        } else {
            // If we don't have a network, try to establish one every tick
            blockEntity.updateNetworkConnections();
        }
    }
    
    /**
     * Validates the integrity of the network, ensuring all blocks are properly connected.
     */
    private static void validateNetworkIntegrity(World world, BlockPos pos, PowerCableBlockEntity cable) {
        if (cable.network == null) {
            return;
        }
        
        // Simple check - just verify our own connection
        // This is fast and runs every tick
        if (cable.getNetwork() != null && cable.getNetwork().getSize() > 0) {
            if (!cable.getNetwork().getConnectedBlockPositions().contains(pos)) {
                // Circuitmod.LOGGER.info("Cable at " + pos + " not found in its own network, reconnecting...");
                cable.updateNetworkConnections();
            }
        }
    }
    
    /**
     * Periodically validates all network connections, removing any invalid ones.
     */
    private static void validateNetworkConnections(World world, PowerCableBlockEntity cable) {
        if (cable.network == null) {
            return;
        }
        
        BlockPos pos = cable.getPos();
        Set<BlockPos> invalidPositions = new HashSet<>();
        Set<BlockPos> connectedPositions = cable.network.getConnectedBlockPositions();
        
        // Check for invalid connections (blocks that no longer exist or are no longer connectable)
        for (BlockPos blockPos : connectedPositions) {
            if (!world.isChunkLoaded(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
                continue; // Skip unloaded chunks
            }
            
            BlockEntity be = world.getBlockEntity(blockPos);
            
            // If block entity doesn't exist or is no longer a power connectable
            if (!(be instanceof IPowerConnectable)) {
                String networkId = cable.network != null ? cable.network.getNetworkId() : "NULL";
                Circuitmod.LOGGER.info("Found invalid block at " + blockPos + " in network " + networkId + ", scheduling removal from network tracking");
                invalidPositions.add(blockPos);
            } 
            // Or if it exists but has a different network
            else if (((IPowerConnectable) be).getNetwork() != cable.network) {
                Circuitmod.LOGGER.info("Block at " + blockPos + " has a different network, scheduling removal from network tracking");
                invalidPositions.add(blockPos);
            }
        }
        
        // Remove invalid positions from the network
        if (!invalidPositions.isEmpty()) {
            for (BlockPos invalidPos : invalidPositions) {
                cable.network.removeBlock(invalidPos);
                String networkId = cable.network != null ? cable.network.getNetworkId() : "NULL";
                Circuitmod.LOGGER.info("Removed reference to block at " + invalidPos + " from network " + networkId + " tracking");
            }
            
            // If we're still in the network after cleanup, rebuild from our position
            if (cable.network.getSize() > 0 && cable.network.getConnectedBlockPositions().contains(pos)) {
                cable.network.rebuild(world, pos);
                String networkId = cable.network != null ? cable.network.getNetworkId() : "NULL";
                Circuitmod.LOGGER.info("Rebuilt network " + networkId + " after updating internal tracking");
                
                // Update chunk loading after network rebuild
                cable.updateChunkLoading();
            }
        }
    }
    
    /**
     * Checks for neighboring blocks that should be connected to this network but aren't.
     */
    private static void checkForUnconnectedNeighbors(World world, BlockPos pos, PowerCableBlockEntity cable) {
        if (cable.network == null) {
            return;
        }
        
        // Use the cooldown to prevent spam
        if (cable.mergeCheckCooldown > 0) {
            return;
        }
        
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                
                // Debug the neighbor's current state
                Circuitmod.LOGGER.debug("Cable at " + pos + " checking neighbor at " + neighborPos + 
                                       ": hasNetwork=" + (connectable.getNetwork() != null) +
                                       ", canConnect=" + connectable.canConnectPower(dir.getOpposite()));
                
                // Check if the neighbor can connect to this side and doesn't have a network yet
                if (connectable.getNetwork() == null && 
                    connectable.canConnectPower(dir.getOpposite()) && 
                    cable.canConnectPower(dir)) {
                    
                    // Circuitmod.LOGGER.info("Cable at " + pos + " found unconnected neighbor at " + neighborPos);
                    
                    // Add the block to our network
                    cable.network.addBlock(neighborPos, connectable);
                    // Circuitmod.LOGGER.info("Added neighbor at " + neighborPos + " to network " + cable.network.getNetworkId());
                }
                // If the neighbor has a different network, merge them
                else if (connectable.getNetwork() != null && 
                         connectable.getNetwork() != cable.network && 
                         connectable.canConnectPower(dir.getOpposite()) && 
                         cable.canConnectPower(dir)) {
                    
                    // Check if the neighbor's network is already merged (prevent infinite loops)
                    String neighborNetworkId = connectable.getNetwork().getNetworkId();
                    if (neighborNetworkId.startsWith("MERGED-")) {
                        Circuitmod.LOGGER.debug("Cable at " + pos + " found neighbor with already merged network at " + neighborPos + 
                                              " (Network ID: " + neighborNetworkId + "), skipping merge");
                        continue;
                    }
                    
                    Circuitmod.LOGGER.info("Cable at " + pos + " found neighbor with different network at " + neighborPos + 
                                          " (Network ID: " + neighborNetworkId + ")");
                    
                    // Merge networks
                    cable.network.mergeWith(connectable.getNetwork());
                    Circuitmod.LOGGER.info("Merged networks at " + pos + ": " + 
                                          neighborNetworkId + " -> " + cable.network.getNetworkId());
                }
            }
        }
        
        // Reset cooldown after checking
        cable.mergeCheckCooldown = MERGE_CHECK_COOLDOWN_MAX;
        
        // Update chunk loading after potential network changes
        cable.updateChunkLoading();
    }
    
    /**
     * Creates new networks for disconnected blocks that are next to each other.
     * 
     * @param disconnectedPositions Positions of blocks that are disconnected from the network
     */
    private void createNetworksForDisconnectedBlocks(Set<BlockPos> disconnectedPositions) {
        // Remove the position of this cable from the set as it's being removed
        disconnectedPositions.remove(pos);
        
        // Make a copy for iterating
        Set<BlockPos> remainingPositions = new HashSet<>(disconnectedPositions);
        
        while (!remainingPositions.isEmpty()) {
            // Get the first position
            BlockPos startPos = remainingPositions.iterator().next();
            BlockEntity be = world.getBlockEntity(startPos);
            
            if (be instanceof IPowerConnectable) {
                // Create a new network
                EnergyNetwork newNetwork = new EnergyNetwork();
                Circuitmod.LOGGER.info("Created new network " + newNetwork.getNetworkId() + " for disconnected blocks");
                
                // Flood fill from this position to find all connected blocks
                Set<BlockPos> visited = new HashSet<>();
                floodFillDisconnected(startPos, remainingPositions, visited, newNetwork);
                
                // Remove all visited positions from the remaining set
                remainingPositions.removeAll(visited);
                
                // Update chunk loading for the new network
                updateChunkLoading();
            } else {
                // If there's no IPowerConnectable at this position, remove it
                remainingPositions.remove(startPos);
            }
        }
    }
    
    /**
     * Performs a flood fill on disconnected blocks to group them into new networks.
     * 
     * @param currentPos Current position to check
     * @param remainingPositions Set of positions still needing assignment
     * @param visited Set of positions already visited
     * @param newNetwork The new network to add blocks to
     */
    private void floodFillDisconnected(BlockPos currentPos, Set<BlockPos> remainingPositions, 
                                       Set<BlockPos> visited, EnergyNetwork newNetwork) {
        // Mark as visited
        visited.add(currentPos);
        
        // Add to new network
        BlockEntity be = world.getBlockEntity(currentPos);
        if (be instanceof IPowerConnectable) {
            IPowerConnectable connectable = (IPowerConnectable) be;
            connectable.setNetwork(null); // Clear old network reference
            newNetwork.addBlock(currentPos, connectable);
        }
        
        // Check neighbors that are in the disconnected set
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = currentPos.offset(dir);
            
            if (remainingPositions.contains(neighborPos) && !visited.contains(neighborPos)) {
                BlockEntity neighborBe = world.getBlockEntity(neighborPos);
                
                if (neighborBe instanceof IPowerConnectable) {
                    IPowerConnectable neighbor = (IPowerConnectable) neighborBe;
                    IPowerConnectable current = (IPowerConnectable) be;
                    
                    // Check if they can connect to each other
                    if (neighbor.canConnectPower(dir.getOpposite()) && current.canConnectPower(dir)) {
                        floodFillDisconnected(neighborPos, remainingPositions, visited, newNetwork);
                    }
                }
            }
        }
    }

    /**
     * Performs recovery operations for this cable if it's in an inconsistent state.
     * This is called during world loading to recover from crashes.
     * 
     * @return true if recovery was performed, false if the cable is healthy
     */
    public boolean performRecovery() {
        if (world == null) return false;
        
        boolean wasRecovered = false;
        
        // Check if our network reference is valid
        if (network != null) {
            // Check if the network is in a valid state
            if (!network.isActive()) {
                if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                    Circuitmod.LOGGER.warn("Cable at {} has inactive network {}, clearing reference", pos, network.getNetworkId());
                }
                network = null;
                wasRecovered = true;
            } else {
                // Check if we're still in the network's connected blocks
                if (!network.getConnectedBlockPositions().contains(pos)) {
                    if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                        Circuitmod.LOGGER.warn("Cable at {} not found in network {}, clearing reference", pos, network.getNetworkId());
                    }
                    network = null;
                    wasRecovered = true;
                }
            }
        }
        
        // If we don't have a network, try to join one
        if (network == null) {
            if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
                Circuitmod.LOGGER.info("Cable at {} has no network, attempting to join or create one", pos);
            }
            joinExistingNetworkOrCreateNew();
            wasRecovered = true;
        }
        
        // Update chunk loading after recovery operations
        if (wasRecovered) {
            updateChunkLoading();
        }
        
        return wasRecovered;
    }
} 
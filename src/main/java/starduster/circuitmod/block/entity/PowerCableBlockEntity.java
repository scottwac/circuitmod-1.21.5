package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
        
        Circuitmod.LOGGER.info("Cable at " + pos + " is updating network connections");
        
        // Check surrounding blocks and either join an existing network or create a new one
        if (network == null) {
            // No network yet, so create or join
            Circuitmod.LOGGER.info("Cable has no network, trying to find or create one");
            joinExistingNetworkOrCreateNew();
        } else {
            // We have a network, check if we need to merge with other networks
            Circuitmod.LOGGER.info("Cable already has a network with " + network.getSize() + " blocks, checking for merges");
            checkAndMergeWithNeighboringNetworks();
        }
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
                        // Found multiple networks, they need to be merged
                        Circuitmod.LOGGER.info("Found multiple networks, merging");
                        existingNetwork.mergeWith(neighborNetwork);
                    }
                } else {
                    Circuitmod.LOGGER.info("Neighbor has no network yet");
                }
            }
        }
        
        if (existingNetwork != null) {
            // Join existing network
            existingNetwork.addBlock(pos, this);
            this.network = existingNetwork;
            Circuitmod.LOGGER.info("Cable at " + pos + " joined existing network with " + existingNetwork.getSize() + " blocks");
            
            // Also add any neighbors that don't have a network yet
            for (Map.Entry<BlockPos, IPowerConnectable> entry : connectableNeighbors.entrySet()) {
                IPowerConnectable connectable = entry.getValue();
                if (connectable.getNetwork() == null) {
                    Circuitmod.LOGGER.info("Adding previously unconnected neighbor at " + entry.getKey() + " to existing network");
                    existingNetwork.addBlock(entry.getKey(), connectable);
                }
            }
        } else {
            // Create new network
            this.network = new EnergyNetwork();
            this.network.addBlock(pos, this);
            Circuitmod.LOGGER.info("Cable at " + pos + " created new network");
            
            // Add all the connectable neighbors to our new network
            for (Map.Entry<BlockPos, IPowerConnectable> entry : connectableNeighbors.entrySet()) {
                Circuitmod.LOGGER.info("Adding neighbor at " + entry.getKey() + " to new network");
                this.network.addBlock(entry.getKey(), entry.getValue());
            }
        }
    }
    
    /**
     * Checks adjacent blocks for networks that need to be merged with this one.
     */
    private void checkAndMergeWithNeighboringNetworks() {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                EnergyNetwork neighborNetwork = connectable.getNetwork();
                
                if (neighborNetwork != null && neighborNetwork != network) {
                    // Found different network, merge them
                    network.mergeWith(neighborNetwork);
                    Circuitmod.LOGGER.debug("Merged networks at " + pos);
                }
            }
        }
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
        
        // Check if we have a network
        if (blockEntity.network != null) {
            // Process network energy transfers once per tick
            blockEntity.network.tick();
            
            // Check for network integrity every tick
            validateNetworkIntegrity(world, pos, blockEntity);
            
            // Check for neighbors that aren't in a network every 2 ticks (high frequency)
            if (world.getTime() % 2 == 0) {
                checkForUnconnectedNeighbors(world, pos, blockEntity);
            }
            
            // Periodically verify all network connections and remove invalid ones
            if (world.getTime() % 10 == 0) {
                validateNetworkConnections(world, blockEntity);
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
                Circuitmod.LOGGER.info("Cable at " + pos + " not found in its own network, reconnecting...");
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
                Circuitmod.LOGGER.info("Found invalid block at " + blockPos + " in network " + cable.network.getNetworkId() + ", scheduling removal from network tracking");
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
                Circuitmod.LOGGER.info("Removed reference to block at " + invalidPos + " from network " + cable.network.getNetworkId() + " tracking");
            }
            
            // If we're still in the network after cleanup, rebuild from our position
            if (cable.network.getSize() > 0 && cable.network.getConnectedBlockPositions().contains(pos)) {
                cable.network.rebuild(world, pos);
                Circuitmod.LOGGER.info("Rebuilt network " + cable.network.getNetworkId() + " after updating internal tracking");
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
                    
                    Circuitmod.LOGGER.info("Cable at " + pos + " found unconnected neighbor at " + neighborPos);
                    
                    // Add the block to our network
                    cable.network.addBlock(neighborPos, connectable);
                    Circuitmod.LOGGER.info("Added neighbor at " + neighborPos + " to network " + cable.network.getNetworkId());
                }
                // If the neighbor has a different network, merge them
                else if (connectable.getNetwork() != null && 
                         connectable.getNetwork() != cable.network && 
                         connectable.canConnectPower(dir.getOpposite()) && 
                         cable.canConnectPower(dir)) {
                    
                    Circuitmod.LOGGER.info("Cable at " + pos + " found neighbor with different network at " + neighborPos + 
                                          " (Network ID: " + connectable.getNetwork().getNetworkId() + ")");
                    
                    // Merge networks
                    cable.network.mergeWith(connectable.getNetwork());
                    Circuitmod.LOGGER.info("Merged networks at " + pos + ": " + 
                                          connectable.getNetwork().getNetworkId() + " -> " + cable.network.getNetworkId());
                }
            }
        }
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
} 
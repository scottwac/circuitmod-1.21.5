package starduster.circuitmod.item.network;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;

/**
 * Global manager for all item networks in the world.
 * Handles network creation, merging, and splitting.
 */
public class ItemNetworkManager {
    private static final Map<String, ItemNetwork> networks = new HashMap<>();
    private static final Map<BlockPos, ItemNetwork> pipeToNetwork = new HashMap<>();
    
    /**
     * Creates a new item network.
     */
    public static ItemNetwork createNetwork(World world) {
        ItemNetwork network = new ItemNetwork(world);
        networks.put(network.getNetworkId(), network);
        return network;
    }
    
    /**
     * Gets a network by ID.
     */
    public static ItemNetwork getNetwork(String networkId) {
        return networks.get(networkId);
    }
    
    /**
     * Gets the network that a pipe belongs to.
     */
    public static ItemNetwork getNetworkForPipe(BlockPos pipePos) {
        return pipeToNetwork.get(pipePos);
    }
    
    /**
     * Removes a network.
     */
    public static void removeNetwork(String networkId) {
        ItemNetwork removed = networks.remove(networkId);
        if (removed != null) {
            // Remove all pipe mappings for this network
            Set<BlockPos> pipesToRemove = new HashSet<>();
            for (Map.Entry<BlockPos, ItemNetwork> entry : pipeToNetwork.entrySet()) {
                if (entry.getValue() == removed) {
                    pipesToRemove.add(entry.getKey());
                }
            }
            for (BlockPos pipe : pipesToRemove) {
                pipeToNetwork.remove(pipe);
            }
            
            Circuitmod.LOGGER.info("Removed item network: " + networkId);
        }
    }
    
    /**
     * Connects a pipe to an item network, creating or joining networks as needed.
     */
    public static void connectPipe(World world, BlockPos pipePos) {
        if (world.isClient()) {
            return;
        }
        
        BlockState pipeState = world.getBlockState(pipePos);
        net.minecraft.block.Block pipeBlock = pipeState.getBlock();
        
        Circuitmod.LOGGER.info("[ITEM-NETWORK-CONNECT] Attempting to connect pipe at {} to network (block: {})", pipePos, pipeBlock.getName().getString());
        
        // Find existing networks this pipe can connect to
        Set<ItemNetwork> connectableNetworks = findConnectableNetworks(world, pipePos);
        
        Circuitmod.LOGGER.info("[ITEM-NETWORK-CONNECT] Found {} connectable networks for pipe at {}", connectableNetworks.size(), pipePos);
        
        if (connectableNetworks.isEmpty()) {
            // No existing networks, create a new one
            ItemNetwork newNetwork = createNetwork(world);
            newNetwork.addPipe(pipePos);
            pipeToNetwork.put(pipePos, newNetwork);
            Circuitmod.LOGGER.info("[ITEM-NETWORK-CONNECT] Created new item network {} for pipe at {}", 
                newNetwork.getNetworkId(), pipePos);
        } else if (connectableNetworks.size() == 1) {
            // Connect to the existing network
            ItemNetwork network = connectableNetworks.iterator().next();
            network.addPipe(pipePos);
            pipeToNetwork.put(pipePos, network);
            Circuitmod.LOGGER.info("[ITEM-NETWORK-CONNECT] Connected pipe at {} to existing network {}", 
                pipePos, network.getNetworkId());
        } else {
            // Multiple networks, need to merge them
            Circuitmod.LOGGER.info("[ITEM-NETWORK-CONNECT] Merging {} networks for pipe at {}", connectableNetworks.size(), pipePos);
            mergeNetworks(connectableNetworks, pipePos);
        }
    }
    
    /**
     * Disconnects a pipe from its network, potentially splitting the network.
     */
    public static void disconnectPipe(World world, BlockPos pipePos) {
        if (world.isClient()) {
            return;
        }
        
        ItemNetwork network = pipeToNetwork.remove(pipePos);
        if (network == null) {
            return;
        }
        
        String networkId = network.getNetworkId();
        Circuitmod.LOGGER.debug("Disconnecting pipe at {} from network {}", pipePos, networkId);
        
        // Remove the pipe from the network
        network.removePipe(pipePos);
        
        // If the network is now empty, remove it entirely
        if (network.isEmpty()) {
            removeNetwork(networkId);
            return;
        }
        
        // Check if the network needs to be split
        Set<BlockPos> remainingPipes = network.getPipes();
        if (!remainingPipes.isEmpty()) {
            // Check if all remaining pipes are still connected
            checkNetworkConnectivity(world, network);
        }
    }
    
    /**
     * Finds networks that a new pipe can connect to.
     */
    private static Set<ItemNetwork> findConnectableNetworks(World world, BlockPos pipePos) {
        Set<ItemNetwork> connectableNetworks = new HashSet<>();
        
        Circuitmod.LOGGER.info("[ITEM-NETWORK-FIND] Checking for connectable networks around pipe at {}", pipePos);
        
        // Check all adjacent positions for other pipes
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            BlockPos neighborPos = pipePos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            Block neighborBlock = neighborState.getBlock();
            
            Circuitmod.LOGGER.info("[ITEM-NETWORK-FIND] Checking {} at {}: block={}, isBasePipeBlock={}", 
                dir, neighborPos, neighborBlock.getName().getString(), 
                neighborBlock instanceof starduster.circuitmod.block.BasePipeBlock);
            
            // Check if there's another pipe that can connect (any pipe type)
            if (neighborBlock instanceof starduster.circuitmod.block.BasePipeBlock) {
                Circuitmod.LOGGER.info("[ITEM-NETWORK-FIND] Found pipe at {} in direction {}", neighborPos, dir);
                ItemNetwork neighborNetwork = pipeToNetwork.get(neighborPos);
                if (neighborNetwork != null) {
                    connectableNetworks.add(neighborNetwork);
                    Circuitmod.LOGGER.info("[ITEM-NETWORK-FIND] Found connectable network {} at {}", 
                        neighborNetwork.getNetworkId(), neighborPos);
                } else {
                    Circuitmod.LOGGER.info("[ITEM-NETWORK-FIND] Pipe at {} has no network", neighborPos);
                }
            } else {
                Circuitmod.LOGGER.info("[ITEM-NETWORK-FIND] No pipe at {} in direction {} (block: {})", 
                    neighborPos, dir, neighborBlock.getName().getString());
            }
        }
        
        Circuitmod.LOGGER.info("[ITEM-NETWORK-FIND] Found {} connectable networks for pipe at {}", connectableNetworks.size(), pipePos);
        return connectableNetworks;
    }
    
    /**
     * Merges multiple networks into one.
     */
    private static void mergeNetworks(Set<ItemNetwork> networksToMerge, BlockPos newPipePos) {
        if (networksToMerge.isEmpty()) {
            return;
        }
        
        // Use the largest network as the base
        ItemNetwork primaryNetwork = networksToMerge.stream()
            .max(Comparator.comparingInt(ItemNetwork::getSize))
            .orElse(null);
        
        if (primaryNetwork == null) {
            return;
        }
        
        Circuitmod.LOGGER.info("Merging {} networks into {}", 
            networksToMerge.size(), primaryNetwork.getNetworkId());
        
        // Add the new pipe to the primary network
        primaryNetwork.addPipe(newPipePos);
        pipeToNetwork.put(newPipePos, primaryNetwork);
        
        // Merge all other networks into the primary
        for (ItemNetwork networkToMerge : networksToMerge) {
            if (networkToMerge == primaryNetwork) {
                continue;
            }
            
            // Transfer all pipes from the old network to the primary
            Set<BlockPos> pipesToTransfer = new HashSet<>(networkToMerge.getPipes());
            primaryNetwork.merge(networkToMerge);
            
            // Update pipe mappings
            for (BlockPos pipePos : pipesToTransfer) {
                pipeToNetwork.put(pipePos, primaryNetwork);
            }
            
            // Remove the old network
            removeNetwork(networkToMerge.getNetworkId());
        }
    }
    
    /**
     * Checks if a network is still connected after a pipe was removed.
     */
    private static void checkNetworkConnectivity(World world, ItemNetwork network) {
        Set<BlockPos> pipes = network.getPipes();
        if (pipes.isEmpty()) {
            return;
        }
        
        // Use flood-fill to check connectivity
        BlockPos startPipe = pipes.iterator().next();
        Set<BlockPos> reachable = new HashSet<>();
        floodFillPipes(world, startPipe, reachable, pipes);
        
        // If not all pipes are reachable, we need to split the network
        if (reachable.size() < pipes.size()) {
            Set<BlockPos> unreachable = new HashSet<>(pipes);
            unreachable.removeAll(reachable);
            
            // Create new networks for unreachable components
            createNetworksForDisconnectedPipes(world, unreachable);
            
            // Remove unreachable pipes from current network
            for (BlockPos unreachablePipe : unreachable) {
                network.removePipe(unreachablePipe);
                pipeToNetwork.remove(unreachablePipe);
            }
        }
    }
    
    /**
     * Creates networks for pipes that were disconnected during a network split.
     */
    private static void createNetworksForDisconnectedPipes(World world, Set<BlockPos> disconnectedPipes) {
        Set<BlockPos> unprocessed = new HashSet<>(disconnectedPipes);
        
        while (!unprocessed.isEmpty()) {
            BlockPos startPos = unprocessed.iterator().next();
            
            // Check if this pipe still exists
            if (world.getBlockState(startPos).getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                // Create a new network starting from this position
                ItemNetwork newNetwork = createNetwork(world);
                
                // Use flood-fill to discover all connected pipes
                Set<BlockPos> connectedPipes = new HashSet<>();
                floodFillPipes(world, startPos, connectedPipes, unprocessed);
                
                // Add all connected pipes to the new network
                for (BlockPos connectedPos : connectedPipes) {
                    if (world.getBlockState(connectedPos).getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                        newNetwork.addPipe(connectedPos);
                        pipeToNetwork.put(connectedPos, newNetwork);
                    }
                }
                
                Circuitmod.LOGGER.info("Created new network {} for {} disconnected pipes", 
                    newNetwork.getNetworkId(), connectedPipes.size());
                
                // Remove processed pipes
                unprocessed.removeAll(connectedPipes);
            } else {
                // Pipe no longer exists, just remove it
                unprocessed.remove(startPos);
            }
        }
    }
    
    /**
     * Flood-fill helper for finding connected pipes.
     */
    private static void floodFillPipes(World world, BlockPos pos, Set<BlockPos> visited, Set<BlockPos> availablePipes) {
        if (visited.contains(pos) || !availablePipes.contains(pos)) {
            return;
        }
        
        visited.add(pos);
        
        if (world.getBlockState(pos).getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
            // Check neighboring pipes
            for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                if (world.getBlockState(neighborPos).getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                    floodFillPipes(world, neighborPos, visited, availablePipes);
                }
            }
        }
    }
    
    /**
     * Gets all networks (for debugging/monitoring).
     */
    public static Collection<ItemNetwork> getAllNetworks() {
        return new ArrayList<>(networks.values());
    }
    
    /**
     * Gets statistics about all networks.
     */
    public static String getNetworkStats() {
        int totalNetworks = networks.size();
        int totalPipes = networks.values().stream().mapToInt(ItemNetwork::getSize).sum();
        
        return String.format("Item Networks: %d networks, %d total pipes", totalNetworks, totalPipes);
    }
    
    /**
     * Clears all networks (for world unload/restart).
     */
    public static void clearAllNetworks() {
        networks.clear();
        pipeToNetwork.clear();
        Circuitmod.LOGGER.info("Cleared all item networks");
    }
} 
package starduster.circuitmod.item.network;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import starduster.circuitmod.block.entity.ItemPipeBlockEntity;
import starduster.circuitmod.block.entity.SortingPipeBlockEntity;
import starduster.circuitmod.block.entity.OutputPipeBlockEntity;

/**
 * Simplified network manager - only handles pipe connections and network discovery.
 * No complex routing logic since pipes handle movement themselves.
 */
public class ItemNetworkManager {
    private static final Map<String, ItemNetwork> networks = new HashMap<>();
    public static final Map<BlockPos, ItemNetwork> pipeToNetwork = new HashMap<>();
    
    /**
     * Creates a new item network.
     */
    public static ItemNetwork createNetwork(World world) {
        ItemNetwork network = new ItemNetwork(world);
        networks.put(network.getNetworkId(), network);
        return network;
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
            
            Circuitmod.LOGGER.debug("Removed item network: " + networkId);
        }
    }
    
    /**
     * Connects a pipe to an existing network or creates a new one.
     */
    public static void connectPipe(World world, BlockPos pipePos) {
        if (world.isClient()) return;
        
        // Verify this is actually a pipe
        BlockEntity pipeBlock = world.getBlockEntity(pipePos);
        if (!(pipeBlock instanceof ItemPipeBlockEntity || 
              pipeBlock instanceof SortingPipeBlockEntity || 
              pipeBlock instanceof OutputPipeBlockEntity)) {
            return;
        }
        
        // Find all connectable networks around this pipe
        Set<ItemNetwork> connectableNetworks = findConnectableNetworks(world, pipePos);
        
        if (connectableNetworks.isEmpty()) {
            // No existing networks found, create a new one
            ItemNetwork newNetwork = createNetwork(world);
            newNetwork.addPipe(pipePos);
            
            Circuitmod.LOGGER.debug("[NETWORK-CONNECT] Created new network {} for pipe at {}",
                newNetwork.getNetworkId(), pipePos);
        } else if (connectableNetworks.size() == 1) {
            // Found exactly one network, join it
            ItemNetwork network = connectableNetworks.iterator().next();
            network.addPipe(pipePos);
            
            Circuitmod.LOGGER.debug("[NETWORK-CONNECT] Connected pipe at {} to network {}",
                pipePos, network.getNetworkId());
        } else {
            // Found multiple networks, merge them
            mergeNetworks(connectableNetworks, pipePos);
            
            Circuitmod.LOGGER.debug("[NETWORK-CONNECT] Merged {} networks for pipe at {}", 
                connectableNetworks.size(), pipePos);
        }
    }
    
    /**
     * Disconnects a pipe from its network.
     */
    public static void disconnectPipe(World world, BlockPos pipePos) {
        if (world.isClient()) return;
        
        ItemNetwork network = pipeToNetwork.remove(pipePos);
        if (network == null) return;
        
        String networkId = network.getNetworkId();
        network.removePipe(pipePos);
        
        // If the network is now empty, remove it entirely
        if (network.isEmpty()) {
            removeNetwork(networkId);
            return;
        }
        
        // Check if the network needs to be split
        checkAndSplitNetwork(world, network);
    }
    
    /**
     * Finds networks that a new pipe can connect to.
     */
    private static Set<ItemNetwork> findConnectableNetworks(World world, BlockPos pipePos) {
        Set<ItemNetwork> connectableNetworks = new HashSet<>();
        
        // Check all adjacent positions for other pipes
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            BlockPos neighborPos = pipePos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            
            // Check if there's another pipe that can connect
            if (neighborState.getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                ItemNetwork neighborNetwork = pipeToNetwork.get(neighborPos);
                if (neighborNetwork != null) {
                    connectableNetworks.add(neighborNetwork);
                }
            }
        }
        
        return connectableNetworks;
    }
    
    /**
     * Merges multiple networks into one.
     */
    private static void mergeNetworks(Set<ItemNetwork> networksToMerge, BlockPos newPipePos) {
        if (networksToMerge.isEmpty()) return;
        
        // Use the largest network as the base
        ItemNetwork primaryNetwork = networksToMerge.stream()
            .max(Comparator.comparingInt(ItemNetwork::getSize))
            .orElse(null);
        
        if (primaryNetwork == null) return;
        
        // Add the new pipe to the primary network
        primaryNetwork.addPipe(newPipePos);
        
        // Merge all other networks into the primary
        for (ItemNetwork networkToMerge : networksToMerge) {
            if (networkToMerge == primaryNetwork) continue;
            
            // Transfer all pipes from the old network to the primary
            Set<BlockPos> pipesToTransfer = new HashSet<>(networkToMerge.getPipes());
            primaryNetwork.merge(networkToMerge);
            
            // Remove the old network
            removeNetwork(networkToMerge.getNetworkId());
        }
        
        Circuitmod.LOGGER.debug("Merged {} networks into {}", 
            networksToMerge.size(), primaryNetwork.getNetworkId());
    }
    
    /**
     * Checks if a network needs to be split after a pipe removal.
     */
    private static void checkAndSplitNetwork(World world, ItemNetwork network) {
        Set<BlockPos> pipes = network.getPipes();
        if (pipes.isEmpty()) return;
        
        // Use flood-fill to check connectivity
        BlockPos startPipe = pipes.iterator().next();
        Set<BlockPos> reachable = new HashSet<>();
        floodFillPipes(world, startPipe, reachable, pipes);
        
        // If not all pipes are reachable, we need to split the network
        if (reachable.size() < pipes.size()) {
            Set<BlockPos> unreachable = new HashSet<>(pipes);
            unreachable.removeAll(reachable);
            
            // Remove unreachable pipes from current network
            for (BlockPos unreachablePipe : unreachable) {
                network.removePipe(unreachablePipe);
                pipeToNetwork.remove(unreachablePipe);
            }
            
            // Create new networks for disconnected pipe groups
            createNetworksForDisconnectedPipes(world, unreachable);
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
                    }
                }
                
                // Remove processed pipes
                unprocessed.removeAll(connectedPipes);
                
                Circuitmod.LOGGER.debug("Created new network {} for {} disconnected pipes", 
                    newNetwork.getNetworkId(), connectedPipes.size());
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
    public static String getNetworkStats() {package starduster.circuitmod.item.network;

        import net.minecraft.util.math.BlockPos;
        import net.minecraft.world.World;
        import starduster.circuitmod.Circuitmod;
        
        import java.util.*;
        import net.minecraft.block.BlockState;
        import net.minecraft.block.Block;
        import net.minecraft.block.entity.BlockEntity;
        import starduster.circuitmod.block.entity.ItemPipeBlockEntity;
        import starduster.circuitmod.block.entity.SortingPipeBlockEntity;
        import starduster.circuitmod.block.entity.OutputPipeBlockEntity;
        
        /**
         * Simplified network manager - only handles pipe connections and network discovery.
         * No complex routing logic since pipes handle movement themselves.
         */
        public class ItemNetworkManager {
            private static final Map<String, ItemNetwork> networks = new HashMap<>();
            public static final Map<BlockPos, ItemNetwork> pipeToNetwork = new HashMap<>();
            
            /**
             * Creates a new item network.
             */
            public static ItemNetwork createNetwork(World world) {
                ItemNetwork network = new ItemNetwork(world);
                networks.put(network.getNetworkId(), network);
                return network;
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
                    
                    Circuitmod.LOGGER.debug("Removed item network: " + networkId);
                }
            }
            
            /**
             * Connects a pipe to an existing network or creates a new one.
             */
            public static void connectPipe(World world, BlockPos pipePos) {
                if (world.isClient()) return;
                
                // Verify this is actually a pipe
                BlockEntity pipeBlock = world.getBlockEntity(pipePos);
                if (!(pipeBlock instanceof ItemPipeBlockEntity || 
                      pipeBlock instanceof SortingPipeBlockEntity || 
                      pipeBlock instanceof OutputPipeBlockEntity)) {
                    return;
                }
                
                // Find all connectable networks around this pipe
                Set<ItemNetwork> connectableNetworks = findConnectableNetworks(world, pipePos);
                
                if (connectableNetworks.isEmpty()) {
                    // No existing networks found, create a new one
                    ItemNetwork newNetwork = createNetwork(world);
                    newNetwork.addPipe(pipePos);
                    
                    Circuitmod.LOGGER.debug("[NETWORK-CONNECT] Created new network {} for pipe at {}",
                        newNetwork.getNetworkId(), pipePos);
                } else if (connectableNetworks.size() == 1) {
                    // Found exactly one network, join it
                    ItemNetwork network = connectableNetworks.iterator().next();
                    network.addPipe(pipePos);
                    
                    Circuitmod.LOGGER.debug("[NETWORK-CONNECT] Connected pipe at {} to network {}",
                        pipePos, network.getNetworkId());
                } else {
                    // Found multiple networks, merge them
                    mergeNetworks(connectableNetworks, pipePos);
                    
                    Circuitmod.LOGGER.debug("[NETWORK-CONNECT] Merged {} networks for pipe at {}", 
                        connectableNetworks.size(), pipePos);
                }
            }
            
            /**
             * Disconnects a pipe from its network.
             */
            public static void disconnectPipe(World world, BlockPos pipePos) {
                if (world.isClient()) return;
                
                ItemNetwork network = pipeToNetwork.remove(pipePos);
                if (network == null) return;
                
                String networkId = network.getNetworkId();
                network.removePipe(pipePos);
                
                // If the network is now empty, remove it entirely
                if (network.isEmpty()) {
                    removeNetwork(networkId);
                    return;
                }
                
                // Check if the network needs to be split
                checkAndSplitNetwork(world, network);
            }
            
            /**
             * Finds networks that a new pipe can connect to.
             */
            private static Set<ItemNetwork> findConnectableNetworks(World world, BlockPos pipePos) {
                Set<ItemNetwork> connectableNetworks = new HashSet<>();
                
                // Check all adjacent positions for other pipes
                for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                    BlockPos neighborPos = pipePos.offset(dir);
                    BlockState neighborState = world.getBlockState(neighborPos);
                    
                    // Check if there's another pipe that can connect
                    if (neighborState.getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                        ItemNetwork neighborNetwork = pipeToNetwork.get(neighborPos);
                        if (neighborNetwork != null) {
                            connectableNetworks.add(neighborNetwork);
                        }
                    }
                }
                
                return connectableNetworks;
            }
            
            /**
             * Merges multiple networks into one.
             */
            private static void mergeNetworks(Set<ItemNetwork> networksToMerge, BlockPos newPipePos) {
                if (networksToMerge.isEmpty()) return;
                
                // Use the largest network as the base
                ItemNetwork primaryNetwork = networksToMerge.stream()
                    .max(Comparator.comparingInt(ItemNetwork::getSize))
                    .orElse(null);
                
                if (primaryNetwork == null) return;
                
                // Add the new pipe to the primary network
                primaryNetwork.addPipe(newPipePos);
                
                // Merge all other networks into the primary
                for (ItemNetwork networkToMerge : networksToMerge) {
                    if (networkToMerge == primaryNetwork) continue;
                    
                    // Transfer all pipes from the old network to the primary
                    Set<BlockPos> pipesToTransfer = new HashSet<>(networkToMerge.getPipes());
                    primaryNetwork.merge(networkToMerge);
                    
                    // Remove the old network
                    removeNetwork(networkToMerge.getNetworkId());
                }
                
                Circuitmod.LOGGER.debug("Merged {} networks into {}", 
                    networksToMerge.size(), primaryNetwork.getNetworkId());
            }
            
            /**
             * Checks if a network needs to be split after a pipe removal.
             */
            private static void checkAndSplitNetwork(World world, ItemNetwork network) {
                Set<BlockPos> pipes = network.getPipes();
                if (pipes.isEmpty()) return;
                
                // Use flood-fill to check connectivity
                BlockPos startPipe = pipes.iterator().next();
                Set<BlockPos> reachable = new HashSet<>();
                floodFillPipes(world, startPipe, reachable, pipes);
                
                // If not all pipes are reachable, we need to split the network
                if (reachable.size() < pipes.size()) {
                    Set<BlockPos> unreachable = new HashSet<>(pipes);
                    unreachable.removeAll(reachable);
                    
                    // Remove unreachable pipes from current network
                    for (BlockPos unreachablePipe : unreachable) {
                        network.removePipe(unreachablePipe);
                        pipeToNetwork.remove(unreachablePipe);
                    }
                    
                    // Create new networks for disconnected pipe groups
                    createNetworksForDisconnectedPipes(world, unreachable);
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
                            }
                        }
                        
                        // Remove processed pipes
                        unprocessed.removeAll(connectedPipes);
                        
                        Circuitmod.LOGGER.debug("Created new network {} for {} disconnected pipes", 
                            newNetwork.getNetworkId(), connectedPipes.size());
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
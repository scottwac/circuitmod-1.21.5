package starduster.circuitmod.item.network;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

import java.util.*;

/**
 * Simplified ItemNetwork - Just tracks connected pipes and inventories.
 * No complex routing logic - pipes handle their own movement decisions.
 */
public class ItemNetwork {
    private final String networkId;
    private final Set<BlockPos> pipes = new HashSet<>();
    private final Map<BlockPos, Inventory> connectedInventories = new HashMap<>();
    private final World world;
    
    public ItemNetwork(World world) {
        this.networkId = UUID.randomUUID().toString();
        this.world = world;
    }
    
    public String getNetworkId() {
        return networkId;
    }
    
    /**
     * Adds a pipe to this network and scans for connected inventories.
     */
    public void addPipe(BlockPos pos) {
        if (pipes.contains(pos)) return;
        
        pipes.add(pos);
        ItemNetworkManager.pipeToNetwork.put(pos, this);
        
        // Scan for connected inventories around this pipe
        scanForConnectedInventories(pos);
        
        Circuitmod.LOGGER.debug("[NETWORK] Added pipe at {} to network {}, now has {} pipes and {} inventories", 
            pos, networkId, pipes.size(), connectedInventories.size());
    }
    
    /**
     * Removes a pipe from this network.
     */
    public void removePipe(BlockPos pos) {
        if (pipes.remove(pos)) {
            ItemNetworkManager.pipeToNetwork.remove(pos);
            // Rescan all inventories since connections may have changed
            rescanAllInventories();
        }
    }
    
    /**
     * Scans around a pipe position for connected inventories.
     */
    private void scanForConnectedInventories(BlockPos pipePos) {
        for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
            BlockPos neighborPos = pipePos.offset(direction);
            
            // Skip other pipes
            if (world.getBlockState(neighborPos).getBlock() instanceof starduster.circuitmod.block.BasePipeBlock) {
                continue;
            }
            
            Inventory inventory = getInventoryAt(world, neighborPos);
            if (inventory != null) {
                connectedInventories.put(neighborPos, inventory);
                Circuitmod.LOGGER.debug("[NETWORK] Found inventory {} at {} connected to pipe at {}", 
                    inventory.getClass().getSimpleName(), neighborPos, pipePos);
            }
        }
    }
    
    /**
     * Gets an inventory at the specified position.
     */
    private static Inventory getInventoryAt(World world, BlockPos pos) {
        if (world.isClient()) return null;
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof Inventory inventory) {
            // Handle special cases like double chests
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (blockEntity instanceof net.minecraft.block.entity.ChestBlockEntity && 
                state.getBlock() instanceof net.minecraft.block.ChestBlock chestBlock) {
                return net.minecraft.block.ChestBlock.getInventory(chestBlock, state, world, pos, true);
            }
            return inventory;
        }
        
        return null;
    }
    
    /**
     * Rescans all pipes in the network for connected inventories.
     */
    private void rescanAllInventories() {
        connectedInventories.clear();
        
        for (BlockPos pipePos : pipes) {
            scanForConnectedInventories(pipePos);
        }
    }
    
    /**
     * Gets all pipes in this network.
     */
    public Set<BlockPos> getPipes() {
        return new HashSet<>(pipes);
    }
    
    /**
     * Gets all connected inventories in this network.
     */
    public Map<BlockPos, Inventory> getConnectedInventories() {
        return new HashMap<>(connectedInventories);
    }
    
    /**
     * Gets the number of pipes in this network.
     */
    public int getSize() {
        return pipes.size();
    }
    
    /**
     * Checks if this network is empty.
     */
    public boolean isEmpty() {
        return pipes.isEmpty();
    }
    
    /**
     * Merges another network into this one.
     */
    public void merge(ItemNetwork other) {
        pipes.addAll(other.pipes);
        
        // Update pipe-to-network mapping
        for (BlockPos pipe : other.pipes) {
            ItemNetworkManager.pipeToNetwork.put(pipe, this);
        }
        
        // Rescan all inventories since we now have more pipes
        rescanAllInventories();
        
        Circuitmod.LOGGER.debug("[NETWORK] Merged network {} into {}, now has {} pipes and {} inventories", 
            other.networkId, networkId, pipes.size(), connectedInventories.size());
    }
    
    /**
     * Force a complete rescan of all inventories.
     */
    public void forceRescanAllInventories() {
        rescanAllInventories();
    }
    
    /**
     * Debug method to print network information.
     */
    public void debugNetworkInfo() {
        Circuitmod.LOGGER.info("[NETWORK-DEBUG] Network {} contains:", networkId);
        Circuitmod.LOGGER.info("[NETWORK-DEBUG] - Pipes ({}): {}", pipes.size(), pipes);
        Circuitmod.LOGGER.info("[NETWORK-DEBUG] - Connected inventories ({}): {}", 
            connectedInventories.size(), connectedInventories.keySet());
    }
}
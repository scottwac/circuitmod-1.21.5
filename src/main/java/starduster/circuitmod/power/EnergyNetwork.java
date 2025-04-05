package starduster.circuitmod.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;

/**
 * Manages a network of energy producers, consumers, and cables.
 */
public class EnergyNetwork {
    private boolean active = true;
    private int storedEnergy = 0;
    private int maxStorage = 10000; // Default value
    
    // Track all components in this network
    private Map<BlockPos, IPowerConnectable> connectedBlocks = new HashMap<>();
    private List<IEnergyProducer> producers = new ArrayList<>();
    private List<IEnergyConsumer> consumers = new ArrayList<>();
    
    // Network statistics
    private int lastTickEnergyProduced = 0;
    private int lastTickEnergyConsumed = 0;
    
    /**
     * Adds a block to this network.
     * 
     * @param pos The position of the block
     * @param block The block to add
     */
    public void addBlock(BlockPos pos, IPowerConnectable block) {
        if (connectedBlocks.containsKey(pos)) {
            return; // Block already in network
        }
        
        connectedBlocks.put(pos, block);
        block.setNetwork(this);
        
        // Sort blocks into appropriate categories
        if (block instanceof IEnergyProducer) {
            producers.add((IEnergyProducer) block);
        }
        if (block instanceof IEnergyConsumer) {
            consumers.add((IEnergyConsumer) block);
        }
        
        Circuitmod.LOGGER.debug("Added block at " + pos + " to energy network. Network size: " + connectedBlocks.size());
    }
    
    /**
     * Removes a block from this network.
     * 
     * @param pos The position of the block to remove
     */
    public void removeBlock(BlockPos pos) {
        IPowerConnectable block = connectedBlocks.remove(pos);
        if (block != null) {
            if (block instanceof IEnergyProducer) {
                producers.remove(block);
            }
            if (block instanceof IEnergyConsumer) {
                consumers.remove(block);
            }
            
            // If we removed a block, we need to check if the network should split
            // This would be called by the block being removed
            Circuitmod.LOGGER.debug("Removed block at " + pos + " from energy network. Network size: " + connectedBlocks.size());
        }
    }
    
    /**
     * Merges another network into this one.
     * 
     * @param other The network to merge with
     */
    public void mergeWith(EnergyNetwork other) {
        if (other == this) return;
        
        // Add all blocks from the other network to this one
        for (Map.Entry<BlockPos, IPowerConnectable> entry : other.connectedBlocks.entrySet()) {
            addBlock(entry.getKey(), entry.getValue());
        }
        
        // Combine energy storage
        this.storedEnergy += other.storedEnergy;
        
        // Clear the old network
        other.clear();
        
        Circuitmod.LOGGER.info("Networks merged. New network size: " + connectedBlocks.size());
    }
    
    /**
     * Clears this network.
     */
    public void clear() {
        connectedBlocks.clear();
        producers.clear();
        consumers.clear();
        storedEnergy = 0;
    }
    
    /**
     * Rebuilds this network by flood-filling from a starting point.
     * 
     * @param world The world
     * @param startPos The position to start rebuilding from
     * @return A set of block positions that are part of this network
     */
    public Set<BlockPos> rebuild(World world, BlockPos startPos) {
        // Store the old blocks to disconnect later
        Set<BlockPos> oldPositions = new HashSet<>(connectedBlocks.keySet());
        
        // Clear the network
        clear();
        
        // Start flood-fill from the given position
        Set<BlockPos> visited = new HashSet<>();
        floodFill(world, startPos, visited);
        
        // Remove positions that are still in the network
        oldPositions.removeAll(visited);
        
        // For any remaining positions, they are now disconnected and need a new network
        return oldPositions;
    }
    
    /**
     * Helper method to flood-fill the network from a starting position.
     * 
     * @param world The world
     * @param pos The current position
     * @param visited Set of positions already visited
     */
    private void floodFill(World world, BlockPos pos, Set<BlockPos> visited) {
        // If we've already visited this position, stop
        if (visited.contains(pos)) {
            return;
        }
        
        // Mark as visited
        visited.add(pos);
        
        // Get the block at this position
        IPowerConnectable connectable = getPowerConnectableAt(world, pos);
        if (connectable == null) {
            return;
        }
        
        // Add to the network
        addBlock(pos, connectable);
        
        // Recursively check neighbors
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            IPowerConnectable neighbor = getPowerConnectableAt(world, neighborPos);
            
            if (neighbor != null && neighbor.canConnectPower(dir.getOpposite()) && connectable.canConnectPower(dir)) {
                floodFill(world, neighborPos, visited);
            }
        }
    }
    
    /**
     * Helper method to get an IPowerConnectable from a position.
     * 
     * @param world The world
     * @param pos The position
     * @return The IPowerConnectable, or null if not found
     */
    private IPowerConnectable getPowerConnectableAt(World world, BlockPos pos) {
        if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            if (world.getBlockEntity(pos) instanceof IPowerConnectable) {
                return (IPowerConnectable) world.getBlockEntity(pos);
            }
        }
        return null;
    }
    
    /**
     * Processes energy production and consumption for this network.
     */
    public void tick() {
        if (!active || connectedBlocks.isEmpty()) return;
        
        // Reset counters
        lastTickEnergyProduced = 0;
        lastTickEnergyConsumed = 0;
        
        // Step 1: Collect energy from producers
        for (IEnergyProducer producer : producers) {
            int produced = producer.produceEnergy(maxStorage - storedEnergy);
            storedEnergy += produced;
            lastTickEnergyProduced += produced;
            
            // Stop if we're full
            if (storedEnergy >= maxStorage) {
                storedEnergy = maxStorage;
                break;
            }
        }
        
        // Step 2: Calculate total demand
        int totalDemand = 0;
        for (IEnergyConsumer consumer : consumers) {
            totalDemand += consumer.getEnergyDemand();
        }
        
        // Step 3: Distribute energy to consumers
        if (totalDemand <= storedEnergy) {
            // We have enough energy for everyone
            for (IEnergyConsumer consumer : consumers) {
                int requested = consumer.getEnergyDemand();
                int provided = consumer.consumeEnergy(requested);
                storedEnergy -= provided;
                lastTickEnergyConsumed += provided;
            }
        } else if (storedEnergy > 0) {
            // Not enough energy - distribute proportionally
            float ratio = (float) storedEnergy / totalDemand;
            
            for (IEnergyConsumer consumer : consumers) {
                int requested = consumer.getEnergyDemand();
                int offer = Math.round(requested * ratio);
                
                if (offer > 0) {
                    int provided = consumer.consumeEnergy(offer);
                    storedEnergy -= provided;
                    lastTickEnergyConsumed += provided;
                    
                    // If we're out of energy, stop
                    if (storedEnergy <= 0) break;
                }
            }
        }
    }
    
    /**
     * Saves network data to an NBT compound.
     * 
     * @param nbt The NBT compound to save to
     */
    public void writeToNbt(NbtCompound nbt) {
        nbt.putBoolean("active", active);
        nbt.putInt("stored_energy", storedEnergy);
        nbt.putInt("max_storage", maxStorage);
    }
    
    /**
     * Loads network data from an NBT compound.
     * 
     * @param nbt The NBT compound to load from
     */
    public void readFromNbt(NbtCompound nbt) {
        active = nbt.getBoolean("active").orElse(true);
        storedEnergy = nbt.getInt("stored_energy").orElse(0);
        maxStorage = nbt.getInt("max_storage").orElse(10000);
    }
    
    // Getters and setters
    
    /**
     * Gets the stored energy amount.
     * 
     * @return The stored energy
     */
    public int getStoredEnergy() { 
        return storedEnergy; 
    }
    
    /**
     * Gets the maximum storage capacity.
     * 
     * @return The max storage
     */
    public int getMaxStorage() { 
        return maxStorage; 
    }
    
    /**
     * Gets the energy produced in the last tick.
     * 
     * @return The energy produced last tick
     */
    public int getLastTickEnergyProduced() { 
        return lastTickEnergyProduced; 
    }
    
    /**
     * Gets the energy consumed in the last tick.
     * 
     * @return The energy consumed last tick
     */
    public int getLastTickEnergyConsumed() { 
        return lastTickEnergyConsumed; 
    }
    
    /**
     * Sets the network's active state.
     * 
     * @param active Whether the network is active
     */
    public void setActive(boolean active) { 
        this.active = active; 
    }
    
    /**
     * Checks if the network is active.
     * 
     * @return True if active, false otherwise
     */
    public boolean isActive() { 
        return active; 
    }
    
    /**
     * Gets the size of this network.
     * 
     * @return The number of blocks in this network
     */
    public int getSize() {
        return connectedBlocks.size();
    }
} 
package starduster.circuitmod.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private int maxStorage = 1; // Limited to 1 energy unit buffer
    private String networkId;
    
    // Track all components in this network
    private Map<BlockPos, IPowerConnectable> connectedBlocks = new HashMap<>();
    private List<IEnergyProducer> producers = new ArrayList<>();
    private List<IEnergyConsumer> consumers = new ArrayList<>();
    private List<IEnergyStorage> batteries = new ArrayList<>();
    
    // Network statistics
    private int lastTickEnergyProduced = 0;
    private int lastTickEnergyConsumed = 0;
    private int lastTickEnergyStoredInBatteries = 0;
    private int lastTickEnergyDrawnFromBatteries = 0;
    
    /**
     * Creates a new network with a random ID.
     */
    public EnergyNetwork() {
        // Generate a unique ID for this network - use first 8 chars of UUID for readability
        this.networkId = "NET-" + UUID.randomUUID().toString().substring(0, 8);
        Circuitmod.LOGGER.info("Created new energy network with ID: " + networkId);
    }
    
    /**
     * Creates a new network with the specified ID.
     * 
     * @param networkId The ID to assign to this network
     */
    public EnergyNetwork(String networkId) {
        this.networkId = networkId;
    }
    
    /**
     * Gets the unique ID of this network.
     * 
     * @return The network ID
     */
    public String getNetworkId() {
        return networkId;
    }
    
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
        if (block instanceof IEnergyStorage) {
            batteries.add((IEnergyStorage) block);
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
            if (block instanceof IEnergyStorage) {
                batteries.remove(block);
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
        
        Circuitmod.LOGGER.info("Networks merged. Network " + other.networkId + " merged into " + this.networkId + ". New size: " + connectedBlocks.size());
    }
    
    /**
     * Clears this network.
     */
    public void clear() {
        connectedBlocks.clear();
        producers.clear();
        consumers.clear();
        batteries.clear();
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
        lastTickEnergyStoredInBatteries = 0;
        lastTickEnergyDrawnFromBatteries = 0;
        
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
        
        // Step 3: Check for energy surplus/deficit
        int energySurplus = storedEnergy - totalDemand;
        
        // If we have a surplus, try to store in batteries
        if (energySurplus > 0 && !batteries.isEmpty()) {
            storeEnergyInBatteries(energySurplus);
        }
        
        // If we have a deficit, try to get energy from batteries
        int energyDeficit = totalDemand - storedEnergy;
        if (energyDeficit > 0 && !batteries.isEmpty()) {
            int energyFromBatteries = drawEnergyFromBatteries(energyDeficit);
            storedEnergy += energyFromBatteries;
            lastTickEnergyDrawnFromBatteries = energyFromBatteries;
        }
        
        // Step 4: Distribute energy to consumers
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
        
        // Step 5: If we still have surplus energy after consumers, store in batteries
        if (storedEnergy > 0 && !batteries.isEmpty()) {
            storeEnergyInBatteries(storedEnergy);
        }
    }
    
    /**
     * Attempts to store energy in batteries.
     * 
     * @param energyToStore Amount of energy to store
     * @return Amount of energy actually stored
     */
    private int storeEnergyInBatteries(int energyToStore) {
        int totalStored = 0;
        int remainingToStore = energyToStore;
        
        // First pass: Try to charge each battery up to its max charge rate
        for (IEnergyStorage battery : batteries) {
            if (remainingToStore <= 0) break;
            if (!battery.canCharge()) continue;
            
            int maxChargeRate = battery.getMaxChargeRate();
            int toCharge = Math.min(remainingToStore, maxChargeRate);
            
            int stored = battery.chargeEnergy(toCharge);
            totalStored += stored;
            remainingToStore -= stored;
            lastTickEnergyStoredInBatteries += stored;
        }
        
        // Update network stored energy
        storedEnergy -= totalStored;
        if (storedEnergy < 0) storedEnergy = 0;
        
        return totalStored;
    }
    
    /**
     * Attempts to draw energy from batteries.
     * 
     * @param energyNeeded Amount of energy needed
     * @return Amount of energy actually drawn
     */
    private int drawEnergyFromBatteries(int energyNeeded) {
        int totalDrawn = 0;
        int remainingNeeded = energyNeeded;
        
        // First pass: Try to discharge each battery up to its max discharge rate
        for (IEnergyStorage battery : batteries) {
            if (remainingNeeded <= 0) break;
            if (!battery.canDischarge()) continue;
            
            int maxDischargeRate = battery.getMaxDischargeRate();
            int toDischarge = Math.min(remainingNeeded, maxDischargeRate);
            
            int drawn = battery.dischargeEnergy(toDischarge);
            totalDrawn += drawn;
            remainingNeeded -= drawn;
        }
        
        return totalDrawn;
    }
    
    /**
     * Saves network data to an NBT compound.
     * 
     * @param nbt The NBT compound to save to
     */
    public void writeToNbt(NbtCompound nbt) {
        // Save basic properties
        nbt.putBoolean("active", active);
        nbt.putInt("storedEnergy", storedEnergy);
        nbt.putInt("maxStorage", maxStorage);
        nbt.putString("networkId", networkId);
        
        // Save statistics
        nbt.putInt("lastTickEnergyProduced", lastTickEnergyProduced);
        nbt.putInt("lastTickEnergyConsumed", lastTickEnergyConsumed);
        nbt.putInt("lastTickEnergyStoredInBatteries", lastTickEnergyStoredInBatteries);
        nbt.putInt("lastTickEnergyDrawnFromBatteries", lastTickEnergyDrawnFromBatteries);
    }
    
    /**
     * Loads network data from an NBT compound.
     * 
     * @param nbt The NBT compound to load from
     */
    public void readFromNbt(NbtCompound nbt) {
        // Load basic properties
        active = nbt.getBoolean("active").orElse(true);
        storedEnergy = nbt.getInt("storedEnergy").orElse(0);
        maxStorage = 1; // Always keep max storage at 1
        
        // Load network ID or generate a new one if not present
        if (nbt.contains("networkId")) {
            networkId = nbt.getString("networkId").orElse("NET-" + UUID.randomUUID().toString().substring(0, 8));
        } else {
            networkId = "NET-" + UUID.randomUUID().toString().substring(0, 8);
            Circuitmod.LOGGER.info("Generated new network ID during NBT load: " + networkId);
        }
        
        // Load statistics
        lastTickEnergyProduced = nbt.getInt("lastTickEnergyProduced").orElse(0);
        lastTickEnergyConsumed = nbt.getInt("lastTickEnergyConsumed").orElse(0);
        lastTickEnergyStoredInBatteries = nbt.getInt("lastTickEnergyStoredInBatteries").orElse(0);
        lastTickEnergyDrawnFromBatteries = nbt.getInt("lastTickEnergyDrawnFromBatteries").orElse(0);
    }
    
    // Getters for network properties
    
    public int getSize() {
        return connectedBlocks.size();
    }
    
    public int getStoredEnergy() {
        return storedEnergy;
    }
    
    public int getMaxStorage() {
        return maxStorage;
    }
    
    public int getLastTickEnergyProduced() {
        return lastTickEnergyProduced;
    }
    
    public int getLastTickEnergyConsumed() {
        return lastTickEnergyConsumed;
    }
    
    public int getLastTickEnergyStoredInBatteries() {
        return lastTickEnergyStoredInBatteries;
    }
    
    public int getLastTickEnergyDrawnFromBatteries() {
        return lastTickEnergyDrawnFromBatteries;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    /**
     * Gets all block positions in this network.
     * 
     * @return Set of all block positions in the network
     */
    public Set<BlockPos> getConnectedBlockPositions() {
        return new HashSet<>(connectedBlocks.keySet());
    }
} 
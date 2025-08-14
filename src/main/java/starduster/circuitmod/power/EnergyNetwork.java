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
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;

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
    
    // Flag to control logging during startup
    public static boolean startupMode = true;
    
    // Debug logging control - set to true only when debugging
    private static final boolean DEBUG_LOGGING = false;
    
    /**
     * Sets startup mode to control logging
     */
    public static void setStartupMode(boolean mode) {
        startupMode = mode;
    }
    
    /**
     * Creates a new network with a random ID.
     */
    public EnergyNetwork() {
        // Generate a unique ID for this network - use first 8 chars of UUID for readability
        this.networkId = "NET-" + UUID.randomUUID().toString().substring(0, 8);
        
        if (DEBUG_LOGGING && !startupMode) {
            Circuitmod.LOGGER.info("Created new energy network with ID: " + networkId);
        }
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
        
        if (DEBUG_LOGGING && !startupMode) {
            Circuitmod.LOGGER.debug("Added block at " + pos + " to energy network. Network size: " + connectedBlocks.size());
        }
    }
    
    /**
     * Removes a block from this network's tracking.
     * This only affects the network's internal data structures, not the actual block in the world.
     * 
     * @param pos The position of the block to remove from network tracking
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
            
            // Clear the network reference from the block
            block.setNetwork(null);
            
            // Log the removal only if not in startup mode
            if (DEBUG_LOGGING && !startupMode) {
                Circuitmod.LOGGER.debug("Removed block reference at " + pos + " from energy network tracking. Network size: " + connectedBlocks.size());
            }
            
            // If the network is now empty, deactivate it
            if (connectedBlocks.isEmpty()) {
                active = false;
                if (DEBUG_LOGGING && !startupMode) {
                    Circuitmod.LOGGER.info("Network " + networkId + " is now empty and inactive");
                }
            }
        }
    }
    
    /**
     * Merges another network into this one.
     * 
     * @param other The network to merge with
     */
    public void mergeWith(EnergyNetwork other) {
        if (other == this) return;
        
        // Store the other network's ID for logging
        String otherNetworkId = other.networkId;
        
        // Create a copy of the entries to avoid ConcurrentModificationException
        List<Map.Entry<BlockPos, IPowerConnectable>> entriesToMerge = new ArrayList<>(other.connectedBlocks.entrySet());
        
        // Add all blocks from the other network to this one
        for (Map.Entry<BlockPos, IPowerConnectable> entry : entriesToMerge) {
            addBlock(entry.getKey(), entry.getValue());
        }
        
        // Combine energy storage
        this.storedEnergy += other.storedEnergy;
        
        // Clear and deactivate the old network
        other.clear();
        other.active = false;
        other.networkId = "MERGED-" + otherNetworkId; // Mark as merged to prevent further operations
        
        if (DEBUG_LOGGING && !startupMode) {
            Circuitmod.LOGGER.info("Networks merged. Network " + otherNetworkId + " merged into " + this.networkId + ". New size: " + connectedBlocks.size());
        }
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
        
        // Step 1: Calculate total energy production from all producers
        int totalProduction = 0;
        for (IEnergyProducer producer : producers) {
            // Just calculate what each producer can produce, but don't actually produce it yet
            int canProduce = producer.getMaxOutput();
            totalProduction += canProduce;
        }
        
        // Step 2: Calculate total energy demand from all consumers
        int totalDemand = 0;
        for (IEnergyConsumer consumer : consumers) {
            totalDemand += consumer.getEnergyDemand();
        }
        
        // Step 3: Determine energy flow
        int actualProduction = 0;
        int energyForConsumers = 0;
        int energyForBatteries = 0;
        int energyFromBatteries = 0;
        
        if (totalProduction >= totalDemand) {
            // We have enough production to meet demand
            actualProduction = totalDemand; // Only produce what we need
            energyForConsumers = totalDemand;
            
            // If we can produce more than needed, store excess in batteries
            int excessProduction = totalProduction - totalDemand;
            if (excessProduction > 0 && !batteries.isEmpty()) {
                energyForBatteries = excessProduction;
            }
        } else {
            // Not enough production, use all available production
            actualProduction = totalProduction;
            energyForConsumers = totalProduction;
            
            // Try to draw the deficit from batteries
            int deficit = totalDemand - totalProduction;
            if (deficit > 0 && !batteries.isEmpty()) {
                energyFromBatteries = drawEnergyFromBatteries(deficit);
                energyForConsumers += energyFromBatteries;
            }
        }
        
        // Step 4: Actually produce energy from producers (up to what we need)
        int remainingToCollect = actualProduction;
        storedEnergy = 0; // Reset stored energy - we'll recalculate it
        
        for (IEnergyProducer producer : producers) {
            if (remainingToCollect <= 0) break;
            
            int produced = producer.produceEnergy(remainingToCollect);
            lastTickEnergyProduced += produced;
            storedEnergy += produced;
            remainingToCollect -= produced;
        }
        
        // Step 5: Store excess energy in batteries (if applicable)
        if (energyForBatteries > 0) {
            int realExcessProduction = totalProduction - actualProduction;
            if (realExcessProduction > 0) {
                // We need to actually produce this excess energy
                int excessToCollect = realExcessProduction;
                int excessCollected = 0;
                
                for (IEnergyProducer producer : producers) {
                    if (excessToCollect <= 0) break;
                    
                    int produced = producer.produceEnergy(excessToCollect);
                    excessCollected += produced;
                    lastTickEnergyProduced += produced;
                    excessToCollect -= produced;
                }
                
                if (excessCollected > 0) {
                    int stored = storeEnergyInBatteries(excessCollected);
                    lastTickEnergyStoredInBatteries = stored;
                }
            }
        }
        
        // Track energy from batteries
        lastTickEnergyDrawnFromBatteries = energyFromBatteries;
        
        // Step 6: Distribute energy to consumers
        if (energyForConsumers >= totalDemand) {
            // We have enough energy for everyone
            for (IEnergyConsumer consumer : consumers) {
                int requested = consumer.getEnergyDemand();
                int provided = consumer.consumeEnergy(requested);
                storedEnergy -= provided;
                lastTickEnergyConsumed += provided;
            }
        } else if (energyForConsumers > 0) {
            // Not enough energy - distribute proportionally
            float ratio = (float) energyForConsumers / totalDemand;
            
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
        
        // Ensure stored energy doesn't exceed our buffer
        if (storedEnergy > maxStorage) {
            // Try to put excess into batteries
            if (!batteries.isEmpty()) {
                int excess = storedEnergy - maxStorage;
                int stored = storeEnergyInBatteries(excess);
                lastTickEnergyStoredInBatteries += stored;
                storedEnergy -= stored;
            }
            
            // Cap at max storage regardless
            if (storedEnergy > maxStorage) {
                storedEnergy = maxStorage;
            }
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
     * Validates and repairs this network if it's in an inconsistent state.
     * This is called during world loading to recover from crashes.
     * 
     * @param world The world
     * @return true if the network was repaired, false if it's healthy
     */
    public boolean validateAndRepair(World world) {
        if (world == null) return false;
        
        boolean wasRepaired = false;
        Set<BlockPos> invalidPositions = new HashSet<>();
        
        // Check each block position to see if the block still exists and is valid
        for (Map.Entry<BlockPos, IPowerConnectable> entry : connectedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            IPowerConnectable connectable = entry.getValue();
            
            // Check if the block still exists at this position
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity == null || !(blockEntity instanceof IPowerConnectable)) {
                // Block no longer exists or is not a power connectable
                invalidPositions.add(pos);
                wasRepaired = true;
                if (DEBUG_LOGGING && !startupMode) {
                    Circuitmod.LOGGER.warn("Found invalid block at {} in network {}, removing", pos, networkId);
                }
                continue;
            }
            
            // Check if the block's network reference is consistent
            IPowerConnectable currentConnectable = (IPowerConnectable) blockEntity;
            EnergyNetwork blockNetwork = currentConnectable.getNetwork();
            if (blockNetwork != this && blockNetwork != null) {
                // Block's network reference is inconsistent - try to fix it first
                Circuitmod.LOGGER.info("Found inconsistent network reference at {} in network {}, attempting to fix", pos, networkId);
                currentConnectable.setNetwork(this);
                // Give it a tick to update
                if (world instanceof ServerWorld) {
                    ((ServerWorld) world).scheduleBlockTick(pos, world.getBlockState(pos).getBlock(), 1);
                }
            } else if (blockNetwork == null) {
                // Block has no network reference - fix it
                Circuitmod.LOGGER.info("Found block with null network reference at {} in network {}, fixing", pos, networkId);
                currentConnectable.setNetwork(this);
            }
        }
        
        // Remove invalid positions
        for (BlockPos pos : invalidPositions) {
            removeBlock(pos);
        }
        
        // If network is now empty, deactivate it
        if (connectedBlocks.isEmpty()) {
            active = false;
            if (DEBUG_LOGGING && !startupMode) {
                Circuitmod.LOGGER.info("Network {} is now empty after repair, deactivating", networkId);
            }
        }
        
        if (wasRepaired && DEBUG_LOGGING && !startupMode) {
            Circuitmod.LOGGER.info("Repaired network {}: removed {} invalid blocks, remaining: {}", 
                networkId, invalidPositions.size(), connectedBlocks.size());
        }
        
        return wasRepaired;
    }
    
    // Note: Removed performGlobalRecovery method as it was causing world loading to hang
    // Individual block entities will handle their own network connections during normal operation
    
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
            if (DEBUG_LOGGING) {
                Circuitmod.LOGGER.info("Generated new network ID during NBT load: " + networkId);
            }
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
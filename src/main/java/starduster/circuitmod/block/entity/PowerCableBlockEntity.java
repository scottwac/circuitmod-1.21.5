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
import java.util.Map;

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
        
        // Remove this block from the network
        network.removeBlock(pos);
        
        // If there are still blocks in the network, choose one to rebuild from
        if (network.getSize() > 0) {
            // Find an adjacent block that's still in the network to rebuild from
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                BlockEntity be = world.getBlockEntity(neighborPos);
                
                if (be instanceof IPowerConnectable) {
                    IPowerConnectable connectable = (IPowerConnectable) be;
                    if (connectable.getNetwork() == network) {
                        // Found a block still in the network, rebuild from it
                        network.rebuild(world, neighborPos);
                        return;
                    }
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
        if (world.isClient() || blockEntity.network == null) {
            return;
        }
        
        // Process network energy transfers once per tick
        blockEntity.network.tick();
    }
} 
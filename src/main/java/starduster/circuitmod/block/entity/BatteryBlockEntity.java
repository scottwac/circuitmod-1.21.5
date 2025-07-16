package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyStorage;
import starduster.circuitmod.power.IPowerConnectable;

public class BatteryBlockEntity extends BlockEntity implements IEnergyStorage {
    // Default values - can be modified for different battery tiers
    private static final int DEFAULT_CAPACITY = 1000;
    private static final int DEFAULT_MAX_CHARGE_RATE = 10;
    private static final int DEFAULT_MAX_DISCHARGE_RATE = 10;
    private static final int UPDATE_INTERVAL = 100; // 5 seconds (at 20 ticks per second)
    
    private EnergyNetwork network;
    private int storedEnergy = 0;
    private int maxCapacity = DEFAULT_CAPACITY;
    private int maxChargeRate = DEFAULT_MAX_CHARGE_RATE;
    private int maxDischargeRate = DEFAULT_MAX_DISCHARGE_RATE;
    private boolean canCharge = true;
    private boolean canDischarge = true;
    private int tickCounter = 0;
    private boolean needsNetworkRefresh = false;
    
    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save battery data
        nbt.putInt("stored_energy", storedEnergy);
        nbt.putInt("max_capacity", maxCapacity);
        nbt.putInt("max_charge_rate", maxChargeRate);
        nbt.putInt("max_discharge_rate", maxDischargeRate);
        nbt.putBoolean("can_charge", canCharge);
        nbt.putBoolean("can_discharge", canDischarge);
        nbt.putInt("tick_counter", tickCounter);
        
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
        
        // Load battery data
        this.storedEnergy = nbt.getInt("stored_energy").orElse(0);
        this.maxCapacity = nbt.getInt("max_capacity").orElse(DEFAULT_CAPACITY);
        this.maxChargeRate = nbt.getInt("max_charge_rate").orElse(DEFAULT_MAX_CHARGE_RATE);
        this.maxDischargeRate = nbt.getInt("max_discharge_rate").orElse(DEFAULT_MAX_DISCHARGE_RATE);
        this.canCharge = nbt.getBoolean("can_charge").orElse(true);
        this.canDischarge = nbt.getBoolean("can_discharge").orElse(true);
        this.tickCounter = nbt.getInt("tick_counter").orElse(0);
        this.needsNetworkRefresh = true;
        
        // Load network data
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
    }
    
    // IEnergyStorage implementation
    
    @Override
    public boolean canConnectPower(Direction side) {
        return true; // Can connect from any side
    }
    
    @Override
    public EnergyNetwork getNetwork() {
        return network;
    }
    
    @Override
    public void setNetwork(EnergyNetwork network) {
        // If we already have a network and it's different, properly disconnect
        if (this.network != null && this.network != network) {
            this.network.removeBlock(pos);
            Circuitmod.LOGGER.info("Battery at " + pos + " disconnected from network " + this.network.getNetworkId());
        }
        
        this.network = network;
    }
    
    @Override
    public int chargeEnergy(int energyToCharge) {
        if (!canCharge || energyToCharge <= 0) {
            return 0;
        }
        
        int spaceAvailable = maxCapacity - storedEnergy;
        int actualCharge = Math.min(energyToCharge, Math.min(spaceAvailable, maxChargeRate));
        
        if (actualCharge > 0) {
            storedEnergy += actualCharge;
            markDirty();
        }
        
        return actualCharge;
    }
    
    @Override
    public int dischargeEnergy(int energyRequested) {
        if (!canDischarge || energyRequested <= 0 || storedEnergy <= 0) {
            return 0;
        }
        
        int actualDischarge = Math.min(energyRequested, Math.min(storedEnergy, maxDischargeRate));
        
        if (actualDischarge > 0) {
            storedEnergy -= actualDischarge;
            markDirty();
        }
        
        return actualDischarge;
    }
    
    @Override
    public int getMaxChargeRate() {
        return maxChargeRate;
    }
    
    @Override
    public int getMaxDischargeRate() {
        return maxDischargeRate;
    }
    
    @Override
    public int getStoredEnergy() {
        return storedEnergy;
    }
    
    @Override
    public int getMaxCapacity() {
        return maxCapacity;
    }
    
    @Override
    public boolean canCharge() {
        return canCharge;
    }
    
    @Override
    public boolean canDischarge() {
        return canDischarge;
    }
    
    @Override
    public Direction[] getInputSides() {
        return Direction.values(); // Can input from any side
    }
    
    @Override
    public Direction[] getOutputSides() {
        return Direction.values(); // Can output to any side
    }
    
    // Setters for configuration
    
    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        // Ensure stored energy doesn't exceed new capacity
        if (storedEnergy > maxCapacity) {
            storedEnergy = maxCapacity;
        }
        markDirty();
    }
    
    public void setMaxChargeRate(int maxChargeRate) {
        this.maxChargeRate = maxChargeRate;
        markDirty();
    }
    
    public void setMaxDischargeRate(int maxDischargeRate) {
        this.maxDischargeRate = maxDischargeRate;
        markDirty();
    }
    
    public void setCanCharge(boolean canCharge) {
        this.canCharge = canCharge;
        markDirty();
    }
    
    public void setCanDischarge(boolean canDischarge) {
        this.canDischarge = canDischarge;
        markDirty();
    }
    
    /**
     * Attempts to find and join a network from adjacent connectable blocks.
     * Should be called when this battery's network connection might have changed.
     */
    public void findAndJoinNetwork() {
        if (world == null || world.isClient) return;
        
        boolean foundNetwork = false;
        
        // Look for adjacent networks
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                EnergyNetwork network = connectable.getNetwork();
                
                if (network != null && network != this.network) {
                    // Found a network, join it
                    if (this.network != null) {
                        // Remove from old network first
                        this.network.removeBlock(pos);
                        Circuitmod.LOGGER.info("Battery at " + pos + " left network " + this.network.getNetworkId());
                    }
                    
                    network.addBlock(pos, this);
                    foundNetwork = true;
                    Circuitmod.LOGGER.info("Battery at " + pos + " joined existing network " + network.getNetworkId());
                    break;
                }
            }
        }
        
        // If no existing network was found, create a new one
        if (!foundNetwork && (this.network == null)) {
            Circuitmod.LOGGER.info("No existing network found for battery at " + pos + ", creating new network");
            
            // Create a new network
            EnergyNetwork newNetwork = new EnergyNetwork();
            newNetwork.addBlock(pos, this);
            
            // Try to add adjacent connectables to this new network
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                BlockEntity be = world.getBlockEntity(neighborPos);
                
                if (be instanceof IPowerConnectable && ((IPowerConnectable) be).getNetwork() == null) {
                    IPowerConnectable connectable = (IPowerConnectable) be;
                    
                    // Only add if it doesn't already have a network and can connect
                    if (connectable.canConnectPower(dir.getOpposite()) && 
                        this.canConnectPower(dir)) {
                        
                        newNetwork.addBlock(neighborPos, connectable);
                        Circuitmod.LOGGER.info("Added neighbor at " + neighborPos + " to new network " + newNetwork.getNetworkId());
                    }
                }
            }
            
            Circuitmod.LOGGER.info("Created new network " + newNetwork.getNetworkId() + " with battery at " + pos);
        }
    }

    // Add network handling logic to the tick method
    public static void tick(World world, BlockPos pos, BlockState state, BatteryBlockEntity entity) {
        if (entity.needsNetworkRefresh) {
            entity.findAndJoinNetwork();
            entity.needsNetworkRefresh = false;
        }
        if (world.isClient() || entity.network == null) {
            return;
        }
        
        // Increment tick counter
        entity.tickCounter++;
        
        // Periodically check if we should be in a network
        if (entity.tickCounter % 20 == 0 && !world.isClient) {  // Check every second
            if (entity.network == null) {
                entity.findAndJoinNetwork();
            }
        }
        
        // Send status message every UPDATE_INTERVAL ticks
        if (entity.tickCounter >= UPDATE_INTERVAL) {
            entity.tickCounter = 0;
            
            // Get all players in a 32 block radius and send them the status message
            for (PlayerEntity player : ((ServerWorld)world).getPlayers(p -> 
                p.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 1024)) {
                
                int chargePercentage = (int)((float)entity.storedEnergy / entity.maxCapacity * 100);
                player.sendMessage(Text.literal("§6[Battery] §7Stored energy: §e" + entity.storedEnergy + "§7/§e" + entity.maxCapacity 
                    + " §7(§e" + chargePercentage + "%§7)"), false);
                
                if (entity.network != null) {
                    player.sendMessage(Text.literal("§7Network §6" + entity.network.getNetworkId() + "§7: §e" 
                        + entity.network.getStoredEnergy() + "§7/§e" 
                        + entity.network.getMaxStorage() + " §7(§a+" 
                        + entity.network.getLastTickEnergyProduced() + "§7, §c-" 
                        + entity.network.getLastTickEnergyConsumed() + "§7)"), false);
                }
            }
        }
    }
} 
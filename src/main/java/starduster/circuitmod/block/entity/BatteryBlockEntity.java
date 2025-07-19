package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.PacketByteBuf;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyStorage;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.screen.BatteryScreenHandler;
import starduster.circuitmod.screen.ModScreenHandlers;

public class BatteryBlockEntity extends BlockEntity implements IEnergyStorage, NamedScreenHandlerFactory, ExtendedScreenHandlerFactory<ModScreenHandlers.BatteryData> {
    // Default values - can be modified for different battery tiers
    private static final int DEFAULT_CAPACITY = 100000;
    private static final int DEFAULT_MAX_CHARGE_RATE = 10;
    private static final int DEFAULT_MAX_DISCHARGE_RATE = 10;
    
    private EnergyNetwork network;
    private int storedEnergy = 0;
    private int maxCapacity = DEFAULT_CAPACITY;
    private int maxChargeRate = DEFAULT_MAX_CHARGE_RATE;
    private int maxDischargeRate = DEFAULT_MAX_DISCHARGE_RATE;
    private boolean canCharge = true;
    private boolean canDischarge = true;
    private boolean needsNetworkRefresh = false;
    
    // Client-side state tracking (for GUI updates)
    private int clientStoredEnergy = 0;
    private int clientMaxCapacity = DEFAULT_CAPACITY;
    private int clientMaxChargeRate = DEFAULT_MAX_CHARGE_RATE;
    private int clientMaxDischargeRate = DEFAULT_MAX_DISCHARGE_RATE;
    private boolean clientCanCharge = true;
    private boolean clientCanDischarge = true;
    private int clientNetworkSize = 0;
    private int clientNetworkStoredEnergy = 0;
    private int clientNetworkMaxStorage = 0;
    private int clientNetworkLastProduced = 0;
    private int clientNetworkLastConsumed = 0;
    private int clientNetworkLastStored = 0;
    private int clientNetworkLastDrawn = 0;
    
    // Property delegate for GUI synchronization
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            if (world != null && world.isClient()) {
                // Return client-side values for GUI
                switch (index) {
                    case 0: return clientStoredEnergy;
                    case 1: return clientMaxCapacity;
                    case 2: return clientMaxChargeRate;
                    case 3: return clientMaxDischargeRate;
                    case 4: return clientCanCharge ? 1 : 0;
                    case 5: return clientCanDischarge ? 1 : 0;
                    case 6: return clientNetworkSize;
                    case 7: return clientNetworkStoredEnergy;
                    case 8: return clientNetworkMaxStorage;
                    case 9: return clientNetworkLastProduced;
                    case 10: return clientNetworkLastConsumed;
                    case 11: return clientNetworkLastStored;
                    case 12: return clientNetworkLastDrawn;
                    default: return 0;
                }
            } else {
                // Return server-side values
                switch (index) {
                    case 0: return storedEnergy;
                    case 1: return maxCapacity;
                    case 2: return maxChargeRate;
                    case 3: return maxDischargeRate;
                    case 4: return canCharge ? 1 : 0;
                    case 5: return canDischarge ? 1 : 0;
                    case 6: return network != null ? network.getSize() : 0;
                    case 7: return network != null ? network.getStoredEnergy() : 0;
                    case 8: return network != null ? network.getMaxStorage() : 0;
                    case 9: return network != null ? network.getLastTickEnergyProduced() : 0;
                    case 10: return network != null ? network.getLastTickEnergyConsumed() : 0;
                    case 11: return network != null ? network.getLastTickEnergyStoredInBatteries() : 0;
                    case 12: return network != null ? network.getLastTickEnergyDrawnFromBatteries() : 0;
                    default: return 0;
                }
            }
        }

        @Override
        public void set(int index, int value) {
            // This method is called by Minecraft's built-in synchronization
            // Update client-side values when server sends updates
            if (world != null && world.isClient()) {
                switch (index) {
                    case 0: 
                        clientStoredEnergy = value; 
                        break;
                    case 1: 
                        clientMaxCapacity = value; 
                        break;
                    case 2: 
                        clientMaxChargeRate = value; 
                        break;
                    case 3: 
                        clientMaxDischargeRate = value; 
                        break;
                    case 4: 
                        clientCanCharge = value == 1; 
                        break;
                    case 5: 
                        clientCanDischarge = value == 1; 
                        break;
                    case 6: 
                        clientNetworkSize = value; 
                        break;
                    case 7: 
                        clientNetworkStoredEnergy = value; 
                        break;
                    case 8: 
                        clientNetworkMaxStorage = value; 
                        break;
                    case 9: 
                        clientNetworkLastProduced = value; 
                        break;
                    case 10: 
                        clientNetworkLastConsumed = value; 
                        break;
                    case 11: 
                        clientNetworkLastStored = value; 
                        break;
                    case 12: 
                        clientNetworkLastDrawn = value; 
                        break;
                }
            }
        }

        @Override
        public int size() {
            return 13;
        }
    };
    
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
            String oldNetworkId = this.network.getNetworkId();
            this.network.removeBlock(pos);
            Circuitmod.LOGGER.info("Battery at " + pos + " disconnected from network " + oldNetworkId);
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
                        String oldNetworkId = this.network.getNetworkId();
                        this.network.removeBlock(pos);
                        Circuitmod.LOGGER.info("Battery at " + pos + " left network " + oldNetworkId);
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
        
        if (world.isClient()) {
            return;
        }
        
        // Periodically check if we should be in a network
        if (world.getTime() % 20 == 0) {  // Check every second
            if (entity.network == null) {
                entity.findAndJoinNetwork();
            }
        }
        
        // Update GUI data every 10 ticks (0.5 seconds) to keep it responsive
        if (world.getTime() % 10 == 0) {
            entity.markDirty(); // Trigger PropertyDelegate update
        }
    }
    
    // NamedScreenHandlerFactory implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.battery");
    }
    
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new BatteryScreenHandler(syncId, playerInventory, this.propertyDelegate, this);
    }
    
    // ExtendedScreenHandlerFactory implementation
    @Override
    public ModScreenHandlers.BatteryData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.BatteryData(pos);
    }
    
    // Getter for property delegate
    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }
} 
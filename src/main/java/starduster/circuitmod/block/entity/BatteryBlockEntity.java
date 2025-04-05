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
    
    // Tick method
    public static void tick(World world, BlockPos pos, BlockState state, BatteryBlockEntity blockEntity) {
        if (world.isClient() || blockEntity.network == null) {
            return;
        }
        
        // Increment tick counter
        blockEntity.tickCounter++;
        
        // Send status message every UPDATE_INTERVAL ticks
        if (blockEntity.tickCounter >= UPDATE_INTERVAL) {
            blockEntity.tickCounter = 0;
            
            // Get all players in a 32 block radius and send them the status message
            for (PlayerEntity player : ((ServerWorld)world).getPlayers(p -> 
                p.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 1024)) {
                
                int chargePercentage = (int)((float)blockEntity.storedEnergy / blockEntity.maxCapacity * 100);
                player.sendMessage(Text.literal("§6[Battery] §7Stored energy: §e" + blockEntity.storedEnergy + "§7/§e" + blockEntity.maxCapacity 
                    + " §7(§e" + chargePercentage + "%§7)"), false);
                
                if (blockEntity.network != null) {
                    player.sendMessage(Text.literal("§7Network §6" + blockEntity.network.getNetworkId() + "§7: §e" 
                        + blockEntity.network.getStoredEnergy() + "§7/§e" 
                        + blockEntity.network.getMaxStorage() + " §7(§a+" 
                        + blockEntity.network.getLastTickEnergyProduced() + "§7, §c-" 
                        + blockEntity.network.getLastTickEnergyConsumed() + "§7)"), false);
                }
            }
        }
    }
} 
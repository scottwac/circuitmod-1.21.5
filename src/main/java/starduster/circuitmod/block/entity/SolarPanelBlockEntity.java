package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyProducer;
import starduster.circuitmod.power.IPowerConnectable;

public class SolarPanelBlockEntity extends BlockEntity implements IEnergyProducer {
    // Energy production settings
    private static final int MAX_ENERGY_PER_TICK = 50; // Peak energy production during noon
    private static final int MIN_ENERGY_PER_TICK = 5;  // Minimum energy production during night/storms
    private static final int UPDATE_INTERVAL = 20; // Update every second
    
    // Network and state
    private EnergyNetwork network;
    private int tickCounter = 0;
    private boolean needsNetworkRefresh = false;
    private int currentEnergyProduction = 0;
    private float lastLightLevel = 0.0f;
    
    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("tick_counter", tickCounter);
        nbt.putInt("current_energy_production", currentEnergyProduction);
        nbt.putFloat("last_light_level", lastLightLevel);
        
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
        this.tickCounter = nbt.getInt("tick_counter", 0);
        this.currentEnergyProduction = nbt.getInt("current_energy_production", 0);
        this.lastLightLevel = nbt.getFloat("last_light_level", 0.0f);
        
        // Load network data
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        this.needsNetworkRefresh = true;
    }

    // Tick method called by the ticker in SolarPanel
    public static void tick(World world, BlockPos pos, BlockState state, SolarPanelBlockEntity blockEntity) {
        if (blockEntity.needsNetworkRefresh) {
            blockEntity.findAndJoinNetwork();
            blockEntity.needsNetworkRefresh = false;
        }
        
        if (world.isClient()) {
            return;
        }

        // Update tick counter
        blockEntity.tickCounter++;
        
        // Update energy production based on light levels every second
        if (blockEntity.tickCounter % UPDATE_INTERVAL == 0) {
            blockEntity.updateEnergyProduction(world, pos);
        }
        
        // Initialize energy production if it's still 0 and we haven't updated yet
        if (blockEntity.currentEnergyProduction == 0 && blockEntity.tickCounter < UPDATE_INTERVAL) {
            blockEntity.updateEnergyProduction(world, pos);
        }
        
        // Debug logging (only log occasionally to avoid spam)
        if (world.getTime() % 100 == 0) { // Only log every 5 seconds
            String networkInfo = blockEntity.network != null ? blockEntity.network.getNetworkId() : "NO NETWORK";
            long timeOfDay = world.getTimeOfDay() % 24000;
            float hour = (timeOfDay / 1000.0f);
            Circuitmod.LOGGER.info("[SOLAR-PANEL-TICK] Light: {}, Energy: {}, Network: {}, Tick: {}, Time: {} ({}:{}), Hour: {:.1f}", 
                blockEntity.lastLightLevel, blockEntity.currentEnergyProduction, networkInfo, blockEntity.tickCounter, 
                timeOfDay, (int)hour, (int)((hour % 1.0f) * 60), hour);
        }
    }
    
    /**
     * Updates energy production based on current light conditions
     */
    private void updateEnergyProduction(World world, BlockPos pos) {
        if (world.isClient()) return;
        
        Circuitmod.LOGGER.info("[SOLAR-PANEL-UPDATE] Starting energy production update at {}", pos);
        
        // Check if there's a clear path to the sky
        BlockPos skyPos = pos.up();
        boolean canSeeSky = world.isSkyVisible(skyPos);
        
        if (!canSeeSky) {
            // If blocked from sky, provide minimal energy
            this.currentEnergyProduction = MIN_ENERGY_PER_TICK / 4;
            this.lastLightLevel = 0.0f;
            Circuitmod.LOGGER.info("[SOLAR-PANEL-DEBUG] Blocked from sky, minimal energy: {}", this.currentEnergyProduction);
            return;
        }
        
        // Get sky light level at the position above the solar panel
        int skyLightLevel = world.getLightLevel(skyPos);
        
        // Get time of day (0-24000, where 0=6AM, 6000=noon, 12000=6PM, 18000=midnight)
        long timeOfDay = world.getTimeOfDay() % 24000;
        
        // Calculate efficiency based on time of day (peak at noon)
        float timeEfficiency = calculateTimeEfficiency(timeOfDay);
        
        // Calculate efficiency based on weather
        float weatherEfficiency = world.isRaining() ? 0.3f : 1.0f;
        if (world.isThundering()) {
            weatherEfficiency = 0.1f; // Very little energy during storms
        }
        
        // Calculate efficiency based on sky light (0-15)
        float lightEfficiency = Math.max(0.1f, skyLightLevel / 15.0f);
        
        // Combine all efficiency factors
        float totalEfficiency = timeEfficiency * weatherEfficiency * lightEfficiency;
        
        // Calculate final energy production
        if (timeEfficiency == 0.0f) {
            // Night time - no energy production
            this.currentEnergyProduction = 0;
        } else {
            // Daytime - ensure minimum of 5 energy per tick
            int baseProduction = MAX_ENERGY_PER_TICK - MIN_ENERGY_PER_TICK;
            this.currentEnergyProduction = MIN_ENERGY_PER_TICK + Math.round(baseProduction * totalEfficiency);
        }
        this.lastLightLevel = lightEfficiency;
        
        // Debug logging
        Circuitmod.LOGGER.info("[SOLAR-PANEL-DEBUG] Sky: {}, Light: {}, Time: {}, TimeEff: {}, WeatherEff: {}, LightEff: {}, TotalEff: {}, Energy: {}", 
            canSeeSky, skyLightLevel, timeOfDay, timeEfficiency, weatherEfficiency, lightEfficiency, totalEfficiency, this.currentEnergyProduction);
        
        // Mark dirty to save the updated state
        markDirty();
    }
    
    /**
     * Calculates time-based efficiency (peak at noon, minimum at midnight)
     */
    private float calculateTimeEfficiency(long timeOfDay) {
        // In Minecraft: 0=6AM, 6000=noon, 12000=6PM, 18000=midnight
        // Convert to a 0-24 hour scale where 0=6AM
        float hour = (timeOfDay / 1000.0f);
        
        // Night time: 18:00 (6PM) to 6:00 (6AM) - no energy production
        if (hour >= 18.0f || hour < 6.0f) {
            return 0.0f;
        } else if (hour >= 10.0f && hour <= 14.0f) {
            // Peak hours (10 AM - 2 PM) - maximum efficiency
            return 1.0f;
        } else {
            // Dawn/dusk - gradual efficiency change
            if (hour < 10.0f) {
                // Morning: gradual increase from 0% at 6 AM to 100% at 10 AM
                return ((hour - 6.0f) / 4.0f);
            } else {
                // Evening: gradual decrease from 100% at 2 PM to 0% at 6 PM
                return 1.0f - ((hour - 14.0f) / 4.0f);
            }
        }
    }
    
    // IEnergyProducer implementation
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
        if (world != null && world.isClient()) {
            return;
        }
        
        // Log network changes
        if (this.network != null && network != null && this.network != network) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-NETWORK] Solar panel at {} changing networks: {} -> {}", 
                pos, this.network.getNetworkId(), network.getNetworkId());
        } else if (network != null && this.network == null) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-NETWORK] Solar panel at {} connecting to network: {}", 
                pos, network.getNetworkId());
        } else if (this.network != null && network == null) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-NETWORK] Solar panel at {} disconnecting from network: {}", 
                pos, this.network.getNetworkId());
        }
        
        this.network = network;
    }
    
    // Network connection logic
    public void findAndJoinNetwork() {
        if (world == null || world.isClient()) return;
        
        boolean foundNetwork = false;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                EnergyNetwork network = connectable.getNetwork();
                if (network != null && network != this.network) {
                    if (this.network != null) {
                        this.network.removeBlock(pos);
                    }
                    network.addBlock(pos, this);
                    foundNetwork = true;
                    break;
                }
            }
        }
        
        if (!foundNetwork && (this.network == null)) {
            EnergyNetwork newNetwork = new EnergyNetwork();
            newNetwork.addBlock(pos, this);
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                BlockEntity be = world.getBlockEntity(neighborPos);
                if (be instanceof IPowerConnectable && ((IPowerConnectable) be).getNetwork() == null) {
                    IPowerConnectable connectable = (IPowerConnectable) be;
                    if (connectable.canConnectPower(dir.getOpposite()) && this.canConnectPower(dir)) {
                        newNetwork.addBlock(neighborPos, connectable);
                    }
                }
            }
        }
    }
    
    @Override
    public int produceEnergy(int maxRequested) {
        if (world == null || world.isClient()) {
            return 0;
        }
        
        int energyToProduce = Math.min(currentEnergyProduction, maxRequested);
        
        // Debug logs (only log occasionally to avoid spam)
        if (world.getTime() % 100 == 0) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-ENERGY] Max requested: {}, Current production: {}, Producing: {}", 
                maxRequested, currentEnergyProduction, energyToProduce);
        }
        
        return energyToProduce;
    }
    
    @Override
    public int getMaxOutput() {
        // Debug logging to see what the network is requesting
        if (world != null && !world.isClient() && world.getTime() % 100 == 0) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-MAX-OUTPUT] Current production: {}, Network: {}", 
                currentEnergyProduction, network != null ? network.getNetworkId() : "NO NETWORK");
        }
        return currentEnergyProduction;
    }
    
    @Override
    public Direction[] getOutputSides() {
        return Direction.values(); // Can output to all sides
    }
    
    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        
        // Initialize energy production when world is set
        if (world != null && !world.isClient()) {
            updateEnergyProduction(world, pos);
        }
    }
    
         
    
          // Getters for interaction and debugging
    public float getLastLightLevel() {
        return lastLightLevel;
    }
        
        // Getters for debugging/GUI
    public int getCurrentEnergyProduction() {
        return currentEnergyProduction;
    }
    
    
    
    public boolean isProducingEnergy() {
        return currentEnergyProduction > MIN_ENERGY_PER_TICK;
    }
} 
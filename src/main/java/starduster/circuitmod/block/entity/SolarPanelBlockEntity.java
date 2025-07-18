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
            int hours = (int) ((timeOfDay + 6000) / 1000) % 24;
            int minutes = (int) (((timeOfDay + 6000) % 1000) * 60 / 1000);
            Circuitmod.LOGGER.info("[SOLAR-PANEL-TICK] Light: {:.2f}, Energy: {}, Network: {}, Tick: {}, Time: {} ticks ({}:{:02d})", 
                blockEntity.lastLightLevel, blockEntity.currentEnergyProduction, networkInfo, blockEntity.tickCounter, 
                timeOfDay, hours, minutes);
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
        
        // Also check if the block above is transparent to sky light
        boolean hasSkyAccess = canSeeSky || world.getBlockState(skyPos).getLuminance() == 0;
        
        Circuitmod.LOGGER.info("[SOLAR-PANEL-SKY] Can see sky: {}, Has sky access: {}, Block above: {}", 
            canSeeSky, hasSkyAccess, world.getBlockState(skyPos).getBlock().toString());
        
        if (!hasSkyAccess) {
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
        
        Circuitmod.LOGGER.info("[SOLAR-PANEL-MAIN] Sky visible: {}, Sky light level: {}, Time of day: {}", 
            canSeeSky, skyLightLevel, timeOfDay);
        
        // Calculate efficiency based on time of day (sinusoidal function)
        float timeEfficiency = calculateSolarTimeEfficiency(timeOfDay);
        
        // Debug time calculation - convert to readable format
        int hours = (int) ((timeOfDay + 6000) / 1000) % 24;
        int minutes = (int) (((timeOfDay + 6000) % 1000) * 60 / 1000);
        Circuitmod.LOGGER.info("[SOLAR-PANEL-TIME] Raw time: {} ticks ({}:{:02d}), Time efficiency: {:.3f}", 
            timeOfDay, hours, minutes, timeEfficiency);
        
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
        if (timeEfficiency <= 0.0f) {
            // Night time - no energy production
            this.currentEnergyProduction = 0;
        } else {
            // Daytime - calculate based on efficiency
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
     * Calculates sinusoidal time-based efficiency for solar power generation
     * Based on official Minecraft daylight cycle: 0=06:00, 6000=12:00, 12000=18:00, 18000=00:00
     * Peak efficiency at noon (6000 ticks), zero during night
     */
    private float calculateSolarTimeEfficiency(long timeOfDay) {
        // Official Minecraft time mapping:
        // 0 ticks = 06:00:00 (dawn/sunrise)
        // 6000 ticks = 12:00:00 (noon - peak sun)
        // 12000 ticks = 18:00:00 (dusk/sunset)
        // 18000 ticks = 00:00:00 (midnight)
        
        // Daytime is from 0 to 12000 ticks (06:00 to 18:00)
        // Nighttime is from 13000 to 23000 ticks (19:00 to 05:00)
        // Sunset transition: 12000-13000 ticks (18:00-19:00)
        // Sunrise transition: 23000-24000(0) ticks (05:00-06:00)
        
        // For realistic solar generation, produce energy only during daylight hours
        if (timeOfDay >= 13000 && timeOfDay <= 23000) {
            // Night time - no solar energy
            return 0.0f;
        }
        
        // Handle transition periods and day time
        float actualTime = timeOfDay;
        if (timeOfDay > 23000) {
            // Early morning transition (23000-24000 = 05:00-06:00)
            // Scale this to the beginning of the sine curve
            actualTime = (timeOfDay - 23000) * 12.0f; // Map 1000 ticks to 12000 for smooth transition
        } else if (timeOfDay > 12000) {
            // Evening transition (12000-13000 = 18:00-19:00)
            // Scale this to the end of the sine curve
            actualTime = 12000 - (timeOfDay - 12000) * 12.0f; // Map last 1000 ticks smoothly
        }
        
        // Create sinusoidal curve with peak at noon (6000 ticks)
        // Shift so that noon becomes 0 radians (peak of cosine)
        double shiftedTime = actualTime - 6000;
        
        // Convert to radians: 6000 ticks = π/2 radians (quarter cycle from peak to zero)
        double angleRadians = (shiftedTime * Math.PI) / 12000.0;
        
        // Use cosine to get peak at noon (when angle = 0)
        double cosValue = Math.cos(angleRadians);
        
        // Clamp to positive values only (no negative energy production)
        float efficiency = (float) Math.max(0.0, cosValue);
        
        // Convert to readable time format for logging
        int hours = (int) ((timeOfDay + 6000) / 1000) % 24;
        int minutes = (int) (((timeOfDay + 6000) % 1000) * 60 / 1000);
        
        Circuitmod.LOGGER.info("[SOLAR-PANEL-TIME-CALC] Time: {}:{:02d} ({} ticks), Shifted: {}, Angle: {:.3f}, Cos: {:.3f}, Efficiency: {:.3f}", 
            hours, minutes, timeOfDay, shiftedTime, angleRadians, cosValue, efficiency);
        
        return efficiency;
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
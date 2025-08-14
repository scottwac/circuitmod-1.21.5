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
import net.minecraft.world.LightType;

/**
 * Solar Panel Block Entity - Generates energy based on sunlight and time of day.
 * 
 * IMPORTANT: During startup, this block entity operates in a very conservative mode
 * to prevent world loading from hanging. It will only do minimal operations and
 * set basic energy production until the world has fully loaded and startup mode
 * is disabled.
 */
public class SolarPanelBlockEntity extends BlockEntity implements IEnergyProducer {
    // Energy production settings
    private static final int MAX_ENERGY_PER_TICK = 10; // Peak energy production during noon
    private static final int MIN_ENERGY_PER_TICK = 1;  // Minimum energy production during night/storms
    private static final int UPDATE_INTERVAL = 20; // Update every second
    
    // Network and state
    private EnergyNetwork network;
    private int tickCounter = 0;
    private int startupTickCounter = 0; // Track ticks during startup to prevent excessive operations
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
        nbt.putInt("startup_tick_counter", startupTickCounter);
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
        tickCounter = nbt.getInt("tick_counter").orElse(0);
        startupTickCounter = nbt.getInt("startup_tick_counter").orElse(0);
        currentEnergyProduction = nbt.getInt("current_energy_production").orElse(0);
        lastLightLevel = nbt.getFloat("last_light_level").orElse(0.0f);
        
        // Load network data if present
        if (nbt.contains("energy_network")) {
            try {
                NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
                network = new EnergyNetwork();
                network.readFromNbt(networkNbt);
            } catch (Exception e) {
                Circuitmod.LOGGER.error("[SOLAR-PANEL] Failed to load network data from NBT", e);
                network = null;
            }
        }
        
        // Mark that we need to refresh network connections
        needsNetworkRefresh = true;
    }

    // Tick method called by the ticker in SolarPanel
    public static void tick(World world, BlockPos pos, BlockState state, SolarPanelBlockEntity blockEntity) {
        // Wait 5 seconds (100 ticks) after world load before doing any solar panel logic
        if (world.getTime() < 100) {
            // During the first 5 seconds, just set minimal energy production and do nothing else
            if (blockEntity.currentEnergyProduction == 0) {
                blockEntity.currentEnergyProduction = 1;
            }
            return;
        }
        
        // After 5 seconds, check if we're still in startup mode
        if (starduster.circuitmod.power.EnergyNetwork.startupMode) {
            // Still in startup mode, be very conservative
            if (blockEntity.needsNetworkRefresh && world.getTime() > 200) {
                // Wait until world has settled before trying to join networks
                blockEntity.findAndJoinNetwork();
                blockEntity.needsNetworkRefresh = false;
            }
            
            // Set minimal energy production during startup
            if (blockEntity.currentEnergyProduction == 0) {
                blockEntity.currentEnergyProduction = 1;
            }
            return;
        }
        
        // Normal operation - after startup mode is disabled
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
        if (blockEntity.currentEnergyProduction == 0 && blockEntity.tickCounter > UPDATE_INTERVAL) {
            blockEntity.updateEnergyProduction(world, pos);
        }
    }
    
    /**
     * Updates energy production based on current light conditions
     */
    private void updateEnergyProduction(World world, BlockPos pos) {
        if (world.isClient()) return;
        
        // CRITICAL SAFETY CHECK: During the first 5 seconds (100 ticks), completely skip all sky checks to prevent hanging
        if (world.getTime() < 100) {
            // Just set minimal energy production and return immediately
            this.currentEnergyProduction = 1;
            this.lastLightLevel = 0.1f;
            return;
        }
        
        // Only log if NOT in startup mode
        if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-UPDATE] Starting energy production update at {}", pos);
        }
        
        // Check if there's a clear path to the sky
        BlockPos skyPos = pos.up();
        
        // Check if the chunk is loaded before doing any sky/light operations
        if (!world.isChunkLoaded(skyPos.getX() >> 4, skyPos.getZ() >> 4)) {
            // Chunk not loaded, use minimal production
            this.currentEnergyProduction = 1;
            this.lastLightLevel = 0.1f;
            return;
        }
        
        // Get light level at the panel position
        float lightLevel = world.getLightLevel(LightType.SKY, pos.up());
        
        // Check if there's a clear path to the sky (only after 20 seconds to prevent hanging)
        boolean hasClearSky = world.getLightLevel(LightType.SKY, pos.up()) > 0;
        
        if (!hasClearSky) {
            // No clear sky, minimal production
            this.currentEnergyProduction = 1;
            this.lastLightLevel = 0.1f;
            return;
        }
        
        // Calculate time-based efficiency
        long timeOfDay = world.getTimeOfDay();
        float timeEfficiency = calculateSolarTimeEfficiency(timeOfDay);
        
        // Calculate weather efficiency
        float weatherEfficiency = calculateWeatherEfficiency(world);
        
        // Calculate final energy production
        float baseProduction = lightLevel * timeEfficiency * weatherEfficiency;
        this.currentEnergyProduction = Math.max(1, Math.round(baseProduction * 10)); // Scale up for reasonable output
        
        // Update last light level for comparison
        this.lastLightLevel = lightLevel;
        
        if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-UPDATE] Light: {}, Time: {}, Weather: {}, Production: {}", 
                lightLevel, timeEfficiency, weatherEfficiency, this.currentEnergyProduction);
        }
    }
    
    /**
     * Calculates weather-based efficiency for solar power generation
     */
    private float calculateWeatherEfficiency(World world) {
        if (world.isThundering()) {
            return 0.1f; // Very little energy during storms
        } else if (world.isRaining()) {
            return 0.3f; // 30% efficiency in rain
        } else {
            return 1.0f; // Full efficiency in clear weather
        }
    }

    /**
     * Calculates sinusoidal time-based efficiency for solar power generation
     * Based on official Minecraft daylight cycle: 0=06:00, 6000=12:00, 12000=18:00, 18000=00:00
     * Peak efficiency at noon (6000 ticks), zero during night
     */
    private float calculateSolarTimeEfficiency(long timeOfDay) {
        // During startup, return minimal efficiency to prevent hanging
        if (starduster.circuitmod.power.EnergyNetwork.startupMode) {
            return 0.1f; // Minimal efficiency during startup
        }
        
        // Shift time so that 0 = midnight (18000 ticks in vanilla)
        long shiftedTime = (timeOfDay + 18000) % 24000;
        
        // Convert to radians (0 to 2π)
        float angleRadians = (float) (shiftedTime * 2 * Math.PI / 24000);
        
        // Calculate cosine value (1 at midnight, -1 at noon)
        float cosValue = (float) Math.cos(angleRadians);
        
        // Clamp to positive values only (no negative energy production)
        float efficiency = (float) Math.max(0.0, cosValue);
        
        // Convert to readable time format for logging
        int hours = (int) ((timeOfDay + 6000) / 1000) % 24;
        int minutes = (int) (((timeOfDay + 6000) % 1000) * 60 / 1000);
        
        // Only log during startup mode if we're not in the very early startup phase
        if (!starduster.circuitmod.power.EnergyNetwork.startupMode || world.getTime() > 50) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-TIME-CALC] Time: {}:{:02d} ({} ticks), Shifted: {}, Angle: {:.3f}, Cos: {:.3f}, Efficiency: {:.3f}", 
                hours, minutes, timeOfDay, shiftedTime, angleRadians, cosValue, efficiency);
        }
        
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
        
        // Log network changes (only if not in startup mode to reduce spam)
        if (!starduster.circuitmod.power.EnergyNetwork.startupMode) {
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
        }
        
        this.network = network;
    }
    
    // Network connection logic
    public void findAndJoinNetwork() {
        if (world == null || world.isClient()) return;
        
        // CRITICAL SAFETY CHECK: During the first 5 seconds, just set minimal values to prevent hanging
        if (world.getTime() < 100) {
            // Just set minimal energy production and do nothing else
            this.currentEnergyProduction = 1;
            this.lastLightLevel = 0.1f;
            return;
        }
        
        // During startup mode, be very conservative
        if (starduster.circuitmod.power.EnergyNetwork.startupMode) {
            // Just create a simple network for this panel during startup
            if (this.network == null) {
                try {
                    EnergyNetwork newNetwork = new EnergyNetwork();
                    newNetwork.addBlock(pos, this);
                } catch (Exception e) {
                    // If anything goes wrong during startup, just continue with no network
                    Circuitmod.LOGGER.warn("[SOLAR-PANEL] Failed to create network during startup: {}", e.getMessage());
                }
            }
            return;
        }
        
        // Normal network joining logic (only after startup)
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
        
        // If no network found, create a new one
        if (!foundNetwork && this.network == null) {
            EnergyNetwork newNetwork = new EnergyNetwork();
            newNetwork.addBlock(pos, this);
        }
    }
    
    @Override
    public int produceEnergy(int maxRequested) {
        if (world == null || world.isClient()) {
            return 0;
        }
        
        int energyToProduce = Math.min(currentEnergyProduction, maxRequested);
        
        // Debug logs (only log occasionally to avoid spam and not during startup)
        if (world.getTime() % 100 == 0 && !starduster.circuitmod.power.EnergyNetwork.startupMode) {
            Circuitmod.LOGGER.info("[SOLAR-PANEL-ENERGY] Max requested: {}, Current production: {}, Producing: {}", 
                maxRequested, currentEnergyProduction, energyToProduce);
        }
        
        return energyToProduce;
    }
    
    @Override
    public int getMaxOutput() {
        // Debug logging to see what the network is requesting
        if (world != null && !world.isClient() && world.getTime() % 100 == 0 && !starduster.circuitmod.power.EnergyNetwork.startupMode) {
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
            // During the first 5 seconds, just set minimal values to prevent hanging
            if (world.getTime() < 100) {
                this.currentEnergyProduction = 1;
                this.lastLightLevel = 0.1f;
            } else {
                // Only call updateEnergyProduction after 5 seconds to prevent hanging
                updateEnergyProduction(world, pos);
            }
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
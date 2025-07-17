package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.entity.damage.ElectricDamageSource;

import java.util.List;
import java.util.function.Predicate;

public class ElectricCarpetBlockEntity extends BlockEntity implements IEnergyConsumer {
    // Energy properties
    private static final int ENERGY_DEMAND_PER_TICK = 1; // Consumes 1 energy per tick when active
    private EnergyNetwork network;
    private boolean needsNetworkRefresh = false;
    private int energyReceived = 0; // Energy received this tick
    private boolean isActive = false; // Whether the carpet is currently powered and active
    
    // Damage properties
    private static final float DAMAGE_PER_TICK = 2.0f; // Damage per tick when entity is on carpet
    private static final int DAMAGE_INTERVAL = 2; // Damage every 10 ticks (0.5 seconds)
    private int damageTimer = 0;
    
    public ElectricCarpetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_CARPET_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putBoolean("is_active", isActive);
        nbt.putInt("energy_received", energyReceived);
        nbt.putInt("damage_timer", damageTimer);
        
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
        this.isActive = nbt.getBoolean("is_active").orElse(false);
        this.energyReceived = nbt.getInt("energy_received").orElse(0);
        this.damageTimer = nbt.getInt("damage_timer").orElse(0);
        
        // Load network data
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        this.needsNetworkRefresh = true;
    }

    // Add network handling logic to the tick method
    public static void tick(World world, BlockPos pos, BlockState state, ElectricCarpetBlockEntity entity) {
        if (entity.needsNetworkRefresh) {
            entity.findAndJoinNetwork();
            entity.needsNetworkRefresh = false;
        }
        
        if (world.isClient()) {
            return;
        }
        
        // Update active status based on energy received
        boolean wasActive = entity.isActive;
        entity.isActive = entity.energyReceived > 0;
        
        // If status changed, mark dirty
        if (wasActive != entity.isActive) {
            entity.markDirty();
            
            // If we just became active and have a network, propagate to adjacent carpets
            if (entity.isActive && entity.network != null) {
                entity.propagateToAdjacentCarpets(entity.network);
            }
        }
        
        // Only damage entities if the carpet is active
        if (entity.isActive) {
            entity.damageTimer++;
            
            // Damage entities every DAMAGE_INTERVAL ticks
            if (entity.damageTimer >= DAMAGE_INTERVAL) {
                entity.damageTimer = 0;
                entity.damageEntitiesOnCarpet((ServerWorld) world, pos);
            }
        } else {
            // Reset damage timer when not active
            entity.damageTimer = 0;
        }
        
        // Reset energy received at the end of each tick
        entity.energyReceived = 0;
    }
    
    /**
     * Damages all entities standing on the electric carpet
     */
    private void damageEntitiesOnCarpet(ServerWorld world, BlockPos pos) {
        // Create a box that covers the carpet area and a bit above it
        Box carpetBox = new Box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1.0, pos.getZ() + 1
        );
        
        // Get all entities in the carpet area
        List<Entity> entities = world.getOtherEntities(null, carpetBox, 
            entity -> entity instanceof LivingEntity);
        
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity livingEntity) {
                // Check if the entity is actually standing on the carpet
                if (isEntityStandingOnCarpet(livingEntity, pos)) {
                    // Create a custom damage source for electrocution
                    DamageSource electrocutionDamage = createElectrocutionDamageSource(world);
                    
                    // Apply damage
                    livingEntity.damage(world, electrocutionDamage, DAMAGE_PER_TICK);
                    
                    // Log the damage (only occasionally to avoid spam)
                    if (world.getTime() % 100 == 0) {
                        Circuitmod.LOGGER.info("[ELECTRIC-CARPET] Damaged {} at {} with {} damage", 
                            livingEntity.getName().getString(), pos, DAMAGE_PER_TICK);
                    }
                }
            }
        }
    }
    
    /**
     * Creates a custom damage source for electrocution
     */
    private DamageSource createElectrocutionDamageSource(ServerWorld world) {
        return ElectricDamageSource.create(world);
    }
    
    /**
     * Checks if an entity is actually standing on the electric carpet
     */
    private boolean isEntityStandingOnCarpet(LivingEntity entity, BlockPos carpetPos) {
        // Get the entity's bounding box
        Box entityBox = entity.getBoundingBox();
        
        // Get the carpet's bounding box (just the top surface)
        Box carpetSurface = new Box(
            carpetPos.getX(), carpetPos.getY() + 0.0625, carpetPos.getZ(), // 1/16th block above carpet
            carpetPos.getX() + 1, carpetPos.getY() + 0.125, carpetPos.getZ() + 1 // 1/8th block above carpet
        );
        
        // Check if the entity's feet are intersecting with the carpet surface
        if (!entityBox.intersects(carpetSurface)) {
            return false;
        }
        
        // Additional check: make sure the entity is actually on top of the carpet
        // The entity's bottom should be very close to the carpet's top
        double entityBottom = entityBox.minY;
        double carpetTop = carpetPos.getY() + 0.125; // 1/8th block above carpet
        
        // Allow a small tolerance for the entity to be considered "standing on" the carpet
        double tolerance = 0.1; // 0.1 blocks tolerance
        
        return Math.abs(entityBottom - carpetTop) <= tolerance;
    }
    
    /**
     * Finds and joins an energy network
     */
    private void findAndJoinNetwork() {
        if (world == null || world.isClient()) {
            return;
        }
        
        // First, look for nearby power cables or other power connectables
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
            
            if (neighborEntity instanceof IPowerConnectable connectable) {
                EnergyNetwork neighborNetwork = connectable.getNetwork();
                if (neighborNetwork != null) {
                    // Join the neighbor's network
                    neighborNetwork.addBlock(pos, this);
                    Circuitmod.LOGGER.info("[ELECTRIC-CARPET] Joined network {} at {} via power connectable", 
                        neighborNetwork.getNetworkId(), pos);
                    
                    // After joining, check for adjacent carpets and add them to the network
                    propagateToAdjacentCarpets(neighborNetwork);
                    return;
                }
            }
        }
        
        // If no direct power connection found, check for adjacent electric carpets
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
            
            if (neighborEntity instanceof ElectricCarpetBlockEntity neighborCarpet) {
                EnergyNetwork neighborNetwork = neighborCarpet.getNetwork();
                if (neighborNetwork != null) {
                    // Join the neighbor carpet's network
                    neighborNetwork.addBlock(pos, this);
                    Circuitmod.LOGGER.info("[ELECTRIC-CARPET] Joined network {} at {} via adjacent carpet", 
                        neighborNetwork.getNetworkId(), pos);
                    
                    // After joining, check for other adjacent carpets and add them to the network
                    propagateToAdjacentCarpets(neighborNetwork);
                    return;
                }
            }
        }
        
        // If no network found, create a new one
        if (network == null) {
            network = new EnergyNetwork();
            network.addBlock(pos, this);
            Circuitmod.LOGGER.info("[ELECTRIC-CARPET] Created new network {} at {}", 
                network.getNetworkId(), pos);
        }
    }
    
    /**
     * Propagates the network to adjacent electric carpets that aren't connected
     */
    private void propagateToAdjacentCarpets(EnergyNetwork network) {
        if (world == null || world.isClient() || network == null) {
            return;
        }
        
        // Check all adjacent positions for electric carpets
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
            
            if (neighborEntity instanceof ElectricCarpetBlockEntity neighborCarpet) {
                // If the neighbor carpet doesn't have a network, add it to ours
                if (neighborCarpet.getNetwork() == null) {
                    network.addBlock(neighborPos, neighborCarpet);
                    Circuitmod.LOGGER.info("[ELECTRIC-CARPET] Added adjacent carpet at {} to network {}", 
                        neighborPos, network.getNetworkId());
                    
                    // Recursively propagate to the neighbor's adjacent carpets
                    neighborCarpet.propagateToAdjacentCarpets(network);
                }
            }
        }
    }
    
    // IEnergyConsumer implementation
    @Override
    public boolean canConnectPower(Direction side) {
        // Can connect from any side, including adjacent electric carpets
        return true;
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
    public int consumeEnergy(int energyOffered) {
        if (world == null || world.isClient()) {
            return 0;
        }
        
        // Calculate how much energy we can actually consume
        int energyToConsume = Math.min(energyOffered, ENERGY_DEMAND_PER_TICK);
        
        // Track the received energy for this tick
        if (energyToConsume > 0) {
            this.energyReceived += energyToConsume;
        }
        
        return energyToConsume;
    }
    
    @Override
    public int getEnergyDemand() {
        return ENERGY_DEMAND_PER_TICK;
    }
    
    @Override
    public Direction[] getInputSides() {
        return Direction.values(); // Can receive from all sides
    }
    
    /**
     * Gets whether the carpet is currently active (powered)
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Called when this carpet is being removed/broken
     * Removes itself from the network and triggers network recalculation
     */
    public void onRemoved() {
        if (network != null) {
            network.removeBlock(pos);
            Circuitmod.LOGGER.info("[ELECTRIC-CARPET] Removed carpet at {} from network {}", 
                pos, network.getNetworkId());
            
            // Trigger network recalculation for adjacent carpets
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.offset(direction);
                BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
                
                if (neighborEntity instanceof ElectricCarpetBlockEntity neighborCarpet) {
                    neighborCarpet.needsNetworkRefresh = true;
                }
            }
        }
    }
} 
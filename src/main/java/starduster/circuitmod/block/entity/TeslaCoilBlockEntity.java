package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
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

public class TeslaCoilBlockEntity extends BlockEntity implements IEnergyConsumer {
    // Energy properties
    private static final int ENERGY_DEMAND_PER_TICK = 20; // Consumes 20 energy per tick when active
    private EnergyNetwork network;
    private boolean needsNetworkRefresh = false;
    private int energyReceived = 0; // Energy received this tick
    private boolean isActive = false; // Whether the tesla coil is currently powered and active
    
    // Damage properties
    private static final float DAMAGE_PER_TICK = 2.0f; // Same damage as electric carpet
    private static final int DAMAGE_INTERVAL = 2; // Damage every 2 ticks (0.1 seconds)
    private static final int DAMAGE_RANGE = 5; // 5 block range for damage
    private int damageTimer = 0;
    
    public TeslaCoilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TESLA_COIL_BLOCK_ENTITY, pos, state);
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
    public static void tick(World world, BlockPos pos, BlockState state, TeslaCoilBlockEntity entity) {
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
        
        // If status changed, mark dirty and update block state
        if (wasActive != entity.isActive) {
            entity.markDirty();
            
            // Update the block state to show running status
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.setBlockState(pos, state.with(starduster.circuitmod.block.machines.TeslaCoil.RUNNING, entity.isActive));
            }
        }
        
        // Only damage entities if the tesla coil is active
        if (entity.isActive) {
            entity.damageTimer++;
            
            // Damage entities every DAMAGE_INTERVAL ticks
            if (entity.damageTimer >= DAMAGE_INTERVAL) {
                entity.damageTimer = 0;
                entity.damageEntitiesInRange((ServerWorld) world, pos);
            }
        } else {
            // Reset damage timer when not active
            entity.damageTimer = 0;
        }
        
        // Reset energy received at the end of each tick
        entity.energyReceived = 0;
    }
    
    /**
     * Damages all entities within the tesla coil's range
     */
    private void damageEntitiesInRange(ServerWorld world, BlockPos pos) {
        // Create a box that covers the tesla coil's range
        Box damageBox = new Box(
            pos.getX() - DAMAGE_RANGE, pos.getY() - DAMAGE_RANGE, pos.getZ() - DAMAGE_RANGE,
            pos.getX() + DAMAGE_RANGE + 1, pos.getY() + DAMAGE_RANGE + 1, pos.getZ() + DAMAGE_RANGE + 1
        );
        
        // Get all entities in the damage range
        List<Entity> entities = world.getOtherEntities(null, damageBox, 
            entity -> entity instanceof LivingEntity);
        
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity livingEntity) {
                // Check if the entity is within the exact range (not just the box)
                if (isEntityInRange(livingEntity, pos)) {
                    // Create a custom damage source for electrocution
                    ElectricDamageSource electrocutionDamage = ElectricDamageSource.create(world);
                    
                    // Apply damage
                    livingEntity.damage(world, electrocutionDamage, DAMAGE_PER_TICK);
                    
                    // Log the damage (only occasionally to avoid spam)
                    if (world.getTime() % 100 == 0) {
                        Circuitmod.LOGGER.info("[TESLA-COIL] Damaged {} at {} with {} damage", 
                            livingEntity.getName().getString(), pos, DAMAGE_PER_TICK);
                    }
                }
            }
        }
    }
    
    /**
     * Checks if an entity is within the tesla coil's damage range
     */
    private boolean isEntityInRange(LivingEntity entity, BlockPos teslaPos) {
        // Calculate the distance from the tesla coil to the entity
        double distance = Math.sqrt(
            Math.pow(entity.getX() - (teslaPos.getX() + 0.5), 2) +
            Math.pow(entity.getY() - (teslaPos.getY() + 0.5), 2) +
            Math.pow(entity.getZ() - (teslaPos.getZ() + 0.5), 2)
        );
        
        return distance <= DAMAGE_RANGE;
    }
    
    /**
     * Finds and joins an energy network
     */
    private void findAndJoinNetwork() {
        if (world == null || world.isClient()) {
            return;
        }
        
        // Look for nearby power cables or other power connectables
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
            
            if (neighborEntity instanceof IPowerConnectable connectable) {
                EnergyNetwork neighborNetwork = connectable.getNetwork();
                if (neighborNetwork != null) {
                    // Join the neighbor's network
                    neighborNetwork.addBlock(pos, this);
                    Circuitmod.LOGGER.info("[TESLA-COIL] Joined network {} at {}", 
                        neighborNetwork.getNetworkId(), pos);
                    return;
                }
            }
        }
        
        // If no network found, create a new one
        if (network == null) {
            network = new EnergyNetwork();
            network.addBlock(pos, this);
            Circuitmod.LOGGER.info("[TESLA-COIL] Created new network {} at {}", 
                network.getNetworkId(), pos);
        }
    }
    
    @Override
    public boolean canConnectPower(Direction side) {
        return true; // Tesla coil can connect from any side
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
        if (!isActive) {
            // If not active, consume all offered energy to become active
            int consumed = Math.min(energyOffered, ENERGY_DEMAND_PER_TICK);
            energyReceived = consumed;
            return consumed;
        } else {
            // If already active, consume the required amount
            int consumed = Math.min(energyOffered, ENERGY_DEMAND_PER_TICK);
            energyReceived = consumed;
            return consumed;
        }
    }
    
    @Override
    public int getEnergyDemand() {
        return ENERGY_DEMAND_PER_TICK;
    }
    
    @Override
    public Direction[] getInputSides() {
        return Direction.values(); // Can receive energy from any direction
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void onRemoved() {
        if (network != null) {
            network.removeBlock(pos);
            Circuitmod.LOGGER.info("[TESLA-COIL] Removed from network {} at {}", 
                network.getNetworkId(), pos);
        }
    }
} 
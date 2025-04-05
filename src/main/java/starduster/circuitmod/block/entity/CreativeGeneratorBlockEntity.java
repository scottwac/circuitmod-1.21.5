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
import starduster.circuitmod.power.IEnergyProducer;

public class CreativeGeneratorBlockEntity extends BlockEntity implements IEnergyProducer {
    private static final int ENERGY_PER_TICK = 1;
    private static final int UPDATE_INTERVAL = 100; // 5 seconds (at 20 ticks per second)
    
    private EnergyNetwork network;
    private int tickCounter = 0;
    
    public CreativeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_GENERATOR_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save tick counter
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
        
        // Load tick counter
        this.tickCounter = nbt.getInt("tick_counter").orElse(0);
        
        // Load network data
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
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
        this.network = network;
    }
    
    @Override
    public int produceEnergy(int maxRequested) {
        // Always produce a fixed amount of energy
        return Math.min(ENERGY_PER_TICK, maxRequested);
    }
    
    @Override
    public int getMaxOutput() {
        return ENERGY_PER_TICK;
    }
    
    @Override
    public Direction[] getOutputSides() {
        return Direction.values(); // Can output to all sides
    }
    
    // Tick method
    public static void tick(World world, BlockPos pos, BlockState state, CreativeGeneratorBlockEntity blockEntity) {
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
                
                player.sendMessage(Text.literal("§a[Creative Generator] §7Producing " + ENERGY_PER_TICK + " energy/tick"), false);
                
                if (blockEntity.network != null) {
                    player.sendMessage(Text.literal("§7Network energy: " + blockEntity.network.getStoredEnergy() + "/" 
                        + blockEntity.network.getMaxStorage() + " (+" 
                        + blockEntity.network.getLastTickEnergyProduced() + ", -" 
                        + blockEntity.network.getLastTickEnergyConsumed() + ")"), false);
                }
            }
        }
    }
} 
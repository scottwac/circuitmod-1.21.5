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
import starduster.circuitmod.power.IPowerConnectable;

public class MegaCreativeGeneratorBlockEntity extends BlockEntity implements IEnergyProducer {
    private static final int ENERGY_PER_TICK = 1000;
    private static final int UPDATE_INTERVAL = 100; // 5 seconds (at 20 ticks per second)
    
    private EnergyNetwork network;
    private int tickCounter = 0;
    private boolean needsNetworkRefresh = false;
    
    public MegaCreativeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MEGA_CREATIVE_GENERATOR_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("tick_counter", tickCounter);
        if (network != null) {
            NbtCompound networkNbt = new NbtCompound();
            network.writeToNbt(networkNbt);
            nbt.put("energy_network", networkNbt);
        }
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.tickCounter = nbt.getInt("tick_counter").orElse(0);
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        this.needsNetworkRefresh = true;
    }
    
    @Override
    public boolean canConnectPower(Direction side) {
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
    public int produceEnergy(int maxRequested) {
        return Math.min(ENERGY_PER_TICK, maxRequested);
    }
    
    @Override
    public int getMaxOutput() {
        return ENERGY_PER_TICK;
    }
    
    @Override
    public Direction[] getOutputSides() {
        return Direction.values();
    }
    
    public static void tick(World world, BlockPos pos, BlockState state, MegaCreativeGeneratorBlockEntity blockEntity) {
        if (world.isClient() || blockEntity.network == null) {
            return;
        }
        blockEntity.tickCounter++;
        if (blockEntity.tickCounter >= UPDATE_INTERVAL) {
            blockEntity.tickCounter = 0;
            for (PlayerEntity player : ((ServerWorld)world).getPlayers(p -> 
                p.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 1024)) {
                player.sendMessage(Text.literal("§a[Mega Creative Generator] §7Producing " + ENERGY_PER_TICK + " energy/tick"), false);
                if (blockEntity.network != null) {
                    player.sendMessage(Text.literal("§7Network energy: " + blockEntity.network.getStoredEnergy() + "/" 
                        + blockEntity.network.getMaxStorage() + " (+" 
                        + blockEntity.network.getLastTickEnergyProduced() + ", -" 
                        + blockEntity.network.getLastTickEnergyConsumed() + ")"), false);
                }
            }
        }
        if (blockEntity.needsNetworkRefresh) {
            blockEntity.findAndJoinNetwork();
            blockEntity.needsNetworkRefresh = false;
        }
    }

    public void findAndJoinNetwork() {
        if (world == null || world.isClient) return;
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
} 
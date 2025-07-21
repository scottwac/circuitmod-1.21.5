package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.FuelRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.block.machines.Generator;
import starduster.circuitmod.block.machines.QuarryBlock;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyProducer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.screen.GeneratorScreenHandler;
import starduster.circuitmod.sound.ModSounds;
import starduster.circuitmod.util.ImplementedInventory;

public class GeneratorBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory, IEnergyProducer, IPowerConnectable {
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    
    private static final int FUEL_SLOT = 0;
    
    protected final PropertyDelegate propertyDelegate;
    private int burnTime = 0;
    private int maxBurnTime = 0;
    private int powerProduction = 0;
    private static final int BASE_POWER_PRODUCTION = 6; // Base power per tick when burning
    
    // Power network connection
    private EnergyNetwork network = null;
    
    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR_BLOCK_ENTITY, pos, state);
        this.propertyDelegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                switch (index) {
                    case 0 -> {return GeneratorBlockEntity.this.burnTime;}
                    case 1 -> {return GeneratorBlockEntity.this.maxBurnTime;}
                    case 2 -> {return GeneratorBlockEntity.this.powerProduction;}
                    default -> {return 0;}
                }
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> GeneratorBlockEntity.this.burnTime = value;
                    case 1 -> GeneratorBlockEntity.this.maxBurnTime = value;
                    case 2 -> GeneratorBlockEntity.this.powerProduction = value;
                }
            }

            @Override
            public int size() {
                return 3;
            }
        };
    }
    
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.generator");
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GeneratorScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }
    
    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, inventory, registries);
        nbt.putInt("generator.burn_time", burnTime);
        nbt.putInt("generator.max_burn_time", maxBurnTime);
        nbt.putInt("generator.power_production", powerProduction);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, inventory, registries);
        burnTime = nbt.getInt("generator.burn_time").orElse(0);
        maxBurnTime = nbt.getInt("generator.max_burn_time").orElse(0);
        powerProduction = nbt.getInt("generator.power_production").orElse(0);
    }

    private int soundClock = 0;
    public void tick(World world, BlockPos pos, BlockState state, GeneratorBlockEntity entity) {
        boolean burningBefore = entity.isBurning();
        
        // Decrease burn time
        if (entity.burnTime > 0) {
            entity.burnTime--;
        }
        
        // Try to start burning if we have fuel and aren't currently burning
        if (entity.burnTime <= 0 && entity.hasFuel()) {
            entity.consumeFuel();
        }
        
        // Update power production based on burning state
        if (entity.isBurning()) {
            entity.powerProduction = BASE_POWER_PRODUCTION;
        } else {
            entity.powerProduction = 0;
        }
        
        // Update block state if burning state changed
        if (burningBefore != entity.isBurning()) {
            world.setBlockState(pos, state.with(Generator.RUNNING, entity.isBurning()), Block.NOTIFY_ALL);
        }
        
        entity.markDirty();

        if(isBurning()) {
            if(soundClock <= 0){
                soundClock = 60;
            }
            if(soundClock == 60) {
                world.playSound(null, pos, ModSounds.BURNING_FUEL_GENERATOR, SoundCategory.BLOCKS, 0.5F, 1F);
            }
            soundClock = soundClock - 1;
        }
        if(!isBurning()) {
            soundClock = 60;
        }
    }
    
    public boolean isBurning() {
        return this.burnTime > 0;
    }
    
    private void consumeFuel() {
        ItemStack fuelStack = this.getStack(FUEL_SLOT);
        if (!fuelStack.isEmpty() && world != null) {
            int fuelTime = world.getFuelRegistry().getFuelTicks(fuelStack);
            if (fuelTime > 0) {
                this.burnTime = fuelTime;
                this.maxBurnTime = fuelTime;
                this.removeStack(FUEL_SLOT, 1);
            }
        }
    }
    
    private boolean hasFuel() {
        ItemStack fuelStack = this.getStack(FUEL_SLOT);
        return !fuelStack.isEmpty() && world != null && world.getFuelRegistry().getFuelTicks(fuelStack) > 0;
    }
    
    // IEnergyProducer implementation
    @Override
    public int produceEnergy(int maxRequested) {
        if (isBurning()) {
            return Math.min(powerProduction, maxRequested);
        }
        return 0;

    }
    
    @Override
    public int getMaxOutput() {
        return isBurning() ? powerProduction : 0;
    }
    
    @Override
    public Direction[] getOutputSides() {
        return new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
    }
    
    // IPowerConnectable implementation
    @Override
    public boolean canConnectPower(Direction side) {
        return true; // Generator can connect to power network from any side
    }
    
    @Override
    public EnergyNetwork getNetwork() {
        return network;
    }
    
    @Override
    public void setNetwork(EnergyNetwork network) {
        this.network = network;
    }
} 
package starduster.circuitmod.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.fluid.IFluidStorage;
import starduster.circuitmod.screen.FluidTankScreenHandler;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.util.ImplementedInventory;

public class FluidTankBlockEntity extends BlockEntity implements IFluidStorage, ImplementedInventory, 
        ExtendedScreenHandlerFactory<ModScreenHandlers.FluidTankData> {

    // Constants
    private static final int CAPACITY_MB = 8 * 1024; // 8 buckets = 8192 millibuckets
    private static final int MB_PER_BUCKET = 1024;
    private static final int INVENTORY_SIZE = 1; // One slot for bucket interaction
    
    // Fluid storage
    private Fluid storedFluidType = Fluids.EMPTY;
    private int storedFluidAmount = 0; // in millibuckets
    
    // Inventory for bucket slot
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    
    // Property delegate for GUI synchronization
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0: return storedFluidAmount;
                case 1: return CAPACITY_MB;
                case 2: return getFluidId();
                default: return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0: storedFluidAmount = value; break;
                case 1: /* read-only capacity */ break;
                case 2: setFluidFromId(value); break;
            }
        }

        @Override
        public int size() {
            return 3;
        }
    };

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_TANK_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, FluidTankBlockEntity blockEntity) {
        blockEntity.tick();
    }

    private void tick() {
        if (world == null || world.isClient()) return;
        
        processBucketInteraction();
    }

    private void processBucketInteraction() {
        ItemStack bucketStack = getStack(0);
        if (bucketStack.isEmpty()) return;

        if (bucketStack.getItem() == Items.BUCKET) {
            // Empty bucket - try to fill from tank
            if (storedFluidAmount >= MB_PER_BUCKET && storedFluidType != Fluids.EMPTY) {
                ItemStack filledBucket = getFilledBucket(storedFluidType);
                if (filledBucket != null) {
                    storedFluidAmount -= MB_PER_BUCKET;
                    if (storedFluidAmount <= 0) {
                        storedFluidType = Fluids.EMPTY;
                        storedFluidAmount = 0;
                    }
                    setStack(0, filledBucket);
                    markDirty();
                }
            }
        } else {
            // Check for filled buckets by item type
            Fluid bucketFluid = getFluidFromBucketItem(bucketStack.getItem());
            if (bucketFluid != Fluids.EMPTY) {
                if (canAcceptFluid(bucketFluid) && storedFluidAmount + MB_PER_BUCKET <= CAPACITY_MB) {
                    if (storedFluidType == Fluids.EMPTY) {
                        storedFluidType = bucketFluid;
                    }
                    storedFluidAmount += MB_PER_BUCKET;
                    setStack(0, new ItemStack(Items.BUCKET));
                    markDirty();
                }
            }
        }
    }

    private ItemStack getFilledBucket(Fluid fluid) {
        // Map fluids to their bucket items
        if (fluid == Fluids.WATER) return new ItemStack(Items.WATER_BUCKET);
        if (fluid == Fluids.LAVA) return new ItemStack(Items.LAVA_BUCKET);
        // Add more fluid types as needed
        return null;
    }

    private Fluid getFluidFromBucketItem(Item item) {
        // Map bucket items to their fluid types
        if (item == Items.WATER_BUCKET) return Fluids.WATER;
        if (item == Items.LAVA_BUCKET) return Fluids.LAVA;
        // Add more bucket types as needed
        return Fluids.EMPTY;
    }

    // IFluidStorage implementation
    @Override
    public int insertFluid(Fluid fluid, int amount, Direction direction) {
        if (!canAcceptFluid(fluid)) return 0;
        
        int available = CAPACITY_MB - storedFluidAmount;
        int toInsert = Math.min(amount, available);
        
        if (toInsert > 0) {
            if (storedFluidType == Fluids.EMPTY) {
                storedFluidType = fluid;
            }
            storedFluidAmount += toInsert;
            markDirty();
        }
        
        return toInsert;
    }

    @Override
    public int extractFluid(Fluid fluid, int amount, Direction direction) {
        if (fluid != null && fluid != storedFluidType) return 0;
        if (storedFluidAmount <= 0) return 0;
        
        int toExtract = Math.min(amount, storedFluidAmount);
        storedFluidAmount -= toExtract;
        
        if (storedFluidAmount <= 0) {
            storedFluidType = Fluids.EMPTY;
            storedFluidAmount = 0;
        }
        
        markDirty();
        return toExtract;
    }

    @Override
    public Fluid getStoredFluidType() {
        return storedFluidType;
    }

    @Override
    public int getStoredFluidAmount() {
        return storedFluidAmount;
    }

    @Override
    public int getMaxFluidCapacity() {
        return CAPACITY_MB;
    }

    @Override
    public boolean canAcceptFluid(Fluid fluid) {
        return storedFluidType == Fluids.EMPTY || storedFluidType == fluid;
    }

    @Override
    public Direction[] getFluidInputSides() {
        return Direction.values(); // Accept from all sides
    }

    @Override
    public Direction[] getFluidOutputSides() {
        return Direction.values(); // Output to all sides
    }

    // IFluidConnectable implementation
    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public void onNetworkUpdate() {
        // Implementation for fluid network updates
    }

    @Override
    public boolean canConnectFluid() {
        return true;
    }

    // Inventory implementation
    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    // Screen handler factory implementation
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.fluid_tank");
    }

    @Override
    public ModScreenHandlers.FluidTankData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.FluidTankData(this.pos);
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new FluidTankScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }

    // NBT serialization
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        
        Inventories.writeNbt(nbt, inventory, registryLookup);
        
        // Simple fluid storage - just store if we have water or lava
        if (storedFluidType == Fluids.WATER) {
            nbt.putString("FluidType", "water");
        } else if (storedFluidType == Fluids.LAVA) {
            nbt.putString("FluidType", "lava");
        } else {
            nbt.putString("FluidType", "empty");
        }
        nbt.putInt("FluidAmount", storedFluidAmount);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        
        Inventories.readNbt(nbt, inventory, registryLookup);
        
        // Simple fluid loading using the codebase pattern
        String fluidType = nbt.getString("FluidType").orElse("empty");
        switch (fluidType) {
            case "water" -> storedFluidType = Fluids.WATER;
            case "lava" -> storedFluidType = Fluids.LAVA;
            default -> storedFluidType = Fluids.EMPTY;
        }
        
        storedFluidAmount = nbt.getInt("FluidAmount").orElse(0);
    }

    // Helper methods for property delegate
    private int getFluidId() {
        return Registries.FLUID.getRawId(storedFluidType);
    }

    private void setFluidFromId(int id) {
        storedFluidType = Registries.FLUID.get(id);
        if (storedFluidType == null) storedFluidType = Fluids.EMPTY;
    }

    // Getters for GUI
    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }

    public double getFluidPercentage() {
        return CAPACITY_MB > 0 ? (double) storedFluidAmount / CAPACITY_MB : 0.0;
    }
} 
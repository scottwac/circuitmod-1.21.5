package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.power.EnergyNetwork;
import starduster.circuitmod.power.IEnergyConsumer;
import starduster.circuitmod.power.IPowerConnectable;
import starduster.circuitmod.screen.ElectricFurnaceScreenHandler;
import starduster.circuitmod.util.ImplementedInventory;

import java.util.Optional;

public class ElectricFurnaceBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory, SidedInventory, IEnergyConsumer {
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
    
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    
    // Furnace properties
    private int cookingTimeSpent = 0;
    private int cookingTotalTime = 0;
    
    // Energy properties
    private static final int ENERGY_DEMAND_PER_TICK = 1; // Consumes 1 energy per tick when powered
    private EnergyNetwork network;
    private boolean needsNetworkRefresh = false;
    private int energyReceived = 0; // Energy received this tick
    private boolean isPowered = false; // Whether we're receiving power
    
    // Property delegate for GUI synchronization
    protected final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0 -> {return ElectricFurnaceBlockEntity.this.isPowered ? 1 : 0;}
                case 1 -> {return 0;} // Not used for electric furnace
                case 2 -> {return ElectricFurnaceBlockEntity.this.cookingTimeSpent;}
                case 3 -> {return ElectricFurnaceBlockEntity.this.cookingTotalTime;}
                case 4 -> {return ElectricFurnaceBlockEntity.this.isPowered ? 1 : 0;}
                default -> {return 0;}
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 2 -> ElectricFurnaceBlockEntity.this.cookingTimeSpent = value;
                case 3 -> ElectricFurnaceBlockEntity.this.cookingTotalTime = value;
            }
        }

        @Override
        public int size() {
            return 5;
        }
    };

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_FURNACE_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.electric_furnace");
    }
    
    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new ElectricFurnaceScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }
    
    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, inventory, registries);
        nbt.putInt("cooking_time_spent", cookingTimeSpent);
        nbt.putInt("cooking_total_time", cookingTotalTime);
        nbt.putBoolean("is_powered", isPowered);
        nbt.putInt("energy_received", energyReceived);
        
        // Save network data if we have a network
        if (network != null) {
            NbtCompound networkNbt = new NbtCompound();
            network.writeToNbt(networkNbt);
            nbt.put("energy_network", networkNbt);
        }
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        Inventories.readNbt(nbt, inventory, registries);
        cookingTimeSpent = nbt.getInt("cooking_time_spent").get();
        cookingTotalTime = nbt.getInt("cooking_total_time").get();
        isPowered = nbt.getBoolean("is_powered").orElse(false);
        energyReceived = nbt.getInt("energy_received").get();
        
        // Load network data
        if (nbt.contains("energy_network")) {
            this.network = new EnergyNetwork();
            NbtCompound networkNbt = nbt.getCompound("energy_network").orElse(new NbtCompound());
            network.readFromNbt(networkNbt);
        }
        this.needsNetworkRefresh = true;
        
        super.readNbt(nbt, registries);
    }

    // Add network handling logic to the tick method
    public static void tick(World world, BlockPos pos, BlockState state, ElectricFurnaceBlockEntity entity) {
        if (world.isClient()) return;
        
        // Handle network refresh
        if (entity.needsNetworkRefresh) {
            entity.needsNetworkRefresh = false;
            if (entity.network != null) {
                entity.network.addBlock(pos, entity);
            }
        }
        
        boolean poweredBefore = entity.isPowered;
        
        // Reset energy received for this tick
        entity.energyReceived = 0;
        entity.isPowered = false;
        
        // Check if we have a recipe and can smelt
        boolean hasRecipe = entity.hasRecipe();
        boolean canSmelt = entity.canSmelt();
        
        // If powered and we have a recipe, we can smelt
        if (entity.isReceivingPower() && hasRecipe && canSmelt) {
            entity.isPowered = true;
        }
        
        // Smelting logic
        if (entity.isPowered && hasRecipe && canSmelt) {
            entity.cookingTimeSpent++;
            if (entity.cookingTimeSpent >= entity.cookingTotalTime) {
                entity.cookingTimeSpent = 0;
                entity.cookingTotalTime = entity.getCookTime();
                entity.craftItem();
            }
        } else {
            entity.cookingTimeSpent = 0;
        }
        
        // Update block state if powered state changed
        if (poweredBefore != entity.isPowered) {
            world.setBlockState(pos, state.with(starduster.circuitmod.block.machines.ElectricFurnace.LIT, entity.isPowered), Block.NOTIFY_ALL);
        }
        
        entity.markDirty();
    }
    
    public boolean isBurning() {
        return this.isPowered;
    }
    
    public boolean isReceivingPower() {
        return this.energyReceived > 0;
    }
    
    private boolean hasRecipe() {
        Optional<RecipeEntry<SmeltingRecipe>> recipe = getCurrentRecipe();
        return recipe.isPresent();
    }
    
    private boolean canSmelt() {
        Optional<RecipeEntry<SmeltingRecipe>> recipe = getCurrentRecipe();
        if (recipe.isEmpty()) {
            return false;
        }
        
        SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
        ItemStack output = recipe.get().value().craft(input, world.getRegistryManager());
        ItemStack outputSlot = this.getStack(OUTPUT_SLOT);
        
        if (outputSlot.isEmpty()) {
            return true;
        }
        
        if (!ItemStack.areItemsAndComponentsEqual(outputSlot, output)) {
            return false;
        }
        
        return outputSlot.getCount() + output.getCount() <= outputSlot.getMaxCount();
    }
    
    private void craftItem() {
        Optional<RecipeEntry<SmeltingRecipe>> recipe = getCurrentRecipe();
        if (recipe.isPresent()) {
            SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
            ItemStack output = recipe.get().value().craft(input, world.getRegistryManager());
            ItemStack outputSlot = this.getStack(OUTPUT_SLOT);
            
            if (outputSlot.isEmpty()) {
                this.setStack(OUTPUT_SLOT, output.copy());
            } else if (ItemStack.areItemsAndComponentsEqual(outputSlot, output)) {
                outputSlot.increment(output.getCount());
            }
            
            this.removeStack(INPUT_SLOT, 1);
        }
    }
    
    private Optional<RecipeEntry<SmeltingRecipe>> getCurrentRecipe() {
        if (world == null || world.isClient()) {
            return Optional.empty();
        }
        
        SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
        return ((ServerWorld) world).getRecipeManager()
                .getFirstMatch(RecipeType.SMELTING, input, world);
    }
    
    private int getCookTime() {
        if (world == null || world.isClient()) {
            return 200; // Default vanilla furnace cook time
        }
        
        SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
        Optional<RecipeEntry<SmeltingRecipe>> recipe = ((ServerWorld) world).getRecipeManager()
                .getFirstMatch(RecipeType.SMELTING, input, world);
        
        return recipe.map(recipeEntry -> recipeEntry.value().getCookingTime()).orElse(200);
    }
    
    // IEnergyConsumer implementation
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
        
        this.network = network;
    }
    
    @Override
    public int consumeEnergy(int energyOffered) {
        if (world == null || world.isClient()) {
            return 0;
        }
        
        int energyToConsume = Math.min(energyOffered, ENERGY_DEMAND_PER_TICK);
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
    
    // SidedInventory implementation
    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.DOWN) {
            return new int[]{OUTPUT_SLOT};
        } else if (side == Direction.UP) {
            return new int[]{INPUT_SLOT};
        } else {
            return new int[]{INPUT_SLOT};
        }
    }
    
    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot == OUTPUT_SLOT) {
            return false;
        } else {
            return true;
        }
    }
    
    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == OUTPUT_SLOT;
    }
    
    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }
    
    // Called when this block is being removed/broken
    public void onRemoved() {
        if (network != null) {
            network.removeBlock(pos);
            Circuitmod.LOGGER.info("[ELECTRIC-FURNACE] Removed from network {} at {}", 
                network.getNetworkId(), pos);
        }
    }
} 
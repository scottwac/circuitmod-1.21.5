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
import starduster.circuitmod.recipe.ElectricFurnaceRecipe;
import starduster.circuitmod.recipe.ModRecipes;
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
                case 0 -> ElectricFurnaceBlockEntity.this.isPowered = (value == 1);
                case 2 -> ElectricFurnaceBlockEntity.this.cookingTimeSpent = value;
                case 3 -> ElectricFurnaceBlockEntity.this.cookingTotalTime = value;
                case 4 -> ElectricFurnaceBlockEntity.this.isPowered = (value == 1);
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
        
        // Periodically check if we should be in a network
        if (world.getTime() % 20 == 0) {  // Check every second
            if (entity.network == null) {
                // Use the standardized network connection method
                starduster.circuitmod.power.EnergyNetworkManager.findAndJoinNetwork(world, pos, entity);
            }
        }
        
        boolean poweredBefore = entity.isPowered;
        
        // Check if we have a recipe and can smelt
        boolean hasRecipe = entity.hasRecipe();
        boolean canSmelt = entity.canSmelt();
        
        // Set powered state based on energy received (not recipe availability)
        entity.isPowered = entity.isReceivingPower();
        
        // Debug logging every 40 ticks (2 seconds)
        if (world.getTime() % 40 == 0) {
            Circuitmod.LOGGER.info("[ELECTRIC-FURNACE] Tick at {}: network={}, energyReceived={}, isPowered={}, hasRecipe={}, canSmelt={}", 
                pos, entity.network != null ? entity.network.getNetworkId() : "null", 
                entity.energyReceived, entity.isPowered, hasRecipe, canSmelt);
        }
        
        // Smelting logic - only smelt if powered AND we have a valid recipe
        if (entity.isPowered && hasRecipe && canSmelt) {
            // If we just started cooking (cookingTimeSpent is 0), set the total time
            if (entity.cookingTimeSpent == 0) {
                entity.cookingTotalTime = entity.getCookTime();
            }
            
            entity.cookingTimeSpent++;
            if (entity.cookingTimeSpent >= entity.cookingTotalTime) {
                entity.cookingTimeSpent = 0;
                entity.cookingTotalTime = entity.getCookTime();
                entity.craftItem();
            }
            // Mark dirty to sync cooking progress
            entity.markDirty();
        } else {
            if (entity.cookingTimeSpent != 0) {
                entity.cookingTimeSpent = 0;
                entity.cookingTotalTime = 0; // Reset total time when not cooking
                // Mark dirty to sync cooking progress reset
                entity.markDirty();
            }
        }
        
        // Update block state if powered state changed
        if (poweredBefore != entity.isPowered) {
            world.setBlockState(pos, state.with(starduster.circuitmod.block.machines.ElectricFurnace.LIT, entity.isPowered), Block.NOTIFY_ALL);
            // Mark dirty to sync powered state change
            entity.markDirty();
        }
        
        // Reset energy received at the end of the tick (after processing)
        entity.energyReceived = 0;
        // Don't reset isPowered here - let it persist for the GUI
        
        // Mark dirty to ensure GUI updates and property delegate synchronization
        entity.markDirty();
        
        // Force block update to ensure clients get the latest state
        world.updateListeners(pos, state, state, 3);
    }
    
    /**
     * Attempts to find and join a network from adjacent connectable blocks.
     * Should be called when this furnace's network connection might have changed.
     */
    public void findAndJoinNetwork() {
        if (world == null || world.isClient) return;
        
        boolean foundNetwork = false;
        
        // Look for adjacent networks
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            BlockEntity be = world.getBlockEntity(neighborPos);
            
            if (be instanceof IPowerConnectable) {
                IPowerConnectable connectable = (IPowerConnectable) be;
                EnergyNetwork network = connectable.getNetwork();
                
                Circuitmod.LOGGER.info("[ELECTRIC-FURNACE-NETWORK] Checking neighbor at {} in direction {}: network={}", 
                    neighborPos, dir, network != null ? network.getNetworkId() : "null");
                
                if (network != null && network != this.network) {
                    // Found a network, join it
                    if (this.network != null) {
                        // Remove from old network first
                        String oldNetworkId = this.network.getNetworkId();
                        this.network.removeBlock(pos);
                        Circuitmod.LOGGER.info("[ELECTRIC-FURNACE] Left network {} at {}", oldNetworkId, pos);
                    }
                    
                    network.addBlock(pos, this);
                    foundNetwork = true;
                    Circuitmod.LOGGER.info("[ELECTRIC-FURNACE] Joined existing network {} at {}", network.getNetworkId(), pos);
                    break;
                }
            } else {
                Circuitmod.LOGGER.info("[ELECTRIC-FURNACE-NETWORK] Neighbor at {} in direction {} is not IPowerConnectable: {}", 
                    neighborPos, dir, be != null ? be.getClass().getSimpleName() : "null");
            }
        }
        
        // If no existing network was found, create a new one
        if (!foundNetwork && (this.network == null)) {
            Circuitmod.LOGGER.info("[ELECTRIC-FURNACE] No existing network found at {}, creating new network", pos);
            
            // Create a new network
            EnergyNetwork newNetwork = new EnergyNetwork();
            newNetwork.addBlock(pos, this);
            
            // Try to add adjacent connectables to this new network
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.offset(dir);
                BlockEntity be = world.getBlockEntity(neighborPos);
                
                if (be instanceof IPowerConnectable && ((IPowerConnectable) be).getNetwork() == null) {
                    IPowerConnectable connectable = (IPowerConnectable) be;
                    
                    // Only add if it doesn't already have a network and can connect
                    if (connectable.canConnectPower(dir.getOpposite()) && 
                        this.canConnectPower(dir)) {
                        
                        newNetwork.addBlock(neighborPos, connectable);
                        Circuitmod.LOGGER.info("[ELECTRIC-FURNACE] Added neighbor at {} to new network {}", neighborPos, newNetwork.getNetworkId());
                    }
                }
            }
            
            Circuitmod.LOGGER.info("[ELECTRIC-FURNACE] Created new network {} with furnace at {}", newNetwork.getNetworkId(), pos);
        }
    }
    
    public boolean isBurning() {
        return this.isPowered;
    }
    
    public boolean isReceivingPower() {
        return this.energyReceived > 0;
    }
    
    private boolean hasRecipe() {
        Optional<RecipeEntry<SmeltingRecipe>> smeltingRecipe = getCurrentSmeltingRecipe();
        Optional<RecipeEntry<ElectricFurnaceRecipe>> electricRecipe = getCurrentElectricRecipe();
        return smeltingRecipe.isPresent() || electricRecipe.isPresent();
    }
    
    private boolean canSmelt() {
        // Check smelting recipes first
        Optional<RecipeEntry<SmeltingRecipe>> smeltingRecipe = getCurrentSmeltingRecipe();
        if (smeltingRecipe.isPresent()) {
            SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
            ItemStack output = smeltingRecipe.get().value().craft(input, world.getRegistryManager());
            return canInsertOutput(output);
        }
        
        // Check electric furnace recipes
        Optional<RecipeEntry<ElectricFurnaceRecipe>> electricRecipe = getCurrentElectricRecipe();
        if (electricRecipe.isPresent()) {
            SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
            ItemStack output = electricRecipe.get().value().craft(input, world.getRegistryManager());
            return canInsertOutput(output);
        }
        
        return false;
    }
    
    private boolean canInsertOutput(ItemStack output) {
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
        // Try smelting recipes first
        Optional<RecipeEntry<SmeltingRecipe>> smeltingRecipe = getCurrentSmeltingRecipe();
        if (smeltingRecipe.isPresent()) {
            SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
            ItemStack output = smeltingRecipe.get().value().craft(input, world.getRegistryManager());
            
            // Double the output amount for smelting recipes
            output.setCount(output.getCount() * 2);
            addToOutput(output);
            this.removeStack(INPUT_SLOT, 1);
            return;
        }
        
        // Try electric furnace recipes
        Optional<RecipeEntry<ElectricFurnaceRecipe>> electricRecipe = getCurrentElectricRecipe();
        if (electricRecipe.isPresent()) {
            SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
            ItemStack output = electricRecipe.get().value().craft(input, world.getRegistryManager());
            
            // Electric furnace recipes have normal output (not doubled)
            addToOutput(output);
            this.removeStack(INPUT_SLOT, 1);
            return;
        }
    }
    
    private void addToOutput(ItemStack output) {
        ItemStack outputSlot = this.getStack(OUTPUT_SLOT);
        
        if (outputSlot.isEmpty()) {
            this.setStack(OUTPUT_SLOT, output.copy());
        } else if (ItemStack.areItemsAndComponentsEqual(outputSlot, output)) {
            outputSlot.increment(output.getCount());
        }
    }
    
    private Optional<RecipeEntry<SmeltingRecipe>> getCurrentSmeltingRecipe() {
        if (world == null || world.isClient()) {
            return Optional.empty();
        }
        
        SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
        return ((ServerWorld) world).getRecipeManager()
                .getFirstMatch(RecipeType.SMELTING, input, world);
    }
    
    private Optional<RecipeEntry<ElectricFurnaceRecipe>> getCurrentElectricRecipe() {
        if (world == null || world.isClient()) {
            return Optional.empty();
        }
        
        SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
        return ((ServerWorld) world).getRecipeManager()
                .getFirstMatch(ModRecipes.ELECTRIC_FURNACE_TYPE, input, world);
    }
    
    private int getCookTime() {
        if (world == null || world.isClient()) {
            return 100; // Default cook time
        }
        
        SingleStackRecipeInput input = new SingleStackRecipeInput(this.getStack(INPUT_SLOT));
        
        // Check electric furnace recipes first
        Optional<RecipeEntry<ElectricFurnaceRecipe>> electricRecipe = ((ServerWorld) world).getRecipeManager()
                .getFirstMatch(ModRecipes.ELECTRIC_FURNACE_TYPE, input, world);
        if (electricRecipe.isPresent()) {
            return electricRecipe.get().value().getCookingTime(); // Use the recipe's cooking time
        }
        
        // Fall back to smelting recipes (half the cooking time)
        Optional<RecipeEntry<SmeltingRecipe>> smeltingRecipe = ((ServerWorld) world).getRecipeManager()
                .getFirstMatch(RecipeType.SMELTING, input, world);
        
        return smeltingRecipe.map(recipeEntry -> recipeEntry.value().getCookingTime() / 2).orElse(100);
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
            Circuitmod.LOGGER.info("[ELECTRIC-FURNACE] Received {} energy at {}, total received this tick: {}", 
                energyToConsume, pos, this.energyReceived);
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
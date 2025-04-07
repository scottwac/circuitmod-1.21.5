package starduster.circuitmod.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.screen.BloomeryScreenHandler;

public class BloomeryBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, Inventory {
    // Slot indices
    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;
    
    // For property delegate
    private static final int BURN_TIME_PROPERTY = 0;
    private static final int COOK_TIME_PROPERTY = 1;
    private static final int COOK_TIME_TOTAL_PROPERTY = 2;
    private static final int PROPERTY_COUNT = 3;
    
    // Smelting
    private static final int DEFAULT_COOK_TIME = 200; // Same as vanilla furnace
    
    // Store items
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);
    
    // Track burn time, cook time, etc.
    private int burnTime = 0;
    private int cookTime = 0;
    private int cookTimeTotal = DEFAULT_COOK_TIME;
    
    // Property delegate for the screen handler
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case BURN_TIME_PROPERTY -> burnTime;
                case COOK_TIME_PROPERTY -> cookTime;
                case COOK_TIME_TOTAL_PROPERTY -> cookTimeTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case BURN_TIME_PROPERTY -> burnTime = value;
                case COOK_TIME_PROPERTY -> cookTime = value;
                case COOK_TIME_TOTAL_PROPERTY -> cookTimeTotal = value;
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public BloomeryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BLOOMERY_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, this.inventory, registries);
        nbt.putInt("BurnTime", this.burnTime);
        nbt.putInt("CookTime", this.cookTime);
        nbt.putInt("CookTimeTotal", this.cookTimeTotal);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, this.inventory, registries);
        this.burnTime = nbt.getInt("BurnTime").orElse(0);
        this.cookTime = nbt.getInt("CookTime").orElse(0);
        this.cookTimeTotal = nbt.getInt("CookTimeTotal").orElse(DEFAULT_COOK_TIME);
    }
    
    // Screen handler factory
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.bloomery").formatted(Formatting.BLACK);
    }
    
    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new BloomeryScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
    }
    
    // Inventory implementation
    @Override
    public int size() {
        return this.inventory.size();
    }
    
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public ItemStack getStack(int slot) {
        return this.inventory.get(slot);
    }
    
    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(this.inventory, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }
    
    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(this.inventory, slot);
    }
    
    @Override
    public void setStack(int slot, ItemStack stack) {
        this.inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }
    
    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (this.world.getBlockEntity(this.pos) != this) {
            return false;
        }
        return player.squaredDistanceTo(
            (double)this.pos.getX() + 0.5, 
            (double)this.pos.getY() + 0.5, 
            (double)this.pos.getZ() + 0.5
        ) <= 64.0;
    }
    
    @Override
    public void clear() {
        this.inventory.clear();
        markDirty();
    }
    
    // Ticking logic
    public static void tick(World world, BlockPos pos, BlockState state, BloomeryBlockEntity blockEntity) {
        boolean burningBefore = blockEntity.isBurning();
        boolean dirty = false;
        
        // If burning, decrement burn time
        if (blockEntity.isBurning()) {
            blockEntity.burnTime--;
        }
        
        ItemStack inputStack = blockEntity.inventory.get(INPUT_SLOT);
        ItemStack fuelStack = blockEntity.inventory.get(FUEL_SLOT);
        
        // Continue only if we have input and either burning or have fuel
        if (!inputStack.isEmpty() && (blockEntity.isBurning() || !fuelStack.isEmpty())) {
            // Check if we can smelt the input
            ItemStack resultStack = blockEntity.getSmeltingResult(inputStack);
            
            if (!resultStack.isEmpty()) {
                // Check if output slot can accept the result
                ItemStack outputStack = blockEntity.inventory.get(OUTPUT_SLOT);
                if (outputStack.isEmpty() || (
                    ItemStack.areItemsEqual(outputStack, resultStack) && 
                    outputStack.getCount() + resultStack.getCount() <= outputStack.getMaxCount()
                )) {
                    // If not burning, start burning with fuel
                    if (!blockEntity.isBurning()) {
                        blockEntity.burnTime = blockEntity.getFuelTime(fuelStack);
                        blockEntity.cookTimeTotal = blockEntity.getCookTime(inputStack);
                        
                        if (blockEntity.isBurning()) {
                            dirty = true;
                            // Consume fuel
                            ItemStack remainder = fuelStack.getItem().getRecipeRemainder();
                            if (remainder != null && !remainder.isEmpty()) {
                                blockEntity.inventory.set(FUEL_SLOT, remainder);
                            } else {
                                fuelStack.decrement(1);
                            }
                        }
                    }
                    
                    // If burning, increment cook time
                    if (blockEntity.isBurning()) {
                        blockEntity.cookTime++;
                        if (blockEntity.cookTime >= blockEntity.cookTimeTotal) {
                            blockEntity.cookTime = 0;
                            // Smelt the item
                            if (outputStack.isEmpty()) {
                                blockEntity.inventory.set(OUTPUT_SLOT, resultStack.copy());
                            } else {
                                outputStack.increment(resultStack.getCount());
                            }
                            
                            inputStack.decrement(1);
                            dirty = true;
                        }
                    } else {
                        // Not burning, reset cook time
                        blockEntity.cookTime = 0;
                    }
                }
            }
        } else if (blockEntity.cookTime > 0) {
            // No input or fuel, reset cook time
            blockEntity.cookTime = 0;
            dirty = true;
        }
        
        // Update the block state if burning state has changed
        if (burningBefore != blockEntity.isBurning()) {
            dirty = true;
        }
        
        if (dirty) {
            blockEntity.markDirty();
        }
    }
    
    // Utility methods
    public boolean isBurning() {
        return this.burnTime > 0;
    }
    
    private int getFuelTime(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        }
        
        // For now, just use coal as fuel with custom burn time
        if (fuel.isOf(Items.COAL) || fuel.isOf(Items.CHARCOAL)) {
            return 1600; // Same as vanilla furnace for coal
        }
        
        return 0;
    }
    
    private int getCookTime(ItemStack input) {
        // Can customize cooking time based on input
        return DEFAULT_COOK_TIME;
    }
    
    private ItemStack getSmeltingResult(ItemStack input) {
        // Simplified recipe system - you would replace this with proper recipe handling
        if (input.isOf(Items.IRON_ORE) || input.isOf(Items.DEEPSLATE_IRON_ORE)) {
            return new ItemStack(Items.IRON_INGOT);
        }
        
        if (input.isOf(Items.RAW_IRON)) {
            return new ItemStack(Items.IRON_INGOT);
        }
        
        return ItemStack.EMPTY;
    }
    
    // Inventory access methods for the screen handler
    public DefaultedList<ItemStack> getInventory() {
        return this.inventory;
    }
} 
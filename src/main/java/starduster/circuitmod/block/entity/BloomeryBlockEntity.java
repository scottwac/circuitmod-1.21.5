package starduster.circuitmod.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
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
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import starduster.circuitmod.recipe.BloomeryRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.server.world.ServerWorld;
import starduster.circuitmod.recipe.ModRecipeTypes;
import starduster.circuitmod.block.BloomeryBlock;
import net.minecraft.util.Identifier;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;

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
    
    // Recipe matching helper
    private final ServerRecipeManager.MatchGetter<SingleStackRecipeInput, ? extends BloomeryRecipe> matchGetter;
    
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
        this.matchGetter = ServerRecipeManager.createCachedMatchGetter(ModRecipeTypes.BLOOMERY);
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, this.inventory, registries);
        nbt.putShort("BurnTime", (short)this.burnTime);
        nbt.putShort("CookTime", (short)this.cookTime);
        nbt.putShort("CookTimeTotal", (short)this.cookTimeTotal);
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, this.inventory, registries);
        this.burnTime = nbt.getShort("BurnTime", (short)0);
        this.cookTime = nbt.getShort("CookTime", (short)0);
        this.cookTimeTotal = nbt.getShort("CookTimeTotal", (short)DEFAULT_COOK_TIME);
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
        
        // Only log every 5 seconds to reduce spam
        boolean shouldLog = world.getTime() % 100 == 0;
        if (shouldLog) {
            Circuitmod.LOGGER.info("[DEBUG-BLOOMERY] State at " + pos + ": burning=" + blockEntity.isBurning() 
                + ", burnTime=" + blockEntity.burnTime 
                + ", cookTime=" + blockEntity.cookTime + "/" + blockEntity.cookTimeTotal);
        }
        
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
                if (shouldLog) {
                    Circuitmod.LOGGER.info("[DEBUG-BLOOMERY] Valid recipe found: " + 
                        inputStack.getItem() + " -> " + resultStack.getItem());
                }
                
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
                            ItemStack fuelRemainder = fuelStack.getItem().getRecipeRemainder();
                            if (!fuelRemainder.isEmpty()) {
                                blockEntity.inventory.set(FUEL_SLOT, fuelRemainder);
                            } else {
                                fuelStack.decrement(1);
                            }
                            
                            if (shouldLog) {
                                Circuitmod.LOGGER.info("[DEBUG-BLOOMERY] Started burning: burnTime=" + 
                                    blockEntity.burnTime + ", cookTimeTotal=" + blockEntity.cookTimeTotal);
                            }
                        }
                    }
                    
                    // If burning, increment cook time
                    if (blockEntity.isBurning()) {
                        blockEntity.cookTime++;
                        if (blockEntity.cookTime >= blockEntity.cookTimeTotal) {
                            Circuitmod.LOGGER.info("[DEBUG-BLOOMERY] Completed smelting: " + 
                                inputStack.getItem() + " -> " + resultStack.getItem());
                                
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
            } else if (shouldLog) {
                Circuitmod.LOGGER.warn("[DEBUG-BLOOMERY] No recipe found for input: " + inputStack.getItem());
            }
        } else if (blockEntity.cookTime > 0) {
            // No input or fuel, reset cook time
            blockEntity.cookTime = 0;
            dirty = true;
        }
        
        // Update the block state if burning state has changed
        if (burningBefore != blockEntity.isBurning()) {
            dirty = true;
            world.setBlockState(pos, world.getBlockState(pos).with(BloomeryBlock.LIT, blockEntity.isBurning()), Block.NOTIFY_ALL);
            
            if (shouldLog) {
                Circuitmod.LOGGER.info("[DEBUG-BLOOMERY] Burning state changed to: " + blockEntity.isBurning());
            }
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
        if (world == null || !(world instanceof ServerWorld serverWorld)) return DEFAULT_COOK_TIME;
        
        SingleStackRecipeInput recipeInput = new SingleStackRecipeInput(input);
        
        // Get cook time from recipe if available
        return matchGetter.getFirstMatch(recipeInput, serverWorld)
            .map(recipe -> recipe.value().getCookingTime())
            .orElse(DEFAULT_COOK_TIME);
    }
    
    private ItemStack getSmeltingResult(ItemStack input) {
        if (world == null || !(world instanceof ServerWorld serverWorld)) {
            return ItemStack.EMPTY;
        }
        
        SingleStackRecipeInput recipeInput = new SingleStackRecipeInput(input);
        
        // More detailed recipe debug
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-DETAIL] Looking for recipe for " + input.getItem() + 
            ", Count=" + input.getCount() + 
            ", Is deepslate ore? " + input.isOf(Items.DEEPSLATE_IRON_ORE));
        
        // Try manual ingredient test for iron ore
        Ingredient ironOreIngredient = Ingredient.ofItems(Items.IRON_ORE);
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-DETAIL] Manual test - iron_ore ingredient matches input? " + 
            ironOreIngredient.test(input));
        
        // Debug recipe look up
        Circuitmod.LOGGER.info("[DEBUG-RECIPE] Looking for recipe - Input: " + input.getItem());
        
        ItemStack result = matchGetter.getFirstMatch(recipeInput, serverWorld)
            .map(recipe -> {
                Circuitmod.LOGGER.info("[DEBUG-RECIPE] Found recipe: " + recipe.id());
                return recipe.value().craft(recipeInput, world.getRegistryManager());
            })
            .orElse(ItemStack.EMPTY);
            
        if (result.isEmpty()) {
            // Debug all available recipes of this type - just log that no recipes were found
            Circuitmod.LOGGER.warn("[DEBUG-RECIPE] No matching recipe found for input: " + input.getItem());
            
            // Try to check if the recipe type exists in the registry
            boolean bloomeryTypeExists = Registries.RECIPE_TYPE.containsId(
                Identifier.of(Circuitmod.MOD_ID, "bloomery"));
            Circuitmod.LOGGER.info("[DEBUG-RECIPE-ALL] Bloomery recipe type in registry: " + bloomeryTypeExists);
            
            // Try to log ALL active recipes in the game to see what's available
            Circuitmod.LOGGER.info("[DEBUG-RECIPE-ALL] Checking for recipes in registry: ");
            try {
                // Check if any recipes at all are loaded
                Circuitmod.LOGGER.info("[DEBUG-RECIPE-ALL] Recipe manager has recipes: " + 
                    (serverWorld.getRecipeManager() != null ? "Yes" : "No"));
            } catch (Exception e) {
                Circuitmod.LOGGER.error("[DEBUG-RECIPE-ALL] Error checking registry: " + e.getMessage());
            }
        }
        
        return result;
    }
    
    // Inventory access methods for the screen handler
    public DefaultedList<ItemStack> getInventory() {
        return this.inventory;
    }
} 
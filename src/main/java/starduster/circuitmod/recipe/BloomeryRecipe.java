package starduster.circuitmod.recipe;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.ModBlocks;
import net.minecraft.registry.Registries;

public class BloomeryRecipe extends AbstractCookingRecipe {
    public static final String ID = "bloomery";
    
    // Static initializer for debugging
    static {
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] BloomeryRecipe class loaded");
    }
    
    public BloomeryRecipe(
            String group,
            CookingRecipeCategory category,
            Ingredient ingredient,
            ItemStack result,
            float experience,
            int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
        
        // Debug details of recipe creation
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Created BloomeryRecipe:");
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Group: " + group);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Category: " + category);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Ingredient: " + ingredient);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Result: " + (result != null ? result.getItem() : "null"));
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Experience: " + experience);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Cooking time: " + cookingTime);
    }
    
    @Override
    protected Item getCookerItem() {
        return ModBlocks.BLOOMERY.asItem();
    }
    
    @Override
    public RecipeSerializer<? extends AbstractCookingRecipe> getSerializer() {
        return BloomeryRecipeSerializer.INSTANCE;
    }
    
    @Override
    public RecipeType<? extends AbstractCookingRecipe> getType() {
        return ModRecipeTypes.BLOOMERY;
    }
    
    @Override
    public RecipeBookCategory getRecipeBookCategory() {
        return null;
    }

    @Override
    public boolean matches(SingleStackRecipeInput input, net.minecraft.world.World world) {
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Testing match for BloomeryRecipe");
        
        // Call super implementation and log the result
        boolean matches = super.matches(input, world);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Match result: " + matches);
        
        // Log detailed debug info about why it might not be matching
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Input: " + input);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - World: " + (world != null ? "present" : "null"));
        Circuitmod.LOGGER.info("[RECIPE-DEBUG]   - Recipe: " + this);
        
        // Try a direct test - OVERRIDE the normal behavior if needed
        try {
            // This is a last resort - try to force-match if we recognize the item
            if (!matches && input != null) {
                Circuitmod.LOGGER.info("[RECIPE-DEBUG] Checking for force-match options");
                // We'll log more details but not override unless absolutely necessary
            }
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[RECIPE-DEBUG] Error in force-match check: " + e.getMessage());
        }
        
        return matches;
    }
}
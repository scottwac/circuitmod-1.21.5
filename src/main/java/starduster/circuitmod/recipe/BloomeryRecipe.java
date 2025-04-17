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
    
    public BloomeryRecipe(
            String group,
            CookingRecipeCategory category,
            Ingredient ingredient,
            ItemStack result,
            float experience,
            int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
        
        // Debug recipe creation
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-CREATE] Created BloomeryRecipe: group=" + group + 
            ", category=" + category +
            ", ingredient=" + ingredient + 
            ", result=" + result.getItem() +
            ", cookTime=" + cookingTime);
    }
    
    @Override
    protected Item getCookerItem() {
        // Return your bloomery item here
        return ModBlocks.BLOOMERY.asItem();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<? extends AbstractCookingRecipe> getSerializer() {
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-SERIALIZER] Recipe asking for serializer: " + BloomeryRecipeSerializer.INSTANCE);
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-SERIALIZER] Serializer ID: " + BloomeryRecipeSerializer.ID);
        return BloomeryRecipeSerializer.INSTANCE;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public RecipeType<? extends AbstractCookingRecipe> getType() {
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-TYPE] BloomeryRecipe.getType called, returning: " + ModRecipeTypes.BLOOMERY);
        
        // Verify type registration
        Identifier typeId = Identifier.of(Circuitmod.MOD_ID, "bloomery");
        boolean typeRegistered = Registries.RECIPE_TYPE.containsId(typeId);
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-TYPE] Recipe type registered: " + typeRegistered);
        
        return ModRecipeTypes.BLOOMERY;
    }
    
    @Override
    public RecipeBookCategory getRecipeBookCategory() {
        // Since we only want mod-specific recipes and don't need recipe book integration
        return null;
    }

    @Override
    public boolean matches(SingleStackRecipeInput input, net.minecraft.world.World world) {
        boolean matches = super.matches(input, world);
        
        // Simple debug log for recipe matching
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-MATCH] Match test result: " + matches);
        
        return matches;
    }
} 
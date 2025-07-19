package starduster.circuitmod.recipe;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.book.RecipeBookCategory;

public class ElectricFurnaceRecipe extends AbstractCookingRecipe {
    
    public ElectricFurnaceRecipe(String group, CookingRecipeCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    protected Item getCookerItem() {
        return Items.FURNACE; // We'll use furnace as the base, but could create a custom item
    }

    @Override
    public RecipeSerializer<ElectricFurnaceRecipe> getSerializer() {
        return ModRecipes.ELECTRIC_FURNACE_SERIALIZER;
    }

    @Override
    public RecipeType<ElectricFurnaceRecipe> getType() {
        return ModRecipes.ELECTRIC_FURNACE_TYPE;
    }

    @Override
    public RecipeBookCategory getRecipeBookCategory() {
        return switch (this.getCategory()) {
            case BLOCKS -> RecipeBookCategories.FURNACE_BLOCKS;
            case FOOD -> RecipeBookCategories.FURNACE_FOOD;
            case MISC -> RecipeBookCategories.FURNACE_MISC;
        };
    }
} 
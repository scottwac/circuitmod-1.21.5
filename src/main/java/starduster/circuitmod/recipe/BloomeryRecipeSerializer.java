package starduster.circuitmod.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class BloomeryRecipeSerializer {
    // Static initializer for debugging
    static {
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] BloomeryRecipeSerializer class loaded");
    }

    // Create serializer for bloomery recipes
    public static final RecipeSerializer<BloomeryRecipe> INSTANCE = new AbstractCookingRecipe.Serializer<BloomeryRecipe>(
            // Constructor reference with debug wrapper
            (group, category, ingredient, result, experience, cookingTime) -> {
                Circuitmod.LOGGER.info("[RECIPE-DEBUG] Serializer creating recipe: " + group);
                BloomeryRecipe recipe = new BloomeryRecipe(group, category, ingredient, result, experience, cookingTime);
                Circuitmod.LOGGER.info("[RECIPE-DEBUG] Serializer created recipe successfully: " + recipe);
                return recipe;
            },
            200 // Default cooking time
    ) {
        // Override codec to add debugging
        @Override
        public MapCodec<BloomeryRecipe> codec() {
            Circuitmod.LOGGER.info("[RECIPE-DEBUG] Serializer codec() called");
            return super.codec();
        }
    };
    
    public static final Identifier ID = Identifier.of(Circuitmod.MOD_ID, "bloomery");
    
    // Register the serializer
    public static void register() {
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Registering BloomeryRecipe serializer: " + ID);
        
        // Check if already registered
        boolean alreadyRegistered = Registries.RECIPE_SERIALIZER.containsId(ID);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Serializer already registered? " + alreadyRegistered);
        
        // Register serializer and check success
        Registry.register(Registries.RECIPE_SERIALIZER, ID, INSTANCE);
        boolean nowRegistered = Registries.RECIPE_SERIALIZER.containsId(ID);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Serializer registration " + 
            (nowRegistered ? "SUCCESS" : "FAILED!"));
    }
} 
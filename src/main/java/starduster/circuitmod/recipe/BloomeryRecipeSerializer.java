package starduster.circuitmod.recipe;

import com.mojang.serialization.Codec;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class BloomeryRecipeSerializer {
    // Create serializer with a wrapped constructor to add logging
    public static final RecipeSerializer<BloomeryRecipe> INSTANCE = new AbstractCookingRecipe.Serializer<BloomeryRecipe>(
            (group, category, ingredient, result, experience, cookingTime) -> {
                Circuitmod.LOGGER.info("[DEBUG-SERIALIZER-RECIPE] Creating bloomery recipe: " +
                    "group=" + group + ", ingredient=" + ingredient + 
                    ", result=" + result.getItem() + ", cookTime=" + cookingTime);
                return new BloomeryRecipe(group, category, ingredient, result, experience, cookingTime);
            }, 
            200 // Default cooking time
    ) {
        // Override codec() to add debugging
        @Override
        public com.mojang.serialization.MapCodec<BloomeryRecipe> codec() {
            Circuitmod.LOGGER.info("[DEBUG-SERIALIZER-CODEC] BloomeryRecipe.codec() called");
            return super.codec();
        }
    };
    
    public static final Identifier ID = Identifier.of(Circuitmod.MOD_ID, "bloomery");
    
    // Register the serializer
    public static void register() {
        Circuitmod.LOGGER.info("[DEBUG-SERIALIZER] Registering BloomeryRecipe serializer with ID: " + ID);
        
        // Check if the recipe type is already registered
        boolean alreadyRegistered = Registries.RECIPE_SERIALIZER.containsId(ID);
        Circuitmod.LOGGER.info("[DEBUG-SERIALIZER] Serializer already registered: " + alreadyRegistered);
        
        Registry.register(Registries.RECIPE_SERIALIZER, ID, INSTANCE);
        
        // Verify registration worked
        boolean registrationSuccessful = Registries.RECIPE_SERIALIZER.containsId(ID);
        Circuitmod.LOGGER.info("[DEBUG-SERIALIZER] Registration successful: " + registrationSuccessful);
        
        // Debug the registered serializer
        RecipeSerializer<?> registeredSerializer = Registries.RECIPE_SERIALIZER.get(ID);
        Circuitmod.LOGGER.info("[DEBUG-SERIALIZER] Registered serializer: " + 
                             (registeredSerializer == INSTANCE ? "matches our instance" : "different instance"));
                             
        // Verify our serializer can be found by the ID used in JSON
        Identifier jsonTypeId = Identifier.of(Circuitmod.MOD_ID, "bloomery");
        RecipeSerializer<?> jsonSerializer = Registries.RECIPE_SERIALIZER.get(jsonTypeId);
        Circuitmod.LOGGER.info("[DEBUG-SERIALIZER] JSON type ID maps to serializer: " + 
                            (jsonSerializer != null ? "Found" : "NOT FOUND!"));
        Circuitmod.LOGGER.info("[DEBUG-SERIALIZER] JSON serializer same as ours? " + 
                            (jsonSerializer == INSTANCE ? "YES" : "NO"));
    }
} 
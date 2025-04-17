package starduster.circuitmod.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.CookingRecipeCategory;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

/**
 * Contains and registers all recipe types and serializers for the mod
 */
public class ModRecipeTypes {
    // Static initializer for debugging
    static {
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] ModRecipeTypes class loaded");
    }

    // Define recipe types
    private static final String BLOOMERY_ID = "bloomery";
    public static RecipeType<BloomeryRecipe> BLOOMERY;
    
    // Register all recipe types and serializers
    public static void register() {
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] ModRecipeTypes.register() started");
        
        // 1. Register bloomery recipe type
        Identifier recipeTypeId = Identifier.of(Circuitmod.MOD_ID, BLOOMERY_ID);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Registering bloomery recipe type with ID: " + recipeTypeId);
        
        BLOOMERY = Registry.register(
            Registries.RECIPE_TYPE,
            recipeTypeId,
            new RecipeType<BloomeryRecipe>() {
                @Override
                public String toString() {
                    return BLOOMERY_ID;
                }
            }
        );
        
        // 2. Register bloomery recipe serializer
        BloomeryRecipeSerializer.register();
        
        // Verify registrations worked
        boolean typeRegistered = Registries.RECIPE_TYPE.containsId(recipeTypeId);
        boolean serializerRegistered = Registries.RECIPE_SERIALIZER.containsId(BloomeryRecipeSerializer.ID);
        
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Recipe type registered: " + typeRegistered);
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Recipe serializer registered: " + serializerRegistered);
        
        // Test direct recipe creation
        try {
            // Create test recipe
            Circuitmod.LOGGER.info("[RECIPE-DEBUG] Testing direct recipe creation");
            BloomeryRecipe testRecipe = new BloomeryRecipe(
                "direct_test",
                CookingRecipeCategory.MISC,
                Ingredient.ofItems(Items.COAL_ORE),
                new ItemStack(Items.COAL),
                0.1f,
                100
            );
            
            // Test recipe properties
            Circuitmod.LOGGER.info("[RECIPE-DEBUG] Test recipe created: " + testRecipe);
            Circuitmod.LOGGER.info("[RECIPE-DEBUG]  - Type: " + testRecipe.getType());
            
            // Try ingredient test
            SingleStackRecipeInput testInput = new SingleStackRecipeInput(new ItemStack(Items.COAL_ORE));
            boolean matches = testRecipe.matches(testInput, null);
            Circuitmod.LOGGER.info("[RECIPE-DEBUG]  - Test match result: " + matches);
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[RECIPE-DEBUG] Error testing direct recipe: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Log registration complete
        Circuitmod.LOGGER.info("[RECIPE-DEBUG] Bloomery recipe system initialized");
    }
} 
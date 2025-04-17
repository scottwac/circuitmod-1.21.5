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
    // Define recipe types
    private static final String BLOOMERY_ID = "bloomery";
    public static RecipeType<BloomeryRecipe> BLOOMERY;
    
    // Register all recipe types
    public static void register() {
        // Register bloomery recipe type
        Identifier recipeTypeId = Identifier.of(Circuitmod.MOD_ID, BLOOMERY_ID);
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-TYPE] Registering bloomery recipe type with ID: " + recipeTypeId);
        
        // First check if already registered
        boolean typeAlreadyRegistered = Registries.RECIPE_TYPE.containsId(recipeTypeId);
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-TYPE] Type already registered: " + typeAlreadyRegistered);
        
        // Register the recipe type
        BLOOMERY = Registry.register(
            Registries.RECIPE_TYPE,
            recipeTypeId,
            new RecipeType<BloomeryRecipe>() {}
        );
        
        // Verify registration
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-TYPE] Registered type: " + BLOOMERY);
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-TYPE] Registry contains type: " + 
            Registries.RECIPE_TYPE.containsId(recipeTypeId));
        
        // Register bloomery recipe serializer - make sure IDs match!
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-TYPE] Serializer ID should match: " + BloomeryRecipeSerializer.ID);
        BloomeryRecipeSerializer.register();
        
        // CRITICAL TEST: Create a hardcoded test recipe to see if the recipe system works at all
        try {
            // Create test recipe for raw iron -> iron ingot
            BloomeryRecipe testRecipe = new BloomeryRecipe(
                "test_group",
                CookingRecipeCategory.MISC,
                Ingredient.ofItems(Items.RAW_IRON),
                new ItemStack(Items.IRON_INGOT),
                0.7f,
                200
            );
            
            Circuitmod.LOGGER.info("[DEBUG-TEST-RECIPE] Created test recipe: " + testRecipe);
            Circuitmod.LOGGER.info("[DEBUG-TEST-RECIPE] Recipe type: " + testRecipe.getType());
            Circuitmod.LOGGER.info("[DEBUG-TEST-RECIPE] Recipe serializer: " + testRecipe.getSerializer());
            
            // Test if it matches raw iron
            SingleStackRecipeInput input = new SingleStackRecipeInput(new ItemStack(Items.RAW_IRON));
            boolean matches = testRecipe.matches(input, null);
            Circuitmod.LOGGER.info("[DEBUG-TEST-RECIPE] Test recipe matches raw iron: " + matches);
            
            // TEST for iron ore 
            SingleStackRecipeInput ironOreInput = new SingleStackRecipeInput(new ItemStack(Items.IRON_ORE));
            BloomeryRecipe testIronOreRecipe = new BloomeryRecipe(
                "test_ironore_group",
                CookingRecipeCategory.MISC,
                Ingredient.ofItems(Items.IRON_ORE),
                new ItemStack(Items.IRON_INGOT),
                0.7f,
                200
            );
            boolean ironOreMatches = testIronOreRecipe.matches(ironOreInput, null);
            Circuitmod.LOGGER.info("[DEBUG-TEST-RECIPE] Test recipe matches iron ore: " + ironOreMatches);
            
            // Test if recipe JSON files can be found
            Identifier ironOreRecipeId = Identifier.of(Circuitmod.MOD_ID, "recipes/bloomery/iron_ingot_from_iron_ore.json");
            Identifier rawIronRecipeId = Identifier.of(Circuitmod.MOD_ID, "recipes/bloomery/iron_ingot_from_raw_iron.json");
            
            // Try to log all resources in the resource manager
            Circuitmod.LOGGER.info("[DEBUG-RESOURCES] Testing if recipe files are visible:");
            Circuitmod.LOGGER.info("[DEBUG-RESOURCES] Looking for: " + ironOreRecipeId);
            Circuitmod.LOGGER.info("[DEBUG-RESOURCES] Looking for: " + rawIronRecipeId);
            Circuitmod.LOGGER.info("[DEBUG-RESOURCES] Recipe directory exists with proper files? "); 
            // We would check this, but need a ResourceManager which we can't get here
            
            Circuitmod.LOGGER.info("[DEBUG-TEST-RECIPE] Hard-coded test recipe created successfully!");
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[DEBUG-TEST-RECIPE] Error creating test recipe: " + e.getMessage());
            e.printStackTrace();
        }
        
        Circuitmod.LOGGER.info("[DEBUG-RECIPE-TYPE] ModRecipeTypes initialized");
    }
} 
package starduster.circuitmod.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Directly reads bloomery recipe JSON files from resources
 * This bypasses Minecraft's recipe system entirely when the normal recipe system fails
 */
public class BloomeryDirectRecipeReader {
    private static final Gson GSON = new Gson();
    private static final Map<Item, ItemStack> RECIPE_CACHE = new HashMap<>();
    private static boolean hasInitialized = false;
    
    /**
     * Load all bloomery recipes from the specified directory
     */
    public static void initialize(MinecraftServer server) {
        if (hasInitialized) {
            Circuitmod.LOGGER.info("[DIRECT-RECIPES] Already initialized, skipping");
            return;
        }
        
        Circuitmod.LOGGER.info("[DIRECT-RECIPES] Initializing direct recipe reader...");
        RECIPE_CACHE.clear();
        
        try {
            ResourceManager resourceManager = server.getResourceManager();
            Circuitmod.LOGGER.info("[DIRECT-RECIPES] Got ResourceManager: " + (resourceManager != null));
            
            // APPROACH 1: Check direct path in resources
            checkSpecificRecipeFiles(resourceManager);
            
            // APPROACH 2: Look for recipes in both standard location and custom subdirectory
            if (RECIPE_CACHE.isEmpty()) {
                loadRecipesFromPath(resourceManager, "data/circuitmod/recipes/");
            }
            
            if (RECIPE_CACHE.isEmpty()) {
                loadRecipesFromPath(resourceManager, "data/circuitmod/recipes/bloomery/");
            }
            
            // APPROACH 3: Try alternate locations (root, mod specific paths, etc)
            if (RECIPE_CACHE.isEmpty()) {
                Circuitmod.LOGGER.info("[DIRECT-RECIPES] Trying alternate locations...");
                loadRecipesFromPath(resourceManager, "circuitmod/recipes/");
                loadRecipesFromPath(resourceManager, "recipes/");
                loadRecipesFromPath(resourceManager, "assets/circuitmod/recipes/");
            }
            
            // Log actual recipe count
            if (RECIPE_CACHE.isEmpty()) {
                Circuitmod.LOGGER.warn("[DIRECT-RECIPES] WARNING: No recipes were loaded from any location!");
            } else {
                Circuitmod.LOGGER.info("[DIRECT-RECIPES] Successfully loaded " + RECIPE_CACHE.size() + " recipes");
            }
            
            // APPROACH 4: Try loading directly from file system as last resort
            if (RECIPE_CACHE.isEmpty()) {
                tryLoadFromFileSystem();
            }
            
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[DIRECT-RECIPES] Error loading recipes: " + e.getMessage());
            e.printStackTrace();
        }
        
        hasInitialized = true;
    }
    
    private static void checkSpecificRecipeFiles(ResourceManager resourceManager) {
        Circuitmod.LOGGER.info("[DIRECT-RECIPES] Checking specific recipe files");
        
        // Check for specific recipes we know should exist
        String[] specificRecipes = {
            "data/circuitmod/recipes/coal_from_coal_ore.json",
            "data/circuitmod/recipes/iron_ingot_from_iron_ore.json",
            "data/circuitmod/recipes/iron_ingot_from_raw_iron.json",
            "circuitmod:recipes/coal_from_coal_ore",
            "circuitmod:recipes/iron_ingot_from_iron_ore",
            "circuitmod:recipes/iron_ingot_from_raw_iron",
            // Add our newly created recipe files
            "data/circuitmod/recipes/bloomery/coal_from_coal_ore.json",
            "data/circuitmod/recipes/bloomery/iron_from_iron_ore.json",
            "circuitmod:recipes/bloomery/coal_from_coal_ore.json",
            "circuitmod:recipes/bloomery/iron_from_iron_ore.json"
        };
        
        for (String recipePath : specificRecipes) {
            try {
                Identifier id;
                if (recipePath.contains(":")) {
                    // Parse as namespace:path format
                    String[] parts = recipePath.split(":", 2);
                    id = Identifier.of(parts[0], parts[1]);
                } else {
                    // Default to minecraft namespace
                    id = Identifier.of("minecraft", recipePath);
                }
                
                Optional<Resource> resource = resourceManager.getResource(id);
                
                Circuitmod.LOGGER.info("[DIRECT-RECIPES] Checking specific file: " + recipePath + 
                    " - Found: " + resource.isPresent());
                
                if (resource.isPresent()) {
                    parseRecipeResource(id, resource.get());
                }
            } catch (Exception e) {
                Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Error checking " + recipePath + ": " + e.getMessage());
            }
        }
    }
    
    private static void loadRecipesFromPath(ResourceManager resourceManager, String basePath) {
        Circuitmod.LOGGER.info("[DIRECT-RECIPES] Searching for recipes in: " + basePath);
        
        try {
            // Get all resources in the directory
            Map<Identifier, Resource> resources = resourceManager.findResources(basePath, 
                path -> path.getPath().endsWith(".json"));
            
            Circuitmod.LOGGER.info("[DIRECT-RECIPES] Found " + resources.size() + " JSON files in " + basePath);
            
            // Print ALL recipe files found for debugging
            for (Identifier id : resources.keySet()) {
                Circuitmod.LOGGER.info("[DIRECT-RECIPES] JSON file found: " + id.getNamespace() + ":" + id.getPath());
            }
            
            // Process each resource
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                Resource resource = entry.getValue();
                parseRecipeResource(id, resource);
            }
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[DIRECT-RECIPES] Error scanning path " + basePath + ": " + e.getMessage());
        }
    }
    
    private static void parseRecipeResource(Identifier id, Resource resource) {
        try {
            Circuitmod.LOGGER.info("[DIRECT-RECIPES] Processing resource: " + id.getPath());
            
            // Parse the JSON
            try (InputStream stream = resource.getInputStream()) {
                JsonObject json = GSON.fromJson(new InputStreamReader(stream), JsonObject.class);
                
                // ALWAYS print recipe file contents for debugging
                Circuitmod.LOGGER.info("[DIRECT-RECIPES] Recipe JSON content: " + json.toString());
                
                // Make sure it has a type field
                if (!json.has("type")) {
                    Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Recipe missing 'type' field: " + id.getPath());
                    return;
                }
                
                // Verify this is a bloomery recipe
                String type = json.get("type").getAsString();
                Circuitmod.LOGGER.info("[DIRECT-RECIPES] Recipe type: " + type);
                
                if (!type.equals("circuitmod:bloomery")) {
                    Circuitmod.LOGGER.info("[DIRECT-RECIPES] Skipping non-bloomery recipe: " + type);
                    return;
                }
                
                // Get the input item
                if (!json.has("ingredient")) {
                    Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Recipe missing 'ingredient' field: " + id.getPath());
                    return;
                }
                
                JsonObject ingredientJson = json.getAsJsonObject("ingredient");
                if (ingredientJson.has("item")) {
                    String inputItemId = ingredientJson.get("item").getAsString();
                    Circuitmod.LOGGER.info("[DIRECT-RECIPES] Input item ID string: " + inputItemId);
                    
                    // Try multiple ways to parse the identifier to handle different formats
                    Item inputItem = null;
                    try {
                        // Method 1: Use Identifier.of directly
                        Identifier inputId = Identifier.of(inputItemId);
                        inputItem = Registries.ITEM.get(inputId);
                        Circuitmod.LOGGER.info("[DIRECT-RECIPES] Method 1 - Input item found: " + (inputItem != null));
                    } catch (Exception e) {
                        Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Method 1 failed: " + e.getMessage());
                    }
                    
                    // Try method 2 if method 1 failed
                    if (inputItem == null) {
                        try {
                            // Method 2: Split namespace and path manually
                            String namespace = "minecraft";
                            String path = inputItemId;
                            
                            if (inputItemId.contains(":")) {
                                String[] parts = inputItemId.split(":", 2);
                                namespace = parts[0];
                                path = parts[1];
                            }
                            
                            Identifier inputId = Identifier.of(namespace, path);
                            inputItem = Registries.ITEM.get(inputId);
                            Circuitmod.LOGGER.info("[DIRECT-RECIPES] Method 2 - Input item found: " + (inputItem != null));
                        } catch (Exception e) {
                            Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Method 2 failed: " + e.getMessage());
                        }
                    }
                    
                    // Continuing only if we found the input item
                    if (inputItem == null) {
                        Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Input item not found after all attempts: " + inputItemId);
                        return;
                    }
                    
                    // Get the output item
                    if (!json.has("result")) {
                        Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Recipe missing 'result' field: " + id.getPath());
                        return;
                    }
                    
                    JsonElement resultElement = json.get("result");
                    String outputItemId;
                    int count = 1;
                    
                    if (resultElement.isJsonObject()) {
                        JsonObject resultJson = resultElement.getAsJsonObject();
                        if (!resultJson.has("item")) {
                            Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Result missing 'item' field: " + id.getPath());
                            return;
                        }
                        
                        outputItemId = resultJson.get("item").getAsString();
                        if (resultJson.has("count")) {
                            count = resultJson.get("count").getAsInt();
                        }
                    } else {
                        // Old format
                        outputItemId = resultElement.getAsString();
                    }
                    
                    Circuitmod.LOGGER.info("[DIRECT-RECIPES] Output item ID string: " + outputItemId);
                    
                    // Try multiple ways to parse the output identifier
                    Item outputItem = null;
                    try {
                        // Method 1: Use Identifier.of directly
                        Identifier outputId = Identifier.of(outputItemId);
                        outputItem = Registries.ITEM.get(outputId);
                        Circuitmod.LOGGER.info("[DIRECT-RECIPES] Method 1 - Output item found: " + (outputItem != null));
                    } catch (Exception e) {
                        Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Method 1 failed for output: " + e.getMessage());
                    }
                    
                    // Try method 2 if method 1 failed
                    if (outputItem == null) {
                        try {
                            // Method 2: Split namespace and path manually
                            String namespace = "minecraft";
                            String path = outputItemId;
                            
                            if (outputItemId.contains(":")) {
                                String[] parts = outputItemId.split(":", 2);
                                namespace = parts[0];
                                path = parts[1];
                            }
                            
                            Identifier outputId = Identifier.of(namespace, path);
                            outputItem = Registries.ITEM.get(outputId);
                            Circuitmod.LOGGER.info("[DIRECT-RECIPES] Method 2 - Output item found: " + (outputItem != null));
                        } catch (Exception e) {
                            Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Method 2 failed for output: " + e.getMessage());
                        }
                    }
                    
                    if (outputItem == null) {
                        Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Output item not found after all attempts: " + outputItemId);
                        return;
                    }
                    
                    // Store the recipe
                    ItemStack result = new ItemStack(outputItem, count);
                    RECIPE_CACHE.put(inputItem, result);
                    
                    Circuitmod.LOGGER.info("[DIRECT-RECIPES] Successfully added recipe: " + 
                        inputItem + " -> " + outputItem + " (x" + count + ")");
                } else {
                    Circuitmod.LOGGER.warn("[DIRECT-RECIPES] Ingredient has no 'item' field: " + id.getPath());
                }
            }
        } catch (Exception e) {
            Circuitmod.LOGGER.error("[DIRECT-RECIPES] Error parsing recipe " + id.getPath() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void tryLoadFromFileSystem() {
        Circuitmod.LOGGER.info("[DIRECT-RECIPES] Attempting to load recipes directly from file system");
        
        // Try a few common locations
        String[] possiblePaths = {
            "src/main/resources/data/circuitmod/recipes/",
            "./resources/data/circuitmod/recipes/",
            "../resources/data/circuitmod/recipes/",
            "./data/circuitmod/recipes/",
            "./circuitmod/recipes/"
        };
        
        for (String path : possiblePaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                Circuitmod.LOGGER.info("[DIRECT-RECIPES] Found directory: " + dir.getAbsolutePath());
                
                File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                if (files != null) {
                    Circuitmod.LOGGER.info("[DIRECT-RECIPES] Found " + files.length + " JSON files");
                    
                    // TODO: Load recipes from file system if needed
                    // This is mainly for debugging - we ideally shouldn't need to go this route
                }
            }
        }
    }
    
    /**
     * Try to find a recipe result for the given input item
     */
    public static Optional<ItemStack> findRecipe(Item input) {
        if (RECIPE_CACHE.containsKey(input)) {
            ItemStack result = RECIPE_CACHE.get(input);
            Circuitmod.LOGGER.info("[DIRECT-RECIPES] Found recipe for " + input + ": " + result.getItem());
            return Optional.of(result.copy());
        }
        return Optional.empty();
    }
} 
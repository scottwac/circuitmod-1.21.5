package starduster.circuitmod.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import starduster.circuitmod.blueprint.Blueprint;
import starduster.circuitmod.Circuitmod;

import java.util.List;

/**
 * Physical blueprint item that players can carry.
 * Stores blueprint data in NBT and displays information in tooltips.
 */
public class BlueprintItem extends Item {
    
    public BlueprintItem(Settings settings) {
        super(settings);
    }
    
    /**
     * Stores a blueprint in an ItemStack
     */
    public static ItemStack createWithBlueprint(Blueprint blueprint, RegistryWrapper.WrapperLookup registries) {
        ItemStack stack = new ItemStack(ModItems.BLUEPRINT);
        setBlueprint(stack, blueprint, registries);
        return stack;
    }
    
    /**
     * Sets the blueprint data on an ItemStack
     */
    public static void setBlueprint(ItemStack stack, Blueprint blueprint, RegistryWrapper.WrapperLookup registries) {
        if (stack.getItem() instanceof BlueprintItem) {
            NbtCompound blueprintNbt = blueprint.writeToNbt(registries);
            NbtComponent component = NbtComponent.of(blueprintNbt);
            stack.set(DataComponentTypes.CUSTOM_DATA, component);
            
            // Set custom name based on blueprint name
            Text customName = Text.literal("Blueprint: " + blueprint.getName())
                .formatted(Formatting.AQUA);
            stack.set(DataComponentTypes.CUSTOM_NAME, customName);
            
            Circuitmod.LOGGER.info("[BLUEPRINT-ITEM] Created blueprint item for '{}'", blueprint.getName());
        }
    }
    
    /**
     * Gets the blueprint data from an ItemStack
     */
    public static Blueprint getBlueprint(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        if (stack.getItem() instanceof BlueprintItem) {
            NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (component != null) {
                try {
                    NbtCompound nbt = component.copyNbt();
                    return Blueprint.readFromNbt(nbt, registries);
                } catch (Exception e) {
                    Circuitmod.LOGGER.error("[BLUEPRINT-ITEM] Failed to read blueprint from item", e);
                }
            }
        }
        return null;
    }
    
    /**
     * Checks if an ItemStack contains a valid blueprint
     */
    public static boolean hasBlueprint(ItemStack stack) {
        if (stack.getItem() instanceof BlueprintItem) {
            NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
            return component != null && !component.copyNbt().isEmpty();
        }
        return false;
    }
    
    /**
     * Gets a preview of the blueprint without full deserialization
     */
    public static String getBlueprintPreview(ItemStack stack) {
        if (stack.getItem() instanceof BlueprintItem) {
            NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (component != null) {
                try {
                    NbtCompound nbt = component.copyNbt();
                    String name = nbt.getString("name", "Untitled");
                    int width = nbt.getInt("width", 0);
                    int height = nbt.getInt("height", 0);
                    int length = nbt.getInt("length", 0);
                    int totalBlocks = nbt.getInt("total_blocks", 0);
                    
                    return String.format("%s (%dx%dx%d, %d blocks)", 
                        name.isEmpty() ? "Untitled" : name, width, height, length, totalBlocks);
                } catch (Exception e) {
                    return "Invalid Blueprint";
                }
            }
        }
        return "Empty Blueprint";
    }
    
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        
        if (hasBlueprint(stack)) {
            String preview = getBlueprintPreview(stack);
            tooltip.add(Text.literal(preview).formatted(Formatting.GRAY));
            
            // Try to get more detailed info if possible
            NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (component != null) {
                try {
                    NbtCompound nbt = component.copyNbt();
                    long createdTime = nbt.getLong("created_time", 0L);
                    
                    if (createdTime > 0) {
                        // Convert timestamp to relative time (simplified)
                        long currentTime = System.currentTimeMillis();
                        long diffSeconds = (currentTime - createdTime) / 1000;
                        
                        String timeAgo;
                        if (diffSeconds < 60) {
                            timeAgo = diffSeconds + " seconds ago";
                        } else if (diffSeconds < 3600) {
                            timeAgo = (diffSeconds / 60) + " minutes ago";
                        } else if (diffSeconds < 86400) {
                            timeAgo = (diffSeconds / 3600) + " hours ago";
                        } else {
                            timeAgo = (diffSeconds / 86400) + " days ago";
                        }
                        
                        tooltip.add(Text.literal("Created: " + timeAgo).formatted(Formatting.DARK_GRAY));
                    }
                    
                    // Add required blocks info if available
                    if (nbt.contains("required_blocks")) {
                        int uniqueTypes = nbt.getList("required_blocks").orElse(new net.minecraft.nbt.NbtList()).size();
                        tooltip.add(Text.literal("Block types: " + uniqueTypes).formatted(Formatting.DARK_GRAY));
                    }
                    
                } catch (Exception e) {
                    tooltip.add(Text.literal("Blueprint data corrupted").formatted(Formatting.RED));
                }
            }
        } else {
            tooltip.add(Text.literal("No blueprint data").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("Use a Blueprinter to create a blueprint").formatted(Formatting.DARK_GRAY));
        }
        
        // Usage instructions
        tooltip.add(Text.literal("").formatted(Formatting.RESET)); // Empty line
        tooltip.add(Text.literal("Place in Constructor to build").formatted(Formatting.YELLOW));
    }
    
    @Override
    public boolean hasGlint(ItemStack stack) {
        // Add enchantment glint if the blueprint has data
        return hasBlueprint(stack);
    }
    
    @Override
    public int getMaxCount() {
        return 1; // Blueprints should be unique items
    }
} 
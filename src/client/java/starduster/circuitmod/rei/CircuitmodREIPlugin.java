// CircuitmodREIPlugin.java
package starduster.circuitmod.rei;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;

import starduster.circuitmod.block.ModBlocks;
import starduster.circuitmod.recipe.CrusherRecipe;
import starduster.circuitmod.recipe.ModRecipes;

@Environment(EnvType.CLIENT)
public class CircuitmodREIPlugin implements REIClientPlugin {
    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new CrusherCategory());
        registry.addWorkstations(
            CrusherCategoryIdentifiers.CRUSHER,
            EntryStacks.of(ModBlocks.CRUSHER)
        );
        // → later, add more categories & workstations here
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry
            .beginFiller(CrusherRecipe.class)
            .filter(r -> r.getType() == ModRecipes.CRUSHER_TYPE)
            .fill(CrusherREIDisplay::new);
        // → later, add more fillers for your other recipe types
    }
}

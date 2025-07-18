package starduster.circuitmod.rei;

import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.recipe.CrusherRecipe;

import java.util.List;
import java.util.Optional;

public class CrusherREIDisplay implements Display {
    private final List<EntryIngredient> inputs;
    private final List<EntryIngredient> outputs;

    public CrusherREIDisplay(CrusherRecipe recipe) {
        // single‐slot input
        this.inputs = EntryIngredients.ofIngredients(recipe.getIngredients());
        // two separate output stacks
        this.outputs = List.of(
            EntryIngredients.of(recipe.output1()),
            EntryIngredients.of(recipe.output2())
        );
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return inputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return outputs;
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        // we don't need to link back to a JSON ID for these
        return Optional.empty();
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CrusherCategoryIdentifiers.CRUSHER;
    }

    @Override
    @Nullable
    public DisplaySerializer<?> getSerializer() {
        // no need for server‑to‑client syncing
        return null;
    }
}

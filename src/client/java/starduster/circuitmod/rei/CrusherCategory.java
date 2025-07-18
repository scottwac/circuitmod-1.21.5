// src/main/java/starduster/circuitmod/rei/CrusherCategory.java
package starduster.circuitmod.rei;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;

import starduster.circuitmod.block.ModBlocks;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CrusherCategory implements DisplayCategory<CrusherREIDisplay> {
    // Again, use the factory method for Identifiers
    private static final Identifier BACKGROUND =
        Identifier.of("circuitmod", "textures/gui/machines/crusher_gui.png");

    @Override
    public CategoryIdentifier<? extends CrusherREIDisplay> getCategoryIdentifier() {
        return CrusherCategoryIdentifiers.CRUSHER;
    }

    @Override
    public Renderer getIcon() {
        // Make sure you import me.shedaniel.rei.api.client.gui.Renderer
        return EntryStacks.of(ModBlocks.CRUSHER);
    }

    @Override
    public Text getTitle() {
        return Text.translatable("rei.circuitmod.crusher");
    }

    @Override
    public List<Widget> setupDisplay(CrusherREIDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();

        // Panel background
        widgets.add(Widgets.createRecipeBase(bounds));

        // Draw your custom texture across the entire recipe area
        widgets.add(Widgets.createTexturedWidget(BACKGROUND, bounds));

        // Slot positions relative to bounds
        int x = bounds.getX(), y = bounds.getY();

        // Input slot
        widgets.add(Widgets.createSlot(new Point(x + 20, y + 20))
            .entries(display.getInputEntries().get(0))
            .disableBackground());

        // Output slots
        widgets.add(Widgets.createSlot(new Point(x + 60, y + 20))
            .entries(display.getOutputEntries().get(0))
            .disableBackground());
        widgets.add(Widgets.createSlot(new Point(x + 80, y + 20))
            .entries(display.getOutputEntries().get(1))
            .disableBackground());

        return widgets;
    }
}

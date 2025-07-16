package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class MassFabricatorScreen extends HandledScreen<MassFabricatorScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/mass_fabricator_gui.png");
    // TODO: Add button and progress bar textures as needed

    public MassFabricatorScreen(MassFabricatorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // Optionally center title: titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        // TODO: Add button widgets for resource selection
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
        renderProgressBar(context, x, y);
        renderResourceButtons(context, x, y);
        // TODO: Render firework effect if needed
    }

    private void renderProgressBar(DrawContext context, int x, int y) {
        int progress = handler.getProgress();
        int maxProgress = handler.getMaxProgress();
        int progressWidth = (int) (24 * (progress / (float) maxProgress));
        // TODO: Use a separate progress bar texture if desired
        if (progress > 0) {
            context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x + 76, y + 34, 176, 0, progressWidth, 16, 256, 256);
        }
    }

    private void renderResourceButtons(DrawContext context, int x, int y) {
        // TODO: Draw the 4 resource selection buttons (diamond, emerald, netherite, gold)
        // Use context.drawTexture(RenderLayer::getGuiTextured, ...) for each button
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
} 
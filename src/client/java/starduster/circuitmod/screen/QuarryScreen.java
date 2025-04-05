package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class QuarryScreen extends HandledScreen<QuarryScreenHandler> {
    // Use generic chest texture
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/generic_54.png");

    public QuarryScreen(QuarryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // Adjust the height since we're using only 3 rows, not 6 like the texture
        this.backgroundHeight = 168; // Default vanilla chest with 3 rows is 168
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        
        // Draw the quarry inventory part (top part)
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, 3 * 18 + 17, 256, 256);
        
        // Draw the player inventory part (bottom part)
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y + 3 * 18 + 17, 0, 126, backgroundWidth, 96, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void init() {
        super.init();
        // Center the title
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
    }
} 
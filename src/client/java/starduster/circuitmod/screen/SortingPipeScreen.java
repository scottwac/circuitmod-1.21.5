package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class SortingPipeScreen extends HandledScreen<SortingPipeScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/sorting_pipe/sorting_pipe_gui.png");
    
    public SortingPipeScreen(SortingPipeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 180;
        this.backgroundWidth = 176;
    }

    @Override
    protected void init() {
        super.init();
        // Center the title
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        titleY = 6;
        
        // Hide player inventory label or move it down
        playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        
        // Draw the main GUI background
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
        
        // Draw direction labels
        drawDirectionLabels(context, x, y);
    }
    
    private void drawDirectionLabels(DrawContext context, int x, int y) {
        int centerX = x + 80;
        int centerY = y + 30; // Updated to match handler positioning
        
        // Draw direction labels next to compass slots
        // NORTH (above center slot)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("N"), centerX, centerY - 28, 0xFFFFFF);
        
        // SOUTH (below center slot)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("S"), centerX, centerY + 28, 0xFFFFFF);
        
        // EAST (right of center slot)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("E"), centerX + 28, centerY, 0xFFFFFF);
        
        // WEST (left of center slot)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("W"), centerX - 28, centerY, 0xFFFFFF);
        
        // UP/DOWN off to the side
        // UP (right side, upper)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("UP"), centerX + 45, centerY - 19, 0xFFFFFF);
        
        // DOWN (right side, lower)
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("DOWN"), centerX + 45, centerY + 19, 0xFFFFFF);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw the title
        context.drawText(textRenderer, title, titleX, titleY, 0x404040, false);
        
        // Draw instruction text
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("Place items to filter by direction"), 
            backgroundWidth / 2, 75, 0x888888);
    }
} 
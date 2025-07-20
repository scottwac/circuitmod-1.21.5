package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class BlueprintDeskScreen extends HandledScreen<BlueprintDeskScreenHandler> {
    // Use quarry GUI texture for now
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/machines/blueprint_desk_gui.png");
    
    public BlueprintDeskScreen(BlueprintDeskScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // Standard GUI size
        this.backgroundHeight = 168;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }
    
    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // Update any dynamic elements here if needed
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Draw the background texture
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        
        // Draw status information
        drawStatusInfo(context);
        
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    
    private void drawStatusInfo(DrawContext context) {
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        
        // Connection status
        Text connectionText;
        int connectionColor;
        if (handler.hasValidPartner()) {
            connectionText = Text.literal("Connected");
            connectionColor = 0x00FF00; // Green
        } else {
            connectionText = Text.literal("No Partner");
            connectionColor = 0xFF0000; // Red
        }
        context.drawText(textRenderer, connectionText, baseX + 10, baseY + 21, connectionColor, false);
        
        // Scanning status
        if (handler.hasValidPartner()) {
            if (handler.isScanning()) {
                Text scanText = Text.literal("Scanning... " + handler.getScanProgress() + "%");
                context.drawText(textRenderer, scanText, baseX + 10, baseY + 33, 0xFFFF00, false); // Yellow
                
                // Draw progress bar
                drawProgressBar(context, baseX + 11, baseY + 57, 64, 10, handler.getScanProgress());
            } else {
                Text readyText = Text.literal("Ready - Insert Blueprint");
                context.drawText(textRenderer, readyText, baseX + 10, baseY + 33, 0x00FF00, false); // Green
            }
        } else {
            Text instructionText = Text.literal("Place partner desk to connect");
            context.drawText(textRenderer, instructionText, baseX + 10, baseY + 33, 0xAAAAAA, false); // Gray
        }
    }
    
    private void drawProgressBar(DrawContext context, int x, int y, int width, int height, int progress) {
        // Background (dark gray)
        context.fill(x, y, x + width, y + height, 0xFF444444);
        
        // Progress fill (bright green)
        int fillWidth = (width * progress) / 100;
        if (fillWidth > 0) {
            context.fill(x, y, x + fillWidth, y + height, 0xFF00FF00);
        }
        
        // Border (light gray)
        context.drawBorder(x, y, width, height, 0xFFAAAAAA);
    }

    @Override
    protected void init() {
        super.init();
        // Center the title
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        
        Circuitmod.LOGGER.info("[BLUEPRINT-DESK-SCREEN] Screen initialized");
    }
} 
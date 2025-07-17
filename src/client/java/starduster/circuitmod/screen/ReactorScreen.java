package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class ReactorScreen extends HandledScreen<ReactorScreenHandler> {
    // Use generic chest texture for now
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/generator/reactor_gui.png");
    // Colors for text
    private static final int ENERGY_COLOR = 0xFF00FF00; // Green
    private static final int ROD_COUNT_COLOR = 0xFFFFFF00; // Yellow
    private static final int STATUS_COLOR = 0xFFFFFFFF; // White

    public ReactorScreen(ReactorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // Adjust the height since we're using only 3 rows, not 6 like the texture
        this.backgroundHeight = 168; // Default vanilla chest with 3 rows is 168
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }
    
    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // Any per-tick updates can go here
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Draw the background
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);
        
        // Draw reactor information
        int energyProduction = handler.getEnergyProduction();
        int rodCount = handler.getRodCount();
        boolean isActive = handler.isActive();
        
        // Debug logging for screen values
        if (System.currentTimeMillis() % 1000 < 50) { // Log roughly once per second
            Circuitmod.LOGGER.info("[REACTOR-SCREEN] Energy: {}, Rods: {}, Active: {}", energyProduction, rodCount, isActive);
        }
        
        // Draw energy production
        Text energyText = Text.literal("Energy: " + energyProduction + "/tick");
        context.drawText(textRenderer, energyText, 8+2, 8+13, ENERGY_COLOR, true);
        
        // Draw rod count
        Text rodText = Text.literal("Rods: " + rodCount + "/9");
        context.drawText(textRenderer, rodText, 8+2, 20+13, ROD_COUNT_COLOR, true);
        
        // Draw status
        Text statusText = Text.literal("Status: " + (isActive ? "Active" : "Inactive"));
        context.drawText(textRenderer, statusText, 8+2, 32+13, STATUS_COLOR, true);
        
        // Draw title
        context.drawText(textRenderer, title, titleX, titleY, 0x404040, false);
    }
} 
package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class GeneratorScreen extends HandledScreen<GeneratorScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/generator/fuel_generator_gui.png");
    
    public GeneratorScreen(GeneratorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }
    
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, 
        this.backgroundHeight, 256, 256);
        
        // Draw fuel burning progress
        if (handler.isBurning()) {
            int fuelProgress = handler.getScaledFuelProgress();
            context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x + 81, this.y + 28 + 12 - fuelProgress,
             176, 12 - fuelProgress, 14, fuelProgress + 1, 256, 256);
        }
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw title
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
        
        // Draw power production info
        int powerProduction = handler.getPowerProduction();
        String powerText = "Power: " + powerProduction + " RF/t";
        context.drawText(this.textRenderer, Text.literal(powerText), 8, 60, 0x404040, false);
        
        // Draw burning status
        String statusText = handler.isBurning() ? "Burning" : "Not Burning";
        int statusColor = handler.isBurning() ? 0x00FF00 : 0xFF0000;
        context.drawText(this.textRenderer, Text.literal(statusText), 8, 72, statusColor, false);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
} 
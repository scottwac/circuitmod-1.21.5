package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class GeneratorScreen extends HandledScreen<GeneratorScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/generator/temp_fuel_gen.png");
    private static final Identifier FLAME_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/sprites/flame.png");

    private static final int ENERGY_COLOR = 0xFF00FF00; // Green
    private static final int STATUS_COLOR = 0xFFFFFFFF;

    public GeneratorScreen(GeneratorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        //titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
        
        // Draw fuel burning progress
        renderFuelIndicator(context, x, y);
    }

    private void renderFuelIndicator(DrawContext context, int x, int y) {
        int indicatorScale = handler.getScaledFuelProgress();
        int offset = 14 - indicatorScale;
        if (handler.isBurning()) {
            context.drawTexture(RenderLayer::getGuiTextured, FLAME_TEXTURE, x + 125, y + 36 + offset, 0, offset,
                    14, indicatorScale, 14, 14);
        }
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw title
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
        
        // Draw power production info
        int powerProduction = handler.getPowerProduction();
        String powerText = "Energy: " + powerProduction + "/tick";
        context.drawText(this.textRenderer, Text.literal(powerText), 8+2, 8+13, ENERGY_COLOR, false);
        
        // Draw burning status
        String statusText = handler.isBurning() ? "Status: Active" : "Status: Inactive";
        int statusColor = handler.isBurning() ? 0x00FF00 : 0xFF0000;
        context.drawText(this.textRenderer, Text.literal(statusText), 8+2, 20+13, STATUS_COLOR, false);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
} 
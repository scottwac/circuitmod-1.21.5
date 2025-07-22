package starduster.circuitmod.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

@Environment(EnvType.CLIENT)
public class ElectricFurnaceScreen extends HandledScreen<ElectricFurnaceScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/machines/electric_furnace_gui.png");
    private static final Identifier POWER_SPRITE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/sprites/energy_sprite.png");
    private static final Identifier ARROW_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/bloomery/bloomery_progress_arrow.png");

    public ElectricFurnaceScreen(ElectricFurnaceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }
    
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Draw the background texture
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, 
        this.backgroundHeight, 256, 256);
        
        // Draw smelting progress arrow
        if (handler.isBurning()) {
            int progress = (int)(handler.getCookProgress() * 24);
            context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x + 79, this.y + 34, 176, 14, progress + 1, 16, 256, 256);
        }
        
        // Draw power indicator if powered
        if (handler.isPowered()) {
            context.drawTexture(RenderLayer::getGuiTextured, POWER_SPRITE, x + 61, y + 35, 0, 0,
                    7, 17, 7, 17);
        }
        renderProgressArrow(context, x, y);
    }

    private void renderProgressArrow(DrawContext context, int x, int y) {
        int arrowScale = handler.getScaledArrowProgress();
        if(handler.isSmelting()) {
            context.drawTexture(RenderLayer::getGuiTextured, ARROW_TEXTURE, x + 79, y + 35, 0, 0,
                    arrowScale, 16, 24, 16);
        }
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw title
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
        
//        // Draw power status
//        String statusText = handler.isPowered() ? "Powered" : "Not Powered";
//        int statusColor = handler.isPowered() ? 0x00FF00 : 0xFF0000;
//        context.drawText(this.textRenderer, Text.literal(statusText), 8, 60, statusColor, false);
//
//        // Draw smelting status
//        String smeltingText = handler.isBurning() ? "Smelting" : "Not Smelting";
//        int smeltingColor = handler.isBurning() ? 0x00FF00 : 0xFF0000;
//        context.drawText(this.textRenderer, Text.literal(smeltingText), 8, 72, smeltingColor, false);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
} 
package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class BloomeryScreen extends HandledScreen<BloomeryScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/bloomery/bloomery_gui.png");
    private static final Identifier ARROW_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/bloomery/bloomery_progress_arrow.png");

    public BloomeryScreen(BloomeryScreenHandler handler, PlayerInventory inventory, Text title) {
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
        
        // Draw the background
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
        
        // Draw the burn progress indicator
        if (this.handler.isBurning()) {
            int burnProgress = this.getBurnProgress(13);
            context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x + 82, y + 36 + 12 - burnProgress, 176, 12 - burnProgress, 14, burnProgress + 1, 256, 256);
        }
        
        // Draw the cook progress indicator
        int cookProgress = this.getCookProgress(24);
        context.drawTexture(RenderLayer::getGuiTextured, ARROW_TEXTURE, x + 82, y + 36, 0, 0, handler.getScaledProgressArrow() + 1, 16, 24, 16);
        //renderProgressArrow(context, x, y);
    }

//    private void renderProgressArrow(DrawContext context, int x, int y) {
//        int cookProgress = this.getCookProgress(24);
//        if(cookProgress > 0) {
//            context.drawTexture(RenderLayer::getGuiTextured, ARROW_TEXTURE, x + 82, y + 36, 0,0, handler.getScaledProgressArrow(), 16, 24, 16);
//        }
//    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    
    private int getBurnProgress(int pixels) {
        int burnTime = this.handler.getBurnTime();
        if (burnTime == 0) {
            return 0;
        }
        
        // Calculate how much of the burn indicator to show
        return burnTime * pixels / 200; // Assuming max burn time is 200 ticks
    }
    
    private int getCookProgress(int pixels) {
        int cookTime = this.handler.getCookTime();
        int cookTimeTotal = this.handler.getCookTimeTotal();
        
        if (cookTimeTotal == 0 || cookTime == 0) {
            return 0;
        }
        
        // Calculate the cook progress
        return cookTime * pixels / cookTimeTotal;
    }
} 
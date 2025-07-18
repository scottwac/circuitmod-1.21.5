package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class CrusherScreen extends HandledScreen<CrusherScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/machines/crusher_gui.png");
    private static final Identifier ARROW_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/sprites/energy_sprite.png");

    public CrusherScreen(CrusherScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        renderProgressArrow(context, x, y);
    }

    private void renderProgressArrow(DrawContext context, int x, int y) {
        int arrowScale = handler.getScaledArrowProgress();
        if(handler.isCrushing()) {
            context.drawTexture(RenderLayer::getGuiTextured, ARROW_TEXTURE, x + 82, y + 36, 0, 0,
                    arrowScale, 16, 24, 16);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
} 
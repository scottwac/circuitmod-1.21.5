package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

import java.util.Optional;

public class RubberTapScreen extends HandledScreen<RubberTapScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/rubber_tap/rubber_tap_gui.png");
    private static final Identifier ARROW_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/rubber_tap/progress_bar.png");
    private static final Identifier GOOD_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/rubber_tap/good_tree.png");
    private static final Identifier ERROR_TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/rubber_tap/bad_tree.png");
    private static final Text ERROR_TOOLTIP = Text.translatable("tooltip.circuitmod.container.rubber_tap.error_tooltip");

    public RubberTapScreen(RubberTapScreenHandler handler, PlayerInventory inventory, Text title) {
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

        renderTreeTexture(context,x,y);
        renderProgressArrow(context, x, y);
        renderToolTips(context, mouseX, mouseY);
    }

    private void renderProgressArrow(DrawContext context, int x, int y) {
        int arrowScale = handler.getScaledArrowProgress();
        int valid = handler.isOnValidBlock();

        if(valid == 1) {
            context.drawTexture(RenderLayer::getGuiTextured, ARROW_TEXTURE, x + 86, y + 37, 0, 0,
                    4, arrowScale, 4, 10);
        }
    }

    private void renderTreeTexture(DrawContext context, int x, int y) {
        int valid = handler.isOnValidBlock();
        if(valid == 1) {
            context.drawTexture(RenderLayer::getGuiTextured, GOOD_TEXTURE,x + 73,y + 8,0,0,30,40,30,40);
        } else {context.drawTexture(RenderLayer::getGuiTextured, ERROR_TEXTURE,x + 73,y + 8,0,0,30,40,30,40);}
    }

    //59, 5

    private void renderToolTips(DrawContext context, int mouseX, int mouseY) {
        Optional<Text> optional = Optional.empty();
        int valid = handler.isOnValidBlock();
        if (valid != 1 && isPointWithinBounds(74, 9, 28, 37, (double)mouseX, (double)mouseY)) {
            optional = Optional.of(ERROR_TOOLTIP);
        }
        optional.ifPresent((text) -> context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(text, 200), mouseX, mouseY));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

    }
} 
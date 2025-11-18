package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.network.ClientNetworking;

public class HologramTableScreen extends HandledScreen<HologramTableScreenHandler> {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 180;
    private static final int FIELD_WIDTH = 70;
    private static final int FIELD_HEIGHT = 18;

    private TextFieldWidget minXField;
    private TextFieldWidget maxXField;
    private TextFieldWidget minZField;
    private TextFieldWidget maxZField;
    private TextFieldWidget minYField;
    private ButtonWidget applyButton;
    private ButtonWidget resetButton;
    private Text statusText = Text.empty();
    private int statusTicks = 0;

    public HologramTableScreen(HologramTableScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = PANEL_WIDTH;
        this.backgroundHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        titleY = 7;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = backgroundHeight - 94;

        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;

        minXField = createNumberField(left + 18, top + 28, FieldLabel.MIN_X);
        maxXField = createNumberField(left + 94, top + 28, FieldLabel.MAX_X);
        minZField = createNumberField(left + 18, top + 62, FieldLabel.MIN_Z);
        maxZField = createNumberField(left + 94, top + 62, FieldLabel.MAX_Z);
        minYField = createNumberField(left + 56, top + 96, FieldLabel.MIN_Y);

        addDrawableChild(minXField);
        addDrawableChild(maxXField);
        addDrawableChild(minZField);
        addDrawableChild(maxZField);
        addDrawableChild(minYField);

        applyButton = ButtonWidget.builder(Text.literal("Apply"), btn -> applyChanges(false))
            .dimensions(left + 16, top + 130, 60, 20)
            .build();
        resetButton = ButtonWidget.builder(Text.literal("Reset"), btn -> applyChanges(true))
            .dimensions(left + 100, top + 130, 60, 20)
            .build();

        addDrawableChild(applyButton);
        addDrawableChild(resetButton);

        syncFieldsFromProperties();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(minXField);
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        // TextFieldWidget doesn't need explicit ticking in 1.21.5

        if (statusTicks > 0) {
            statusTicks--;
            if (statusTicks == 0) {
                statusText = Text.empty();
            }
        }
    }

    private TextFieldWidget createNumberField(int x, int y, FieldLabel label) {
        TextFieldWidget widget = new TextFieldWidget(textRenderer, x, y, FIELD_WIDTH, FIELD_HEIGHT, Text.literal(label.display()));
        widget.setTextPredicate(text -> text.matches("-?\\d{0,6}"));
        widget.setChangedListener(text -> validateFields());
        return widget;
    }

    private void syncFieldsFromProperties() {
        minXField.setText(Integer.toString(handler.getMinX()));
        maxXField.setText(Integer.toString(handler.getMaxX()));
        minZField.setText(Integer.toString(handler.getMinZ()));
        maxZField.setText(Integer.toString(handler.getMaxZ()));
        minYField.setText(Integer.toString(handler.getMinY()));
    }

    private void applyChanges(boolean reset) {
        BlockPos pos = handler.getBlockEntityPos();
        if (pos == null || client == null) {
            return;
        }

        if (reset) {
            ClientNetworking.sendHologramAreaUpdate(pos, 0, 0, 0, 0, 0, true);
            showStatus(Text.literal("Rendering reset to chunk."), Formatting.GREEN);
            return;
        }

        try {
            int minX = Integer.parseInt(minXField.getText());
            int maxX = Integer.parseInt(maxXField.getText());
            int minZ = Integer.parseInt(minZField.getText());
            int maxZ = Integer.parseInt(maxZField.getText());
            int minY = Integer.parseInt(minYField.getText());

            ClientNetworking.sendHologramAreaUpdate(pos, minX, maxX, minZ, maxZ, minY, false);
            showStatus(Text.literal("Render area updated."), Formatting.GREEN);
        } catch (NumberFormatException ex) {
            showStatus(Text.literal("Invalid numbers entered."), Formatting.RED);
        }
    }

    private void showStatus(Text base, Formatting formatting) {
        statusText = base.copy().formatted(formatting);
        statusTicks = 60;
    }

    private void validateFields() {
        // Optionally update live state or button availability
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;

        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xAA101010);
        context.fill(left + 7, top + 20, left + backgroundWidth - 7, top + 122, 0xFF1E1E1E);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;

        // Draw labels
        context.drawText(textRenderer, Text.literal("X Range").formatted(Formatting.AQUA), left + 14, top + 18, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal("Z Range").formatted(Formatting.AQUA), left + 14, top + 52, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal("Min Y").formatted(Formatting.AQUA), left + 14, top + 86, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal("Render up to world max Y."), left + 14, top + 110, 0x888888, false);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Max area 128 × 128").formatted(Formatting.GOLD), width / 2, top + 118, 0xFFFFFF);

        // Render text fields (required for them to be visible and interactive)
        minXField.render(context, mouseX, mouseY, delta);
        maxXField.render(context, mouseX, mouseY, delta);
        minZField.render(context, mouseX, mouseY, delta);
        maxZField.render(context, mouseX, mouseY, delta);
        minYField.render(context, mouseX, mouseY, delta);

        if (!statusText.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, statusText, width / 2, top + 155, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minXField.mouseClicked(mouseX, mouseY, button)) return true;
        if (maxXField.mouseClicked(mouseX, mouseY, button)) return true;
        if (minZField.mouseClicked(mouseX, mouseY, button)) return true;
        if (maxZField.mouseClicked(mouseX, mouseY, button)) return true;
        if (minYField.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minXField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (maxXField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (minZField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (maxZField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (minYField.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (minXField.charTyped(chr, modifiers)) return true;
        if (maxXField.charTyped(chr, modifiers)) return true;
        if (minZField.charTyped(chr, modifiers)) return true;
        if (maxZField.charTyped(chr, modifiers)) return true;
        if (minYField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    private enum FieldLabel {
        MIN_X("Min X"),
        MAX_X("Max X"),
        MIN_Z("Min Z"),
        MAX_Z("Max Z"),
        MIN_Y("Min Y");

        private final String display;

        FieldLabel(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }
    }
}


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

public class MissileControlScreen extends HandledScreen<MissileControlScreenHandler> {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 180;
    private static final int FIELD_WIDTH = 70;
    private static final int FIELD_HEIGHT = 18;

    private TextFieldWidget targetXField;
    private TextFieldWidget targetYField;
    private TextFieldWidget targetZField;
    private ButtonWidget setButton;
    private ButtonWidget fireButton;
    private Text statusText = Text.empty();
    private int statusTicks = 0;

    public MissileControlScreen(MissileControlScreenHandler handler, PlayerInventory inventory, Text title) {
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

        // Create coordinate input fields
        targetXField = createNumberField(left + 18, top + 35, FieldLabel.TARGET_X);
        targetYField = createNumberField(left + 18, top + 60, FieldLabel.TARGET_Y);
        targetZField = createNumberField(left + 18, top + 85, FieldLabel.TARGET_Z);

        addDrawableChild(targetXField);
        addDrawableChild(targetYField);
        addDrawableChild(targetZField);

        // Create SET button to update coordinates
        setButton = ButtonWidget.builder(Text.literal("SET"), btn -> setTargetCoordinates())
            .dimensions(left + 100, top + 50, 60, 20)
            .build();

        // Create FIRE button to launch missile
        fireButton = ButtonWidget.builder(Text.literal("FIRE").formatted(Formatting.RED, Formatting.BOLD), btn -> fireMissile())
            .dimensions(left + 58, top + 115, 60, 20)
            .build();

        addDrawableChild(setButton);
        addDrawableChild(fireButton);

        syncFieldsFromProperties();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(targetXField);
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();

        if (statusTicks > 0) {
            statusTicks--;
            if (statusTicks == 0) {
                statusText = Text.empty();
            }
        }
    }

    private TextFieldWidget createNumberField(int x, int y, FieldLabel label) {
        TextFieldWidget widget = new TextFieldWidget(textRenderer, x, y, FIELD_WIDTH, FIELD_HEIGHT, Text.literal(label.display()));
        widget.setTextPredicate(text -> text.matches("-?\\d{0,7}"));
        widget.setChangedListener(text -> validateFields());
        return widget;
    }

    private void syncFieldsFromProperties() {
        targetXField.setText(Integer.toString(handler.getTargetX()));
        targetYField.setText(Integer.toString(handler.getTargetY()));
        targetZField.setText(Integer.toString(handler.getTargetZ()));
    }

    private void setTargetCoordinates() {
        BlockPos pos = handler.getBlockEntityPos();
        if (pos == null || client == null) {
            return;
        }

        try {
            int targetX = Integer.parseInt(targetXField.getText());
            int targetY = Integer.parseInt(targetYField.getText());
            int targetZ = Integer.parseInt(targetZField.getText());

            ClientNetworking.sendMissileControlUpdate(pos, targetX, targetY, targetZ);
            showStatus(Text.literal("Target coordinates set."), Formatting.GREEN);
        } catch (NumberFormatException ex) {
            showStatus(Text.literal("Invalid coordinates entered."), Formatting.RED);
        }
    }

    private void fireMissile() {
        BlockPos pos = handler.getBlockEntityPos();
        if (pos == null || client == null) {
            return;
        }

        ClientNetworking.sendMissileFireCommand(pos);
        showStatus(Text.literal("MISSILE FIRING!"), Formatting.YELLOW);
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

        // Dark background
        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xAA101010);
        // Inner panel
        context.fill(left + 7, top + 20, left + backgroundWidth - 7, top + 145, 0xFF1E1E1E);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;

        // Draw title and labels
        context.drawText(textRenderer, Text.literal("Target X").formatted(Formatting.AQUA), left + 18, top + 25, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal("Target Y").formatted(Formatting.AQUA), left + 18, top + 50, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal("Target Z").formatted(Formatting.AQUA), left + 18, top + 75, 0xFFFFFF, false);
        
        context.drawText(textRenderer, Text.literal("Place missile on top of block").formatted(Formatting.GRAY), left + 14, top + 105, 0xFFFFFF, false);

        // Render text fields
        targetXField.render(context, mouseX, mouseY, delta);
        targetYField.render(context, mouseX, mouseY, delta);
        targetZField.render(context, mouseX, mouseY, delta);

        // Render status message
        if (!statusText.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, statusText, width / 2, top + 155, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (targetXField.mouseClicked(mouseX, mouseY, button)) return true;
        if (targetYField.mouseClicked(mouseX, mouseY, button)) return true;
        if (targetZField.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (targetXField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (targetYField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (targetZField.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (targetXField.charTyped(chr, modifiers)) return true;
        if (targetYField.charTyped(chr, modifiers)) return true;
        if (targetZField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    private enum FieldLabel {
        TARGET_X("Target X"),
        TARGET_Y("Target Y"),
        TARGET_Z("Target Z");

        private final String display;

        FieldLabel(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }
    }
}


package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;

public class BlueprintDeskScreen extends HandledScreen<BlueprintDeskScreenHandler> {
    // Use quarry GUI texture for now
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/quarry/quarry_gui.png");
    
    // Text field for blueprint naming
    private TextFieldWidget nameField;
    private String currentName = "New Blueprint";
    
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
        
        // Render the text field
        if (nameField != null) {
            nameField.render(context, mouseX, mouseY, delta);
        }
        
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle text field input first
        if (nameField != null && nameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        // Allow Escape key to close the screen even when text field is focused
        if (keyCode == 256) { // Escape key
            this.close();
            return true;
        }
        
        // Prevent other keys from closing inventory when typing in text field
        if (nameField != null && nameField.isFocused()) {
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Handle text field character input
        if (nameField != null && nameField.charTyped(chr, modifiers)) {
            return true;
        }
        
        return super.charTyped(chr, modifiers);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle text field mouse clicks first
        if (nameField != null && nameField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        // If clicked outside the text field, unfocus it
        if (nameField != null && nameField.isFocused()) {
            nameField.setFocused(false);
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
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
        context.drawText(textRenderer, connectionText, baseX + 8, baseY + 20, connectionColor, false);
        
        // Blueprint name label
        context.drawText(textRenderer, Text.literal("Blueprint Name:"), baseX + 8, baseY + 45, 0xFFFFFF, false);
        
        // Scanning status
        if (handler.hasValidPartner()) {
            if (handler.isScanning()) {
                Text scanText = Text.literal("Scanning... " + handler.getScanProgress() + "%");
                context.drawText(textRenderer, scanText, baseX + 8, baseY + 70, 0xFFFF00, false); // Yellow
                
                // Draw progress bar
                drawProgressBar(context, baseX + 8, baseY + 82, 100, 8, handler.getScanProgress());
            } else {
                Text readyText = Text.literal("Ready - Insert Blueprint");
                context.drawText(textRenderer, readyText, baseX + 8, baseY + 70, 0x00FF00, false); // Green
            }
        } else {
            Text instructionText = Text.literal("Place partner desk to connect");
            context.drawText(textRenderer, instructionText, baseX + 8, baseY + 70, 0xAAAAAA, false); // Gray
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
        
        // Initialize the name text field
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        
        this.nameField = new TextFieldWidget(this.textRenderer, baseX + 8, baseY + 55, 120, 12, Text.literal("Blueprint Name"));
        this.nameField.setMaxLength(32);
        this.nameField.setText(currentName);
        this.nameField.setChangedListener(this::onNameChanged);
        this.nameField.setEditable(true);
        
        // Add the text field to the screen
        this.addSelectableChild(this.nameField);
        this.setInitialFocus(this.nameField);
        
        // Try to get the current name from the block entity if available
        if (handler != null) {
            BlockPos pos = handler.getBlockEntityPos();
            if (pos != null) {
                // Request the current name from the server
                starduster.circuitmod.network.ClientNetworking.requestBlueprintName(pos);
            }
        }
        
        Circuitmod.LOGGER.info("[BLUEPRINT-DESK-SCREEN] Screen initialized with name field");
    }
    
    private void onNameChanged(String newName) {
        this.currentName = newName;
        // Send the name to the server
        if (handler != null) {
            BlockPos pos = handler.getBlockEntityPos();
            if (pos != null && !newName.isEmpty()) {
                starduster.circuitmod.network.ClientNetworking.sendBlueprintNameUpdate(pos, newName);
                Circuitmod.LOGGER.info("[BLUEPRINT-DESK-SCREEN] Name changed to: {}", newName);
            }
        }
    }
    
    /**
     * Update the blueprint name from server sync
     */
    public void updateBlueprintName(String name) {
        this.currentName = name;
        if (nameField != null) {
            nameField.setText(name);
            Circuitmod.LOGGER.info("[BLUEPRINT-DESK-SCREEN] Updated blueprint name from server: {}", name);
        }
    }
} 
package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.network.ClientNetworking;
import starduster.circuitmod.block.entity.QuarryBlockEntity;
import net.minecraft.block.entity.BlockEntity;

public class QuarryScreen extends HandledScreen<QuarryScreenHandler> {
    // Use generic chest texture
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/quarry/quarry_gui.png");
    // Green color for the mining speed text
    private static final int MINING_SPEED_COLOR = 0xFF00FF00; // ARGB format (alpha, red, green, blue)
    
    // Direct mining speed tracking
    private int displayedMiningSpeed = 0;
    
    // Toggle mining button
    private ButtonWidget toggleMiningButton;
    
    // Text input fields for quarry dimensions
    private TextFieldWidget widthField;
    private TextFieldWidget lengthField;
    
    // Track mining enabled state changes
    private boolean lastMiningEnabled = true;
    
    // Flag to prevent change listeners from firing during initialization
    private boolean isInitializing = false;
    
    // Method to update text fields with new dimensions
    public void updateTextFields(int width, int length) {
        if (widthField != null && lengthField != null) {
            isInitializing = true;
            widthField.setText(String.valueOf(width));
            lengthField.setText(String.valueOf(length));
            isInitializing = false;
            Circuitmod.LOGGER.info("[QUARRY-SCREEN] Updated text fields with dimensions: {}x{}", width, length);
        }
    }

    public QuarryScreen(QuarryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // Adjust the height since we're using only 3 rows, not 6 like the texture
        this.backgroundHeight = 168; // Default vanilla chest with 3 rows is 168
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }
    
    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // Update the displayed mining speed from the handler
        int newSpeed = handler.getMiningSpeed();
        
        if (newSpeed != displayedMiningSpeed) {
            Circuitmod.LOGGER.info("[QUARRY-SCREEN] Updating displayed mining speed from {} to {}", displayedMiningSpeed, newSpeed);
            displayedMiningSpeed = newSpeed;
        }
        
        // Check mining enabled status and log changes
        boolean currentMiningEnabled = handler.isMiningEnabled();
        
        if (currentMiningEnabled != lastMiningEnabled) {
            Circuitmod.LOGGER.info("[QUARRY-SCREEN] Mining enabled status changed: {} -> {}", lastMiningEnabled, currentMiningEnabled);
            lastMiningEnabled = currentMiningEnabled;
        }
        
        // Update toggle button (no text, we'll draw texture manually)
        if (toggleMiningButton != null) {
            // Keep button text empty since we're drawing texture manually
            toggleMiningButton.setMessage(Text.literal(""));
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Draw the background
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
        
        // Draw the player inventory part (bottom part)
        //context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y + 3 * 18 + 17, 0, 126, backgroundWidth, 96, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        
        // Refresh mining speed every render - this ensures we catch updates
        int currentSpeed = handler.getMiningSpeed();
        if (currentSpeed != displayedMiningSpeed) {
            Circuitmod.LOGGER.info("[QUARRY-SCREEN] render() updating mining speed from {} to {}", displayedMiningSpeed, currentSpeed);
            displayedMiningSpeed = currentSpeed;
        }
        
        // Draw mining speed text in green above the inventory
        // Use our cached value that gets updated each tick
        Text miningSpeedText = Text.literal("Mining Speed: " + displayedMiningSpeed + " blocks/sec");
        int x = (width - backgroundWidth) / 2 + 8;
        int y = (height - backgroundHeight) / 2 + 6;
        
        context.drawText(
            textRenderer, 
            miningSpeedText, 
            x, 
            y, 
            MINING_SPEED_COLOR, 
            false
        );
        
        // Draw labels for the text fields
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        
        // Width label
        Text widthLabel = Text.literal("Width:");
        context.drawText(textRenderer, widthLabel, baseX + 58, baseY + 8, 0xFFFFFF, false);
        
        // Length label
        Text lengthLabel = Text.literal("Length:");
        context.drawText(textRenderer, lengthLabel, baseX + 58, baseY + 30, 0xFFFFFF, false);
        
        // Draw toggle button indicator
        if (toggleMiningButton != null) {
            drawToggleButtonIndicator(context);
        }
        
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    
    // Helper methods to get current values from text fields
    private int getWidthValue() {
        try {
            if (widthField != null && !widthField.getText().isEmpty()) {
                int value = Integer.parseInt(widthField.getText());
                Circuitmod.LOGGER.info("[QUARRY-SCREEN] getWidthValue() returning: {}", value);
                return value;
            } else {
                Circuitmod.LOGGER.warn("[QUARRY-SCREEN] getWidthValue() - text field is null or empty, returning 16");
                return 16;
            }
        } catch (NumberFormatException e) {
            Circuitmod.LOGGER.warn("[QUARRY-SCREEN] getWidthValue() - invalid number format, returning 16");
            return 16; // Default value
        }
    }
    
    private int getLengthValue() {
        try {
            if (lengthField != null && !lengthField.getText().isEmpty()) {
                int value = Integer.parseInt(lengthField.getText());
                Circuitmod.LOGGER.info("[QUARRY-SCREEN] getLengthValue() returning: {}", value);
                return value;
            } else {
                Circuitmod.LOGGER.warn("[QUARRY-SCREEN] getLengthValue() - text field is null or empty, returning 16");
                return 16;
            }
        } catch (NumberFormatException e) {
            Circuitmod.LOGGER.warn("[QUARRY-SCREEN] getLengthValue() - invalid number format, returning 16");
            return 16; // Default value
        }
    }

    @Override
    protected void init() {
        super.init();
        // Center the title
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        
        // Add toggle mining button at position (18, 52) with size 14x14
        int buttonX = (width - backgroundWidth) / 2 + 18;
        int buttonY = (height - backgroundHeight) / 2 + 52;
        
        Circuitmod.LOGGER.info("[QUARRY-SCREEN] init() - Creating toggle button at ({}, {})", buttonX, buttonY);
        Circuitmod.LOGGER.info("[QUARRY-SCREEN] init() - Handler block pos: {}", handler.getBlockPos());
        
        toggleMiningButton = ButtonWidget.builder(
            Text.literal(""), // Empty text - we'll draw texture underneath
            button -> {
                // Find the quarry position from the client world instead of relying on handler
                BlockPos pos = findQuarryPosition();
                Circuitmod.LOGGER.info("[QUARRY-SCREEN] Button clicked! Found quarry at: {}", pos);
                Circuitmod.LOGGER.info("[QUARRY-SCREEN] Handler pos (for comparison): {}", handler.getBlockPos());
                Circuitmod.LOGGER.info("[QUARRY-SCREEN] Mining enabled before toggle: {}", handler.isMiningEnabled());
                
                if (!pos.equals(BlockPos.ORIGIN)) {
                    ClientNetworking.sendToggleMiningRequest(pos);
                    Circuitmod.LOGGER.info("[QUARRY-SCREEN] Sent toggle mining request to server for position: {}", pos);
                } else {
                    Circuitmod.LOGGER.error("[QUARRY-SCREEN] Could not find quarry position, not sending toggle request!");
                }
            })
            .dimensions(buttonX, buttonY, 14, 14)
            .build();
            
        addDrawableChild(toggleMiningButton);
        
        // Add text input fields for quarry dimensions
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        
        // Width field: (58, 17) to (93, 36) - size 35x19
        widthField = new TextFieldWidget(
            textRenderer,
            baseX + 58, baseY + 17, 35, 19,
            Text.literal("Width")
        );
        widthField.setMaxLength(3); // Max 3 digits
        widthField.setTextPredicate(text -> text.matches("\\d*")); // Only numbers
        widthField.setChangedListener(text -> {
            if (isInitializing) {
                Circuitmod.LOGGER.info("[QUARRY-SCREEN] Skipping width change listener during initialization");
                return;
            }
            if (!text.isEmpty()) {
                try {
                    int width = Integer.parseInt(text);
                    if (width > 0 && width <= 100) { // Reasonable limits
                        BlockPos pos = findQuarryPosition();
                        if (!pos.equals(BlockPos.ORIGIN)) {
                            Circuitmod.LOGGER.info("[QUARRY-SCREEN] Width changed to: {}, sending to server", width);
                            ClientNetworking.sendQuarryDimensions(pos, width, getLengthValue());
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });
        
        // Length field: (58, 39) to (93, 58) - size 35x19
        lengthField = new TextFieldWidget(
            textRenderer,
            baseX + 58, baseY + 39, 35, 19,
            Text.literal("Length")
        );
        lengthField.setMaxLength(3); // Max 3 digits
        lengthField.setTextPredicate(text -> text.matches("\\d*")); // Only numbers
        lengthField.setChangedListener(text -> {
            if (isInitializing) {
                Circuitmod.LOGGER.info("[QUARRY-SCREEN] Skipping length change listener during initialization");
                return;
            }
            if (!text.isEmpty()) {
                try {
                    int length = Integer.parseInt(text);
                    if (length > 0 && length <= 100) { // Reasonable limits
                        BlockPos pos = findQuarryPosition();
                        if (!pos.equals(BlockPos.ORIGIN)) {
                            Circuitmod.LOGGER.info("[QUARRY-SCREEN] Length changed to: {}, sending to server", length);
                            ClientNetworking.sendQuarryDimensions(pos, getWidthValue(), length);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });
        
        // Initialize text fields with current values from the quarry
        isInitializing = true;
        BlockPos quarryPos = findQuarryPosition();
        if (!quarryPos.equals(BlockPos.ORIGIN)) {
            QuarryBlockEntity quarry = getQuarryBlockEntity(quarryPos);
            if (quarry != null) {
                int actualWidth = quarry.getMiningWidth();
                int actualLength = quarry.getMiningLength();
                widthField.setText(String.valueOf(actualWidth));
                lengthField.setText(String.valueOf(actualLength));
                Circuitmod.LOGGER.info("[QUARRY-SCREEN] Initialized text fields with actual width: {}, length: {} from quarry", 
                    actualWidth, actualLength);
            } else {
                Circuitmod.LOGGER.warn("[QUARRY-SCREEN] Could not get quarry block entity for initialization");
            }
        } else {
            Circuitmod.LOGGER.warn("[QUARRY-SCREEN] Could not find quarry position for text field initialization");
        }
        isInitializing = false;
        
        addDrawableChild(widthField);
        addDrawableChild(lengthField);
        
        Circuitmod.LOGGER.info("[QUARRY-SCREEN] init() - Toggle button and text fields created and added");
    }
    
    private void drawToggleButtonIndicator(DrawContext context) {
        // Completely transparent button - no visual elements
        // The button is still clickable but shows the GUI underneath
    }
    
    private BlockPos findQuarryPosition() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            Circuitmod.LOGGER.warn("[QUARRY-SCREEN] Client player or world is null!");
            return BlockPos.ORIGIN;
        }
        
        // Search in a reasonable radius around the player for a quarry with an open screen
        BlockPos playerPos = client.player.getBlockPos();
        int searchRadius = 16; // Search within 16 blocks
        
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (client.world.getBlockEntity(pos) instanceof QuarryBlockEntity quarry) {
                        // Check if this quarry can be used by the player (same as the one they opened)
                        if (quarry.canPlayerUse(client.player)) {
                            Circuitmod.LOGGER.info("[QUARRY-SCREEN] Found quarry at position: {}", pos);
                            return pos;
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.warn("[QUARRY-SCREEN] Could not find quarry block entity near player!");
        return BlockPos.ORIGIN;
    }
    
    private QuarryBlockEntity getQuarryBlockEntity(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            BlockEntity blockEntity = client.world.getBlockEntity(pos);
            if (blockEntity instanceof QuarryBlockEntity) {
                return (QuarryBlockEntity) blockEntity;
            }
        }
        return null;
    }
} 
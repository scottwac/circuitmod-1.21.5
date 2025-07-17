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
import starduster.circuitmod.block.entity.DrillBlockEntity;
import net.minecraft.block.entity.BlockEntity;

public class DrillScreen extends HandledScreen<DrillScreenHandler> {
    // Use generic chest texture
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/quarry/quarry_gui.png");
    // Green color for the mining speed text
    private static final int MINING_SPEED_COLOR = 0xFF00FF00; // ARGB format (alpha, red, green, blue)
    
    // Toggle mining button
    private ButtonWidget toggleMiningButton;
    
    // Text input fields for drill dimensions
    private TextFieldWidget heightField;
    private TextFieldWidget widthField;
    
    // Track mining enabled state changes
    private boolean lastMiningEnabled = true;
    
    // Flag to prevent change listeners from firing during initialization
    private boolean isInitializing = false;
    
    // Method to update text fields with new dimensions
    public void updateTextFields(int height, int width) {
        if (heightField != null && widthField != null) {
            isInitializing = true;
            heightField.setText(String.valueOf(height));
            widthField.setText(String.valueOf(width));
            isInitializing = false;
            Circuitmod.LOGGER.info("[DRILL-SCREEN] Updated text fields with dimensions: {}x{}", height, width);
        }
    }

    public DrillScreen(DrillScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // Adjust the height since we're using only 3 rows, not 6 like the texture
        this.backgroundHeight = 168; // Default vanilla chest with 3 rows is 168
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }
    
    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // Only keep mining enabled status tracking
        boolean currentMiningEnabled = handler.isMiningEnabled();
        if (currentMiningEnabled != lastMiningEnabled) {
            Circuitmod.LOGGER.info("[DRILL-SCREEN] Mining enabled status changed: {} -> {}", lastMiningEnabled, currentMiningEnabled);
            lastMiningEnabled = currentMiningEnabled;
        }
        if (toggleMiningButton != null) {
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
        
        // Draw labels for the text fields
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        // Height label
        Text heightLabel = Text.literal("Height:");
        context.drawText(textRenderer, heightLabel, baseX + 58, baseY + 8, 0xFFFFFF, false);
        // Width label
        Text widthLabel = Text.literal("Width:");
        context.drawText(textRenderer, widthLabel, baseX + 58, baseY + 30, 0xFFFFFF, false);
        // Draw toggle button indicator
        if (toggleMiningButton != null) {
            drawToggleButtonIndicator(context);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    
    // Helper methods to get current values from text fields
    private int getHeightValue() {
        try {
            if (heightField != null && !heightField.getText().isEmpty()) {
                int value = Integer.parseInt(heightField.getText());
                Circuitmod.LOGGER.info("[DRILL-SCREEN] getHeightValue() returning: {}", value);
                return value;
            } else {
                Circuitmod.LOGGER.warn("[DRILL-SCREEN] getHeightValue() - text field is null or empty, returning 16");
                return 16;
            }
        } catch (NumberFormatException e) {
            Circuitmod.LOGGER.warn("[DRILL-SCREEN] getHeightValue() - invalid number format, returning 16");
            return 16; // Default value
        }
    }
    
    private int getWidthValue() {
        try {
            if (widthField != null && !widthField.getText().isEmpty()) {
                int value = Integer.parseInt(widthField.getText());
                Circuitmod.LOGGER.info("[DRILL-SCREEN] getWidthValue() returning: {}", value);
                return value;
            } else {
                Circuitmod.LOGGER.warn("[DRILL-SCREEN] getWidthValue() - text field is null or empty, returning 16");
                return 16;
            }
        } catch (NumberFormatException e) {
            Circuitmod.LOGGER.warn("[DRILL-SCREEN] getWidthValue() - invalid number format, returning 16");
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
        
        Circuitmod.LOGGER.info("[DRILL-SCREEN] init() - Creating toggle button at ({}, {})", buttonX, buttonY);
        Circuitmod.LOGGER.info("[DRILL-SCREEN] init() - Handler block pos: {}", handler.getBlockPos());
        
        toggleMiningButton = ButtonWidget.builder(
            Text.literal(""), // Empty text - we'll draw texture underneath
            button -> {
                // Find the drill position from the client world instead of relying on handler
                BlockPos pos = findDrillPosition();
                Circuitmod.LOGGER.info("[DRILL-SCREEN] Button clicked! Found drill at: {}", pos);
                Circuitmod.LOGGER.info("[DRILL-SCREEN] Handler pos (for comparison): {}", handler.getBlockPos());
                Circuitmod.LOGGER.info("[DRILL-SCREEN] Mining enabled before toggle: {}", handler.isMiningEnabled());
                
                if (!pos.equals(BlockPos.ORIGIN)) {
                    ClientNetworking.sendToggleMiningRequest(pos);
                    Circuitmod.LOGGER.info("[DRILL-SCREEN] Sent toggle mining request to server for position: {}", pos);
                } else {
                    Circuitmod.LOGGER.error("[DRILL-SCREEN] Could not find drill position, not sending toggle request!");
                }
            })
            .dimensions(buttonX, buttonY, 14, 14)
            .build();
            
        addDrawableChild(toggleMiningButton);
        
        // Add text input fields for drill dimensions
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        
        // Height field: (58, 17) to (93, 36) - size 35x19
        heightField = new TextFieldWidget(
            textRenderer,
            baseX + 58, baseY + 17, 35, 19,
            Text.literal("Height")
        );
        heightField.setMaxLength(3); // Max 3 digits
        heightField.setTextPredicate(text -> text.matches("\\d*")); // Only numbers
        heightField.setChangedListener(text -> {
            if (isInitializing) {
                Circuitmod.LOGGER.info("[DRILL-SCREEN] Skipping height change listener during initialization");
                return;
            }
            if (!text.isEmpty()) {
                try {
                    int height = Integer.parseInt(text);
                    if (height > 0 && height <= 100) { // Reasonable limits
                        BlockPos pos = findDrillPosition();
                        if (!pos.equals(BlockPos.ORIGIN)) {
                            Circuitmod.LOGGER.info("[DRILL-SCREEN] Height changed to: {}, sending to server", height);
                            ClientNetworking.sendDrillDimensions(pos, height, getWidthValue());
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });
        
        // Width field: (58, 39) to (93, 58) - size 35x19
        widthField = new TextFieldWidget(
            textRenderer,
            baseX + 58, baseY + 39, 35, 19,
            Text.literal("Width")
        );
        widthField.setMaxLength(3); // Max 3 digits
        widthField.setTextPredicate(text -> text.matches("\\d*")); // Only numbers
        widthField.setChangedListener(text -> {
            if (isInitializing) {
                Circuitmod.LOGGER.info("[DRILL-SCREEN] Skipping width change listener during initialization");
                return;
            }
            if (!text.isEmpty()) {
                try {
                    int width = Integer.parseInt(text);
                    if (width > 0 && width <= 100) { // Reasonable limits
                        BlockPos pos = findDrillPosition();
                        if (!pos.equals(BlockPos.ORIGIN)) {
                            Circuitmod.LOGGER.info("[DRILL-SCREEN] Width changed to: {}, sending to server", width);
                            ClientNetworking.sendDrillDimensions(pos, getHeightValue(), width);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });
        
        // Initialize text fields with current values from the drill
        isInitializing = true;
        BlockPos drillPos = findDrillPosition();
        if (!drillPos.equals(BlockPos.ORIGIN)) {
            DrillBlockEntity drill = getDrillBlockEntity(drillPos);
            if (drill != null) {
                int actualHeight = drill.getMiningHeight();
                int actualWidth = drill.getMiningWidth();
                heightField.setText(String.valueOf(actualHeight));
                widthField.setText(String.valueOf(actualWidth));
                Circuitmod.LOGGER.info("[DRILL-SCREEN] Initialized text fields with actual height: {}, width: {} from drill", 
                    actualHeight, actualWidth);
            } else {
                Circuitmod.LOGGER.warn("[DRILL-SCREEN] Could not get drill block entity for initialization");
            }
        } else {
            Circuitmod.LOGGER.warn("[DRILL-SCREEN] Could not find drill position for initialization");
        }
        isInitializing = false;
        
        // Add text fields to the screen
        addDrawableChild(heightField);
        addDrawableChild(widthField);
    }
    
    private void drawToggleButtonIndicator(DrawContext context) {
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        boolean miningEnabled = handler.isMiningEnabled();
        
        // Draw a colored indicator based on mining status
        int color = miningEnabled ? 0xFF00FF00 : 0xFFFF0000; // Green if enabled, red if disabled
        context.fill(baseX + 18, baseY + 52, baseX + 32, baseY + 66, color);
    }
    
    private BlockPos findDrillPosition() {
        // Try to get position from handler first
        BlockPos handlerPos = handler.getBlockPos();
        if (!handlerPos.equals(BlockPos.ORIGIN)) {
            return handlerPos;
        }
        
        // Fallback: search for drill block entity in the client world
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            // Search in a reasonable radius around the player
            BlockPos playerPos = client.player.getBlockPos();
            for (int x = -10; x <= 10; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -10; z <= 10; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        BlockEntity entity = client.world.getBlockEntity(pos);
                        if (entity instanceof DrillBlockEntity) {
                            Circuitmod.LOGGER.info("[DRILL-SCREEN] Found drill at: {}", pos);
                            return pos;
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.warn("[DRILL-SCREEN] Could not find drill position");
        return BlockPos.ORIGIN;
    }
    
    private DrillBlockEntity getDrillBlockEntity(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            BlockEntity entity = client.world.getBlockEntity(pos);
            if (entity instanceof DrillBlockEntity) {
                return (DrillBlockEntity) entity;
            }
        }
        return null;
    }
} 
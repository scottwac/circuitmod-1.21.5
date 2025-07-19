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
import starduster.circuitmod.block.entity.LaserMiningDrillBlockEntity;
import net.minecraft.block.entity.BlockEntity;

public class LaserMiningDrillScreen extends HandledScreen<LaserMiningDrillScreenHandler> {
    // Use the same texture as regular drill for now
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/quarry/drill_gui.png");
    // Green color for the mining speed text
    private static final int MINING_SPEED_COLOR = 0xFF00FF00; // ARGB format (alpha, red, green, blue)
    
    // Toggle mining button
    private ButtonWidget toggleMiningButton;
    
    // Text input field for laser mining drill depth
    private TextFieldWidget depthField;
    
    // Track mining enabled state changes
    private boolean lastMiningEnabled = true;
    
    // Flag to prevent change listeners from firing during initialization
    private boolean isInitializing = false;
    
    // Method to update text field with new depth
    public void updateDepthField(int depth) {
        if (depthField != null) {
            isInitializing = true;
            depthField.setText(String.valueOf(depth));
            isInitializing = false;
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Updated depth field with depth: {}", depth);
        }
    }

    public LaserMiningDrillScreen(LaserMiningDrillScreenHandler handler, PlayerInventory inventory, Text title) {
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
            Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Mining enabled status changed: {} -> {}", lastMiningEnabled, currentMiningEnabled);
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
        
        // Draw labels and current depth counter
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        
        // Draw current depth counter
        int currentDepth = handler.getCurrentDepth();
        Text depthLabel = Text.literal("Current: " + currentDepth);
        context.drawText(textRenderer, depthLabel, baseX + 58, baseY + 8, 0xFFFFFF, false);
        
        // Draw toggle button indicator
        if (toggleMiningButton != null) {
            drawToggleButtonIndicator(context);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    
    // Helper methods to get current values from text fields
    private int getDepthValue() {
        try {
            if (depthField != null && !depthField.getText().isEmpty()) {
                int value = Integer.parseInt(depthField.getText());
                Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] getDepthValue() returning: {}", value);
                return value;
            } else {
                Circuitmod.LOGGER.warn("[LASER-MINING-DRILL-SCREEN] getDepthValue() - text field is null or empty, returning 50");
                return 50;
            }
        } catch (NumberFormatException e) {
            Circuitmod.LOGGER.warn("[LASER-MINING-DRILL-SCREEN] getDepthValue() - invalid number format, returning 50");
            return 50; // Default value
        }
    }

    @Override
    protected void init() {
        super.init();
        // Center the title
        //titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        
        // Add toggle mining button at position (18, 52) with size 14x14
        int buttonX = (width - backgroundWidth) / 2 + 18;
        int buttonY = (height - backgroundHeight) / 2 + 52;
        
        Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] init() - Creating toggle button at ({}, {})", buttonX, buttonY);
        Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] init() - Handler block pos: {}", handler.getBlockPos());
        
        toggleMiningButton = ButtonWidget.builder(
            Text.literal(""), // Empty text - we'll draw texture underneath
            button -> {
                // Find the laser mining drill position from the client world instead of relying on handler
                BlockPos pos = findLaserMiningDrillPosition();
                Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Button clicked! Found laser mining drill at: {}", pos);
                Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Handler pos (for comparison): {}", handler.getBlockPos());
                Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Mining enabled before toggle: {}", handler.isMiningEnabled());
                
                if (!pos.equals(BlockPos.ORIGIN)) {
                    ClientNetworking.sendToggleMiningRequest(pos);
                    Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Sent toggle mining request to server for position: {}", pos);
                } else {
                    Circuitmod.LOGGER.error("[LASER-MINING-DRILL-SCREEN] Could not find laser mining drill position, not sending toggle request!");
                }
            })
            .dimensions(buttonX, buttonY, 15, 15)
            .build();
            
        addDrawableChild(toggleMiningButton);
        
        // Add text input field for laser mining drill depth
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        
        // Depth field: (58, 17) to (93, 36) - size 35x19
        depthField = new TextFieldWidget(
            textRenderer,
            baseX + 58, baseY + 17, 36, 20,
            Text.literal("Depth")
        );
        depthField.setMaxLength(4); // Max 4 digits (up to 9999)
        depthField.setTextPredicate(text -> text.matches("\\d*")); // Only numbers
        depthField.setChangedListener(text -> {
            if (isInitializing) {
                Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Skipping depth change listener during initialization");
                return;
            }
            if (!text.isEmpty()) {
                try {
                    int depth = Integer.parseInt(text);
                    if (depth > 0 && depth <= 2000) { // Increased limit to 9999
                        BlockPos pos = findLaserMiningDrillPosition();
                        if (!pos.equals(BlockPos.ORIGIN)) {
                            Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Depth changed to: {}, sending to server", depth);
                            ClientNetworking.sendDrillDepth(pos, depth);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });
        
        // Initialize text field with current value from the laser mining drill
        isInitializing = true;
        BlockPos laserMiningDrillPos = findLaserMiningDrillPosition();
        if (!laserMiningDrillPos.equals(BlockPos.ORIGIN)) {
            LaserMiningDrillBlockEntity laserMiningDrill = getLaserMiningDrillBlockEntity(laserMiningDrillPos);
            if (laserMiningDrill != null) {
                int actualDepth = laserMiningDrill.getMiningDepth();
                depthField.setText(String.valueOf(actualDepth));
                Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Initialized depth field with actual depth: {} from laser mining drill", actualDepth);
            } else {
                Circuitmod.LOGGER.warn("[LASER-MINING-DRILL-SCREEN] Could not get laser mining drill block entity for initialization");
            }
        } else {
            Circuitmod.LOGGER.warn("[LASER-MINING-DRILL-SCREEN] Could not find laser mining drill position for initialization");
        }
        isInitializing = false;
        
        // Add text field to the screen
        addDrawableChild(depthField);
    }
    
    private void drawToggleButtonIndicator(DrawContext context) {
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        boolean miningEnabled = handler.isMiningEnabled();
        
        // Draw a colored indicator based on mining status
        int color = miningEnabled ? 0xFF00FF00 : 0xFFFF0000; // Green if enabled, red if disabled
        context.fill(baseX + 19, baseY + 53, baseX + 19+13, baseY + 53+13, color);
    }
    
    private BlockPos findLaserMiningDrillPosition() {
        // Try to get position from handler first
        BlockPos handlerPos = handler.getBlockPos();
        if (!handlerPos.equals(BlockPos.ORIGIN)) {
            return handlerPos;
        }
        
        // Fallback: search for laser mining drill block entity in the client world
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            // Search in a reasonable radius around the player
            BlockPos playerPos = client.player.getBlockPos();
            for (int x = -10; x <= 10; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -10; z <= 10; z++) {
                        BlockPos pos = playerPos.add(x, y, z);
                        BlockEntity entity = client.world.getBlockEntity(pos);
                        if (entity instanceof LaserMiningDrillBlockEntity) {
                            Circuitmod.LOGGER.info("[LASER-MINING-DRILL-SCREEN] Found laser mining drill at: {}", pos);
                            return pos;
                        }
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.warn("[LASER-MINING-DRILL-SCREEN] Could not find laser mining drill position");
        return BlockPos.ORIGIN;
    }
    
    private LaserMiningDrillBlockEntity getLaserMiningDrillBlockEntity(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            BlockEntity entity = client.world.getBlockEntity(pos);
            if (entity instanceof LaserMiningDrillBlockEntity) {
                return (LaserMiningDrillBlockEntity) entity;
            }
        }
        return null;
    }
} 
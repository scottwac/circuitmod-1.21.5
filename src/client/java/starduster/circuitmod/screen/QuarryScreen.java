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
    
    // Remove mining speed tracking
    // private int displayedMiningSpeed = 0;
    
    // Toggle mining button
    private ButtonWidget toggleMiningButton;
    
    // Reset height button
    private ButtonWidget resetHeightButton;
    
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
        // Remove mining speed update logic
        // Remove displayedMiningSpeed update
        // Only keep mining enabled status tracking
        boolean currentMiningEnabled = handler.isMiningEnabled();
        if (currentMiningEnabled != lastMiningEnabled) {
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
        // Remove mining speed text rendering
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
                return value;
            } else {
                return 16;
            }
        } catch (NumberFormatException e) {
            return 16; // Default value
        }
    }
    
    private int getLengthValue() {
        try {
            if (lengthField != null && !lengthField.getText().isEmpty()) {
                int value = Integer.parseInt(lengthField.getText());
                return value;
            } else {
                return 16;
            }
        } catch (NumberFormatException e) {
            return 16; // Default value
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
        
        toggleMiningButton = ButtonWidget.builder(
            Text.literal(""), // Empty text - we'll draw texture underneath
            button -> {
                // Find the quarry position from the client world instead of relying on handler
                BlockPos pos = findQuarryPosition();
                
                if (!pos.equals(BlockPos.ORIGIN)) {
                    ClientNetworking.sendToggleMiningRequest(pos);
                }
            })
            .dimensions(buttonX, buttonY, 15, 15)
            .build();
            
        addDrawableChild(toggleMiningButton);
        
        // Add reset height button at position (18, 70) with size 15x15
        int resetButtonX = (width - backgroundWidth) / 2 + 18;
        int resetButtonY = (height - backgroundHeight) / 2 + 70;
        
        resetHeightButton = ButtonWidget.builder(
            Text.literal("Reset Height"),
            button -> {
                // Find the quarry position from the client world
                BlockPos pos = findQuarryPosition();
                
                if (!pos.equals(BlockPos.ORIGIN)) {
                    ClientNetworking.sendQuarryResetHeight(pos);
                }
            })
            .dimensions(resetButtonX, resetButtonY, 60, 15)
            .build();
            
        addDrawableChild(resetHeightButton);
        
        // Add text input fields for quarry dimensions
        int baseX = (width - backgroundWidth) / 2;
        int baseY = (height - backgroundHeight) / 2;
        
        // Width field: (58, 17) to (93, 36) - size 35x19
        widthField = new TextFieldWidget(
            textRenderer,
            baseX + 58, baseY + 17, 36, 20,
            Text.literal("Width")
        );
        widthField.setMaxLength(3); // Max 3 digits
        widthField.setTextPredicate(text -> text.matches("\\d*")); // Only numbers
        widthField.setChangedListener(text -> {
            if (isInitializing) {
                return;
            }
            if (!text.isEmpty()) {
                try {
                    int width = Integer.parseInt(text);
                    if (width > 0 && width <= 100) { // Reasonable limits
                        BlockPos pos = findQuarryPosition();
                        if (!pos.equals(BlockPos.ORIGIN)) {
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
            baseX + 58, baseY + 39, 36, 20,
            Text.literal("Length")
        );
        lengthField.setMaxLength(3); // Max 3 digits
        lengthField.setTextPredicate(text -> text.matches("\\d*")); // Only numbers
        lengthField.setChangedListener(text -> {
            if (isInitializing) {
                return;
            }
            if (!text.isEmpty()) {
                try {
                    int length = Integer.parseInt(text);
                    if (length > 0 && length <= 100) { // Reasonable limits
                        BlockPos pos = findQuarryPosition();
                        if (!pos.equals(BlockPos.ORIGIN)) {
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
            }
        }
        isInitializing = false;
        
        addDrawableChild(widthField);
        addDrawableChild(lengthField);
    }
    
    private void drawToggleButtonIndicator(DrawContext context) {
        // Completely transparent button - no visual elements
        // The button is still clickable but shows the GUI underneath
    }
    
    private BlockPos findQuarryPosition() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
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
                            return pos;
                        }
                    }
                }
            }
        }
        
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
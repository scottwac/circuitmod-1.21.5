package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.network.ClientNetworking;

public class DrillScreen extends HandledScreen<DrillScreenHandler> {
    // Use generic chest texture
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/quarry/quarry_gui.png");
    // Green color for the mining speed text
    private static final int MINING_SPEED_COLOR = 0xFF00FF00; // ARGB format (alpha, red, green, blue)

    // Direct mining speed tracking
    private int displayedMiningSpeed = 0;
    
    // Toggle mining button
    private ButtonWidget toggleMiningButton;

    public DrillScreen(DrillScreenHandler handler, PlayerInventory inventory, Text title) {
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
      //  Circuitmod.LOGGER.info("[CLIENT-SCREEN] handledScreenTick getting mining speed: " + newSpeed +
      //      " (current displayed: " + displayedMiningSpeed + ")");
            
        if (newSpeed != displayedMiningSpeed) {
      //      Circuitmod.LOGGER.info("[CLIENT-SCREEN] Updating displayed mining speed from " + 
          //      displayedMiningSpeed + " to " + newSpeed);
            displayedMiningSpeed = newSpeed;
        }
        
        // Update toggle button text
        if (toggleMiningButton != null) {
            toggleMiningButton.setMessage(getMiningButtonText());
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
            Circuitmod.LOGGER.info("[CLIENT-SCREEN] render() updating mining speed from " + 
                displayedMiningSpeed + " to " + currentSpeed);
            displayedMiningSpeed = currentSpeed;
        }
        
        // Draw mining speed text in green above the inventory
        // Use our cached value that gets updated each tick
        Text miningSpeedText = Text.literal("Mining Speed: " + displayedMiningSpeed + " blocks/sec");
        int x = (width - backgroundWidth) / 2 + 8;
        int y = (height - backgroundHeight) / 2 + 6;
        
      //  Circuitmod.LOGGER.info("[CLIENT-SCREEN] Drawing mining speed text: " + displayedMiningSpeed);
        
        context.drawText(
            textRenderer, 
            miningSpeedText, 
            x, 
            y, 
            MINING_SPEED_COLOR, 
            false
        );
        
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void init() {
        super.init();
        // Center the title
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        
        // Add toggle mining button at position (18, 52) with size 14x14
        int buttonX = (width - backgroundWidth) / 2 + 18;
        int buttonY = (height - backgroundHeight) / 2 + 52;
        
        toggleMiningButton = ButtonWidget.builder(
            getMiningButtonText(), 
            button -> {
                // Send toggle mining packet to server
                ClientNetworking.sendToggleMiningRequest(handler.getBlockPos());
            })
            .dimensions(buttonX, buttonY, 14, 14)
            .build();
            
        addDrawableChild(toggleMiningButton);
    }
    
    private Text getMiningButtonText() {
        boolean miningEnabled = handler.isMiningEnabled();
        return Text.literal(miningEnabled ? "ON" : "OFF");
    }
} 
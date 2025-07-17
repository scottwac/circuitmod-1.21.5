package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ConstructorBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConstructorScreen extends HandledScreen<ConstructorScreenHandler> {
    // Use the quarry texture for now
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/quarry/quarry_gui.png");
    
    // Colors
    private static final int STATUS_COLOR = 0xFF00FF00; // Green
    private static final int ERROR_COLOR = 0xFFFF0000; // Red
    private static final int INFO_COLOR = 0xFF888888; // Gray
    
    // Buttons
    private ButtonWidget startStopButton;
    
    // Track state changes
    private boolean lastBuildingState = false;
    
    public ConstructorScreen(ConstructorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 175;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Create start/stop button
        int buttonX = this.x + 8;
        int buttonY = this.y + 60;
        
        this.startStopButton = ButtonWidget.builder(
            Text.literal("Start Building"),
            button -> onStartStopClicked()
        ).dimensions(buttonX, buttonY, 80, 20).build();
        
        this.addDrawableChild(this.startStopButton);
        
        updateButtonState();
    }
    
    private void onStartStopClicked() {
        ConstructorBlockEntity entity = handler.getBlockEntity();
        if (entity != null) {
            if (entity.isBuilding()) {
                entity.stopBuilding();
            } else {
                entity.startBuilding();
            }
        }
    }
    
    private void updateButtonState() {
        if (startStopButton == null) return;
        
        boolean building = handler.isBuilding();
        boolean hasBlueprint = handler.hasBlueprint();
        
        if (building) {
            startStopButton.setMessage(Text.literal("Stop Building"));
            startStopButton.active = true;
        } else if (hasBlueprint) {
            startStopButton.setMessage(Text.literal("Start Building"));
            startStopButton.active = true;
        } else {
            startStopButton.setMessage(Text.literal("No Blueprint"));
            startStopButton.active = false;
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        
        // Update button state if building state changed
        boolean currentBuildingState = handler.isBuilding();
        if (currentBuildingState != lastBuildingState) {
            updateButtonState();
            lastBuildingState = currentBuildingState;
        }
    }
    
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw title
        Text title = Text.translatable("block.circuitmod.constructor_block");
        context.drawText(this.textRenderer, title, 8, 6, 0x404040, false);
        
        // Draw inventory label
        context.drawText(this.textRenderer, this.playerInventoryTitle, 8, this.backgroundHeight - 94, 0x404040, false);
        
        // Draw blueprint status
        ConstructorBlockEntity entity = handler.getBlockEntity();
        if (entity != null) {
            String blueprintName = entity.getBlueprintName();
            context.drawText(this.textRenderer, Text.literal("Blueprint:"), 8, 35, INFO_COLOR, false);
            context.drawText(this.textRenderer, Text.literal(blueprintName), 8, 45, 0x404040, false);
            
            // Draw status message
            String statusMessage = entity.getStatusMessage();
            int statusColor = entity.isBuilding() ? STATUS_COLOR : 
                            statusMessage.contains("Missing") || statusMessage.contains("failed") ? ERROR_COLOR : INFO_COLOR;
            
            // Wrap long status messages
            if (statusMessage.length() > 15) {
                statusMessage = statusMessage.substring(0, 15) + "...";
            }
            
            context.drawText(this.textRenderer, Text.literal(statusMessage), 8, 85, statusColor, false);
            
            // Draw power status
            String powerStatus = "Power: " + (entity.isBuilding() ? "ON" : "OFF");
            int powerColor = entity.isBuilding() ? STATUS_COLOR : INFO_COLOR;
            context.drawText(this.textRenderer, Text.literal(powerStatus), 8, 75, powerColor, false);
            
            // Draw progress if building
            if (entity.isBuilding()) {
                int progress = entity.getBuildProgress();
                int total = entity.getTotalBuildBlocks();
                String progressText = progress + " / " + total;
                context.drawText(this.textRenderer, Text.literal(progressText), 8, 95, STATUS_COLOR, false);
                
                // Draw progress bar
                int barX = 8;
                int barY = 105;
                int barWidth = 80;
                int barHeight = 6;
                
                // Background
                context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
                
                // Progress fill
                if (total > 0) {
                    int fillWidth = (progress * barWidth) / total;
                    context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00FF00);
                }
                
                // Border
                context.drawBorder(barX, barY, barWidth, barHeight, 0xFF666666);
            }
            
            // Draw materials list (simplified for now)
            drawMaterialsList(context, entity);
        }
    }
    
    private void drawMaterialsList(DrawContext context, ConstructorBlockEntity entity) {
        Map<String, Integer> required = entity.getRequiredMaterials();
        Map<String, Integer> available = entity.getAvailableMaterials();
        
        if (required.isEmpty()) {
            return;
        }
        
        int startY = 115;
        int lineHeight = 10;
        int maxLines = 4; // Limit display to avoid overflow
        
        context.drawText(this.textRenderer, Text.literal("Materials:"), 95, startY, INFO_COLOR, false);
        
        List<String> materialList = new ArrayList<>(required.keySet());
        int displayCount = Math.min(materialList.size(), maxLines);
        
        for (int i = 0; i < displayCount; i++) {
            String material = materialList.get(i);
            int req = required.get(material);
            int avail = available.getOrDefault(material, 0);
            
            // Shorten material names
            String shortName = material.length() > 8 ? material.substring(0, 8) : material;
            String text = shortName + ": " + avail + "/" + req;
            
            int color = avail >= req ? STATUS_COLOR : ERROR_COLOR;
            context.drawText(this.textRenderer, Text.literal(text), 95, startY + 10 + (i * lineHeight), color, false);
        }
        
        if (materialList.size() > maxLines) {
            context.drawText(this.textRenderer, Text.literal("..."), 95, startY + 10 + (maxLines * lineHeight), INFO_COLOR, false);
        }
    }
} 
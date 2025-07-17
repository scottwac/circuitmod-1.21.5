package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.entity.ConstructorBlockEntity;
import starduster.circuitmod.network.ClientNetworking;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;



public class ConstructorScreen extends HandledScreen<ConstructorScreenHandler> {
    // Use the constructor texture
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/constructor/constructor_gui.png");
    
    // Colors
    private static final int STATUS_COLOR = 0xFF00FF00; // Green
    private static final int ERROR_COLOR = 0xFFFF0000; // Red
    private static final int INFO_COLOR = 0xFF888888; // Gray
    
    // Buttons
    private ButtonWidget startStopButton;
    
    // Scrollable materials section
    private static final int MAX_MATERIALS_VISIBLE = 6;
    private static final int MATERIAL_ENTRY_HEIGHT = 12;
    private static final int MATERIALS_AREA_HEIGHT = MAX_MATERIALS_VISIBLE * MATERIAL_ENTRY_HEIGHT;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_HEIGHT = 15;
    private int materialsScrollOffset = 0;
    private boolean scrolling = false;
    private List<Map.Entry<String, Integer>> materialsList = new ArrayList<>();
    
    // Track state changes
    private boolean lastBuildingState = false;
    private boolean lastHasBlueprint = false;
    private boolean lastHasPower = false;
    
    public ConstructorScreen(ConstructorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 200; // Increased height to accommodate materials list
        this.backgroundWidth = 175;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Create start/stop button - positioned below the blueprint status
        int buttonX = this.x + 8;
        int buttonY = this.y + 55;
        
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
            ClientNetworking.sendConstructorBuildingToggle(entity.getPos());
        }
    }
    
    private void updateButtonState() {
        if (startStopButton == null) return;
        
        boolean building = handler.isBuilding();
        boolean hasBlueprint = handler.hasBlueprint();
        boolean hasPower = handler.isReceivingPower();
        
        // Debug logging
        Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Button state - building: {}, hasBlueprint: {}, hasPower: {}", building, hasBlueprint, hasPower);
        Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Property delegate values - building: {}, hasBlueprint: {}, hasPower: {}", 
            handler.isBuilding(), handler.hasBlueprint(), handler.isReceivingPower());
        
        if (building) {
            // If building, always allow stopping
            startStopButton.setMessage(Text.literal("Stop Building"));
            startStopButton.active = true;
        } else if (hasBlueprint && hasPower) {
            // If not building but have blueprint and power, allow starting
            startStopButton.setMessage(Text.literal("Start Building"));
            startStopButton.active = true;
        } else if (hasBlueprint && !hasPower) {
            // If have blueprint but no power, show disabled state
            startStopButton.setMessage(Text.literal("No Power"));
            startStopButton.active = false;
        } else {
            startStopButton.setMessage(Text.literal("No Blueprint"));
            startStopButton.active = false;
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        
        // Update button state if any relevant state changed
        boolean currentBuildingState = handler.isBuilding();
        boolean currentHasBlueprint = handler.hasBlueprint();
        boolean currentHasPower = handler.isReceivingPower();
        
        // Check if any state has changed since last update
        if (currentBuildingState != lastBuildingState || 
            currentHasBlueprint != lastHasBlueprint || 
            currentHasPower != lastHasPower) {
            updateButtonState();
            lastBuildingState = currentBuildingState;
            lastHasBlueprint = currentHasBlueprint;
            lastHasPower = currentHasPower;
        }
    }
    
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw status message at the top
        ConstructorBlockEntity blockEntity = handler.getBlockEntity();
        if (blockEntity != null) {
            String statusMessage = blockEntity.getStatusMessage();
            context.drawText(this.textRenderer, Text.literal(statusMessage), 8, 6, INFO_COLOR, false);
        }
        
        // ALWAYS draw materials section title for debugging
        context.drawText(this.textRenderer, Text.literal("Required Materials:"), 8, 95, INFO_COLOR, false);
        
        // Draw materials information in scrollable section
        if (handler.hasBlueprint()) {
            Map<String, Integer> requiredMaterials = handler.getSyncedRequiredMaterials();
            Map<String, Integer> availableMaterials = handler.getSyncedAvailableMaterials();
            
            // Debug logging for materials display
            if (System.currentTimeMillis() % 5000 < 50) { // Log every ~5 seconds
                Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Materials display - hasBlueprint: {}, requiredMaterials: {}, availableMaterials: {}", 
                    handler.hasBlueprint(), requiredMaterials.size(), availableMaterials.size());
                if (!requiredMaterials.isEmpty()) {
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Required materials: {}", requiredMaterials);
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Available materials: {}", availableMaterials);
                }
            }
            
            if (!requiredMaterials.isEmpty()) {
                // Update materials list
                materialsList.clear();
                for (Map.Entry<String, Integer> entry : requiredMaterials.entrySet()) {
                    materialsList.add(entry);
                }
                
                // Draw scrollable materials list
                drawScrollableMaterials(context, mouseX, mouseY, availableMaterials);
                
                // Draw scrollbar
                drawScrollbar(context);
            } else {
                // Debug: Show when no materials are found
                context.drawText(this.textRenderer, Text.literal("No materials found"), 8, 110, ERROR_COLOR, false);
                if (System.currentTimeMillis() % 5000 < 50) { // Log every ~5 seconds
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] No required materials found to display");
                }
            }
        } else {
            // Debug: Show when blueprint or block entity is missing
            context.drawText(this.textRenderer, Text.literal("No blueprint loaded"), 8, 110, ERROR_COLOR, false);
            if (System.currentTimeMillis() % 5000 < 50) { // Log every ~5 seconds
                Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Cannot display materials - hasBlueprint: {}, blockEntity: {}", 
                    handler.hasBlueprint(), blockEntity != null);
            }
        }
        
        // Draw power status
        String powerStatus = "Power: " + (handler.isReceivingPower() ? "ON" : "OFF");
        int powerColor = handler.isReceivingPower() ? STATUS_COLOR : ERROR_COLOR;
        context.drawText(this.textRenderer, Text.literal(powerStatus), 8, 80, powerColor, false);
        
        // Draw progress if building
        if (handler.isBuilding()) {
            int progress = handler.getBuildProgress();
            int total = handler.getTotalBuildBlocks();
            String progressText = "Progress: " + progress + " / " + total;
            context.drawText(this.textRenderer, Text.literal(progressText), 8, 185, STATUS_COLOR, false);
            
            // Draw progress bar
            int barX = 8;
            int barY = 195;
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
    }
    
    private void drawScrollableMaterials(DrawContext context, int mouseX, int mouseY, Map<String, Integer> availableMaterials) {
        int materialsAreaX = 8;
        int materialsAreaY = 110;
        int materialsAreaWidth = 140;
        
        // Calculate scroll limits
        int maxScroll = Math.max(0, materialsList.size() - MAX_MATERIALS_VISIBLE);
        materialsScrollOffset = MathHelper.clamp(materialsScrollOffset, 0, maxScroll);
        
        // Draw visible materials
        for (int i = 0; i < MAX_MATERIALS_VISIBLE && i + materialsScrollOffset < materialsList.size(); i++) {
            Map.Entry<String, Integer> entry = materialsList.get(i + materialsScrollOffset);
            String materialName = entry.getKey();
            int required = entry.getValue();
            int available = availableMaterials.getOrDefault(materialName, 0);
            
            String materialText = materialName + ": " + available + "/" + required;
            int materialColor = available >= required ? STATUS_COLOR : ERROR_COLOR;
            
            int yPos = materialsAreaY + (i * MATERIAL_ENTRY_HEIGHT);
            context.drawText(this.textRenderer, Text.literal(materialText), materialsAreaX, yPos, materialColor, false);
        }
    }
    
    private void drawScrollbar(DrawContext context) {
        if (materialsList.size() <= MAX_MATERIALS_VISIBLE) {
            return; // No scrollbar needed
        }
        
        int scrollbarX = this.x + 150;
        int scrollbarY = this.y + 110;
        int scrollbarAreaHeight = MATERIALS_AREA_HEIGHT;
        
        // Calculate scrollbar position
        int maxScroll = materialsList.size() - MAX_MATERIALS_VISIBLE;
        float scrollProgress = maxScroll > 0 ? (float) materialsScrollOffset / maxScroll : 0.0f;
        int scrollbarPos = (int) (scrollProgress * (scrollbarAreaHeight - SCROLLBAR_HEIGHT));
        
        // Draw scrollbar background
        context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarAreaHeight, 0xFF555555);
        
        // Draw scrollbar handle
        context.fill(scrollbarX, scrollbarY + scrollbarPos, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarPos + SCROLLBAR_HEIGHT, 0xFFAAAAAA);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        
        // Handle materials scrolling
        if (materialsList.size() > MAX_MATERIALS_VISIBLE) {
            int maxScroll = materialsList.size() - MAX_MATERIALS_VISIBLE;
            materialsScrollOffset = MathHelper.clamp((int) (materialsScrollOffset - verticalAmount), 0, maxScroll);
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrolling && materialsList.size() > MAX_MATERIALS_VISIBLE) {
            int scrollbarX = this.x + 150;
            int scrollbarY = this.y + 110;
            int scrollbarAreaHeight = MATERIALS_AREA_HEIGHT;
            
            float scrollProgress = ((float) mouseY - scrollbarY - SCROLLBAR_HEIGHT / 2.0f) / (scrollbarAreaHeight - SCROLLBAR_HEIGHT);
            scrollProgress = MathHelper.clamp(scrollProgress, 0.0f, 1.0f);
            
            int maxScroll = materialsList.size() - MAX_MATERIALS_VISIBLE;
            materialsScrollOffset = (int) (scrollProgress * maxScroll);
            return true;
        }
        
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        scrolling = false;
        
        // Check if clicking on scrollbar
        if (materialsList.size() > MAX_MATERIALS_VISIBLE) {
            int scrollbarX = this.x + 150;
            int scrollbarY = this.y + 110;
            int scrollbarAreaHeight = MATERIALS_AREA_HEIGHT;
            
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH &&
                mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarAreaHeight) {
                scrolling = true;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

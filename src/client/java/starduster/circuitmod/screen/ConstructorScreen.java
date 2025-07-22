package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
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
    // Offset/rotation buttons
    private ButtonWidget xPlusButton;
    private ButtonWidget xMinusButton;
    private ButtonWidget yPlusButton;
    private ButtonWidget yMinusButton;
    private ButtonWidget zPlusButton;
    private ButtonWidget zMinusButton;
    private ButtonWidget rotCwButton;
    private ButtonWidget rotCcwButton;

    // Scrollable materials section - made smaller and more compact
    private static final int MAX_MATERIALS_VISIBLE = 3; // Reduced to fit above player inventory
    private static final int MATERIAL_ENTRY_HEIGHT = 9; // Reduced since icons are smaller
    private static final int MATERIALS_AREA_HEIGHT = MAX_MATERIALS_VISIBLE * MATERIAL_ENTRY_HEIGHT;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_HEIGHT = 10;
    private int materialsScrollOffset = 0;
    private boolean scrolling = false;
    private List<Map.Entry<String, Integer>> materialsList = new ArrayList<>();
    
    // Track state changes
    private boolean lastBuildingState = false;
    private boolean lastHasBlueprint = false;
    private boolean lastHasPower = false;
    
    public ConstructorScreen(ConstructorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 180; // Reduced height since materials list is now smaller
        this.backgroundWidth = 175;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Create start/stop button - positioned just to the left of the top row of constructor block inventory
        int buttonX = this.x + 8 + 19; // Moved right so button edge is close to block inventory at x=97
        int buttonY = this.y + 17; // Aligned with the top row of constructor block inventory
        
        this.startStopButton = ButtonWidget.builder(
            Text.literal("Build"),
            button -> onStartStopClicked()
        ).dimensions(buttonX, buttonY, 68, 12).build(); // Made smaller
        
        this.addDrawableChild(this.startStopButton);

        // --- Offset/rotation buttons ---
        int btnSize = 12;
        int lrgBtnSize = 17;
        int btnSpacing = 2;
        int baseX = this.x + 55; // near blueprint slot
        int baseY = this.y + 31; // below blueprint slot

        xPlusButton = ButtonWidget.builder(Text.literal("X+"), b -> adjustOffset(0, +1, 0)).dimensions(baseX, baseY, btnSize, btnSize).build();
        xMinusButton = ButtonWidget.builder(Text.literal("X-"), b -> adjustOffset(0, -1, 0)).dimensions(baseX, baseY + btnSize + btnSpacing, btnSize, btnSize).build();

        yPlusButton = ButtonWidget.builder(Text.literal("Y+"), b -> adjustOffset(0, 0, +1)).dimensions(baseX + btnSize + btnSpacing, baseY, btnSize, btnSize).build();
        yMinusButton = ButtonWidget.builder(Text.literal("Y-"), b -> adjustOffset(0, 0, -1)).dimensions(baseX + btnSize + btnSpacing, baseY + btnSize + btnSpacing, btnSize, btnSize).build();

        zPlusButton = ButtonWidget.builder(Text.literal("Z+"), b -> adjustOffset(+1, 0, 0)).dimensions(baseX + 2 * (btnSize + btnSpacing), baseY, btnSize, btnSize).build();
        zMinusButton = ButtonWidget.builder(Text.literal("Z-"), b -> adjustOffset(-1, 0, 0)).dimensions(baseX + 2 * (btnSize + btnSpacing), baseY + btnSize + btnSpacing, btnSize, btnSize).build();

        rotCwButton = ButtonWidget.builder(Text.literal("R>"), b -> adjustRotation(+1)).dimensions(baseX, baseY + 2 * (btnSize + btnSpacing), lrgBtnSize + btnSpacing, btnSize).build();
        rotCcwButton = ButtonWidget.builder(Text.literal("<R"), b -> adjustRotation(-1)).dimensions(baseX + 2 + (lrgBtnSize + btnSpacing), baseY + 2 * (btnSize + btnSpacing), lrgBtnSize + btnSpacing, btnSize).build();

        this.addDrawableChild(xPlusButton);
        this.addDrawableChild(xMinusButton);
        this.addDrawableChild(yPlusButton);
        this.addDrawableChild(yMinusButton);
        this.addDrawableChild(zPlusButton);
        this.addDrawableChild(zMinusButton);
        this.addDrawableChild(rotCwButton);
        this.addDrawableChild(rotCcwButton);
        
        updateButtonState();
    }
    
    private void onStartStopClicked() {
        ConstructorBlockEntity entity = handler.getBlockEntity();
        if (entity != null) {
            ClientNetworking.sendConstructorBuildingToggle(entity.getPos());
        }
    }

    // Helper to adjust offsets (forward/right/up). The parameters are deltaForward, deltaRight, deltaUp in relative coordinates.
    private void adjustOffset(int deltaForward, int deltaRight, int deltaUp) {
        ConstructorBlockEntity entity = handler.getBlockEntity();
        if (entity == null) return;

        int newForward = entity.getForwardOffset() + deltaForward;
        int newRight = entity.getRightOffset() + deltaRight;
        int newUp = entity.getUpOffset() + deltaUp;
        int rotation = entity.getBlueprintRotation();

        // Update client-side immediately for visual feedback
        entity.setForwardOffset(newForward);
        entity.setRightOffset(newRight);
        entity.setUpOffset(newUp);

        ClientNetworking.sendConstructorTransform(entity.getPos(), newForward, newRight, newUp, rotation);
    }

    private void adjustRotation(int deltaRot) {
        ConstructorBlockEntity entity = handler.getBlockEntity();
        if (entity == null) return;

        int newRot = (entity.getBlueprintRotation() + deltaRot) & 3; // keep 0-3
        int fwd = entity.getForwardOffset();
        int right = entity.getRightOffset();
        int up = entity.getUpOffset();

        entity.setBlueprintRotation(newRot);

        ClientNetworking.sendConstructorTransform(entity.getPos(), fwd, right, up, newRot);
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
            startStopButton.setMessage(Text.literal("Stop"));
            startStopButton.active = true;
        } else if (hasBlueprint && hasPower) {
            // If not building but have blueprint and power, allow starting
            startStopButton.setMessage(Text.literal("Start"));
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
        
        // Draw materials section title - positioned below blueprint slot, smaller size
        context.getMatrices().push();
        context.getMatrices().scale(0.8f, 0.8f, 1.0f);
        context.drawText(this.textRenderer, Text.literal("Materials:"), 9, 46, INFO_COLOR, false);
        context.getMatrices().pop();
        
        // Draw materials information in scrollable section
        if (handler.hasBlueprint()) {
            Map<String, Integer> requiredMaterials = handler.getSyncedRequiredMaterials();
            Map<String, Integer> availableMaterials = handler.getSyncedAvailableMaterials();
            
            // Debug logging for materials display (reduced frequency)
            if (System.currentTimeMillis() % 10000 < 50) { // Log every ~10 seconds
                Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Materials display - hasBlueprint: {}, requiredMaterials: {}, availableMaterials: {}", 
                    handler.hasBlueprint(), requiredMaterials.size(), availableMaterials.size());
                // Log the actual material IDs for debugging
                for (Map.Entry<String, Integer> entry : requiredMaterials.entrySet()) {
                    Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Required material: {} = {}", entry.getKey(), entry.getValue());
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
                // Show when no materials are found - smaller size
                context.getMatrices().push();
                context.getMatrices().scale(0.5f, 0.5f, 1.0f);
                context.drawText(this.textRenderer, Text.literal("No materials"), 16, 90, ERROR_COLOR, false);
                context.getMatrices().pop();
            }
        } else {
            // Show when blueprint is missing - smaller size
            context.getMatrices().push();
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawText(this.textRenderer, Text.literal("No blueprint"), 16, 90, ERROR_COLOR, false);
            context.getMatrices().pop();
        }
        
        // Draw power status - positioned on the right between constructor and player inventory
//        String powerStatus = "Power: " + (handler.isReceivingPower() ? "ON" : "OFF");
//        int powerColor = handler.isReceivingPower() ? STATUS_COLOR : ERROR_COLOR;
//        context.drawText(this.textRenderer, Text.literal(powerStatus), 97, 70, powerColor, false);
        
        // Draw progress if building
        if (handler.isBuilding()) {
            int progress = handler.getBuildProgress();
            int total = handler.getTotalBuildBlocks();
            String progressText = "Progress: " + progress + " / " + total;
            context.drawText(this.textRenderer, Text.literal(progressText), 8, 165, STATUS_COLOR, false);
            
            // Draw progress bar
            int barX = 8;
            int barY = 175;
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
        int materialsAreaX = 16;
        int materialsAreaY = 44; // Moved up to be below blueprint slot
        int materialsAreaWidth = 70; // Further reduced to ensure no overlap with player inventory
        
        // Calculate scroll limits
        int maxScroll = Math.max(0, materialsList.size() - MAX_MATERIALS_VISIBLE);
        materialsScrollOffset = MathHelper.clamp(materialsScrollOffset, 0, maxScroll);
        
        // Draw visible materials as item icons with counts
        for (int i = 0; i < MAX_MATERIALS_VISIBLE && i + materialsScrollOffset < materialsList.size(); i++) {
            Map.Entry<String, Integer> entry = materialsList.get(i + materialsScrollOffset);
            String materialId = entry.getKey();
            int required = entry.getValue();
            int available = availableMaterials.getOrDefault(materialId, 0);
            
            // Try to create an ItemStack from the material ID
            ItemStack itemStack = createItemStackFromId(materialId);
            
            int yPos = materialsAreaY + (i * MATERIAL_ENTRY_HEIGHT);
            int numberX = materialsAreaX;
            int numberY = yPos + 3; // Center vertically with smaller icons
            
            // Draw count number first (smaller size)
            String countText = available + "/" + required;
            int numberColor = available >= required ? STATUS_COLOR : ERROR_COLOR;
            
            // Draw smaller number text by scaling the matrix
            context.getMatrices().push();
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawText(this.textRenderer, Text.literal(countText), numberX * 2, numberY * 2, numberColor, false);
            context.getMatrices().pop();
            
                    // Draw item icon to the right of the number (much closer)
        if (!itemStack.isEmpty()) {
            int iconX = numberX - 9; // Position icon much closer to the number (was 25)
            int iconY = yPos + 1; // Add 1 pixel offset for better positioning
            
            // Check if icon would extend beyond materials area
            if (iconX + 8 <= materialsAreaWidth) { // 8 pixels for half-size icon
                // Draw the item at half size by scaling the matrix
                context.getMatrices().push();
                context.getMatrices().scale(0.5f, 0.5f, 1.0f);
                context.drawItem(itemStack, iconX * 2, iconY * 2);
                context.getMatrices().pop();
                
                // Draw availability indicator (color-coded text to the right) - smaller size
                String availabilityText = available >= required ? "✓" : "✗";
                int textX = iconX - 5; // Position text closer to the smaller icon (was 16)
                int textY = iconY + 2;  // Center vertically with the smaller icon
                
                // Determine color based on availability
                int textColor = available >= required ? STATUS_COLOR : ERROR_COLOR;
                // Draw smaller text by scaling the matrix
//                context.getMatrices().push();
//                context.getMatrices().scale(0.5f, 0.5f, 1.0f);
//                context.drawText(this.textRenderer, Text.literal(availabilityText), textX * 2, textY * 2, textColor, true);
//                context.getMatrices().pop();
            }
            } else {
                // Fallback to text if we can't create an item stack - smaller size
                String shortName = materialId.length() > 12 ? materialId.substring(0, 9) + "..." : materialId;
                String materialText = shortName + ": " + available + "/" + required;
                int materialColor = available >= required ? STATUS_COLOR : ERROR_COLOR;
                // Draw smaller text by scaling the matrix
                context.getMatrices().push();
                context.getMatrices().scale(0.5f, 0.5f, 1.0f);
                context.drawText(this.textRenderer, Text.literal(materialText), numberX * 2, (numberY + 5) * 2, materialColor, false);
                context.getMatrices().pop();
            }
        }
    }
    
    /**
     * Helper method to create an ItemStack from a material ID string
     */
    private ItemStack createItemStackFromId(String materialId) {
        try {
            // Parse the identifier (e.g., "minecraft:stone")
            Identifier identifier = Identifier.tryParse(materialId);
            if (identifier != null) {
                // Try to get the item directly first
                Item item = Registries.ITEM.get(identifier);
                if (item != Items.AIR) {
                    return new ItemStack(item);
                }
                
                // If not found as item, try as block
                Block block = Registries.BLOCK.get(identifier);
                if (block != Blocks.AIR) {
                    Item blockItem = block.asItem();
                    if (blockItem != Items.AIR) {
                        return new ItemStack(blockItem);
                    }
                }
                
                // Debug logging to see what we're trying to parse
                Circuitmod.LOGGER.info("[CONSTRUCTOR-SCREEN] Could not create ItemStack for material ID: {}", materialId);
            }
        } catch (Exception e) {
            Circuitmod.LOGGER.warn("[CONSTRUCTOR-SCREEN] Failed to create ItemStack from material ID: {}", materialId, e);
        }
        
        return ItemStack.EMPTY;
    }
    
    private void drawScrollbar(DrawContext context) {
        if (materialsList.size() <= MAX_MATERIALS_VISIBLE) {
            return; // No scrollbar needed
        }
        
        // Position scrollbar inside the materials area, on the right side
        int scrollbarX = 49; // Positioned within the 70-pixel materials area width
        int scrollbarY = 44; // Moved up to match materials area
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
            int scrollbarX = this.x + 75; // Convert back to screen coordinates for mouse interaction
            int scrollbarY = this.y + 45; // Moved up to match materials area
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
            int scrollbarX = this.x + 75; // Convert back to screen coordinates for mouse interaction
            int scrollbarY = this.y + 45; // Moved up to match materials area
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

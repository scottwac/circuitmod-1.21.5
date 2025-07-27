package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class FluidTankScreen extends HandledScreen<FluidTankScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/machines/fluid_tank_gui.png");

    public FluidTankScreen(FluidTankScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 176;
    }

    @Override
    protected void init() {
        super.init();
        // Center the title and player inventory title
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        playerInventoryTitleX = (backgroundWidth - textRenderer.getWidth(playerInventoryTitle)) / 2;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Draw the main GUI background
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, 
                this.backgroundHeight, 256, 256);
        
        // Draw fluid tank fill level
        drawFluidTank(context);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw title
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
        
        // Draw player inventory title
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
    }

    private void drawFluidTank(DrawContext context) {
        int fluidAmount = handler.getStoredFluidAmount();
        int maxCapacity = handler.getMaxCapacity();
        
        if (fluidAmount > 0 && maxCapacity > 0) {
            // Tank dimensions (adjust these based on your GUI texture)
            int tankX = this.x + 130; // Position of tank in GUI
            int tankY = this.y + 20;
            int tankWidth = 16;
            int tankHeight = 54;
            
            // Calculate fill percentage
            double fillPercentage = (double) fluidAmount / maxCapacity;
            int fillHeight = (int) (tankHeight * fillPercentage);
            
            // Get fluid color (simplified for basic fluids)
            int fluidColor = getFluidColor();
            
            // Draw the fluid fill from bottom up
            int fillY = tankY + tankHeight - fillHeight;
            context.fill(tankX + 1, fillY, tankX + tankWidth - 1, tankY + tankHeight, fluidColor);
        }
    }
    
    private int getFluidColor() {
        int fluidId = handler.getFluidId();
        if (fluidId == Registries.FLUID.getRawId(Fluids.WATER)) {
            return 0xFF3F76E4; // Blue for water
        } else if (fluidId == Registries.FLUID.getRawId(Fluids.LAVA)) {
            return 0xFFFC9003; // Orange/red for lava
        }
        return 0xFF808080; // Gray for unknown fluids
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        
        // Draw fluid amount tooltip when hovering over tank
        int tankX = this.x + 130;
        int tankY = this.y + 20;
        int tankWidth = 16;
        int tankHeight = 54;
        
        if (mouseX >= tankX && mouseX <= tankX + tankWidth && 
            mouseY >= tankY && mouseY <= tankY + tankHeight) {
            
            int fluidAmount = handler.getStoredFluidAmount();
            int maxCapacity = handler.getMaxCapacity();
            
            String fluidName = getFluidName();
            String amountText = fluidAmount + " / " + maxCapacity + " mB";
            
            context.drawTooltip(textRenderer, 
                java.util.List.of(Text.literal(fluidName), Text.literal(amountText)), 
                mouseX, mouseY);
        }
    }
    
    private String getFluidName() {
        int fluidId = handler.getFluidId();
        if (fluidId == Registries.FLUID.getRawId(Fluids.WATER)) {
            return "Water";
        } else if (fluidId == Registries.FLUID.getRawId(Fluids.LAVA)) {
            return "Lava";
        } else if (fluidId == Registries.FLUID.getRawId(Fluids.EMPTY)) {
            return "Empty";
        }
        return "Unknown Fluid";
    }
} 
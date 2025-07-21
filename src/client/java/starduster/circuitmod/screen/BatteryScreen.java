package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class BatteryScreen extends HandledScreen<BatteryScreenHandler> {
    // Use a simple battery texture (we'll create this)
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/battery/battery_gui.png");
    
    // Colors
    private static final int STATUS_COLOR = 0xFF00FF00; // Green
    private static final int ERROR_COLOR = 0xFFFF0000; // Red
    private static final int INFO_COLOR = 0xFF888888; // Gray
    private static final int ENERGY_COLOR = 0xFFFFFF00; // Yellow
    private static final int NETWORK_COLOR = 0xFF00FFFF; // Cyan
    
    public BatteryScreen(BatteryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 176;
    }
    
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, 
        this.backgroundHeight, 256, 256);
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw title
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
        
        // Draw battery information
        int storedEnergy = handler.getStoredEnergy();
        int maxCapacity = handler.getMaxCapacity();
        int chargePercentage = maxCapacity > 0 ? (int)((float)storedEnergy / maxCapacity * 100) : 0;
        
        // Battery info and settings (left column, compact)
        int leftY = 40;
        int leftYStep = 15;

        // Battery status
        context.getMatrices().push();
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawText(this.textRenderer, Text.literal("Battery Status"), 16, leftY, INFO_COLOR, false);
        context.getMatrices().pop();
        leftY += leftYStep;

        // Current energy
        double storedKJ = storedEnergy / 1000.0;
        String currentEnergyText = String.format("Current Energy: %.1f kJ", storedKJ);
        context.getMatrices().push();
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawText(this.textRenderer, Text.literal(currentEnergyText), 16, leftY, ENERGY_COLOR, false);
        context.getMatrices().pop();
        leftY += leftYStep;

        // Max capacity
        double maxKJ = handler.getMaxCapacityKJ();
        String energyText = String.format("Max Capacity: %.1f kJ", maxKJ);
        context.getMatrices().push();
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawText(this.textRenderer, Text.literal(energyText), 16, leftY, ENERGY_COLOR, false);
        context.getMatrices().pop();
        leftY += leftYStep;

        // Max charge rate
        String chargeRateText = "Max Charge Rate: " + handler.getMaxChargeRate() + " energy/tick";
        context.getMatrices().push();
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawText(this.textRenderer, Text.literal(chargeRateText), 16, leftY, INFO_COLOR, false);
        context.getMatrices().pop();
        leftY += leftYStep;

        // Max discharge rate
        String dischargeRateText = "Max Discharge Rate: " + handler.getMaxDischargeRate() + " energy/tick";
        context.getMatrices().push();
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawText(this.textRenderer, Text.literal(dischargeRateText), 16, leftY, INFO_COLOR, false);
        context.getMatrices().pop();
        leftY += leftYStep;

        // Draw energy bar (unchanged)
        int barX = 8;
        int barY = 65;
        int barWidth = 160;
        int barHeight = 8;
        
        // Background
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        
        // Progress fill
        if (maxCapacity > 0) {
            int fillWidth = (storedEnergy * barWidth) / maxCapacity;
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00FF00);
        }
        
        // Border
        context.drawBorder(barX, barY, barWidth, barHeight, 0xFF666666);
        
        // Start right column at x = 180 (adjust as needed for your GUI width)
        int rightColX = 180;
        int rightY = 40;
        int rightYStep = 15;

        // Charge/Discharge status (right column, compact)
        String chargeStatus = "Charging: " + (handler.canCharge() ? "Enabled" : "Disabled");
        int chargeColor = handler.canCharge() ? STATUS_COLOR : ERROR_COLOR;
        context.getMatrices().push();
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawText(this.textRenderer, Text.literal(chargeStatus), rightColX, rightY, chargeColor, false);
        context.getMatrices().pop();
        rightY += rightYStep;

        String dischargeStatus = "Discharging: " + (handler.canDischarge() ? "Enabled" : "Disabled");
        int dischargeColor = handler.canDischarge() ? STATUS_COLOR : ERROR_COLOR;
        context.getMatrices().push();
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawText(this.textRenderer, Text.literal(dischargeStatus), rightColX, rightY, dischargeColor, false);
        context.getMatrices().pop();
        rightY += rightYStep;

        // Network information (right column, compact)
        context.getMatrices().push();
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.drawText(this.textRenderer, Text.literal("Network Information"), rightColX, rightY, NETWORK_COLOR, false);
        context.getMatrices().pop();
        rightY += rightYStep;

        int networkSize = handler.getNetworkSize();
        if (networkSize > 0) {
            String networkText = "Connected blocks: " + networkSize;
            context.getMatrices().push();
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawText(this.textRenderer, Text.literal(networkText), rightColX, rightY, NETWORK_COLOR, false);
            context.getMatrices().pop();
            rightY += rightYStep;
            
            int networkStored = handler.getNetworkStoredEnergy();
            int networkMax = handler.getNetworkMaxStorage();
            String networkEnergyText;
            if (networkStored < 10000 && networkMax < 10000) {
                networkEnergyText = "Network energy: " + networkStored + " / " + networkMax;
            } else {
                double netStoredKJ = networkStored / 1000.0;
                double netMaxKJ = networkMax / 1000.0;
                networkEnergyText = String.format("Network energy: %.1f kJ / %.1f kJ", netStoredKJ, netMaxKJ);
            }
            context.getMatrices().push();
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawText(this.textRenderer, Text.literal(networkEnergyText), rightColX, rightY, NETWORK_COLOR, false);
            context.getMatrices().pop();
            rightY += rightYStep;
            
            int lastProduced = handler.getNetworkLastProduced();
            int lastConsumed = handler.getNetworkLastConsumed();
            String productionText = "Last tick: +" + lastProduced + " produced, -" + lastConsumed + " consumed";
            context.getMatrices().push();
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawText(this.textRenderer, Text.literal(productionText), rightColX, rightY, NETWORK_COLOR, false);
            context.getMatrices().pop();
            rightY += rightYStep;
            
            int lastStored = handler.getNetworkLastStored();
            int lastDrawn = handler.getNetworkLastDrawn();
            String batteryActivityText = "Battery activity: +" + lastStored + " stored, -" + lastDrawn + " drawn";
            context.getMatrices().push();
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawText(this.textRenderer, Text.literal(batteryActivityText), rightColX, rightY, NETWORK_COLOR, false);
            context.getMatrices().pop();
        } else {
            context.getMatrices().push();
            context.getMatrices().scale(0.5f, 0.5f, 1.0f);
            context.drawText(this.textRenderer, Text.literal("Not connected to any network!"), rightColX, rightY, ERROR_COLOR, false);
            context.getMatrices().pop();
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
} 
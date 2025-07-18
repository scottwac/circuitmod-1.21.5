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
        
        // Battery status
        context.drawText(this.textRenderer, Text.literal("Battery Status"), 8, 20, INFO_COLOR, false);
        
        // Energy storage
        String energyText = "Stored Energy: " + storedEnergy + " / " + maxCapacity + " (" + chargePercentage + "%)";
        context.drawText(this.textRenderer, Text.literal(energyText), 8, 35, ENERGY_COLOR, false);
        
        // Draw energy bar
        int barX = 8;
        int barY = 50;
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
        
        // Battery settings
        context.drawText(this.textRenderer, Text.literal("Settings"), 8, 70, INFO_COLOR, false);
        
        String chargeRateText = "Max Charge Rate: " + handler.getMaxChargeRate() + " energy/tick";
        context.drawText(this.textRenderer, Text.literal(chargeRateText), 8, 85, INFO_COLOR, false);
        
        String dischargeRateText = "Max Discharge Rate: " + handler.getMaxDischargeRate() + " energy/tick";
        context.drawText(this.textRenderer, Text.literal(dischargeRateText), 8, 100, INFO_COLOR, false);
        
        // Charge/Discharge status
        String chargeStatus = "Charging: " + (handler.canCharge() ? "Enabled" : "Disabled");
        int chargeColor = handler.canCharge() ? STATUS_COLOR : ERROR_COLOR;
        context.drawText(this.textRenderer, Text.literal(chargeStatus), 8, 115, chargeColor, false);
        
        String dischargeStatus = "Discharging: " + (handler.canDischarge() ? "Enabled" : "Disabled");
        int dischargeColor = handler.canDischarge() ? STATUS_COLOR : ERROR_COLOR;
        context.drawText(this.textRenderer, Text.literal(dischargeStatus), 8, 130, dischargeColor, false);
        
        // Network information
        context.drawText(this.textRenderer, Text.literal("Network Information"), 8, 150, NETWORK_COLOR, false);
        
        int networkSize = handler.getNetworkSize();
        if (networkSize > 0) {
            String networkText = "Connected blocks: " + networkSize;
            context.drawText(this.textRenderer, Text.literal(networkText), 8, 165, NETWORK_COLOR, false);
            
            int networkStored = handler.getNetworkStoredEnergy();
            int networkMax = handler.getNetworkMaxStorage();
            String networkEnergyText = "Network energy: " + networkStored + " / " + networkMax;
            context.drawText(this.textRenderer, Text.literal(networkEnergyText), 8, 180, NETWORK_COLOR, false);
            
            int lastProduced = handler.getNetworkLastProduced();
            int lastConsumed = handler.getNetworkLastConsumed();
            String productionText = "Last tick: +" + lastProduced + " produced, -" + lastConsumed + " consumed";
            context.drawText(this.textRenderer, Text.literal(productionText), 8, 195, NETWORK_COLOR, false);
            
            int lastStored = handler.getNetworkLastStored();
            int lastDrawn = handler.getNetworkLastDrawn();
            String batteryActivityText = "Battery activity: +" + lastStored + " stored, -" + lastDrawn + " drawn";
            context.drawText(this.textRenderer, Text.literal(batteryActivityText), 8, 210, NETWORK_COLOR, false);
        } else {
            context.drawText(this.textRenderer, Text.literal("Not connected to any network!"), 8, 165, ERROR_COLOR, false);
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
} 
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

    // --- New fields for toggleable views ---
    private boolean showBatteryInfo = true; // if true battery info, else network info
    private net.minecraft.client.gui.widget.ButtonWidget toggleButton;
    
    public BatteryScreen(BatteryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 176;
    }

    // Add init method to create toggle button
    @Override
    protected void init() {
        super.init();
        // Position the button at top-left inside the GUI background
        int buttonWidth = 9;
        int buttonHeight = 11;
        int buttonX = this.x + this.backgroundWidth - buttonWidth - 8; // right side with 8px padding
        int buttonY = this.y + 6; // 6px from top
        toggleButton = net.minecraft.client.gui.widget.ButtonWidget.builder(
                getToggleButtonLabel(),
                btn -> {
                    showBatteryInfo = !showBatteryInfo;
                    btn.setMessage(getToggleButtonLabel());
                })
            .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
            .build();
        this.addDrawableChild(toggleButton);
    }

    private net.minecraft.text.Text getToggleButtonLabel() {
        return net.minecraft.text.Text.literal("\\");
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

        if (showBatteryInfo) {
            drawBatteryInfo(context);
        } else {
            drawNetworkInfo(context);
        }
        drawEnergyBar(context);
    }

    private void drawEnergyBar(DrawContext context) {
        int storedEnergy = handler.getStoredEnergy();
        int maxCapacity = handler.getMaxCapacity();
        // Energy bar
        int barX = 7;
        int barY = 82;
        int barWidth = 162;
        int barHeight = 8;
        // Background
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        if (maxCapacity > 0) {
            int fillWidth = (storedEnergy * barWidth) / maxCapacity;
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00FF00);
        }
        context.drawBorder(barX, barY, barWidth, barHeight, 0xFF666666);
    }

    // Draw only battery-related information
    private void drawBatteryInfo(DrawContext context) {
        int storedEnergy = handler.getStoredEnergy();
        int maxCapacity = handler.getMaxCapacity();

        int leftY = 20;
        int leftYStep = 12;
        int colX = 9;

//        // Section header
//        drawScaledText(context, "Battery Status", 7, leftY, INFO_COLOR);
//        leftY += leftYStep;

        String chargeStatus = "Charging: " + (handler.canCharge() ? "Enabled" : "Disabled");
        int chargeColor = handler.canCharge() ? STATUS_COLOR : ERROR_COLOR;
        drawScaledText(context, chargeStatus, colX, leftY, chargeColor);
        leftY += leftYStep;

        String dischargeStatus = "Discharging: " + (handler.canDischarge() ? "Enabled" : "Disabled");
        int dischargeColor = handler.canDischarge() ? STATUS_COLOR : ERROR_COLOR;
        drawScaledText(context, dischargeStatus, colX, leftY, dischargeColor);
        leftY += leftYStep;

        // Current energy
        double storedKJ = storedEnergy / 1000.0;
        drawScaledText(context, String.format("Current Energy: %.1f kJ", storedKJ), colX, leftY, ENERGY_COLOR);
        leftY += leftYStep;

        // Max capacity
        double maxKJ = handler.getMaxCapacityKJ();
        drawScaledText(context, String.format("Max Capacity: %.1f kJ", maxKJ), colX, leftY, ENERGY_COLOR);
        leftY += leftYStep;

        // Max charge/discharge rates
        drawScaledText(context, "Max Charge Rate: " + handler.getMaxChargeRate() + " energy/tick", colX, leftY, INFO_COLOR);
        leftY += leftYStep;

        drawScaledText(context, "Max Discharge Rate: " + handler.getMaxDischargeRate() + " energy/tick", colX, leftY, INFO_COLOR);
        leftY += leftYStep;

    }

    // Draw only network-related information
    private void drawNetworkInfo(DrawContext context) {
        int leftY = 20;
        int leftYStep = 12;
        int colX = 9;

        drawScaledText(context, "Network Information", colX, leftY, NETWORK_COLOR);
        leftY += leftYStep;

        int networkSize = handler.getNetworkSize();
        if (networkSize > 0) {
            drawScaledText(context, "Connected blocks: " + networkSize, colX, leftY, NETWORK_COLOR);
            leftY += leftYStep;

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
            drawScaledText(context, networkEnergyText, colX, leftY, NETWORK_COLOR);
            leftY += leftYStep;

            int lastProduced = handler.getNetworkLastProduced();
            int lastConsumed = handler.getNetworkLastConsumed();
            drawScaledText(context, "Last tick: +" + lastProduced + " prod, -" + lastConsumed + " cons", colX, leftY, NETWORK_COLOR);
            leftY += leftYStep;

            int lastStored = handler.getNetworkLastStored();
            int lastDrawn = handler.getNetworkLastDrawn();
            drawScaledText(context, "Batt. activity: +" + lastStored + " stored, -" + lastDrawn + " drawn", colX, leftY, NETWORK_COLOR);
            leftY += leftYStep;
        } else {
            drawScaledText(context, "Not connected to any network!", colX, leftY, ERROR_COLOR);
        }
    }

    // Utility helper to draw half-scaled text (0.5f) – keeps code DRY
    private void drawScaledText(DrawContext context, String string, int x, int y, int color) {
        context.getMatrices().push();
        context.getMatrices().scale(0.88f, 0.88f, 1.0f);
        context.drawText(this.textRenderer, net.minecraft.text.Text.literal(string), x, y, color, false);
        context.getMatrices().pop();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
} 
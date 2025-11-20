package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import starduster.circuitmod.network.ClientNetworking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SatelliteControlScreen extends HandledScreen<SatelliteControlScreenHandler> {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONSOLE_HEIGHT = 170;
    private static final int LINE_HEIGHT = 9;
    private static final float TEXT_SCALE = 0.85f;
    
    private List<String> outputLines = new ArrayList<>();
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private int scrollOffset = 0;
    private String currentInput = "";
    
    // Temporary map overlay data
    private String currentMapData = null;
    private int currentMapSize = 0;
    private byte[] currentMapColors = null;
    private int processedLineCount = 0;
    
    public SatelliteControlScreen(SatelliteControlScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = PANEL_WIDTH;
        this.backgroundHeight = PANEL_HEIGHT;
        this.playerInventoryTitleY = 10000; // Hide player inventory title
        
        // Initialize output lines from handler
        this.outputLines = new ArrayList<>(handler.getOutputLines());
        this.processedLineCount = this.outputLines.size();
    }
    
    @Override
    public void removed() {
        super.removed();
        // Clear map overlay when screen is closed
        currentMapData = null;
        currentMapSize = 0;
        currentMapColors = null;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        titleY = 7;

        // Scroll to bottom
        scrollToBottom();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // If map is showing, block all keys except Enter (to dismiss) and Escape (to close)
        if (currentMapData != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                // Dismiss the map
                currentMapData = null;
                currentMapSize = 0;
                currentMapColors = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            // Block all other keys while map is showing
            return true;
        }
        
        // Handle Enter key to submit command
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!currentInput.isEmpty()) {
                sendCommand(currentInput);
                
                // Add to command history
                commandHistory.add(currentInput);
                historyIndex = commandHistory.size();
                
                currentInput = "";
            }
            return true;
        }
        
        // Handle Up arrow for command history (previous)
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (!commandHistory.isEmpty() && historyIndex > 0) {
                historyIndex--;
                currentInput = commandHistory.get(historyIndex);
            } else if (commandHistory.isEmpty()) {
                currentInput = "";
            }
            return true;
        }
        
        // Handle Down arrow for command history (next)
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (!commandHistory.isEmpty()) {
                historyIndex++;
                if (historyIndex >= commandHistory.size()) {
                    historyIndex = commandHistory.size();
                    currentInput = "";
                } else {
                    currentInput = commandHistory.get(historyIndex);
                }
            }
            return true;
        }
        
        // Handle Backspace
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!currentInput.isEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
            }
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        
        // Handle scrolling with Page Up/Page Down
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollOffset = Math.max(0, scrollOffset - getMaxRenderableLines());
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollOffset = Math.min(Math.max(0, outputLines.size() - 1), scrollOffset + getMaxRenderableLines());
            return true;
        }
        
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Block scrolling when map is showing
        if (currentMapData != null) {
            return true;
        }
        
        // Scroll the console output
        int scrollAmount = (int) (-verticalAmount * 3);
        scrollOffset = Math.max(0, Math.min(Math.max(0, outputLines.size() - 1), scrollOffset + scrollAmount));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Block character input when map is showing
        if (currentMapData != null) {
            return true;
        }
        
        if (!Character.isISOControl(chr)) {
            currentInput += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Block mouse clicks when map is showing
        if (currentMapData != null) {
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendCommand(String command) {
        BlockPos pos = handler.getBlockEntityPos();
        if (pos == null || client == null) {
            return;
        }
        
        // Add command to local output immediately for responsiveness
        outputLines.add("> " + command);
        scrollToBottom();
        
        // Send to server
        ClientNetworking.sendSatelliteCommand(pos, command);
    }

    private void scrollToBottom() {
        if (outputLines.isEmpty()) {
            scrollOffset = 0;
            return;
        }
        int maxLines = getMaxRenderableLines();
        int count = 0;
        int index = outputLines.size() - 1;
        while (index >= 0) {
            count += countWrappedLines(outputLines.get(index));
            if (count >= maxLines) {
                break;
            }
            index--;
        }
        scrollOffset = Math.max(0, index);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;

        // Dark background
        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF000000);
        
        // Console area with slight border
        context.fill(left + 5, top + 23, left + backgroundWidth - 5, top + 23 + CONSOLE_HEIGHT, 0xFF001100);
        
        // Command input area
        context.fill(left + 5, top + backgroundHeight - 35, left + backgroundWidth - 5, top + backgroundHeight - 5, 0xFF001100);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Call super to properly handle REI compatibility (background + foreground)
        super.render(context, mouseX, mouseY, delta);
        
        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;

        // Draw title
        context.drawText(
            textRenderer, 
            title.copy().formatted(Formatting.GREEN, Formatting.BOLD), 
            left + titleX, 
            top + titleY, 
            0x00FF00, 
            true
        );

        // Draw console output with scrolling
        int consoleTop = top + 25;
        int y = consoleTop;
        
        int maxRenderableLines = getMaxRenderableLines();
        int lineHeight = getScaledLineHeight();
        int startLine = Math.min(Math.max(0, scrollOffset), Math.max(0, outputLines.size() - 1));
        int renderedSubLines = 0;
        
        outer:
        for (int i = startLine; i < outputLines.size(); i++) {
            String line = outputLines.get(i);
            
            // Skip raw map data lines if any remain
            if (line.startsWith("MAP_DATA:")) {
                continue;
            }
            
            // Color coding for different line types
            int color = 0x00FF00; // Default green
            if (line.startsWith("> ")) {
                color = 0xFFFF00; // Yellow for commands
            } else if (line.startsWith("Error:")) {
                color = 0xFF0000; // Red for errors
            } else if (line.isEmpty()) {
                // Skip empty lines in rendering
                y += LINE_HEIGHT;
                continue;
            }
            
            List<OrderedText> wrappedLines = wrapTextLines(line, getConsoleMaxWidth());
            if (wrappedLines.isEmpty()) {
                wrappedLines = Collections.singletonList(Text.literal("").asOrderedText());
            }
            for (OrderedText ordered : wrappedLines) {
                if (renderedSubLines >= maxRenderableLines) {
                    break outer;
                }
                drawScaledOrderedText(context, ordered, left + 10, y, color);
                y += lineHeight;
                renderedSubLines++;
            }
        }
        
        // Draw scroll indicator if needed
        if (outputLines.size() > maxRenderableLines) {
            String scrollInfo = String.format("[%d/%d]", scrollOffset + 1, Math.max(1, outputLines.size() - maxRenderableLines + 1));
            context.drawText(textRenderer, scrollInfo, left + backgroundWidth - 45, consoleTop - 12, 0x808080, false);
        }

        // Draw command prompt and current input
        drawInputLine(context, left + 8, top + backgroundHeight - 28);

        // Render map overlay if present (on top of everything)
        if (currentMapData != null && currentMapColors != null) {
            renderMapOverlay(context, left, top);
        }
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Override to prevent drawing slot labels (we don't have traditional slots)
    }
    
    private int getScaledLineHeight() {
        if (textRenderer == null) {
            return LINE_HEIGHT;
        }
        return Math.max(7, Math.round(textRenderer.fontHeight * TEXT_SCALE) + 1);
    }
    
    private int getAvailableConsoleHeight() {
        int lineHeight = getScaledLineHeight();
        return Math.max(lineHeight, backgroundHeight - 57 - lineHeight);
    }
    
    private int getMaxRenderableLines() {
        int lineHeight = getScaledLineHeight();
        return Math.max(1, getAvailableConsoleHeight() / lineHeight);
    }
    
    private int getConsoleMaxWidth() {
        return backgroundWidth - 20;
    }
    
    private List<OrderedText> wrapTextLines(String line, int maxWidth) {
        if (line == null || line.isEmpty()) {
            return Collections.emptyList();
        }
        if (textRenderer == null) {
            return Collections.singletonList(Text.literal(line).asOrderedText());
        }
        int wrapWidth = (int)Math.max(1, maxWidth / TEXT_SCALE);
        return textRenderer.wrapLines(Text.literal(line), wrapWidth);
    }
    
    private void drawScaledOrderedText(DrawContext context, OrderedText text, int x, int y, int color) {
        if (text == null) {
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        context.drawText(textRenderer, text, 0, 0, color, false);
        context.getMatrices().pop();
    }
    
    private void drawInputLine(DrawContext context, int x, int y) {
        long blink = (System.currentTimeMillis() / 500) % 2;
        String prompt = "> " + currentInput + (blink == 0 ? "_" : "");
        drawScaledOrderedText(context, Text.literal(prompt).asOrderedText(), x, y, 0x00FF00);
    }
    
    private int countWrappedLines(String line) {
        int maxWidth = getConsoleMaxWidth();
        List<OrderedText> wrapped = wrapTextLines(line, maxWidth);
        return Math.max(1, wrapped.size());
    }

    /**
     * Render the map as a centered overlay
     */
    private void renderMapOverlay(DrawContext context, int panelLeft, int panelTop) {
        if (currentMapColors == null || currentMapColors.length != currentMapSize * currentMapSize) {
            return;
        }
        
        // Calculate console area
        int consoleLeft = panelLeft + 5;
        int consoleTop = panelTop + 23;
        int consoleWidth = backgroundWidth - 10;
        int consoleHeight = CONSOLE_HEIGHT;
        
        // Calculate map display size to fit console area (with padding)
        int maxWidth = consoleWidth - 20;
        int maxHeight = consoleHeight - 30; // Leave room for title and hint
        
        int pixelSizeForWidth = Math.max(1, maxWidth / currentMapSize);
        int pixelSizeForHeight = Math.max(1, maxHeight / currentMapSize);
        int pixelSize = Math.min(pixelSizeForWidth, pixelSizeForHeight);
        pixelSize = Math.max(1, Math.min(4, pixelSize));
        
        int displaySize = currentMapSize * pixelSize;
        
        // Center the map in the console area
        int mapLeft = consoleLeft + (consoleWidth - displaySize) / 2;
        int mapTop = consoleTop + (consoleHeight - displaySize - 20) / 2; // 20 for title and hint
        
        // Draw semi-transparent dark background overlay
        context.fill(consoleLeft, consoleTop, consoleLeft + consoleWidth, consoleTop + consoleHeight, 0xE0000000);
        
        // Draw title above map
        String title = "Orbital Scan Map (" + currentMapSize + "x" + currentMapSize + ")";
        int titleWidth = textRenderer.getWidth(title);
        context.drawText(textRenderer, title, consoleLeft + (consoleWidth - titleWidth) / 2, mapTop - 12, 0x00FF00, false);
        
        // Draw the map
        for (int mapZ = 0; mapZ < currentMapSize; mapZ++) {
            for (int mapX = 0; mapX < currentMapSize; mapX++) {
                byte colorByte = currentMapColors[mapZ * currentMapSize + mapX];
                int color = starduster.circuitmod.satellite.ScanSatellite.getColorFromByte(colorByte);
                
                int pixelX = mapLeft + mapX * pixelSize;
                int pixelY = mapTop + mapZ * pixelSize;
                context.fill(pixelX, pixelY, pixelX + pixelSize, pixelY + pixelSize, 0xFF000000 | color);
            }
        }
        
        // Draw hint below map
        String hint = "[Press ENTER to dismiss]";
        int hintWidth = textRenderer.getWidth(hint);
        context.drawText(textRenderer, hint, consoleLeft + (consoleWidth - hintWidth) / 2, 
                        mapTop + displaySize + 5, 0xFFFF00, false);
    }

    /**
     * Process newly received output lines for special content (like maps)
     */
    private void processNewOutputLines(int startIndex) {
        if (startIndex < 0) {
            startIndex = 0;
        }
        
        for (int i = startIndex; i < outputLines.size(); i++) {
            String line = outputLines.get(i);
            if (line.startsWith("MAP_DATA:")) {
                boolean parsed = tryStoreMapData(line);
                if (parsed) {
                    outputLines.set(i, "[Orbital scan data received - displaying map]");
                } else {
                    outputLines.set(i, "[Error parsing orbital scan data]");
                }
            }
        }
    }
    
    private boolean tryStoreMapData(String mapDataLine) {
        try {
            String[] parts = mapDataLine.split(":", 3);
            if (parts.length < 3) {
                return false;
            }
            
            int size = Integer.parseInt(parts[1]);
            byte[] colors = java.util.Base64.getDecoder().decode(parts[2]);
            if (colors.length != size * size) {
                return false;
            }
            
            currentMapSize = size;
            currentMapColors = colors;
            currentMapData = mapDataLine;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Update the output lines from the server
     */
    public void updateOutputLines(List<String> lines) {
        int startIndex = Math.min(processedLineCount, lines.size());
        this.outputLines = new ArrayList<>(lines);
        processNewOutputLines(startIndex);
        processedLineCount = this.outputLines.size();
        scrollToBottom();
    }
}


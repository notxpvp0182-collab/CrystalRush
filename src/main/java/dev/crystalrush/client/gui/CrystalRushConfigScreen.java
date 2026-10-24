package dev.crystalrush.client.gui;

import dev.crystalrush.client.CrystalRushClient;
import dev.crystalrush.client.input.KeybindManager;
import dev.crystalrush.config.CrystalRushConfig;
import dev.crystalrush.config.CrystalRushConfigManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * Clean, dark-themed configuration screen with restrained cyan accents for CrystalRush.
 * Built using standard Minecraft 1.21.11 GUI widgets and persisted immediately via {@link CrystalRushConfigManager}.
 */
public class CrystalRushConfigScreen extends Screen {

    // Dark theme palette with restrained cyan accents (ARGB)
    private static final int COLOR_BG_TOP = 0xEE0B1017;
    private static final int COLOR_BG_BOTTOM = 0xF4101924;
    private static final int COLOR_PANEL_FILL = 0xCC141E2B;
    private static final int COLOR_CYAN_ACCENT = 0xFF00BCD4;
    private static final int COLOR_CYAN_SUBTLE = 0x6600BCD4;
    private static final int COLOR_TEXT_PRIMARY = 0xFFE6F4F8;
    private static final int COLOR_TEXT_MUTED = 0xFF8CA8B5;

    private final Screen parent;
    private final CrystalRushConfigManager configManager;
    private final CrystalRushConfig workingConfig;

    private boolean listeningForKey = false;
    private ButtonWidget keybindButton;

    public CrystalRushConfigScreen(Screen parent) {
        super(Text.translatable("screen.crystalrush.config.title"));
        this.parent = parent;
        this.configManager = CrystalRushClient.getConfigManager();
        this.workingConfig = this.configManager.getConfig();
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();

        int centerX = this.width / 2;
        int colLeftX = centerX - 155;
        int colRightX = centerX + 5;
        int buttonWidth = 150;
        int buttonHeight = 20;
        int startY = 58;
        int rowSpacing = 26;

        // Row 1 Left: Master Enable/Disable
        this.addDrawableChild(ButtonWidget.builder(getEnabledText(), button -> {
            workingConfig.setEnabled(!workingConfig.isEnabled());
            button.setMessage(getEnabledText());
            persistChanges();
        }).dimensions(colLeftX, startY, buttonWidth, buttonHeight).build());

        // Row 1 Right: Hold-to-Activate Keybind
        this.keybindButton = ButtonWidget.builder(getKeybindText(), button -> {
            listeningForKey = !listeningForKey;
            button.setMessage(getKeybindText());
        }).dimensions(colRightX, startY, buttonWidth, buttonHeight).build();
        this.addDrawableChild(this.keybindButton);

        // Row 2 Left: Automatic Crystal Slot Detection
        this.addDrawableChild(ButtonWidget.builder(getAutoSlotText(), button -> {
            workingConfig.setAutoCrystalSlotDetection(!workingConfig.isAutoCrystalSlotDetection());
            button.setMessage(getAutoSlotText());
            persistChanges();
        }).dimensions(colLeftX, startY + rowSpacing, buttonWidth, buttonHeight).build());

        // Row 2 Right: Restore Previous Hotbar Slot
        this.addDrawableChild(ButtonWidget.builder(getRestoreSlotText(), button -> {
            workingConfig.setRestorePreviousHotbarSlot(!workingConfig.isRestorePreviousHotbarSlot());
            button.setMessage(getRestoreSlotText());
            persistChanges();
        }).dimensions(colRightX, startY + rowSpacing, buttonWidth, buttonHeight).build());

        // Row 3 Left: Crystal Action Delay Slider (0..20 ticks)
        this.addDrawableChild(new IntRangeSlider(
                colLeftX,
                startY + rowSpacing * 2,
                buttonWidth,
                buttonHeight,
                "option.crystalrush.action_delay",
                CrystalRushConfig.MIN_ACTION_DELAY_TICKS,
                CrystalRushConfig.MAX_ACTION_DELAY_TICKS,
                workingConfig.getCrystalActionDelayTicks(),
                newValue -> {
                    workingConfig.setCrystalActionDelayTicks(newValue);
                    persistChanges();
                }
        ));

        // Row 3 Right: Minimum Action Interval Slider (1..40 ticks)
        this.addDrawableChild(new IntRangeSlider(
                colRightX,
                startY + rowSpacing * 2,
                buttonWidth,
                buttonHeight,
                "option.crystalrush.action_interval",
                CrystalRushConfig.MIN_ACTION_INTERVAL_TICKS,
                CrystalRushConfig.MAX_ACTION_INTERVAL_TICKS,
                workingConfig.getActionIntervalTicks(),
                newValue -> {
                    workingConfig.setActionIntervalTicks(newValue);
                    persistChanges();
                }
        ));

        // Row 4 Left: Display Status Messages
        this.addDrawableChild(ButtonWidget.builder(getStatusMessagesText(), button -> {
            workingConfig.setDisplayStatusMessages(!workingConfig.isDisplayStatusMessages());
            button.setMessage(getStatusMessagesText());
            persistChanges();
        }).dimensions(colLeftX, startY + rowSpacing * 3, buttonWidth, buttonHeight).build());

        // Row 4 Right: Debug Logging
        this.addDrawableChild(ButtonWidget.builder(getDebugLoggingText(), button -> {
            workingConfig.setDebugLogging(!workingConfig.isDebugLogging());
            button.setMessage(getDebugLoggingText());
            persistChanges();
        }).dimensions(colRightX, startY + rowSpacing * 3, buttonWidth, buttonHeight).build());

        // Bottom Buttons: Restore Defaults & Save/Close
        int footerY = Math.min(this.height - 32, startY + rowSpacing * 4 + 24);

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("option.crystalrush.reset_defaults").formatted(Formatting.GRAY),
                button -> {
                    listeningForKey = false;
                    workingConfig.resetToDefaults();
                    persistChanges();
                    this.init();
                }
        ).dimensions(colLeftX, footerY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("option.crystalrush.done").formatted(Formatting.AQUA),
                button -> this.close()
        ).dimensions(colRightX, footerY, buttonWidth, buttonHeight).build());
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listeningForKey) {
            int keyCode = input.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                workingConfig.setActivationKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                workingConfig.setActivationKeyIsMouse(false);
            } else {
                workingConfig.setActivationKeyCode(keyCode);
                workingConfig.setActivationKeyIsMouse(false);
            }
            listeningForKey = false;
            persistChanges();
            if (keybindButton != null) {
                keybindButton.setMessage(getKeybindText());
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listeningForKey) {
            int button = click.button();
            if (button >= 0 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
                workingConfig.setActivationKeyCode(button);
                workingConfig.setActivationKeyIsMouse(true);
                listeningForKey = false;
                persistChanges();
                if (keybindButton != null) {
                    keybindButton.setMessage(getKeybindText());
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark gradient backdrop
        context.fillGradient(0, 0, this.width, this.height, COLOR_BG_TOP, COLOR_BG_BOTTOM);

        // Central card panel with subtle cyan top & bottom borders
        int panelLeft = (this.width / 2) - 168;
        int panelRight = (this.width / 2) + 168;
        int panelTop = 44;
        int panelBottom = Math.min(this.height - 16, 212);

        context.fill(panelLeft, panelTop, panelRight, panelBottom, COLOR_PANEL_FILL);
        context.fill(panelLeft, panelTop, panelRight, panelTop + 2, COLOR_CYAN_ACCENT);
        context.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, COLOR_CYAN_SUBTLE);

        // Header & Subtitle
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                16,
                COLOR_CYAN_ACCENT
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.crystalrush.config.subtitle"),
                this.width / 2,
                30,
                COLOR_TEXT_PRIMARY
        );

        // Render interactive widgets
        super.render(context, mouseX, mouseY, delta);

        // Informational notice regarding server authority
        int noticeY = Math.min(this.height - 50, 166);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.crystalrush.config.delay_notice"),
                this.width / 2,
                noticeY,
                COLOR_TEXT_MUTED
        );
    }

    @Override
    public void close() {
        persistChanges();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private void persistChanges() {
        workingConfig.validate();
        KeybindManager.applyConfigToKeyBinding(workingConfig);
        configManager.save();
    }

    private Text getEnabledText() {
        return formatToggle("option.crystalrush.enabled", workingConfig.isEnabled());
    }

    private Text getAutoSlotText() {
        return formatToggle("option.crystalrush.auto_slot", workingConfig.isAutoCrystalSlotDetection());
    }

    private Text getRestoreSlotText() {
        return formatToggle("option.crystalrush.restore_slot", workingConfig.isRestorePreviousHotbarSlot());
    }

    private Text getStatusMessagesText() {
        return formatToggle("option.crystalrush.status_messages", workingConfig.isDisplayStatusMessages());
    }

    private Text getDebugLoggingText() {
        return formatToggle("option.crystalrush.debug_logging", workingConfig.isDebugLogging());
    }

    private Text getKeybindText() {
        if (listeningForKey) {
            return Text.translatable("option.crystalrush.keybind.listening").formatted(Formatting.AQUA);
        }
        String keyLabel;
        if (workingConfig.getActivationKeyCode() == GLFW.GLFW_KEY_UNKNOWN) {
            keyLabel = InputUtil.UNKNOWN_KEY.getLocalizedText().getString();
        } else {
            InputUtil.Type type = workingConfig.isActivationKeyIsMouse()
                    ? InputUtil.Type.MOUSE
                    : InputUtil.Type.KEYSYM;
            keyLabel = type.createFromCode(workingConfig.getActivationKeyCode()).getLocalizedText().getString();
        }
        return Text.translatable("option.crystalrush.keybind")
                .append(Text.literal(": "))
                .append(Text.literal(keyLabel).formatted(Formatting.AQUA));
    }

    private static Text formatToggle(String translationKey, boolean state) {
        Text stateText = state
                ? Text.literal("ON").formatted(Formatting.AQUA)
                : Text.literal("OFF").formatted(Formatting.GRAY);
        return Text.translatable(translationKey).append(Text.literal(": ")).append(stateText);
    }

    /**
     * Reusable integer range slider widget for tick settings.
     */
    private static class IntRangeSlider extends SliderWidget {

        private final String translationKey;
        private final int min;
        private final int max;
        private final java.util.function.IntConsumer onValueChanged;

        public IntRangeSlider(
                int x,
                int y,
                int width,
                int height,
                String translationKey,
                int min,
                int max,
                int currentValue,
                java.util.function.IntConsumer onValueChanged
        ) {
            super(x, y, width, height, Text.empty(), toNormalized(currentValue, min, max));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.onValueChanged = onValueChanged;
            this.updateMessage();
        }

        private static double toNormalized(int value, int min, int max) {
            if (max <= min) {
                return 0.0D;
            }
            int clamped = Math.max(min, Math.min(max, value));
            return (double) (clamped - min) / (double) (max - min);
        }

        private int getDiscreteValue() {
            return (int) Math.round(this.min + this.value * (this.max - this.min));
        }

        @Override
        protected void updateMessage() {
            int discrete = getDiscreteValue();
            this.setMessage(Text.translatable(this.translationKey,
                    Text.literal(String.valueOf(discrete)).formatted(Formatting.AQUA)));
        }

        @Override
        protected void applyValue() {
            this.onValueChanged.accept(getDiscreteValue());
        }
    }
}

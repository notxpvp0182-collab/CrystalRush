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
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;

/**
 * Standard Default Minecraft Theme Configuration Screen for CrystalRush.
 * Features wide 155px buttons so text never clips and non-linear sliders from 0.1 ms to 2.00 seconds.
 */
public class CrystalRushConfigScreen extends Screen {

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
        int buttonWidth = 155;
        int buttonHeight = 20;
        int colLeftX = centerX - buttonWidth - 5;
        int colRightX = centerX + 5;
        int startY = 42;
        int rowSpacing = 25;

        // Row 1 Left: Master Enable/Disable
        this.addDrawableChild(ButtonWidget.builder(getEnabledText(), button -> {
            workingConfig.setEnabled(!workingConfig.isEnabled());
            button.setMessage(getEnabledText());
            persistChanges();
        }).dimensions(colLeftX, startY, buttonWidth, buttonHeight).build());

        // Row 1 Right: Activation Keybind
        this.keybindButton = ButtonWidget.builder(getKeybindText(), button -> {
            listeningForKey = !listeningForKey;
            button.setMessage(getKeybindText());
        }).dimensions(colRightX, startY, buttonWidth, buttonHeight).build();
        this.addDrawableChild(this.keybindButton);

        // Row 2 Left: Auto Crystal Slot
        this.addDrawableChild(ButtonWidget.builder(getAutoSlotText(), button -> {
            workingConfig.setAutoCrystalSlotDetection(!workingConfig.isAutoCrystalSlotDetection());
            button.setMessage(getAutoSlotText());
            persistChanges();
        }).dimensions(colLeftX, startY + rowSpacing, buttonWidth, buttonHeight).build());

        // Row 2 Right: Restore Old Slot
        this.addDrawableChild(ButtonWidget.builder(getRestoreSlotText(), button -> {
            workingConfig.setRestorePreviousHotbarSlot(!workingConfig.isRestorePreviousHotbarSlot());
            button.setMessage(getRestoreSlotText());
            persistChanges();
        }).dimensions(colRightX, startY + rowSpacing, buttonWidth, buttonHeight).build());

        // Row 3 Left: Place Delay Slider (0.1 ms to 2000 ms / 2.0 s)
        this.addDrawableChild(new MillisecondDelaySlider(
                colLeftX,
                startY + rowSpacing * 2,
                buttonWidth,
                buttonHeight,
                "option.crystalrush.place_delay",
                workingConfig.getPlaceDelayMs(),
                newDelayMs -> {
                    workingConfig.setPlaceDelayMs(newDelayMs);
                    persistChanges();
                }
        ));

        // Row 3 Right: Blast/Break Delay Slider (0.1 ms to 2000 ms / 2.0 s)
        this.addDrawableChild(new MillisecondDelaySlider(
                colRightX,
                startY + rowSpacing * 2,
                buttonWidth,
                buttonHeight,
                "option.crystalrush.break_delay",
                workingConfig.getBreakDelayMs(),
                newDelayMs -> {
                    workingConfig.setBreakDelayMs(newDelayMs);
                    persistChanges();
                }
        ));

        // Row 4 Left: Status Messages
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

        // Bottom Row: Reset to Defaults & Done (Standard Minecraft footer)
        int footerY = Math.min(this.height - 28, startY + rowSpacing * 4 + 16);

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("option.crystalrush.reset_defaults"),
                button -> {
                    listeningForKey = false;
                    workingConfig.resetToDefaults();
                    persistChanges();
                    this.init();
                }
        ).dimensions(colLeftX, footerY, buttonWidth, buttonHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                ScreenTexts.DONE,
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
        // Uses default Minecraft menu background & widget rendering
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFF);
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
            return Text.translatable("option.crystalrush.keybind.listening").formatted(Formatting.YELLOW);
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
                .append(Text.literal(keyLabel).formatted(Formatting.GREEN));
    }

    private static Text formatToggle(String translationKey, boolean state) {
        Text stateText = state
                ? ScreenTexts.ON.copy().formatted(Formatting.GREEN)
                : ScreenTexts.OFF.copy().formatted(Formatting.RED);
        return Text.translatable(translationKey).append(Text.literal(": ")).append(stateText);
    }

    /**
     * Smooth exponential slider allowing fine-grained control between 0.1 ms, 1 ms, 50 ms, and 2.00 seconds.
     */
    private static class MillisecondDelaySlider extends SliderWidget {

        private final String translationKey;
        private final DoubleConsumer onValueChanged;

        public MillisecondDelaySlider(
                int x,
                int y,
                int width,
                int height,
                String translationKey,
                double currentDelayMs,
                DoubleConsumer onValueChanged
        ) {
            super(x, y, width, height, Text.empty(), delayToNormalized(currentDelayMs));
            this.translationKey = translationKey;
            this.onValueChanged = onValueChanged;
            this.updateMessage();
        }

        private static double delayToNormalized(double delayMs) {
            double clamped = Math.max(CrystalRushConfig.MIN_DELAY_MS, Math.min(CrystalRushConfig.MAX_DELAY_MS, delayMs));
            double ratio = (clamped - CrystalRushConfig.MIN_DELAY_MS)
                    / (CrystalRushConfig.MAX_DELAY_MS - CrystalRushConfig.MIN_DELAY_MS);
            return Math.pow(ratio, 1.0D / 3.0D);
        }

        private double normalizedToDelayMs() {
            double curved = Math.pow(this.value, 3.0D);
            double rawMs = CrystalRushConfig.MIN_DELAY_MS
                    + curved * (CrystalRushConfig.MAX_DELAY_MS - CrystalRushConfig.MIN_DELAY_MS);
            if (rawMs < 10.0D) {
                return Math.round(rawMs * 10.0D) / 10.0D; // 0.1 ms precision
            } else {
                return Math.round(rawMs); // 1 ms precision
            }
        }

        @Override
        protected void updateMessage() {
            double ms = normalizedToDelayMs();
            String formatted = CrystalRushConfig.formatDelayLabel(ms);
            this.setMessage(Text.translatable(this.translationKey,
                    Text.literal(formatted).formatted(Formatting.YELLOW)));
        }

        @Override
        protected void applyValue() {
            this.onValueChanged.accept(normalizedToDelayMs());
        }
    }
}

package com.randomrun.features.voice.ui;

import com.randomrun.features.voice.VoicePack;
import com.randomrun.features.voice.VoiceSettings;
import com.randomrun.features.voice.VoiceSettings.VoiceLanguage;
import com.randomrun.features.voice.command.VoiceCommand;
import com.randomrun.features.voice.command.VoiceParser;
import com.randomrun.features.voice.control.VoiceController;
import com.randomrun.features.voice.engine.VoiceEngine;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.settings.AnimatedSettingsScreen;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Icons;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;
import com.randomrun.ui.widget.styled.OptionRow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * First-time setup for voice runs: language, download, microphone check.
 * Also reachable later from the speedrun screen to change language or mic.
 */
public class VoiceSetupScreen extends AnimatedSettingsScreen implements VoiceEngine.Listener {
    private enum Step { LANGUAGE, DOWNLOAD, MIC }

    private final Consumer<Boolean> onFinish;
    private final VoiceSettings settings = VoiceSettings.get();
    private Step step;
    private VoiceLanguage language;

    private volatile VoicePack.Phase phase = VoicePack.Phase.ENGINE;
    private volatile long done;
    private volatile long total = 1;
    private volatile String downloadError;

    private volatile String heard = "";
    private volatile String understood = "";
    private volatile boolean success;
    private List<String> microphones = new ArrayList<>();
    private ButtonRainbow nextButton;
    private boolean finished;
    private final boolean enableSolo;

    /** @param onFinish called with true when the player finished setup, false when they backed out. */
    public VoiceSetupScreen(Screen parent, Consumer<Boolean> onFinish) {
        this(parent, onFinish, true);
    }

    /** @param enableSolo whether finishing also turns on voice mode for solo runs (not for battle rooms). */
    public VoiceSetupScreen(Screen parent, Consumer<Boolean> onFinish, boolean enableSolo) {
        super(parent, false, LanguageManager.text("randomrun.voice.setup.title"));
        this.onFinish = onFinish;
        this.enableSolo = enableSolo;
        this.language = settings.language();
        this.step = Step.LANGUAGE;
    }

    @Override
    protected int widgetHeight() {
        return Math.max(150, Math.min(206, height - 82));
    }

    @Override
    protected void initContent() {
        switch (step) {
            case LANGUAGE -> initLanguage();
            case DOWNLOAD -> initDownload();
            case MIC -> initMic();
        }
    }

    private void rebuild() {
        clearWidgets();
        nextButton = null;
        initContent();
    }

    // ------------------------------------------------------------- language

    private void initLanguage() {
        int y = getWidgetY() + 92;
        for (VoiceLanguage lang : VoiceLanguage.values()) {
            final VoiceLanguage option = lang;
            addRenderableWidget(new OptionRow(rowX(), y, rowWidth(), Component.literal(lang.displayName),
                () -> Component.literal((option == language ? "§a● " : "§8○ ") + "§7" + sizeLabel(option)),
                button -> {
                    language = option;
                    if (nextButton != null) nextButton.setMessage(nextLabel());
                }, 0.04f * (lang.ordinal() + 1)));
            y += 24;
        }
        boolean supported = VoicePack.isPlatformSupported();
        ButtonRainbow next = new ButtonRainbow(width / 2 - 100, height - 55, 200, 20, nextLabel(), button -> {
            settings.setLanguage(language);
            if (VoicePack.isInstalled(language)) {
                step = Step.MIC;
            } else {
                step = Step.DOWNLOAD;
                startDownload();
            }
            rebuild();
        }, 1, 0.16f);
        next.active = supported;
        nextButton = next;
        addRenderableWidget(next);
        addRenderableWidget(new ButtonDefault(width / 2 - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> finish(false), 2, 0.2f));
    }

    private Component nextLabel() {
        long pending = VoicePack.pendingBytes(language);
        if (pending <= 0) return LanguageManager.text("randomrun.voice.setup.next");
        return LanguageManager.text("randomrun.voice.setup.download", Math.round(pending / 1_048_576.0));
    }

    private String sizeLabel(VoiceLanguage lang) {
        return VoicePack.isModelInstalled(lang)
            ? LanguageManager.translate("randomrun.voice.setup.installed")
            : LanguageManager.translate("randomrun.voice.setup.size", lang.sizeMb());
    }

    // ------------------------------------------------------------- download

    private void startDownload() {
        downloadError = null;
        done = 0;
        total = 1;
        VoicePack.install(language, (p, d, t) -> {
            phase = p;
            done = d;
            total = Math.max(1, t);
        }).whenComplete((ok, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                String message = String.valueOf(cause.getMessage());
                downloadError = message.contains("checksum") ? "checksum"
                    : message.contains("cancelled") ? "cancelled"
                    : message.contains("unsupported") ? "platform" : "network";
                if (step == Step.DOWNLOAD) rebuild();
                return;
            }
            Ui.playSound(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.5f);
            if (step == Step.DOWNLOAD && Minecraft.getInstance().gui.screen() == this) {
                step = Step.MIC;
                rebuild();
            }
        }));
    }

    private void initDownload() {
        if (downloadError != null) {
            addRenderableWidget(new ButtonRainbow(width / 2 - 100, height - 55, 200, 20,
                LanguageManager.text("randomrun.voice.setup.retry"), button -> {
                    startDownload();
                    rebuild();
                }, 1, 0.05f));
        }
        addRenderableWidget(new ButtonDefault(width / 2 - 100, height - 30, 200, 20,
            LanguageManager.text(downloadError == null ? "randomrun.voice.setup.cancel" : "randomrun.button.back"), button -> {
                VoicePack.cancel();
                step = Step.LANGUAGE;
                rebuild();
            }, 2, 0.1f));
    }

    // ------------------------------------------------------------------ mic

    private void initMic() {
        microphones = VoiceEngine.microphones();
        addRenderableWidget(new OptionRow(rowX(), getWidgetY() + 34, rowWidth(),
            LanguageManager.text("randomrun.voice.setup.microphone"),
            () -> Component.literal(Ui.ellipsize(font, micName(), rowWidth() / 2)),
            button -> {
                List<String> options = new ArrayList<>();
                options.add(null);
                options.addAll(microphones);
                int index = options.indexOf(settings.microphone());
                String next = options.get((index + 1) % options.size());
                settings.setMicrophone(next);
                restartEngine();
            }, 0.04f));
        addRenderableWidget(new OptionRow(rowX(), getWidgetY() + 58, rowWidth(),
            LanguageManager.text("randomrun.voice.setup.mode"),
            () -> LanguageManager.text(settings.isFreeSpeech() ? "randomrun.voice.setup.mode.free" : "randomrun.voice.setup.mode.strict"),
            button -> {
                settings.setFreeSpeech(!settings.isFreeSpeech());
                restartEngine();
            }, 0.08f));
        addRenderableWidget(new ButtonRainbow(width / 2 - 100, height - 55, 200, 20,
            LanguageManager.text("randomrun.voice.setup.done"), button -> {
                settings.setSetupDone(true);
                if (enableSolo) settings.setVoiceRunEnabled(true);
                finish(true);
            }, 1, 0.12f));
        addRenderableWidget(new ButtonDefault(width / 2 - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> {
                stopEngine();
                step = Step.LANGUAGE;
                rebuild();
            }, 2, 0.16f));
        restartEngine();
    }

    private String micName() {
        String mic = settings.microphone();
        return mic == null ? LanguageManager.translate("randomrun.voice.setup.mic_default") : mic;
    }

    private void restartEngine() {
        if (VoiceController.get().isActive()) return;
        heard = "";
        understood = "";
        success = false;
        VoiceEngine engine = VoiceEngine.get();
        engine.setListener(this);
        engine.start(language, settings.microphone(), settings.grammar());
    }

    private void stopEngine() {
        if (VoiceController.get().isActive()) return;
        VoiceEngine.get().setListener(null);
        VoiceEngine.get().stop();
    }

    @Override
    public void onPartial(String text) {
        heard = text;
    }

    @Override
    public void onFinal(String text) {
        heard = text;
        List<VoiceCommand> commands = VoiceParser.parse(text, language.id);
        if (!commands.isEmpty()) {
            success = true;
            StringBuilder out = new StringBuilder();
            for (VoiceCommand command : commands) {
                if (out.length() > 0) out.append(", ");
                out.append(describe(command));
            }
            understood = out.toString();
            Minecraft.getInstance().execute(() -> Ui.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.6f));
        } else {
            understood = "";
        }
    }

    private static String describe(VoiceCommand command) {
        // Explicit, stable names (not the class names).
        String name = switch (command) {
            case VoiceCommand.Start c -> "start";
            case VoiceCommand.Stop c -> "stop";
            case VoiceCommand.Move c -> "move";
            case VoiceCommand.Sprint c -> "sprint";
            case VoiceCommand.Jump c -> "jump";
            case VoiceCommand.JumpOff c -> "jumpoff";
            case VoiceCommand.Swim c -> "swim";
            case VoiceCommand.Sneak c -> "sneak";
            case VoiceCommand.Turn c -> "turn";
            case VoiceCommand.Pitch c -> "pitch";
            case VoiceCommand.Face c -> "face";
            case VoiceCommand.Aim c -> "aim";
            case VoiceCommand.Hit c -> "hit";
            case VoiceCommand.Use c -> "use";
            case VoiceCommand.Place c -> "place";
            case VoiceCommand.Eat c -> "eat";
            case VoiceCommand.Select c -> "select";
            case VoiceCommand.Get c -> "get";
            case VoiceCommand.Inventory c -> "inventory";
            case VoiceCommand.Craft c -> "craft";
            case VoiceCommand.Drop c -> "drop";
            case VoiceCommand.Approach c -> "approach";
            case VoiceCommand.Pickup c -> "pickup";
            case VoiceCommand.Repeat c -> "repeat";
            case VoiceCommand.Refine c -> "refine";
            case VoiceCommand.NewSeed c -> "newseed";
            case VoiceCommand.UpgraderOpen c -> "upgraderopen";
            case VoiceCommand.UpgraderChance c -> "upgraderchance";
            case VoiceCommand.UpgraderTarget c -> "upgradertarget";
            case VoiceCommand.UpgraderRoll c -> "upgraderroll";
            case VoiceCommand.UpgraderClear c -> "upgraderclear";
            case VoiceCommand.UpgraderSkip c -> "upgraderskip";
        };
        return LanguageManager.translate("randomrun.voice.cmd." + name);
    }

    // ------------------------------------------------------------- render

    @Override
    protected void renderWidgetContent(GuiGraphicsExtractor g, int x, int y, float fade) {
        int cx = width / 2;
        int textWidth = widgetWidth() - 40;
        switch (step) {
            case LANGUAGE -> {
                lines(g, LanguageManager.translate("randomrun.voice.setup.intro"), cx, y + 34, textWidth, Ui.argb(0xD4D4D8, fade));
                if (!VoicePack.isPlatformSupported()) {
                    GuiText.centeredText(g, font, LanguageManager.translate("randomrun.voice.setup.unsupported"), cx, y + 78, Ui.argb(0xFF6B6B, fade));
                } else {
                    GuiText.centeredText(g, font, LanguageManager.translate("randomrun.voice.setup.pick_language"), cx, y + 78, Ui.argb(0xA1A1AA, fade));
                }
            }
            case DOWNLOAD -> renderDownload(g, cx, y, fade);
            case MIC -> renderMic(g, cx, y, fade, textWidth);
        }
    }

    private void renderDownload(GuiGraphicsExtractor g, int cx, int y, float fade) {
        Theme theme = Theme.current();
        if (downloadError != null) {
            GuiText.centeredText(g, font, LanguageManager.translate("randomrun.voice.setup.error." + downloadError), cx, y + 80, Ui.argb(0xFF6B6B, fade));
            return;
        }
        String phaseText = LanguageManager.translate("randomrun.voice.setup.phase." + phase.name().toLowerCase(java.util.Locale.ROOT));
        GuiText.centeredText(g, font, phaseText, cx, y + 60, Ui.argb(0xFFFFFF, fade));
        int barW = Math.min(280, widgetWidth() - 60), barX = cx - barW / 2, barY = y + 80;
        float progress = phase == VoicePack.Phase.EXTRACT ? 1f : Mth.clamp(done / (float) total, 0f, 1f);
        g.fill(barX, barY, barX + barW, barY + 10, Ui.argb(0x000000, 0.5f * fade));
        int filled = Math.round((barW - 2) * progress);
        for (int i = 0; i < filled; i++) {
            float hue = ((System.currentTimeMillis() % 3000L) / 3000f + i / (float) barW * 0.6f) % 1f;
            g.fill(barX + 1 + i, barY + 1, barX + 2 + i, barY + 9, 0xFF000000 | Mth.hsvToRgb(hue, 0.55f, 1f));
        }
        Ui.border(g, barX, barY, barX + barW, barY + 10, 1, Ui.argb(theme.border, fade));
        String amount = phase == VoicePack.Phase.EXTRACT ? LanguageManager.translate("randomrun.voice.setup.unpacking")
            : String.format(java.util.Locale.ROOT, "%.1f / %.1f MB", done / 1_048_576.0, total / 1_048_576.0);
        GuiText.centeredText(g, font, amount, cx, barY + 16, Ui.argb(0xA1A1AA, fade));
        GuiText.centeredText(g, font, LanguageManager.translate("randomrun.voice.setup.offline_note"), cx, barY + 36, Ui.argb(0x71717A, fade));
    }

    private void renderMic(GuiGraphicsExtractor g, int cx, int y, float fade, int textWidth) {
        VoiceEngine engine = VoiceEngine.get();
        long now = System.currentTimeMillis();
        int top = y + 84;
        int meterW = Math.min(220, widgetWidth() - 80), meterX = cx - meterW / 2 + 10;
        boolean hearing = engine.state() == VoiceEngine.State.LISTENING && engine.isHearingVoice();
        int micColor = engine.state() == VoiceEngine.State.ERROR ? 0xFFFF6B6B : hearing ? 0xFF7CF29A : 0xFFD4D4D8;
        Icons.MIC.draw(g, meterX - 16, top - 1, micColor);
        int bars = 24;
        for (int i = 0; i < bars; i++) {
            float t = (i + 0.5f) / bars;
            int x0 = meterX + i * meterW / bars, x1 = meterX + (i + 1) * meterW / bars - 1;
            int color = t < 0.6f ? 0x4ADE80 : t < 0.85f ? 0xFACC15 : 0xF87171;
            g.fill(x0, top, x1, top + 9, Ui.argb(color, (t <= engine.level() ? 1f : 0.13f) * fade));
        }
        String state = switch (engine.state()) {
            case LOADING -> LanguageManager.translate("randomrun.voice.hud.loading");
            case ERROR -> LanguageManager.translate("randomrun.voice.hud.error." + engine.error());
            case LISTENING -> LanguageManager.translate("randomrun.voice.setup.say_something");
            case OFF -> "";
        };
        GuiText.centeredText(g, font, state, cx, top + 16, Ui.argb(micColor & 0xFFFFFF, fade));

        String shown = heard.isEmpty() ? "…" : "« " + heard + " »";
        GuiText.centeredText(g, font, Ui.ellipsize(font, shown, textWidth), cx, top + 32, Ui.argb(heard.isEmpty() ? 0x71717A : 0xFFFFFF, fade));
        if (!understood.isEmpty()) {
            float pop = Ui.easeOut(((now / 10) % 100) / 100f);
            GuiText.centeredText(g, font, "✔ " + Ui.ellipsize(font, understood, textWidth - 20), cx, top + 46,
                Ui.argb(0x7CF29A, fade * (0.85f + 0.15f * pop)));
        }
        int examplesY = top + 60;
        int room = (getWidgetY() + widgetHeight() - 4 - examplesY) / 10;
        var exampleLines = font.split(Component.literal(LanguageManager.translate("randomrun.voice.setup.examples")), textWidth);
        for (int i = 0; i < Math.min(room, exampleLines.size()); i++) {
            GuiText.centeredText(g, font, exampleLines.get(i), cx, examplesY + i * 10, Ui.argb(0x8A8A93, fade));
        }
        if (success) Ui.glowBorder(g, getWidgetX(), getWidgetY(), getWidgetX() + widgetWidth(), getWidgetY() + widgetHeight(), 2, 0x7CF29A);
    }

    private void lines(GuiGraphicsExtractor g, String text, int cx, int y, int maxWidth, int color) {
        for (var line : font.split(Component.literal(text), maxWidth)) {
            GuiText.centeredText(g, font, line, cx, y, color);
            y += 10;
        }
    }

    // ------------------------------------------------------------- closing

    private void finish(boolean completed) {
        if (finished) return;
        finished = true;
        stopEngine();
        if (onFinish != null) onFinish.accept(completed);
        startClosing();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (step == Step.DOWNLOAD && downloadError == null) VoicePack.cancel();
        finish(false);
    }
}

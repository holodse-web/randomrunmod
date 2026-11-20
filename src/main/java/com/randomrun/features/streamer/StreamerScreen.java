package com.randomrun.features.streamer;

import com.randomrun.ui.util.InterfaceStyle;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.settings.AnimatedSettingsScreen;
import com.randomrun.ui.util.GuiQuadBatch;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.util.UiScale;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class StreamerScreen extends AnimatedSettingsScreen {
    private static final int LIST_SLOTS = 4;
    private static final int ROW_HEIGHT = 24;
    private static int accent() { return InterfaceStyle.isClassic() ? 0xFFD56B : 0xC084FC; }

    private final Consumer<StreamerCharacter> onPicked;
    private final boolean startsRun;
    private final List<StreamerCharacter> streamers = List.of(StreamerCharacter.values());
    private final long openedAt = System.currentTimeMillis();
    private int selected;
    private int hovered = -1;
    private long selectedAt;
    private PlayerModel previewModel;
    private float rotation = 200f;
    private boolean dragging;
    private Roulette roulette;
    private ButtonDefault randomButton;
    private ButtonDefault noneButton;
    private ButtonDefault listenButton;
    private ButtonRainbow playButton;
    private String quote;
    private long quoteAt;
    private Runnable roomAction;

    public static StreamerScreen beforeRun(Screen parent, Runnable start) {
        return new StreamerScreen(parent, character -> start.run(), true);
    }

    public static StreamerScreen forRoom(Screen parent) {
        return new StreamerScreen(parent, null, false, LanguageManager.text("randomrun.streamer.title"));
    }

    public static void askForRoom(Screen parent, com.randomrun.features.battle.manager.BattleRoom room, Runnable proceed) {
        if (room != null && room.hasStreamerMode()) {
            Minecraft.getInstance().gui.setScreen(beforeRoom(parent, proceed));
            return;
        }
        if (StreamerProfile.get().isEnabled()) StreamerProfile.get().setEnabled(false);
        proceed.run();
    }

    public static void lookupRoom(String code, Consumer<com.randomrun.features.battle.manager.BattleRoom> found,
                                  Consumer<com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult> failed) {
        com.randomrun.network.HttpClient.getInstance().getNoCache("/rooms/" + code).whenComplete((value, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null || value == null || !value.isJsonObject()) {
                Throwable cause = error;
                while (cause != null && cause.getCause() != null) cause = cause.getCause();
                boolean missing = cause == null || "ROOM_NOT_FOUND".equals(cause.getMessage());
                failed.accept(missing ? com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult.ROOM_NOT_FOUND
                    : com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult.CONNECTION_FAILED);
                return;
            }
            found.accept(new com.google.gson.Gson().fromJson(value, com.randomrun.features.battle.manager.BattleRoom.class));
        }));
    }

    public static StreamerScreen beforeRoom(Screen parent, Runnable proceed) {
        StreamerScreen screen = new StreamerScreen(parent, null, false, LanguageManager.text("randomrun.streamer.ask.title"));
        screen.roomAction = proceed;
        return screen;
    }

    private StreamerScreen(Screen parent, Consumer<StreamerCharacter> onPicked, boolean startsRun) {
        this(parent, onPicked, startsRun, LanguageManager.text("randomrun.streamer.title"));
    }

    private StreamerScreen(Screen parent, Consumer<StreamerCharacter> onPicked, boolean startsRun, Component title) {
        super(parent, false, title);
        this.onPicked = onPicked;
        this.startsRun = startsRun;
        this.selected = Math.max(0, streamers.indexOf(StreamerProfile.get().character()));
    }

    @Override
    protected int widgetWidth() {
        return Math.min(width - 20, 540);
    }

    @Override
    protected int widgetHeight() {
        return Math.min(height - 50, 272);
    }

    private int contentTop() {
        return getWidgetY() + 32;
    }

    private int contentBottom() {
        return getWidgetY() + widgetHeight() - 34;
    }

    private int showLeft() {
        return getWidgetX() + 12;
    }

    private int showRight() {
        return showLeft() + Math.round((widgetWidth() - 24) * 0.46f);
    }

    private int listLeft() {
        return showRight() + 10;
    }

    private int listRight() {
        return getWidgetX() + widgetWidth() - 12;
    }

    private int rowTop(int index) {
        return contentTop() + 13 + index * (ROW_HEIGHT + 2);
    }

    private int infoTop() {
        return rowTop(LIST_SLOTS) + 4;
    }

    @Override
    protected void initContent() {
        int buttonY = getWidgetY() + widgetHeight() - 28;
        int center = width / 2;
        int playX = roomAction != null ? center + 64 : center + 4;
        listenButton = addRenderableWidget(new ButtonDefault(playX, contentBottom() - 22, 120, 20,
            LanguageManager.text("randomrun.streamer.pick.listen"), b -> listen(), 0, 0.08f));
        if (roomAction != null) {
            randomButton = addRenderableWidget(new ButtonDefault(center - 184, buttonY, 120, 20,
                LanguageManager.text("randomrun.streamer.pick.random"), b -> spin(), 1, 0.1f));
            noneButton = addRenderableWidget(new ButtonDefault(center - 60, buttonY, 120, 20,
                LanguageManager.text("randomrun.streamer.pick.none"), b -> playAsSelf(), 2, 0.11f));
            playButton = addRenderableWidget(new ButtonRainbow(center + 64, buttonY, 120, 20,
                LanguageManager.text("randomrun.streamer.pick.play"), b -> confirm(), 3, 0.12f));
        } else {
            randomButton = addRenderableWidget(new ButtonDefault(center - 124, buttonY, 120, 20,
                LanguageManager.text("randomrun.streamer.pick.random"), b -> spin(), 1, 0.1f));
            playButton = addRenderableWidget(new ButtonRainbow(center + 4, buttonY, 120, 20,
                LanguageManager.text(startsRun ? "randomrun.streamer.pick.play" : "randomrun.streamer.pick.choose"), b -> confirm(), 2, 0.12f));
        }
        playButton.setAnimationDirection(ButtonRainbow.AnimationDirection.HORIZONTAL_LEFT);
        addBackButton(4);
    }

    private void confirm() {
        if (roulette != null) return;
        StreamerCharacter character = streamers.get(selected);
        StreamerProfile.get().choose(character);
        StreamerVoice.stopPreview();
        Ui.playSound(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.5f);
        if (roomAction != null) {
            continueToRoom();
        } else if (onPicked != null) {
            onPicked.accept(character);
        } else {
            startClosing();
        }
    }

    private void playAsSelf() {
        if (roulette != null) return;
        if (StreamerProfile.get().isEnabled()) StreamerProfile.get().setEnabled(false);
        StreamerVoice.stopPreview();
        Ui.click();
        continueToRoom();
    }

    private void continueToRoom() {
        Runnable action = roomAction;
        roomAction = null;
        Minecraft.getInstance().gui.setScreen(parent);
        action.run();
    }

    private void listen() {
        if (roulette != null) return;
        StreamerCharacter character = streamers.get(selected);
        List<StreamerCharacter.Phrase> pool = new ArrayList<>(character.phrases(StreamerCharacter.Event.START));
        if (pool.isEmpty()) for (StreamerCharacter.Event event : StreamerCharacter.Event.values()) pool.addAll(character.phrases(event));
        if (pool.isEmpty()) return;
        StreamerCharacter.Phrase phrase = pool.get(new Random().nextInt(pool.size()));
        quote = phrase.text();
        quoteAt = System.currentTimeMillis();
        StreamerVoice.preview(phrase.sound());
    }

    private void spin() {
        if (roulette != null) return;
        StreamerVoice.stopPreview();
        roulette = new Roulette(streamers, new Random().nextInt(streamers.size()));
    }

    private void select(int index) {
        if (index == selected || index < 0 || index >= streamers.size()) return;
        selected = index;
        selectedAt = System.currentTimeMillis();
        quote = null;
        Ui.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.35f);
    }

    @Override
    public void tick() {
        super.tick();
        if (roulette != null && roulette.isComplete()) {
            select(roulette.result);
            roulette = null;
        }
        boolean busy = roulette != null;
        if (playButton != null) playButton.active = !busy;
        if (randomButton != null) randomButton.active = !busy;
        if (noneButton != null) noneButton.active = !busy;
        if (listenButton != null) listenButton.active = !busy;
    }

    @Override
    public void removed() {
        StreamerVoice.stopPreview();
        super.removed();
    }

    @Override
    protected void renderWidgetContent(GuiGraphicsExtractor context, int widgetX, int widgetY, float fade) {
        float appear = Ui.easeOut(Math.min(1f, (System.currentTimeMillis() - openedAt) / 360f));
        drawShowcase(context, fade * appear);
        drawList(context, fade * appear);
        drawInfo(context, fade * appear);
        if (roulette != null) {
            int top = contentTop(), bottom = contentBottom();
            if (InterfaceStyle.isClassic()) {
                Ui.bevel(context, widgetX + 4, top - 4, widgetX + widgetWidth() - 4, bottom + 4, 0x30302B, fade, true);
            } else {
                context.fill(widgetX + 4, top - 4, widgetX + widgetWidth() - 4, bottom + 4, Ui.argb(0x000000, 0.8f * fade));
            }
            roulette.draw(context, width / 2, (top + bottom) / 2, widgetWidth() - 40, fade);
        }
    }

    private void drawShowcase(GuiGraphicsExtractor context, float fade) {
        StreamerCharacter character = streamers.get(selected);
        int left = showLeft(), right = showRight(), top = contentTop(), bottom = contentBottom();
        int nameTop = bottom - 34;
        if (InterfaceStyle.isClassic()) {
            Ui.slot(context, left, top, right, bottom, fade);
        } else {
            context.fill(left, top, right, bottom, Ui.argb(0x000000, 0.42f * fade));
            int glowTop = top + (nameTop - top) / 3;
            context.fillGradient(left + 1, glowTop, right - 1, nameTop, Ui.argb(accent(), 0f), Ui.argb(accent(), 0.12f * fade));
            Ui.border(context, left, top, right, bottom, 1, Ui.argb(accent(), 0.35f * fade));
        }
        context.enableScissor(left + 3, top + 3, right - 3, nameTop - 2);
        if (!dragging) rotation += 0.6f;
        renderFigure(context, character.skin().body().texturePath(), character.look().body(), (left + right) / 2, top + 8, nameTop - 6, rotation, true);
        context.disableScissor();
        float pop = selectedAt == 0 ? 1f : Ui.easeOut((System.currentTimeMillis() - selectedAt) / 320f);
        Ui.centeredScaled(context, font, Component.literal(Ui.ellipsize(font, character.nickname(), Math.max(1, (right - left - 16) * 2 / 3))),
            (left + right) / 2f, nameTop + 4 + Math.round((1f - pop) * 6f), 1.6f, Ui.argb(accent(), fade * pop));
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.streamer.pick.drag"), (left + right) / 2, bottom - 11,
            Ui.argb(InterfaceStyle.isClassic() ? 0x9A978D : 0x71717A, fade));
    }

    private void drawList(GuiGraphicsExtractor context, float fade) {
        int left = listLeft(), right = listRight();
        GuiText.text(context, font, LanguageManager.text("randomrun.streamer.pick.list"), left + 2, contentTop() + 1,
            Ui.argb(InterfaceStyle.isClassic() ? 0xC0BDB2 : 0xA1A1AA, fade), false);
        for (int i = 0; i < LIST_SLOTS; i++) {
            int top = rowTop(i), bottom = top + ROW_HEIGHT;
            float rowFade = fade * Ui.easeOut(Math.min(1f, Math.max(0f, (System.currentTimeMillis() - openedAt - i * 60L) / 300f)));
            int shift = Math.round((1f - rowFade / Math.max(0.001f, fade)) * 10f);
            if (i >= streamers.size()) {
                drawSoonRow(context, left + shift, top, right + shift, bottom, rowFade);
                continue;
            }
            StreamerCharacter character = streamers.get(i);
            boolean isSelected = i == selected, isHovered = i == hovered;
            if (InterfaceStyle.isClassic()) {
                Ui.bevel(context, left + shift, top, right + shift, bottom, isSelected ? 0x5A5240 : isHovered ? 0x45453F : 0x383833, rowFade, !isSelected);
            } else {
                context.fill(left + shift, top, right + shift, bottom, Ui.argb(isSelected ? accent() : 0xFFFFFF, (isSelected ? 0.16f : isHovered ? 0.07f : 0.03f) * rowFade));
                if (isSelected) {
                    Ui.border(context, left + shift, top, right + shift, bottom, 1, Ui.argb(accent(), 0.55f * rowFade));
                } else if (isHovered) {
                    Ui.border(context, left + shift, top, right + shift, bottom, 1, Ui.argb(0xFFFFFF, 0.25f * rowFade));
                }
            }
            int face = ROW_HEIGHT - 8;
            Identifier texture = character.skin().body().texturePath();
            context.blit(RenderPipelines.GUI_TEXTURED, texture, left + shift + 6, top + 4, 64f, 64f, face, face, 64, 64, 512, 512);
            context.blit(RenderPipelines.GUI_TEXTURED, texture, left + shift + 6, top + 4, 320f, 64f, face, face, 64, 64, 512, 512);
            int textX = left + shift + 6 + face + 6;
            int textTop = top + (ROW_HEIGHT - 18) / 2;
            GuiText.text(context, font, Ui.ellipsize(font, character.nickname(), right - textX - 70), textX, textTop,
                Ui.argb(isSelected ? accent() : 0xFFFFFF, rowFade), false);
            GuiText.text(context, font, LanguageManager.translate("randomrun.streamer.pick.phrases", character.phraseCount()).replaceAll("§.", ""), textX, textTop + 10,
                Ui.argb(InterfaceStyle.isClassic() ? 0xC0BDB2 : 0x8A8A93, rowFade), false);
            if (isSelected) {
                Component chip = LanguageManager.text("randomrun.streamer.pick.selected");
                int chipWidth = font.width(chip) + 8;
                Ui.chip(context, font, chip, right + shift - chipWidth - 5, top + (ROW_HEIGHT - 13) / 2,
                    Ui.argb(InterfaceStyle.isClassic() ? 0x5A4A20 : 0x3B0764, 0.9f * rowFade), Ui.argb(accent(), rowFade));
            }
        }
    }

    private void drawSoonRow(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float fade) {
        if (InterfaceStyle.isClassic()) {
            Ui.bevel(context, left, top, right, bottom, 0x2E2E2A, fade * 0.8f, true);
        } else {
            context.fill(left, top, right, bottom, Ui.argb(0x000000, 0.25f * fade));
            Ui.border(context, left, top, right, bottom, 1, Ui.argb(0xFFFFFF, 0.04f * fade));
        }
        int face = ROW_HEIGHT - 8;
        context.fill(left + 6, top + 4, left + 6 + face, top + 4 + face, Ui.argb(0x1C1C21, fade));
        Ui.mystery(context, font, left + 6 + face / 2, top + 4 + face / 2, 1.2f, InterfaceStyle.isClassic() ? 0x9A978D : 0x52525B);
        GuiText.text(context, font, LanguageManager.text("randomrun.streamer.pick.soon"), left + 6 + face + 6, top + (ROW_HEIGHT - 8) / 2,
            Ui.argb(InterfaceStyle.isClassic() ? 0x9A978D : 0x52525B, fade), false);
    }

    private void drawInfo(GuiGraphicsExtractor context, float fade) {
        StreamerCharacter character = streamers.get(selected);
        int left = listLeft(), right = listRight(), top = infoTop(), bottom = contentBottom() - 26;
        if (bottom - top < 30) return;
        Ui.card(context, left, top, right, bottom, accent(), fade);
        int muted = InterfaceStyle.isClassic() ? 0xC0BDB2 : 0xA1A1AA;
        int x = left + 7, y = top + 6;
        int chipX = x;
        chipX += Ui.chip(context, font, Component.literal(LanguageManager.translate("randomrun.streamer.pick.moments", character.momentCount())), chipX, y,
            Ui.argb(0x000000, 0.45f * fade), Ui.argb(0xFFFFFF, fade)) + 4;
        Ui.chip(context, font, Component.literal("§a✔ §f" + LanguageManager.translate("randomrun.streamer.pick.minecraft")), chipX, y,
            Ui.argb(0x000000, 0.45f * fade), Ui.argb(0xFFFFFF, fade));
        y += 17;
        GuiText.text(context, font, "§6⚠ §7" + LanguageManager.translate("randomrun.streamer.pick.swearing"), x, y, Ui.argb(0xFFFFFF, fade), false);
        y += 13;
        if (quote != null && y + 10 <= bottom - 3) {
            float quoteFade = fade * Ui.easeOut(Math.min(1f, (System.currentTimeMillis() - quoteAt) / 260f));
            context.fill(x, y - 1, x + 2, Math.min(bottom - 3, y + 19), Ui.argb(accent(), quoteFade));
            int lineY = y;
            for (var line : font.split(Component.literal("«" + quote + "»"), right - x - 14)) {
                if (lineY + 9 > bottom - 3) break;
                GuiText.text(context, font, line, x + 6, lineY, Ui.argb(muted, quoteFade), false);
                lineY += 10;
            }
        }
    }



    private void renderFigure(GuiGraphicsExtractor context, Identifier texture, StreamerCharacter.Look.Body body,
                              int centerX, int top, int bottom, float turn, boolean active) {
        if (previewModel == null) {
            try {
                previewModel = new PlayerModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            } catch (Throwable e) {
                return;
            }
        }
        float time = (System.currentTimeMillis() - openedAt) / 1000f;
        PlayerModel model = previewModel;
        model.resetPose();
        model.allParts().forEach(part -> part.visible = true);
        float swing = active ? (float) Math.cos(time * 4.2f) * 0.45f : 0f;
        model.leftArm.xRot = swing;
        model.rightArm.xRot = -swing;
        model.leftLeg.xRot = -swing * 0.9f;
        model.rightLeg.xRot = swing * 0.9f;
        model.head.xRot = (float) Math.sin(time * 1.3f) * 0.06f;
        StreamerSkin.shape(model, body);

        float f = UiScale.active() ? UiScale.factor() : 1f;
        int half = (bottom - top) / 2;
        float scale = 0.85f * (bottom - top) / 2.125f;
        context.skin(new Model.Simple(model.root(), RenderTypes::entityTranslucent),
            texture, scale * f, 0, -turn, -1.0625f,
            Math.round((centerX - half) * f), Math.round(top * f), Math.round((centerX + half) * f), Math.round(bottom * f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        hovered = -1;
        if (roulette == null && mouseX >= listLeft() && mouseX < listRight()) {
            for (int i = 0; i < streamers.size() && i < LIST_SLOTS; i++) {
                if (mouseY >= rowTop(i) && mouseY < rowTop(i) + ROW_HEIGHT) hovered = i;
            }
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (roulette == null && hovered >= 0 && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            if (doubleClick && hovered == selected) confirm();
            else select(hovered);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (roulette == null && event.x() >= showLeft() && event.x() < showRight() && event.y() >= contentTop() && event.y() < contentBottom()) {
            dragging = true;
            rotation -= (float) dx * 2f;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    private static final class Roulette {
        private static final int REEL = 46;
        private static final int RESULT_INDEX = 38;
        private static final long SPIN_MS = 3800;
        private static final long SETTLE_MS = 350;
        private static final long HOLD_MS = 1100;

        private final List<Integer> reel = new ArrayList<>();
        private final List<StreamerCharacter> streamers;
        private final int result;
        private final float jitter;
        private final long start = System.nanoTime();
        private final boolean animated = RandomRunMod.getInstance().getConfig().isAnimationsEnabled();
        private int lastTick = -1;
        private boolean finished;

        Roulette(List<StreamerCharacter> streamers, int result) {
            this.streamers = streamers;
            this.result = result;
            Random random = new Random();

            for (int i = 0; i < REEL; i++) {
                if (i == RESULT_INDEX) reel.add(result);
                else reel.add(random.nextInt(3) == 0 ? -1 : random.nextInt(streamers.size()));
            }
            jitter = (random.nextFloat() - 0.5f) * 0.6f;
        }

        long elapsed() {
            return (System.nanoTime() - start) / 1_000_000L;
        }

        boolean isComplete() {
            return elapsed() >= (animated ? SPIN_MS : 0) + SETTLE_MS + HOLD_MS;
        }

        boolean spinning() {
            return elapsed() < (animated ? SPIN_MS : 0);
        }

        float offset(int pitch) {
            float time = (System.nanoTime() - start) / 1_000_000f;
            long spin = animated ? SPIN_MS : 0;
            float target = RESULT_INDEX * pitch;
            if (time < spin) {
                float t = time / spin;
                float eased = 1f - (float) Math.pow(1f - t, 4.0);
                return eased * (target + jitter * pitch);
            }
            float settle = Math.min(1f, (time - spin) / SETTLE_MS);
            return target + jitter * pitch * (1f - Ui.easeInOut(settle));
        }

        void draw(GuiGraphicsExtractor g, int centerX, int centerY, int viewport, float fade) {
            Minecraft mc = Minecraft.getInstance();
            int slot = 56;
            int pitch = slot + 8;
            int half = viewport / 2;
            int left = centerX - half, right = centerX + half;
            int top = centerY - slot / 2 - 10, bottom = centerY + slot / 2 + 10;
            float offset = offset(pitch);

            if (InterfaceStyle.isClassic()) {
                Ui.bevel(g, left - 2, top - 2, right + 2, bottom + 2, 0x30302B, fade, true);
            } else {
                g.fill(left - 2, top - 2, right + 2, bottom + 2, Ui.argb(Theme.current().border, 0.9f * fade));
                g.fill(left, top, right, bottom, 0xF0080808);
            }

            int center = Math.round(offset / pitch);
            if (center != lastTick && spinning()) {
                lastTick = center;
                float progress = Math.min(1f, elapsed() / (float) SPIN_MS);
                Ui.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f + progress * 0.5f, 0.2f);
            }
            if (!finished && !spinning()) {
                finished = true;
                Ui.playSound(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.6f);
                GlobalParticleSystem.getInstance().addFastExplosion(centerX, centerY, 40, accent());
            }

            g.enableScissor(left, top, right, bottom);
            g.pose().pushMatrix();
            int whole = (int) Math.floor(offset);
            g.pose().translate(-(offset - whole), 0);
            int first = Math.max(0, (int) Math.floor((offset - half) / pitch) - 1);
            int last = Math.min(reel.size() - 1, (int) Math.ceil((offset + half) / pitch) + 1);
            for (int i = first; i <= last; i++) {
                int x = centerX + i * pitch - whole - slot / 2;
                int y = centerY - slot / 2;
                int entry = reel.get(i);
                boolean winner = finished && i == RESULT_INDEX;
                if (InterfaceStyle.isClassic()) {
                    Ui.bevel(g, x, y, x + slot, y + slot, winner ? 0x73603A : 0x59594F, 1f, true);
                } else {
                    g.fill(x, y, x + slot, y + slot, winner ? Ui.argb(accent(), 0.3f) : 0xFF141418);
                }
                if (entry < 0) {
                    g.fill(x + 6, y + 6, x + slot - 6, y + slot - 6, (InterfaceStyle.isClassic() ? 0xFF383832 : 0xFF1F1F23));
                    g.fill(x, y + slot - 3, x + slot, y + slot, (InterfaceStyle.isClassic() ? 0xFF66665D : 0xFF3F3F46));
                } else {
                    Identifier texture = streamers.get(entry).skin().body().texturePath();
                    int face = slot - 12;

                    g.blit(RenderPipelines.GUI_TEXTURED, texture, x + 6, y + 6, 64f, 64f, face, face, 64, 64, 512, 512);
                    g.blit(RenderPipelines.GUI_TEXTURED, texture, x + 6, y + 6, 320f, 64f, face, face, 64, 64, 512, 512);
                    g.fill(x, y + slot - 3, x + slot, y + slot, Ui.argb(accent(), winner ? 1f : 0.85f));
                }
                g.fill(x, y, x + slot, y + 1, 0x18FFFFFF);
            }
            g.pose().popMatrix();
            g.disableScissor();
            g.nextStratum();

            if (InterfaceStyle.isClassic()) {
                g.fill(left, top, left + 3, bottom, 0xFF24241F);
                g.fill(right - 3, top, right, bottom, 0xFF8A8A7C);
            } else {
                int fadeWidth = Math.min(50, half / 3);
                GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(fadeWidth * 2);
                for (int i = 0; i < fadeWidth; i++) {
                    int color = Ui.argb(0x080808, 0.94f * (1f - i / (float) fadeWidth));
                    batch.fill(left + i, top, left + i + 1, bottom, color);
                    batch.fill(right - i - 1, top, right - i, bottom, color);
                }
                batch.build().extract(g);
            }

            int marker = finished ? (InterfaceStyle.isClassic() ? 0xFFFFD56B : 0xFFFFD700) : 0xFF000000 | accent();
            g.fill(centerX - 1, top - 4, centerX + 1, bottom + 4, marker);
            g.fill(centerX - 5, top - 7, centerX + 5, top - 4, marker);
            g.fill(centerX - 5, bottom + 4, centerX + 5, bottom + 7, marker);
            if (finished) {
                float glow = 0.5f + 0.5f * (float) Math.sin(elapsed() / 120.0);
                Ui.border(g, centerX - slot / 2 - 2, centerY - slot / 2 - 2, centerX + slot / 2 + 2, centerY + slot / 2 + 2, 2,
                    Ui.argb((InterfaceStyle.isClassic() ? 0xFFD56B : 0xFFD700), 0.6f + 0.4f * glow));
                GuiText.centeredText(g, mc.font, LanguageManager.translate("randomrun.streamer.pick.picked", streamers.get(result).nickname()),
                    centerX, bottom + 12, (InterfaceStyle.isClassic() ? 0xFFFFD56B : 0xFFFFD700));
            }
        }
    }
}

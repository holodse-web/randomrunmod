package com.randomrun.challenges.time.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.classic.data.ItemDifficulty;
import com.randomrun.ui.widget.StyledButton2;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import com.randomrun.challenges.classic.screen.ItemRevealScreen;
import com.randomrun.challenges.classic.screen.SeedInputScreen;
import com.randomrun.challenges.classic.screen.SpeedrunScreen;
import com.randomrun.challenges.advancement.data.AdvancementLoader;
import com.randomrun.challenges.advancement.screen.AchievementRevealScreen;
import com.randomrun.ui.screen.MainModScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

import java.util.Random;

import com.randomrun.challenges.advancement.data.AchievementDifficulty;

public class TimeSelectionScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final Item targetItem;
    private final AdvancementLoader.AdvancementInfo targetAdvancement;
    private final ItemDifficulty.Difficulty itemDifficulty;
    private final AchievementDifficulty.Difficulty advDifficulty;
    private final boolean isAdvancement;
    
    // 3D item animation
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private long openTime;
    
    // Slot machine for time
    private boolean slotMachineActive = false;
    private long slotMachineStartTime;
    private int currentDisplayTime = 0;
    private int selectedTime = 0;
    private boolean buttonsRefreshed = false;
    private boolean soundPlayed = false;
    private long lastTickTime = 0;
    private static final long SLOT_MACHINE_DURATION = 2000; // Ускорено до 2 секунд
    
    public TimeSelectionScreen(Screen parent, Item item) {
        super(Text.translatable("randomrun.screen.time_selection.title"));
        this.parent = parent;
        this.targetItem = item;
        this.targetAdvancement = null;
        this.itemDifficulty = ItemDifficulty.getDifficulty(item);
        this.advDifficulty = null;
        this.isAdvancement = false;
    }
    
    public TimeSelectionScreen(Screen parent, AdvancementLoader.AdvancementInfo advancement) {
        super(Text.translatable("randomrun.screen.time_selection.title"));
        this.parent = parent;
        this.targetItem = null;
        this.targetAdvancement = advancement;
        this.itemDifficulty = null;
        this.advDifficulty = AchievementDifficulty.getDifficulty(advancement.id);
        this.isAdvancement = true;
    }
    
    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        
        int centerX = width / 2;
        int buttonY = height - 55;
        
        // Всегда показываем кнопки в зависимости от состояния
        long elapsed = slotMachineActive ? System.currentTimeMillis() - slotMachineStartTime : SLOT_MACHINE_DURATION;
        
        if (slotMachineActive && elapsed < SLOT_MACHINE_DURATION) {
            // Во время анимации - только кнопка назад
            addDrawableChild(new StyledButton2(
                centerX - 100, buttonY,
                200, 20,
                Text.translatable("randomrun.button.back"),
                button -> {
                    if (isAdvancement) {
                        MinecraftClient.getInstance().setScreen(new com.randomrun.challenges.advancement.screen.AchievementSelectionScreen(new MainModScreen(null)));
                    } else {
                        MinecraftClient.getInstance().setScreen(new SpeedrunScreen(new MainModScreen(null)));
                    }
                }
            ));
        } else if (!slotMachineActive || elapsed >= SLOT_MACHINE_DURATION) {
            // До анимации или после - показываем соответствующие кнопки
            if (selectedTime == 0) {
                // Random time button (before slot machine)
                addDrawableChild(new StyledButton2(
                    centerX - 100, buttonY,
                    200, 20,
                    Text.translatable("randomrun.button.random_time"),
                    button -> startTimeSlotMachine()
                ));
            } else {
                // Start speedrun button (after slot machine)
                addDrawableChild(new StyledButton2(
                    centerX - 100, buttonY,
                    200, 20,
                    Text.translatable("randomrun.button.start_speedrun"),
                    button -> {
                        boolean askForSeed = RandomRunMod.getInstance().getConfig().isAskForCustomSeed();
                        
                        if (isAdvancement) {
                            if (askForSeed) {
                                MinecraftClient.getInstance().setScreen(new SeedInputScreen(parent, targetAdvancement, selectedTime * 1000L));
                            } else {
                                startAdvancementSpeedrunWithTime(selectedTime);
                            }
                        } else {
                            if (askForSeed) {
                                MinecraftClient.getInstance().setScreen(new SeedInputScreen(parent, targetItem, selectedTime * 1000L));
                            } else {
                                startSpeedrunWithTime(selectedTime);
                            }
                        }
                    }
                ));
            }
            
            // Back button
            addDrawableChild(new StyledButton2(
                centerX - 100, height - 30,
                200, 20,
                Text.translatable("randomrun.button.back"),
                button -> {
                    if (isAdvancement) {
                        MinecraftClient.getInstance().setScreen(new com.randomrun.challenges.advancement.screen.AchievementSelectionScreen(new MainModScreen(null)));
                    } else {
                        MinecraftClient.getInstance().setScreen(new SpeedrunScreen(new MainModScreen(null)));
                    }
                }
            ));
        }
    }
    
    private void startTimeSlotMachine() {
        slotMachineActive = true;
        slotMachineStartTime = System.currentTimeMillis();
        lastTickTime = 0;
        soundPlayed = false;
        buttonsRefreshed = false;
        
        // Generate random time based on difficulty
        if (isAdvancement) {
            boolean useDifficulty = RandomRunMod.getInstance().getConfig().isUseAchievementDifficulty();
            if (useDifficulty && advDifficulty != null) {
                selectedTime = advDifficulty.getRandomTime();
            } else {
                selectedTime = 30 + new Random().nextInt(91); // 30-120 seconds
            }
        } else {
            boolean useDifficulty = RandomRunMod.getInstance().getConfig().isUseItemDifficulty();
            if (useDifficulty && itemDifficulty != null) {
                selectedTime = itemDifficulty.getRandomTime();
            } else {
                selectedTime = 30 + new Random().nextInt(91); // 30-120 seconds
            }
        }
    }
    
    public void startSpeedrunWithTime(int timeSeconds) {
        startSpeedrunWithTime(timeSeconds, null);
    }

    public void startSpeedrunWithTime(int timeSeconds, String seed) {
        if (parent instanceof ItemRevealScreen itemRevealScreen) {
            itemRevealScreen.startSpeedrunWithSeed(timeSeconds * 1000L, seed);
        }
    }
    
    public void startAdvancementSpeedrunWithTime(int timeSeconds) {
        startAdvancementSpeedrunWithTime(timeSeconds, null);
    }

    public void startAdvancementSpeedrunWithTime(int timeSeconds, String seed) {
        if (parent instanceof AchievementRevealScreen achievementRevealScreen) {
            achievementRevealScreen.startSpeedrunWithSeed(timeSeconds * 1000L, seed);
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Call parent render (handles fade automatically)
        super.render(context, mouseX, mouseY, delta);
        
        RenderSystem.enableBlend();
        
        int centerX = width / 2;
        int centerY = height / 2 + 50; // Опустили меню еще ниже (было +30)

        // Render 3D item (outside the box, higher and larger)
        render3DItem(context, centerX, centerY - 150, delta);
        
        // Render time selection box (like slot machine in SpeedrunScreen)
        renderTimeSelectionBox(context);
        
        // Render item/achievement name below 3D item
        String displayName = isAdvancement ? targetAdvancement.title.getString() : targetItem.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer, displayName, centerX, centerY - 80, 0xFFFFFF);

        RenderSystem.disableBlend();
    }
    
    private void render3DItem(DrawContext context, int x, int y, float delta) {
        // Update animations
        long elapsed = System.currentTimeMillis() - openTime;
        rotationY += delta * 2f;
        levitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
        
        ItemStack stack = isAdvancement ? targetAdvancement.icon : new ItemStack(targetItem);
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y + levitationOffset, 100);
        context.getMatrices().scale(80f, -80f, 80f); // Увеличили с 64 до 80
        
        context.getMatrices().multiply(new Quaternionf().rotateY((float) Math.toRadians(rotationY)));
        
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
        
        DiffuseLighting.disableGuiDepthLighting();
        
        client.getItemRenderer().renderItem(
            stack,
            ModelTransformationMode.GUI,
            false,
            context.getMatrices(),
            context.getVertexConsumers(),
            15728880,
            OverlayTexture.DEFAULT_UV,
            model
        );
        
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
        
        context.getMatrices().pop();
    }
    
    private void renderTimeSelectionBox(DrawContext context) {
        int centerX = width / 2;
        int centerY = height / 2 + 50; // Опустили меню еще ниже (было +30)
        
        // Background and border (расширенная рамка для текстового описания - 140x120)
        context.fill(centerX - 70, centerY - 60, centerX + 70, centerY + 60, 0xCC000000);
        context.drawBorder(centerX - 70, centerY - 60, 140, 120, 0xFF6930c3);
        
        // Title text - меняется в зависимости от состояния (светло-фиолетовый)
        String titleText = selectedTime == 0 
            ? Text.translatable("randomrun.time_selection.title_text").getString()
            : Text.translatable("randomrun.time_selection.time_text").getString();
        context.drawCenteredTextWithShadow(
            textRenderer, 
            Text.literal("§d" + titleText),
            centerX, 
            centerY - 40, 
            0xD946FF
        );

        if (slotMachineActive) {
            long elapsed = System.currentTimeMillis() - slotMachineStartTime;
            if (elapsed < SLOT_MACHINE_DURATION) {
                // Change number with tick sounds (fixed timing)
                long currentTime = System.currentTimeMillis();
                int tickInterval = (int) (80 + (elapsed / (float) SLOT_MACHINE_DURATION) * 150); // Slow down over time
                
                if (currentTime - lastTickTime >= tickInterval) {
                    lastTickTime = currentTime;
                    currentDisplayTime = 10 + new Random().nextInt(111); // 10-120 seconds
                    
                    // SLOT MACHINE SOUND
                    if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                        float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                        MinecraftClient.getInstance().getSoundManager().play(
                            net.minecraft.client.sound.PositionedSoundInstance.master(
                                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                                1.0f + new Random().nextFloat() * 0.3f,
                                volume * 0.2f
                            )
                        );
                    }
                }
            } else {
                // Final sound when stopping (only once)
                if (!soundPlayed && RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                    float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                    MinecraftClient.getInstance().getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                            1.0f,
                            volume * 0.6f
                        )
                    );
                    soundPlayed = true;
                }
                slotMachineActive = false;
            }
        }
        
        // Render time (с анимацией минут и секунд)
        int time = slotMachineActive ? currentDisplayTime : selectedTime;
        int displayMinutes = time / 60;
        int displaySeconds = time % 60;
        String timeStr = "§l" + String.format("%02d:%02d", displayMinutes, displaySeconds);
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY - 5, 0);
        context.getMatrices().scale(1.5f, 1.5f, 1f);
        int textWidth = textRenderer.getWidth(timeStr);
        context.drawTextWithShadow(textRenderer, Text.literal(timeStr), -textWidth / 2, 0, 0xFFFFFF);
        context.getMatrices().pop();
        
        // Текстовое описание времени (жирное серое)
        String textDesc = "§l" + formatTimeText(time);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(textDesc), centerX, centerY + 20, 0xAAAAAA);
        
        if (!slotMachineActive && !buttonsRefreshed) {
            buttonsRefreshed = true;
            clearChildren();
            init();
        }
    }
    
    private String formatTimeText(int seconds) {
        int hours = seconds / 3600;
        int min = (seconds % 3600) / 60;
        int sec = seconds % 60;
        
        StringBuilder result = new StringBuilder();
        if (hours > 0) {
            String unit = hours == 1 ? Text.translatable("randomrun.time.hour").getString() : Text.translatable("randomrun.time.hours").getString();
            result.append(hours).append(" ").append(unit);
            if (min > 0 || sec > 0) result.append(" ");
        }
        if (min > 0) {
            String unit = min == 1 ? Text.translatable("randomrun.time.minute").getString() : Text.translatable("randomrun.time.minutes").getString();
            result.append(min).append(" ").append(unit);
            if (sec > 0) result.append(" ");
        }
        if (sec > 0 || (hours == 0 && min == 0)) {
            String unit = sec == 1 ? Text.translatable("randomrun.time.second").getString() : Text.translatable("randomrun.time.seconds").getString();
            result.append(sec).append(" ").append(unit);
        }
        return result.toString();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
}
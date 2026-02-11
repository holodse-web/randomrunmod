package com.randomrun.mixin;

import com.randomrun.challenges.classic.world.WorldCreator;
import com.randomrun.main.RandomRunMod;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public class AutoSelectWorldMixin {
    
    @Inject(method = "init", at = @At("TAIL"))
    private void autoClickCreate(CallbackInfo ci) {
        if (WorldCreator.hasPendingRun()) {
            RandomRunMod.LOGGER.info("🤖 Авто-клик 'Создать новый мир' для отложенного забега...");
            
            // Перебор дочерних элементов для поиска кнопки
            for (net.minecraft.client.gui.Element element : ((net.minecraft.client.gui.screen.Screen)(Object)this).children()) {
                if (element instanceof net.minecraft.client.gui.widget.ButtonWidget) {
                    net.minecraft.client.gui.widget.ButtonWidget button = (net.minecraft.client.gui.widget.ButtonWidget) element;
                    Text message = button.getMessage();
                    String text = message.getString();
                    
                    boolean isCreateButton = false;
                    
                    // Проверка по ключу, если возможно
                    if (message.getContent() instanceof TranslatableTextContent) {
                        String key = ((TranslatableTextContent) message.getContent()).getKey();
                        if ("selectWorld.create".equals(key)) {
                            isCreateButton = true;
                        }
                    }
                    
                    // Запасной вариант - проверка текста
                    if (!isCreateButton) {
                        if (text.equals("Create New World") || 
                            text.equals("Создать новый мир")) {
                            isCreateButton = true;
                        }
                    }
                    
                    if (isCreateButton) {
                        RandomRunMod.LOGGER.info("✅ Найдена кнопка Создать: " + text);
                        button.onPress();
                        return;
                    }
                }
            }
            RandomRunMod.LOGGER.warn("⚠ Не удалось найти кнопку 'Создать новый мир'!");
        }
    }
}

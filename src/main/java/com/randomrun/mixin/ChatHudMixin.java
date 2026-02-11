package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.main.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    
    // Паттерн для поиска e4mc доменов
    private static final Pattern E4MC_DOMAIN_PATTERN = 
        Pattern.compile("([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+e4mc\\.link");
    
    // Кэш для предотвращения дубликатов
    private static final Set<String> processedDomains = new HashSet<>();
    private static long lastCleanupTime = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL = 300000; // 5 минут

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        if (message == null) return;
        
        try {
            // Периодическая очистка кэша
            cleanupCacheIfNeeded();
            
            // Получаем чистый текст БЕЗ форматирования
            String rawMessage = message.getString();
            
            // Проверяем содержит ли e4mc
            if (!rawMessage.contains("e4mc")) {
                return; // Не e4mc сообщение, пропускаем
            }
            
            // Если мы НЕ ждем e4mc (например, режим Radmin VPN), то СКРЫВАЕМ сообщение
            if (!BattleManager.getInstance().isAwaitingE4mcDomain()) {
                RandomRunMod.LOGGER.info("[E4MC] ⚠ Сообщение e4mc скрыто (режим Radmin/Other): " + rawMessage);
                ci.cancel();
                return;
            }
            
            RandomRunMod.LOGGER.info("════════════════════════════════════");
            RandomRunMod.LOGGER.info("[E4MC-DEBUG] Сообщение с e4mc обнаружено");
            RandomRunMod.LOGGER.info("════════════════════════════════════");
            
            // Извлечь e4mc домен
            String domain = extractE4mcDomain(message);
            
            if (domain != null && !domain.isEmpty()) {
                // Проверка на дубликаты
                if (processedDomains.contains(domain)) {
                    RandomRunMod.LOGGER.debug("[E4MC] ⚠ Домен уже обработан (дубликат): " + domain);
                    // Скрываем дубликаты тоже
                    ci.cancel();
                    return;
                }
                
                RandomRunMod.LOGGER.info("╔══════════════════════════════════════╗");
                RandomRunMod.LOGGER.info("║  ✓ E4MC ДОМЕН ЗАХВАЧЕН!              ║");
                RandomRunMod.LOGGER.info("║  Domain: " + domain);
                RandomRunMod.LOGGER.info("╚══════════════════════════════════════╝");
                
                processedDomains.add(domain);
                
                // Скрываем сообщение из чата, так как мы его обработали
                ci.cancel();
                
                // Отправка в BattleManager асинхронно
                final String finalDomain = domain;
                new Thread(() -> {
                    try {
                        RandomRunMod.LOGGER.info("[E4MC] → Отправка в Firebase: " + finalDomain);
                        BattleManager.getInstance().updateServerAddress(finalDomain);
                        
                        // Уведомление игрока в главном потоке
                        MinecraftClient.getInstance().execute(() -> {
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.sendMessage(
                                    Text.literal("§a§l[✓] §7IP отправлен в базу данных!"),
                                    false
                                );
                            }
                        });
                        
                        RandomRunMod.LOGGER.info("[E4MC] ✓ Домен успешно обработан");
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.error("[E4MC] ✗ ОШИБКА отправки в Firebase!", e);
                        processedDomains.remove(finalDomain); // Удалить из кэша при ошибке
                    }
                }, "E4MC-Uploader").start();
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[E4MC] ✗ КРИТИЧЕСКАЯ ОШИБКА в ChatHudMixin", e);
        }
    }

    private void cleanupCacheIfNeeded() {
        if (System.currentTimeMillis() - lastCleanupTime > CLEANUP_INTERVAL) {
            processedDomains.clear();
            lastCleanupTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Извлекает e4mc домен из сообщения чата
     */
    private String extractE4mcDomain(Text message) {
        Set<String> foundDomains = new HashSet<>();
        
        try {
            // Обход всех компонентов текста
            StringBuilder fullText = new StringBuilder();
            
            message.visit((style, text) -> {
                // Удаляем Minecraft форматирование из текста
                String cleanText = stripMinecraftFormatting(text);
                fullText.append(cleanText);
                
                // Проверка ClickEvent
                ClickEvent clickEvent = style.getClickEvent();
                if (clickEvent != null) {
                    String clickValue = clickEvent.getValue();
                    if (clickValue != null && clickValue.contains("e4mc")) {
                        String cleanClickValue = stripMinecraftFormatting(clickValue);
                        extractDomainsFromString(cleanClickValue, foundDomains);
                    }
                }
                
                return Optional.empty();
            }, Style.EMPTY);
            
            // Проверка основного текста
            String content = fullText.toString();
            if (content.contains("e4mc")) {
                extractDomainsFromString(content, foundDomains);
            }
            
            // Вернуть первый найденный домен
            if (!foundDomains.isEmpty()) {
                return foundDomains.iterator().next();
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[E4MC] ✗ Ошибка при извлечении домена", e);
        }
        
        return null;
    }
    
    /**
     * Удаляет Minecraft форматирование из строки (§x коды)
     */
    private String stripMinecraftFormatting(String text) {
        if (text == null) return "";
        // Удаляем все §x коды (где x = любой символ)
        return text.replaceAll("§.", "");
    }
    
    /**
     * Извлекает домены из строки
     */
    private void extractDomainsFromString(String text, Set<String> domains) {
        if (text == null || text.isEmpty()) return;
        
        try {
            // Удаляем форматирование
            text = stripMinecraftFormatting(text);
            
            // Regex поиск
            Matcher matcher = E4MC_DOMAIN_PATTERN.matcher(text);
            while (matcher.find()) {
                String domain = matcher.group(0).toLowerCase();
                if (isValidE4mcDomain(domain)) {
                    domains.add(domain);
                    RandomRunMod.LOGGER.info("[E4MC] ✓ Найден валидный домен: " + domain);
                    return; // Нашли один - этого достаточно
                }
            }
            
            // Fallback: поиск по словам
            String[] words = text.split("[\\s\\[\\](){}\"'<>,]+");
            for (String word : words) {
                if (word.contains(".e4mc.link")) {
                    String cleaned = word.replaceAll("[^a-zA-Z0-9.-]", "").toLowerCase();
                    if (isValidE4mcDomain(cleaned)) {
                        domains.add(cleaned);
                        RandomRunMod.LOGGER.info("[E4MC] ✓ Fallback нашел: " + cleaned);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[E4MC] ✗ Ошибка парсинга: " + text, e);
        }
    }
    
    /**
     * Проверяет валидность e4mc домена
     */
    private boolean isValidE4mcDomain(String domain) {
        if (domain == null || domain.isEmpty()) return false;
        
        // Должен заканчиваться на .e4mc.link
        if (!domain.endsWith(".e4mc.link")) return false;
        
        // Не должен быть просто "e4mc.link"
        if (domain.equals("e4mc.link")) return false;
        
        // Должен содержать минимум один поддомен
        String[] parts = domain.split("\\.");
        if (parts.length < 4) return false; // subdomain.e4mc.link = минимум 4 части
        
        // Проверка на валидные символы
        if (!domain.matches("^[a-z0-9.-]+$")) return false;
        
        // Не должно быть двойных точек
        if (domain.contains("..")) return false;
        
        // Разумная длина
        if (domain.length() > 253 || domain.length() < 10) return false;
        
        // Поддомен не должен быть пустым
        String subdomain = domain.substring(0, domain.indexOf(".e4mc.link"));
        if (subdomain.isEmpty() || subdomain.startsWith(".") || subdomain.endsWith(".")) {
            return false;
        }
        
        return true;
    }
}
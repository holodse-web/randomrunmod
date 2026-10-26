package com.randomrun.mixin.ui;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.util.CompactLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

@Mixin(ChatComponent.class)
public class E4mcDomainCaptureMixin {
    private static final String TAG = "E4MC";
    private static final Pattern E4MC_DOMAIN_PATTERN = 
        Pattern.compile("([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+e4mc\\.link");
    
    private static final Set<String> processedDomains = new HashSet<>();
    private static long lastCleanupTime = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL = 300000;

    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessage(Component message, MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, GuiMessageTag indicator, CallbackInfo ci) {
        if (message == null) return;
        
        try {
            cleanupCacheIfNeeded();
            String rawMessage = message.getString();
            
            if (!rawMessage.contains("e4mc")) return;
            
            if (!BattleManager.getInstance().isAwaitingE4mcDomain()) {
                ci.cancel();
                return;
            }
            
            String domain = extractDomainFromTranslatable(message);
            
            if (domain == null || domain.isEmpty()) {
                domain = extractE4mcDomain(message);
            }
            
            if (domain != null && !domain.isEmpty()) {
                if (processedDomains.contains(domain)) {
                    ci.cancel();
                    return;
                }
                
                processedDomains.add(domain);
                ci.cancel();
                
                final String finalDomain = domain;
                Thread.ofPlatform().daemon().name("E4MC-Uploader").start(() -> {
                    try {
                        BattleManager.getInstance().updateServerAddress(finalDomain);
                        BattleManager.getInstance().saveServerAddressToDatabase(finalDomain);
                        
                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null) {
                                com.randomrun.infrastructure.util.ClientMessages.send(Minecraft.getInstance().player, Component.literal("§a§l[✓] §7IP processed"), false);
                            }
                        });
                    } catch (Exception e) {
                        CompactLogger.networkError("{} Save failed: {}", TAG, e.getMessage());
                        processedDomains.remove(finalDomain);
                    }
                });
            }
        } catch (Exception e) {
            CompactLogger.error("{} Critical error: {}", TAG, e.getMessage());
        }
    }

    private void cleanupCacheIfNeeded() {
        if (System.currentTimeMillis() - lastCleanupTime > CLEANUP_INTERVAL) {
            processedDomains.clear();
            lastCleanupTime = System.currentTimeMillis();
        }
    }
    
    private String extractDomainFromTranslatable(Component message) {
        try {
            if (message.getContents() instanceof TranslatableContents) {
                TranslatableContents content = (TranslatableContents) message.getContents();
                String key = content.getKey();
                
                if ("text.e4mc_minecraft.domainAssigned".equals(key)) {
                    Object[] args = content.getArgs();
                    
                    if (args != null && args.length > 0) {
                        String domain = String.valueOf(args[0]);
                        
                        if (isValidE4mcDomain(domain)) {
                            return domain;
                        }
                    }
                }
            }
            
            for (Component sibling : message.getSiblings()) {
                String domain = extractDomainFromTranslatable(sibling);
                if (domain != null && !domain.isEmpty()) {
                    return domain;
                }
            }
        } catch (Exception e) {
            CompactLogger.networkError("{} Extract failed: {}", TAG, e.getMessage());
        }
        
        return null;
    }
    
    private String extractE4mcDomain(Component message) {
        Set<String> foundDomains = new HashSet<>();
        
        try {
            StringBuilder fullText = new StringBuilder();
            
            message.visit((style, text) -> {
                String cleanText = stripMinecraftFormatting(text);
                fullText.append(cleanText);
                
                ClickEvent clickEvent = style.getClickEvent();
                if (clickEvent != null) {
                    String clickValue = switch (clickEvent) {
                        case ClickEvent.OpenUrl url -> url.uri().toString();
                        case ClickEvent.CopyToClipboard copy -> copy.value();
                        case ClickEvent.RunCommand command -> command.command();
                        case ClickEvent.SuggestCommand command -> command.command();
                        default -> null;
                    };
                    if (clickValue != null && clickValue.contains("e4mc")) {
                        String cleanClickValue = stripMinecraftFormatting(clickValue);
                        extractDomainsFromString(cleanClickValue, foundDomains);
                    }
                }
                
                return Optional.empty();
            }, Style.EMPTY);
            
            String content = fullText.toString();
            if (content.contains("e4mc")) {
                extractDomainsFromString(content, foundDomains);
            }
            
            if (!foundDomains.isEmpty()) {
                return foundDomains.iterator().next();
            }
        } catch (Exception e) {
            CompactLogger.networkError("{} Parse failed: {}", TAG, e.getMessage());
        }
        
        return null;
    }
    
    private String stripMinecraftFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "");
    }
    
    private void extractDomainsFromString(String text, Set<String> domains) {
        if (text == null || text.isEmpty()) return;
        
        try {
            text = stripMinecraftFormatting(text);
            
            Matcher matcher = E4MC_DOMAIN_PATTERN.matcher(text);
            while (matcher.find()) {
                String domain = matcher.group(0).toLowerCase();
                if (isValidE4mcDomain(domain)) {
                    domains.add(domain);
                    return;
                }
            }
            
            String[] words = text.split("[\\s\\[\\](){}\"'<>,]+");
            for (String word : words) {
                if (word.contains(".e4mc.link")) {
                    String cleaned = word.replaceAll("[^a-zA-Z0-9.-]", "").toLowerCase();
                    if (isValidE4mcDomain(cleaned)) {
                        domains.add(cleaned);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            CompactLogger.networkError("{} Parse error: {}", TAG, e.getMessage());
        }
    }
    
    private boolean isValidE4mcDomain(String domain) {
        if (domain == null || domain.isEmpty()) return false;
        if (!domain.endsWith(".e4mc.link")) return false;
        if (domain.equals("e4mc.link")) return false;
        
        String[] parts = domain.split("\\.");
        if (parts.length < 4) return false;
        if (!domain.matches("^[a-z0-9.-]+$")) return false;
        if (domain.contains("..")) return false;
        if (domain.length() > 253 || domain.length() < 10) return false;
        
        String subdomain = domain.substring(0, domain.indexOf(".e4mc.link"));
        if (subdomain.isEmpty() || subdomain.startsWith(".") || subdomain.endsWith(".")) {
            return false;
        }
        
        return true;
    }
}

package com.randomrun.infrastructure.security;

import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.data.RunDataManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class InventoryMonitor {
    private static final Map<UUID, Long> lastCheck = new ConcurrentHashMap<>();
    private static final long CHECK_INTERVAL_MS = 5000;

    private static final int MAX_NORMAL_ENCHANT_LEVEL = 15;
    private static final int SUSPICIOUS_ENCHANT_LEVEL = 30;

    public static void checkInventory() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        UUID playerId = client.player.getUUID();
        long now = System.currentTimeMillis();
        Long last = lastCheck.get(playerId);
        
        if (last != null && now - last < CHECK_INTERVAL_MS) {
            return;
        }
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        if (!isRunActive(runManager)) return;

        lastCheck.put(playerId, now);
        
        List<String> violations = new ArrayList<>();

        var inventory = client.player.getInventory();
        int size = inventory.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            checkItemStack(stack, violations);
        }

        if (!violations.isEmpty()) {
            handleViolations(violations);
        }
    }

    private static void checkItemStack(ItemStack stack, List<String> violations) {
        var enchantments = stack.getEnchantments();

        for (var entry : enchantments.keySet()) {
            int level = enchantments.getLevel(entry);
            if (level > SUSPICIOUS_ENCHANT_LEVEL) {
                violations.add(String.format("Читерское зачарование: %s уровень %d",
                    entry.getRegisteredName(), level));
            } else if (level > MAX_NORMAL_ENCHANT_LEVEL) {
                violations.add(String.format("Подозрительное зачарование: %s уровень %d",
                    entry.getRegisteredName(), level));
            }
        }

        if (stack.getCount() > stack.getMaxStackSize()) {
            violations.add(String.format("Недопустимое количество: %d (макс %d)",
                stack.getCount(), stack.getMaxStackSize()));
        }

        if (stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)) {
            violations.add("Неразрушимый предмет");
        }
    }

    private static void handleViolations(List<String> violations) {
        RandomRunMod.LOGGER.error("[AntiCheat] Обнаружены читерские предметы: {}", 
            String.join(", ", violations));
        
        PlayerProfile profile = PlayerProfile.get();
        if (profile != null) {
            profile.applyTrustPenalty(-8, 
                "Читерские предметы: " + violations.size());
            PlayerProfile.save();
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            com.randomrun.infrastructure.util.ClientMessages.send(client.player, net.minecraft.network.chat.Component.literal("§c§l[RandomRun] §cОбнаружены читерские предметы!"), false);
        }

        RandomRunMod.getInstance().getRunDataManager().failRun();
    }
    
    private static boolean isRunActive(RunDataManager runManager) {
        RunDataManager.RunStatus status = runManager.getStatus();
        return status == RunDataManager.RunStatus.RUNNING || status == RunDataManager.RunStatus.FROZEN;
    }
    
    public static void reset() {
        lastCheck.clear();
    }
}

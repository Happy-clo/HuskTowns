package me.william278.husktowns.util;

import me.william278.husktowns.data.DataManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class AutoClaimUtil {

    private static final HashMap<UUID,AutoClaimMode> autoClaimers = new HashMap<>();

    public static boolean isAutoClaiming(Player player) {
        return autoClaimers.keySet().contains(player.getUniqueId());
    }

    public static AutoClaimMode getAutoClaimType(Player player) {
        return autoClaimers.get(player.getUniqueId());
    }

    public static void addAutoClaimer(Player player, AutoClaimMode mode) {
        autoClaimers.put(player.getUniqueId(), mode);
    }

    public static void removeAutoClaimer(Player player) {
        autoClaimers.remove(player.getUniqueId());
    }

    public static void autoClaim(Player player) {
        if (!isAutoClaiming(player)) {
            return;
        }
        if (getAutoClaimType(player) == AutoClaimMode.REGULAR) {
            DataManager.claimChunk(player, player.getLocation());
        } else if (getAutoClaimType(player) == AutoClaimMode.ADMIN) {
            DataManager.createAdminClaim(player);
        }
    }

    public enum AutoClaimMode {
        REGULAR,
        ADMIN
    }

}

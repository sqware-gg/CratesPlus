package dev.cratesplus.economy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyService {
    private final JavaPlugin plugin;
    private Object economy;
    private Class<?> economyClass;

    public EconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        economy = null;
        economyClass = null;
        try {
            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager().getRegistration(economyClass);
            economy = registration == null ? null : registration.getProvider();
        } catch (ClassNotFoundException e) {
            economyClass = null;
        }
    }

    public boolean available() {
        return economy != null;
    }

    public String providerName() {
        if (economy == null) {
            return "none";
        }
        return stringCall("getName", "unknown");
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (economy == null) {
            return false;
        }
        try {
            Method method = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            Object result = method.invoke(economy, player, amount);
            return result instanceof Boolean allowed && allowed;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Could not check Vault balance: " + e.getMessage());
            return false;
        }
    }

    public EconomyTransaction withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return EconomyTransaction.ok();
        }
        if (economy == null) {
            return EconomyTransaction.failed("No economy provider");
        }
        return transaction("withdrawPlayer", player, amount);
    }

    public EconomyTransaction deposit(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return EconomyTransaction.ok();
        }
        if (economy == null) {
            return EconomyTransaction.failed("No economy provider");
        }
        return transaction("depositPlayer", player, amount);
    }

    public String format(double amount) {
        if (economy == null) {
            return String.format("%.2f", amount);
        }
        try {
            Method method = economyClass.getMethod("format", double.class);
            Object result = method.invoke(economy, amount);
            return result == null ? String.format("%.2f", amount) : result.toString();
        } catch (ReflectiveOperationException e) {
            return String.format("%.2f", amount);
        }
    }

    private EconomyTransaction transaction(String methodName, OfflinePlayer player, double amount) {
        try {
            Method method = economyClass.getMethod(methodName, OfflinePlayer.class, double.class);
            Object response = method.invoke(economy, player, amount);
            boolean success = booleanCall(response, "transactionSuccess");
            if (success) {
                return EconomyTransaction.ok();
            }
            return EconomyTransaction.failed(errorMessage(response));
        } catch (ReflectiveOperationException e) {
            return EconomyTransaction.failed(e.getMessage());
        }
    }

    private String stringCall(String methodName, String fallback) {
        try {
            Method method = economyClass.getMethod(methodName);
            Object result = method.invoke(economy);
            return result == null ? fallback : result.toString();
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    private boolean booleanCall(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        Object result = method.invoke(target);
        return result instanceof Boolean bool && bool;
    }

    private String errorMessage(Object response) {
        if (response == null) {
            return "unknown";
        }
        try {
            Field field = response.getClass().getField("errorMessage");
            Object value = field.get(response);
            return value == null || value.toString().isBlank() ? "unknown" : value.toString();
        } catch (ReflectiveOperationException e) {
            return "unknown";
        }
    }
}

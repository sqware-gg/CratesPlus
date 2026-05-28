package dev.cratesplus.economy;

public record EconomyTransaction(boolean success, String errorMessage) {
    public static EconomyTransaction ok() {
        return new EconomyTransaction(true, "");
    }

    public static EconomyTransaction failed(String errorMessage) {
        return new EconomyTransaction(false, errorMessage == null || errorMessage.isBlank() ? "unknown" : errorMessage);
    }
}

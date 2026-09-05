package ru.virtuoz.client;

public class User {

    public static final String ROLE_OWNER = "Owner";
    public static final String ROLE_ADMIN = "Admin";
    public static final String ROLE_BETA = "Beta";
    public static final String ROLE_YOUTUBE = "YouTube";
    public static final String ROLE_PREMIUM = "Premium";
    public static final String ROLE_USER = "User";

    public static native boolean auth();

    public static native int getUID();
    public static native String getSubscribe();
    public static native String getLogin();
    public static native String getRole();

    public static String subscribe() {
        return getSubscribe();
    }

    public static boolean hasSubscription() {
        String value = getSubscribe();
        return value != null && !value.isBlank();
    }

    public static boolean hasRole(String role) {
        String current = getRole();
        return current != null && current.equalsIgnoreCase(role);
    }

    public static boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUser() {
        return hasRole(ROLE_USER);
    }

    public static boolean isPremium() {
        return hasRole(ROLE_PREMIUM);
    }

    public static boolean isBeta() {
        return hasRole(ROLE_BETA);
    }

    public static boolean isAdmin() {
        return hasRole(ROLE_ADMIN);
    }

    public static boolean isOwner() {
        return hasRole(ROLE_OWNER);
    }

    public static boolean isYouTube() {
        return hasRole(ROLE_YOUTUBE);
    }

    public static boolean isStaff() {
        return isAdmin() || isOwner();
    }

    public static Role role() {
        return Role.from(getRole());
    }

    public enum Role {
        OWNER(ROLE_OWNER),
        ADMIN(ROLE_ADMIN),
        BETA(ROLE_BETA),
        YOUTUBE(ROLE_YOUTUBE),
        PREMIUM(ROLE_PREMIUM),
        USER(ROLE_USER),
        UNKNOWN("");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Role from(String value) {
            for (Role role : values()) {
                if (role != UNKNOWN && role.value.equalsIgnoreCase(value)) {
                    return role;
                }
            }
            return UNKNOWN;
        }
    }
}
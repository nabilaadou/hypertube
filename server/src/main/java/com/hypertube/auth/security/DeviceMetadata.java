package com.hypertube.auth.security;

import jakarta.servlet.http.HttpServletRequest;

public record DeviceMetadata(String deviceInfo, String ipAddress) {
    public static DeviceMetadata from(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        String info = "Unknown device";
        if (agent != null && !agent.isBlank()) {
            agent = agent.substring(0, Math.min(agent.length(), 512)).replaceAll("[\\p{Cntrl}]", "");
            String browser = agent.contains("Edg/") ? "Edge" : agent.contains("OPR/") ? "Opera"
                    : agent.contains("Firefox/") ? "Firefox" : agent.contains("Chrome/") ? "Chrome"
                    : agent.contains("Safari/") ? "Safari" : "Unknown browser";
            String os = agent.contains("Android") ? "Android" : agent.contains("iPhone") || agent.contains("iPad") ? "iOS"
                    : agent.contains("Windows") ? "Windows" : agent.contains("Macintosh") ? "macOS"
                    : agent.contains("Linux") ? "Linux" : "Unknown OS";
            info = browser.equals("Unknown browser") ? agent.substring(0, Math.min(agent.length(), 255)) : browser + " on " + os;
        }
        String ip = request.getRemoteAddr();
        return new DeviceMetadata(info, ip == null ? "unknown" : ip.substring(0, Math.min(ip.length(), 45)));
    }
}

package com.kobosh.oneMcServerVelocity.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a small set of required Velocity settings in velocity.toml.
 *
 * Note: Velocity loads this file before plugins initialize, so any change made
 * here will take effect after the next proxy restart.
 */
public final class VelocityTomlUpdater {

    private static final Pattern KEY_VALUE = Pattern.compile("^\\s*([a-zA-Z0-9\\-]+)\\s*=.*$");

    private VelocityTomlUpdater() {
    }

    public static void enforce(Path pluginDataDir, Logger logger) {
        Path velocityToml = locateVelocityToml(pluginDataDir);
        if (velocityToml == null) {
            logger.warn("Could not find velocity.toml from plugin data dir: {}", pluginDataDir);
            return;
        }

        final Map<String, String> required = new LinkedHashMap<>();
        required.put("online-mode", "false");
        required.put("player-info-forwarding-mode", "\"modern\"");
        required.put("ping-passthrough", "\"disabled\"");
        required.put("accepts-transfers", "false");

        try {
            List<String> lines = Files.readAllLines(velocityToml);
            List<String> updated = new ArrayList<>(lines.size());
            boolean changed = false;
            Map<String, Boolean> seen = new LinkedHashMap<>();
            required.keySet().forEach(k -> seen.put(k, false));

            for (String line : lines) {
                Matcher m = KEY_VALUE.matcher(line);
                if (m.matches()) {
                    String key = m.group(1);
                    if (required.containsKey(key)) {
                        String desired = key + " = " + required.get(key);
                        if (!line.trim().equals(desired)) {
                            updated.add(desired);
                            changed = true;
                        } else {
                            updated.add(line);
                        }
                        seen.put(key, true);
                        continue;
                    }
                }
                updated.add(line);
            }

            List<String> missingKeys = new ArrayList<>();
            for (Map.Entry<String, Boolean> e : seen.entrySet()) {
                if (!e.getValue()) {
                    missingKeys.add(e.getKey());
                }
            }

            if (changed) {
                Files.write(velocityToml, updated);
                logger.warn("Updated {} with required settings. Restart Velocity to apply changes.", velocityToml);
            } else {
                logger.info("velocity.toml already contains the required enforced settings.");
            }

            if (!missingKeys.isEmpty()) {
                logger.warn("velocity.toml is missing keys and they were not auto-added: {}", String.join(", ", missingKeys));
            }
        } catch (IOException e) {
            logger.error("Failed to enforce velocity.toml settings at {}", velocityToml, e);
        }
    }

    private static Path locateVelocityToml(Path start) {
        Path cursor = start;
        int depth = 0;
        while (cursor != null && depth < 8) {
            Path candidate = cursor.resolve("velocity.toml");
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
            depth++;
        }
        return null;
    }
}
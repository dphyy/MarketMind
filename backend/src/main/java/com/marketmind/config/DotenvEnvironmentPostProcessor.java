package com.marketmind.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a project-root {@code .env} file into the Spring {@code Environment} so
 * {@code ${KEY}} placeholders in {@code application.yml} resolve from it. Spring
 * Boot does not read {@code .env} natively.
 *
 * <p>The backend is typically launched from {@code backend/}, while {@code .env}
 * lives in the project root, so this searches the working directory and its
 * ancestors. If no {@code .env} is found, nothing happens and the app falls back
 * to its defaults (i.e. full mock mode) — exactly as before.
 *
 * <p>Registered via
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "dotenv";
    private static final int MAX_PARENT_LEVELS = 6;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = locate();
        if (envFile == null) {
            return;
        }
        Map<String, Object> values = parse(envFile);
        if (!values.isEmpty()) {
            // addFirst: the .env is the source of truth for these keys, but real
            // OS env vars / -D system properties added later still resolve too.
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, values));
            System.out.println("[dotenv] Loaded " + values.size() + " entries from " + envFile);
        }
    }

    private Path locate() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < MAX_PARENT_LEVELS && dir != null; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private Map<String, Object> parse(Path file) {
        Map<String, Object> map = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(file);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("[dotenv] Failed to read " + file + ": " + e.getMessage());
        }
        return map;
    }
}

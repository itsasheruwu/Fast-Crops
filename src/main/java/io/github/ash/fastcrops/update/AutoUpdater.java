package io.github.ash.fastcrops.update;

import io.github.ash.fastcrops.FastCropsPlugin;
import io.github.ash.fastcrops.config.FastCropsConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoUpdater {
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ASSET_PATTERN = Pattern.compile(
            "\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+\\\\.jar)\\\"[\\s\\S]*?\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );

    private final FastCropsPlugin plugin;
    private final FastCropsConfig config;
    private final HttpClient httpClient;

    public AutoUpdater(FastCropsPlugin plugin, FastCropsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public void checkAndUpdateAsync() {
        if (!config.isAutoUpdateEnabled() || !config.isAutoUpdateCheckOnStartup()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                checkAndUpdate();
            } catch (Exception ex) {
                plugin.getLogger().warning("[FastThings] Auto-update check failed: " + ex.getMessage());
            }
        });
    }

    private void checkAndUpdate() throws IOException, InterruptedException {
        String owner = config.getAutoUpdateRepositoryOwner();
        String repo = config.getAutoUpdateRepositoryName();

        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            plugin.getLogger().warning("[FastThings] Auto-update skipped: repository owner/name not configured.");
            return;
        }

        String releasePath = "latest";
        if (!"latest".equalsIgnoreCase(config.getAutoUpdateChannel())) {
            releasePath = "tags/" + config.getAutoUpdateChannel();
        }

        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/" + releasePath;
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FastThings-Updater")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            plugin.getLogger().warning("[FastThings] Auto-update check failed: GitHub API status " + response.statusCode());
            return;
        }

        String body = response.body();
        Optional<String> latestTagOptional = matchFirst(TAG_PATTERN, body);
        if (latestTagOptional.isEmpty()) {
            plugin.getLogger().warning("[FastThings] Auto-update check failed: could not parse latest tag.");
            return;
        }

        String latestTag = normalizeVersion(latestTagOptional.get());
        String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
        if (compareVersions(latestTag, currentVersion) <= 0) {
            if (config.isDebug()) {
                plugin.getLogger().info("[FastThings] Auto-update: already on latest version " + currentVersion + ".");
            }
            return;
        }

        plugin.getLogger().info("[FastThings] Update available: " + currentVersion + " -> " + latestTag + ".");

        if (!config.isAutoUpdateDownloadOnUpdate()) {
            plugin.getLogger().info("[FastThings] Auto-update download disabled by config.");
            return;
        }

        Matcher matcher = ASSET_PATTERN.matcher(body);
        if (!matcher.find()) {
            plugin.getLogger().warning("[FastThings] Auto-update failed: no jar asset found on release.");
            return;
        }

        String assetName = matcher.group(1);
        String downloadUrl = matcher.group(2);
        downloadToUpdateFolder(assetName, downloadUrl);
    }

    private void downloadToUpdateFolder(String assetName, String downloadUrl) throws IOException, InterruptedException {
        HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(downloadUrl))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "FastThings-Updater")
                .GET()
                .build();

        HttpResponse<InputStream> downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (downloadResponse.statusCode() != 200) {
            plugin.getLogger().warning("[FastThings] Auto-update download failed: HTTP " + downloadResponse.statusCode());
            return;
        }

        Path updateDir = Paths.get(plugin.getServer().getUpdateFolder());
        if (!updateDir.isAbsolute()) {
            updateDir = plugin.getDataFolder().toPath().getParent().resolve(updateDir).normalize();
        }
        Files.createDirectories(updateDir);

        Path target = updateDir.resolve(assetName);
        try (InputStream in = downloadResponse.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        plugin.getLogger().info("[FastThings] Downloaded update to " + target + ". Restart server to apply.");
    }

    private Optional<String> matchFirst(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private static String normalizeVersion(String raw) {
        String normalized = raw == null ? "0.0.0" : raw.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\\\.");
        String[] rightParts = right.split("\\\\.");
        int max = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < max; i++) {
            int l = i < leftParts.length ? parseSegment(leftParts[i]) : 0;
            int r = i < rightParts.length ? parseSegment(rightParts[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }

        return 0;
    }

    private static int parseSegment(String segment) {
        String digits = segment.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}

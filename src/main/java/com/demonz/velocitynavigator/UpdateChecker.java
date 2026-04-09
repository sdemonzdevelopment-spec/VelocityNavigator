package com.demonz.velocitynavigator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class UpdateChecker {

    private static final String API_URL = "https://api.modrinth.com/v2/project/velocitynavigator/version";

    private final Logger logger;
    private final String currentVersion;
    private final HttpClient httpClient;
    private final UpdateStatus updateStatus = new UpdateStatus();

    public UpdateChecker(Logger logger, String currentVersion) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public UpdateStatus status() {
        return updateStatus;
    }

    public CompletableFuture<Void> checkAsync(Config.UpdateCheckerSettings settings) {
        if (!settings.enabled()) {
            return CompletableFuture.completedFuture(null);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("User-Agent", "DemonZDevelopment/VelocityNavigator/" + currentVersion + " (https://modrinth.com/plugin/velocitynavigator)")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> processResponse(response, settings))
                .exceptionally(throwable -> {
                    String message = "Failed to check Modrinth for updates: " + throwable.getMessage();
                    updateStatus.recordFailure(message);
                    if (settings.notifyConsole()) {
                        logger.warn(message);
                    }
                    return null;
                });
    }

    private void processResponse(HttpResponse<String> response, Config.UpdateCheckerSettings settings) {
        if (response.statusCode() != 200) {
            String message = "Modrinth update check returned HTTP " + response.statusCode() + ".";
            updateStatus.recordFailure(message);
            if (settings.notifyConsole()) {
                logger.warn(message);
            }
            return;
        }

        try {
            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            SemanticVersion installed = SemanticVersion.parse(currentVersion);
            SemanticVersion latestAllowed = installed;
            String latestAllowedRaw = currentVersion;

            for (JsonElement element : versions) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject version = element.getAsJsonObject();
                String remoteVersion = stringField(version, "version_number");
                Config.RemoteVersionType remoteType = Config.RemoteVersionType.fromString(stringField(version, "version_type"));
                if (!isAllowed(settings.channel(), installed.isPrerelease(), remoteType)) {
                    continue;
                }
                SemanticVersion parsed = SemanticVersion.parse(remoteVersion);
                if (parsed.compareTo(latestAllowed) > 0) {
                    latestAllowed = parsed;
                    latestAllowedRaw = remoteVersion;
                }
            }

            boolean updateAvailable = latestAllowed.compareTo(installed) > 0;
            updateStatus.recordSuccess(latestAllowedRaw, updateAvailable);
            if (updateAvailable && settings.notifyConsole()) {
                logger.info("VelocityNavigator update available: {} -> {}", currentVersion, latestAllowedRaw);
                logger.info("Download: https://modrinth.com/plugin/velocitynavigator");
            }
        } catch (RuntimeException exception) {
            String message = "Unable to parse Modrinth update response: " + exception.getMessage();
            updateStatus.recordFailure(message);
            if (settings.notifyConsole()) {
                logger.warn(message);
            }
        }
    }

    private boolean isAllowed(Config.UpdateChannel channel, boolean installedIsPrerelease, Config.RemoteVersionType remoteType) {
        return switch (channel) {
            case RELEASE -> installedIsPrerelease || remoteType == Config.RemoteVersionType.RELEASE;
            case BETA -> remoteType == Config.RemoteVersionType.RELEASE || remoteType == Config.RemoteVersionType.BETA;
            case ALPHA -> true;
        };
    }

    private String stringField(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null ? "" : value.getAsString();
    }
}


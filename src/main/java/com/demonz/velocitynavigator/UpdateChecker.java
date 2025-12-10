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
import java.nio.file.Path;
import java.time.Duration;

/**
 * Checks for updates using the Modrinth API v2.
 */
public class UpdateChecker {

    private static final String MODRINTH_PROJECT_SLUG = "velocitynavigator";
    private static final String API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version";
    
    private final Logger logger;
    private final String currentVersion;
    private final Path dataDirectory;
    private final HttpClient httpClient;

    public UpdateChecker(Logger logger, String currentVersion, Path dataDirectory) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.dataDirectory = dataDirectory;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void check() {
        // Modrinth requires a specific User-Agent format: User/Project/Version
        String userAgent = "DemonZDevelopment/VelocityNavigator/" + currentVersion + " (admin@demonz.com)";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("User-Agent", userAgent)
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(this::processResponse)
                .exceptionally(e -> {
                    logger.warn("Failed to check for updates on Modrinth: " + e.getMessage());
                    return null;
                });
    }

    private void processResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            logger.warn("Modrinth API returned error code: " + response.statusCode());
            return;
        }

        try {
            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            if (versions.size() == 0) return;

            // Get latest version object
            JsonObject latest = versions.get(0).getAsJsonObject();
            String versionNumber = latest.get("version_number").getAsString();

            if (isNewer(versionNumber, currentVersion)) {
                logger.info("-------------------------------------------------------");
                logger.info("VelocityNavigator Update Available!");
                logger.info("Current: {} -> New: {}", currentVersion, versionNumber);
                logger.info("Download: https://modrinth.com/plugin/velocitynavigator");
                logger.info("-------------------------------------------------------");
            }

        } catch (Exception e) {
            logger.warn("Error parsing Modrinth update data: " + e.getMessage());
        }
    }

    private boolean isNewer(String remote, String current) {
        String s1 = remote.replaceAll("[^0-9.]", "");
        String s2 = current.replaceAll("[^0-9.]", "");
        return !s1.equals(s2) && s1.compareTo(s2) > 0; // Simple string compare for now, ideally SemVer
    }
}
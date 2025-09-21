package com.demonz.velocitynavigator;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final String GITHUB_REPO = "sdemonzdevelopment-spec/VelocityNavigator";
    private final Logger logger;
    private final String currentVersion;
    private final Path dataDirectory;

    public UpdateChecker(Logger logger, String currentVersion, Path dataDirectory) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.dataDirectory = dataDirectory;
    }

    public void check() {
        CompletableFuture.runAsync(() -> {
            try {
                checkGitHub();
            } catch (Exception e) {
                logger.warn("Failed to check for updates: {}", e.getMessage());
            }
        });
    }

    private void checkGitHub() throws Exception {
        String url = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.warn("Update check failed. GitHub API returned status {}.", response.statusCode());
            return;
        }

        String body = response.body();
        String latestVersion = getStringFromJson(body, "tag_name");

        if (latestVersion != null && isNewer(latestVersion, currentVersion)) {
            logger.info("A new version ({}) of VelocityNavigator was found. Automatically downloading...", latestVersion);
            String assetUrl = findJarDownloadUrl(body);
            if (assetUrl == null) {
                logger.error("Could not find a JAR download URL for the latest release. Please update manually.");
                return;
            }
            downloadUpdate(assetUrl);
        }
    }

    private void downloadUpdate(String url) {
        try {
            Path updateDir = dataDirectory.getParent().resolve("update");
            if (!Files.exists(updateDir)) {
                Files.createDirectories(updateDir);
            }
            // The file name must match the plugin ID for Velocity to update it.
            Path destination = updateDir.resolve("velocitynavigator.jar"); 

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            
            client.send(request, HttpResponse.BodyHandlers.ofFile(destination));

            logger.info("----------------------------------------------------------");
            logger.info("VelocityNavigator has been successfully downloaded!");
            logger.info("Please restart the proxy to apply the update.");
            logger.info("----------------------------------------------------------");

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to download the update automatically. Please download it manually.", e);
        }
    }

    private String findJarDownloadUrl(String json) {
        Pattern pattern = Pattern.compile("\"browser_download_url\":\"([^\"]+\\.jar)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String getStringFromJson(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isNewer(String latest, String current) {
        String latestClean = latest.replaceAll("[^0-9.]", "");
        String currentClean = current.replaceAll("[^0-9.]", "");
        if (latestClean.isEmpty() || currentClean.isEmpty()) return false;
        String[] latestParts = latestClean.split("\\.");
        String[] currentParts = currentClean.split("\\.");
        int len = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < len; i++) {
            int latestPart = (i < latestParts.length && !latestParts[i].isEmpty()) ? Integer.parseInt(latestParts[i]) : 0;
            int currentPart = (i < currentParts.length && !currentParts[i].isEmpty()) ? Integer.parseInt(currentParts[i]) : 0;
            if (latestPart > currentPart) return true;
            if (latestPart < currentPart) return false;
        }
        return false;
    }
}
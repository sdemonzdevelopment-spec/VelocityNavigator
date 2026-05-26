package com.demonz.velocitynavigator;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FirstRunHandler {

    private static final String VERSION_FILE = "last_known_version.dat";

    private FirstRunHandler() {
    }

    public static void checkAndShowWelcome(Logger logger, Path dataDir, String currentVersion, boolean welcomeEnabled, String wikiUrl) {
        if (!welcomeEnabled) {
            return;
        }

        Path versionFilePath = dataDir.resolve(VERSION_FILE);
        boolean isFreshInstall = !Files.exists(versionFilePath);
        boolean showWelcome = false;
        boolean showUpgrade = false;

        if (isFreshInstall) {
            showWelcome = true;
            try {
                Files.createDirectories(dataDir);
                Files.writeString(versionFilePath, currentVersion, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warn("[VelocityNavigator] Failed to write version marker file: {}", e.getMessage());
            }
        } else {
            try {
                String lastKnownVersion = Files.readString(versionFilePath, StandardCharsets.UTF_8).trim();
                if (lastKnownVersion.isEmpty()) {
                    showWelcome = true;
                    Files.writeString(versionFilePath, currentVersion, StandardCharsets.UTF_8);
                } else if (!lastKnownVersion.equals(currentVersion)) {
                    showUpgrade = true;
                    // Update version marker file
                    Files.writeString(versionFilePath, currentVersion, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                logger.warn("[VelocityNavigator] Failed to read or update version marker file. Re-creating it... Error: {}", e.getMessage());
                showWelcome = true;
                try {
                    Files.writeString(versionFilePath, currentVersion, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    // Ignore
                }
            }
        }

        if (showWelcome) {
            logger.info(" ");
            logger.info("=================================================================================");
            logger.info("              VelocityNavigator v{} — Getting Started", currentVersion);
            logger.info("=================================================================================");
            logger.info("  Thank you for installing VelocityNavigator! The ultimate lobby-routing");
            logger.info("  and load-balancing solution for premium Velocity proxy networks.");
            logger.info("  ");
            logger.info("  To get started: ");
            logger.info("  1. Configure your lobbies in navigator.toml");
            logger.info("  2. Reload configuration using: /vn reload");
            logger.info("  ");
            logger.info("  For detailed documentation, configuration options, and commands,");
            logger.info("  please visit our official wiki:");
            logger.info("  {}", wikiUrl);
            logger.info("=================================================================================");
            logger.info(" ");
        } else if (showUpgrade) {
            logger.info(" ");
            logger.info("=================================================================================");
            logger.info("              VelocityNavigator v{} — Upgraded Successfully", currentVersion);
            logger.info("=================================================================================");
            logger.info("  VelocityNavigator has been updated! Here is what's new in this release:");
            logger.info("  ");
            logger.info("  • Bedrock & Geyser Support: Seamless routing for Bedrock players.");
            logger.info("  • Rich Configuration: Comments, wiki anchors, and auto-validation.");
            logger.info("  • Legacy Color Conversion: MiniMessage transition made easy.");
            logger.info("  • Status Dashboard: High-fidelity `/vn servers` dashboard.");
            logger.info("  • Operations & Reliability: Exponential backoffs and empty lobby strategies.");
            logger.info("  ");
            logger.info("  Read the full v{} release notes and upgrade guide at:", currentVersion);
            logger.info("  {}", wikiUrl);
            logger.info("=================================================================================");
            logger.info(" ");
        }
    }
}

package com.demonz.velocitynavigator;

import java.util.List;

public record RouteDecision(
        String sourceServer,
        String requestedGroup,
        String usedGroup,
        List<String> configuredCandidates,
        List<String> onlineCandidates,
        String selectedServer,
        boolean fallbackToDefault,
        String reason,
        Config.SelectionMode selectionMode
) {
    public boolean hasSelection() {
        return selectedServer != null && !selectedServer.isBlank();
    }
}


package com.cobre.notifications.application.model;

import java.util.Set;

public record AuthenticatedClient(String clientId, Set<String> scopes) {

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}

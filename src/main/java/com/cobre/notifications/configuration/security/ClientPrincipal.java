package com.cobre.notifications.configuration.security;

import java.util.Set;

public record ClientPrincipal(String clientId, Set<String> scopes) {
}

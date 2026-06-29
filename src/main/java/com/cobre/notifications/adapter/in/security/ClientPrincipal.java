package com.cobre.notifications.adapter.in.security;

import java.util.Set;

public record ClientPrincipal(String clientId, Set<String> scopes) {
}

package com.cobre.notifications.configuration.security;

import com.cobre.notifications.adapter.api.CorrelationIdFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ClientHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_HEADER = "X-Client-Id";
    public static final String SCOPES_HEADER = "X-Scopes";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = request.getHeader(CLIENT_ID_HEADER);
        if (clientId == null || clientId.isBlank()) {
            writeUnauthorized(request, response);
            return;
        }

        Set<String> scopes = parseScopes(request.getHeader(SCOPES_HEADER));
        ClientPrincipal principal = new ClientPrincipal(clientId.trim(), scopes);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                scopes.stream()
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                        .toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Set<String> parseScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(scope -> scope.trim().toLowerCase(Locale.ROOT))
                .filter(scope -> !scope.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String correlationId = CorrelationIdFilter.current(request);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error":{"code":"unauthorized","message":"Missing X-Client-Id header","correlation_id":"%s"}}"""
                .formatted(correlationId));
    }
}

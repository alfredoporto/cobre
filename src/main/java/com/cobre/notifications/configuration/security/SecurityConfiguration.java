package com.cobre.notifications.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.cobre.notifications.adapter.in.security.ClientHeaderAuthenticationFilter;
import com.cobre.notifications.adapter.in.web.CorrelationIdFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorrelationIdFilter correlationIdFilter,
            ClientHeaderAuthenticationFilter clientHeaderAuthenticationFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(clientHeaderAuthenticationFilter, CorrelationIdFilter.class);

        return http.build();
    }
}

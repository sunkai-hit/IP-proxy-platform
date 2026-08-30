package com.ipproxy.platform.security;

import org.springframework.context.annotation.*; import org.springframework.http.HttpMethod; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.config.http.SessionCreationPolicy; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.security.web.SecurityFilterChain; import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; import org.springframework.web.cors.*; import java.util.List;

@Configuration(proxyBeanMethods=false)
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwtFilter)throws Exception{
        http.csrf(csrf->csrf.disable()).cors(cors->cors.configurationSource(corsConfigurationSource())).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/api/admin/v1/auth/login").permitAll().requestMatchers("/api/open/v1/**").permitAll().requestMatchers("/actuator/health","/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").permitAll().anyRequest().authenticated()).addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class);return http.build();
    }
    @Bean CorsConfigurationSource corsConfigurationSource(){CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(List.of("http://localhost:5173","http://localhost:8081"));c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));c.setAllowedHeaders(List.of("*"));c.setExposedHeaders(List.of("X-Request-Id"));c.setAllowCredentials(true);UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/api/**",c);return s;}
}

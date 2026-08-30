package com.ipproxy.platform.security;

import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokenService; public JwtAuthenticationFilter(JwtTokenService tokenService){this.tokenService=tokenService;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String header=request.getHeader("Authorization");
        if(header!=null&&header.startsWith("Bearer ")&&SecurityContextHolder.getContext().getAuthentication()==null){try{UserPrincipal p=tokenService.parse(header.substring(7));var a=p.roles().stream().map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,a));}catch(Exception ignored){SecurityContextHolder.clearContext();}}
        chain.doFilter(request,response);
    }
}

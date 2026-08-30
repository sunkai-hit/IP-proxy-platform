package com.ipproxy.platform.security;

import io.jsonwebtoken.Claims; import io.jsonwebtoken.Jwts; import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey; import java.nio.charset.StandardCharsets; import java.time.Instant; import java.util.*;

@Service
public class JwtTokenService {
    private final JwtProperties properties; private final SecretKey key;
    public JwtTokenService(JwtProperties properties){this.properties=properties;this.key=Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));}
    public String create(UserPrincipal principal){Instant now=Instant.now();return Jwts.builder().subject(principal.username()).claim("uid",principal.userId()).claim("name",principal.displayName()).claim("roles",principal.roles()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(properties.expirationSeconds()))).signWith(key).compact();}
    public UserPrincipal parse(String token){Claims c=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();Long uid=c.get("uid",Long.class);String name=c.get("name",String.class);List<?> raw=c.get("roles",List.class);List<String> roles=raw==null?List.of():raw.stream().map(String::valueOf).toList();return new UserPrincipal(uid,c.getSubject(),name,roles);}
    public long expirationSeconds(){return properties.expirationSeconds();}
}

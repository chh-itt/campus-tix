package com.chh.campustix.common.security;

import com.chh.campustix.common.props.CampustixProps;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final CampustixProps props;

    public JwtUtil(CampustixProps props) {
        this.props = props;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String create(LoginUser user) {
        long now = System.currentTimeMillis();
        Date exp = new Date(now + props.getJwt().getExpireHours() * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("username", user.username())
                .claim("role", user.role())
                .issuedAt(new Date(now))
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    public LoginUser parse(String token) {
        Claims c = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        return new LoginUser(Long.valueOf(c.getSubject()), c.get("username", String.class), c.get("role", String.class));
    }
}

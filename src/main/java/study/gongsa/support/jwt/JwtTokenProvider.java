package study.gongsa.support.jwt;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {
    @Value("${jwt.issuer}")
    private String issuer;

    private final String secretKey;
    private String tokenPrefix = "Bearer ";

    public JwtTokenProvider(@Value("${jwt.secretKey}")String secretKey){
        this.secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String makeAccessToken(int userUID, int userAuthUID) {
        Date now = new Date();

        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + Duration.ofDays(30).toMillis()))
                .claim("userUID", userUID)
                .claim("userAuthUID", userAuthUID)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public String makeRefreshToken(int userUID) {
        Date now = new Date();

        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + Duration.ofDays(14).toMillis()))
                .claim("userUID", userUID)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public Claims checkValid(String authorizationHeader) {
        validationAuthorizationHeader(authorizationHeader);
        String token = extractToken(authorizationHeader);
        return verifyToken(token);
    }

    private void validationAuthorizationHeader(String header) {
        if (!header.startsWith(tokenPrefix)) {
            throw new IllegalArgumentException();
        }
    }

    private String extractToken(String authorizationHeader) {
        return authorizationHeader.substring(tokenPrefix.length());
    }

    public Claims verifyToken(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }
}

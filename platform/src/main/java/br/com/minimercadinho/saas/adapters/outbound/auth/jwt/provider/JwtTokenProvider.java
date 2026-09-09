package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.provider;

import br.com.minimercadinho.saas.adapters.outbound.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Map;
import javax.crypto.SecretKey;

public class JwtTokenProvider {
    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_UID = "uid";
    private final JwtProperties props;
    private final SecretKey hmacKey;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        byte[] keyBytes = JwtTokenProvider.tryDecodeBase64(props.getSecret());
        this.hmacKey = Keys.hmacShaKeyFor((byte[])keyBytes);
    }

    public String generateAccessToken(String subject, Map<String, Object> claims, Instant expiresAt) {
        return this.buildToken(claims, subject, expiresAt);
    }

    public String generateRefreshToken(String subject, String jti, Map<String, Object> claims, Instant expiresAt) {
        claims.put("jti", jti);
        claims.put("typ", "refresh");
        return this.buildToken(claims, subject, expiresAt);
    }

    public Jws<Claims> parseAndValidate(String token) {
        return this.parse(token);
    }

    public Jwt<JwsHeader, Claims> parseAndValidateAsJwt(String token) {
        return this.parse(token);
    }

    public Claims parseClaims(String token) {
        return (Claims)this.parse(token).getPayload();
    }

    private String buildToken(Map<String, Object> map, String string, Instant instant) {
        throw new Error("Unresolved compilation problem: \n\tThe method add(String) in the type CollectionMutator<String,NestedCollection<String,JwtBuilder>> is not applicable for the arguments (Object)\n");
    }

    private Jws<Claims> parse(String token) {
        JwtParserBuilder parser = Jwts.parser().verifyWith(this.hmacKey).requireIssuer(this.props.getIssuer()).clockSkewSeconds((long)this.props.getClockSkewSeconds().intValue());
        if (this.props.getAudience() != null && !this.props.getAudience().isBlank()) {
            parser.requireAudience(this.props.getAudience());
        }
        return parser.build().parseSignedClaims((CharSequence)token);
    }

    private static byte[] tryDecodeBase64(String string) {
        throw new Error("Unresolved compilation problem: \n\tThe method decode(CharSequence) in the type Decoder<CharSequence,byte[]> is not applicable for the arguments (Object)\n");
    }
}


package br.com.lojagenerica.adapters.outbound.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    @NotBlank
    private String issuer;

    private String audience;

    @NotNull
    @Min(1)
    private Integer accessTokenExpirationMinutes;

    @NotNull
    @Min(10)
    private Integer refreshTokenExpirationMinutes;

    @NotNull
    @Min(0)
    private Integer clockSkewSeconds = 60;

    @NotBlank
    private String header = "Authorization";

    @NotBlank
    private String prefix = "Bearer";

    private String refreshCookieName = "REFRESH_TOKEN";
    private boolean refreshCookieSecure = true;
    private boolean refreshCookieHttpOnly = true;
    private String refreshCookieDomain;
    private String refreshCookiePath = "/";

    // Getters e setters

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public Integer getAccessTokenExpirationMinutes() { return accessTokenExpirationMinutes; }
    public void setAccessTokenExpirationMinutes(Integer accessTokenExpirationMinutes) {
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
    }

    public Integer getRefreshTokenExpirationMinutes() { return refreshTokenExpirationMinutes; }
    public void setRefreshTokenExpirationMinutes(Integer refreshTokenExpirationMinutes) {
        this.refreshTokenExpirationMinutes = refreshTokenExpirationMinutes;
    }

    public Integer getClockSkewSeconds() { return clockSkewSeconds; }
    public void setClockSkewSeconds(Integer clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; }

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String refreshCookieName) { this.refreshCookieName = refreshCookieName; }

    public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
    public void setRefreshCookieSecure(boolean refreshCookieSecure) { this.refreshCookieSecure = refreshCookieSecure; }

    public boolean isRefreshCookieHttpOnly() { return refreshCookieHttpOnly; }
    public void setRefreshCookieHttpOnly(boolean refreshCookieHttpOnly) { this.refreshCookieHttpOnly = refreshCookieHttpOnly; }

    public String getRefreshCookieDomain() { return refreshCookieDomain; }
    public void setRefreshCookieDomain(String refreshCookieDomain) { this.refreshCookieDomain = refreshCookieDomain; }

    public String getRefreshCookiePath() { return refreshCookiePath; }
    public void setRefreshCookiePath(String refreshCookiePath) { this.refreshCookiePath = refreshCookiePath; }
}

package br.com.minimercadinho.saas.adapters.outbound.persistence.mapper;

import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.RefreshToken;
import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.RefreshTokenEntity;

public final class RefreshTokenMapper {
    private RefreshTokenMapper() {
    }

    public static RefreshToken toModel(RefreshTokenEntity e) {
        if (e == null) {
            return null;
        }
        RefreshToken m = new RefreshToken();
        m.setId(e.getId());
        m.setUserId(e.getUserId());
        m.setTenantId(e.getTenantId());
        m.setToken(e.getToken());
        m.setIssuedAt(e.getIssuedAt());
        m.setExpiresAt(e.getExpiresAt());
        m.setRevokedAt(e.getRevokedAt());
        m.setUserAgent(e.getUserAgent());
        m.setIpAddress(e.getIpAddress());
        return m;
    }

    public static RefreshTokenEntity toEntity(RefreshToken m) {
        if (m == null) {
            return null;
        }
        RefreshTokenEntity e = new RefreshTokenEntity();
        if (m.getId() != null) {
            e.setId(m.getId());
        }
        e.setUserId(m.getUserId());
        e.setTenantId(m.getTenantId());
        e.setToken(m.getToken());
        e.setIssuedAt(m.getIssuedAt());
        e.setExpiresAt(m.getExpiresAt());
        e.setRevokedAt(m.getRevokedAt());
        e.setUserAgent(m.getUserAgent());
        e.setIpAddress(m.getIpAddress());
        return e;
    }
}


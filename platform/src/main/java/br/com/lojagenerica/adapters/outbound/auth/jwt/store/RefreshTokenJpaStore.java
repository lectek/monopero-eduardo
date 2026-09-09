package br.com.lojagenerica.adapters.outbound.auth.jwt.store;

import br.com.lojagenerica.adapters.outbound.auth.jwt.model.RefreshToken;
import br.com.lojagenerica.adapters.outbound.persistence.entity.RefreshTokenEntity;
import br.com.lojagenerica.adapters.outbound.persistence.jpa.RefreshTokenJpaRepository;
import br.com.lojagenerica.adapters.outbound.persistence.mapper.RefreshTokenMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@ConditionalOnProperty(prefix="jwt.refresh-store", name={"type"}, havingValue="jpa", matchIfMissing=true)
public class RefreshTokenJpaStore
implements RefreshTokenStore {
    private final RefreshTokenJpaRepository repo;

    public RefreshTokenJpaStore(RefreshTokenJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public RefreshToken save(RefreshToken rt) {
        RefreshTokenEntity saved = (RefreshTokenEntity)this.repo.save(RefreshTokenMapper.toEntity(rt));
        return RefreshTokenMapper.toModel(saved);
    }

    @Override
    @Transactional(readOnly=true)
    public Optional<RefreshToken> findValidByToken(String token) {
        Instant now = Instant.now();
        return this.repo.findByToken(token).map(RefreshTokenMapper::toModel).filter(rt -> !rt.isRevoked() && !rt.isExpired(now));
    }

    @Override
    public void revokeByToken(String token, Instant revokedAt) {
        this.repo.findByToken(token).ifPresent(e -> {
            e.setRevokedAt(revokedAt != null ? revokedAt : Instant.now());
            this.repo.save(e);
        });
    }

    @Override
    public void revokeAllForUser(Long userId, String tenantId, Instant revokedAt) {
        this.repo.revokeAllForUser(userId, tenantId, revokedAt != null ? revokedAt : Instant.now());
    }

    @Override
    public RefreshToken rotate(String oldToken, RefreshToken newToken, Instant revokedAt) {
        this.revokeByToken(oldToken, revokedAt);
        return this.save(newToken);
    }

    @Override
    public long deleteExpired(Instant now) {
        return this.repo.deleteByExpiresAtBefore(now != null ? now : Instant.now());
    }
}


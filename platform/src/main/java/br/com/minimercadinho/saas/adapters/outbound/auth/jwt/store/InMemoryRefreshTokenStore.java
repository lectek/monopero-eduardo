package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store;

import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.RefreshToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ConditionalOnProperty(prefix = "jwt.refresh-store", name = {"type"}, havingValue = "memory")
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Clock clock;
    private final Map<String, Entry> byToken = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArraySet<String>> tokensByUser = new ConcurrentHashMap<>();

    public InMemoryRefreshTokenStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<RefreshToken> findValidByToken(String token) {
        if (token == null) return Optional.empty();
        Entry e = byToken.get(token);
        if (e == null) return Optional.empty();

        Instant now = clock.instant();
        boolean expired = e.expiresAt != null && e.expiresAt.isBefore(now);
        boolean revoked = e.revokedAt != null;
        if (expired || revoked) return Optional.empty();

        return Optional.of(e.toModel());
    }

    @Override
    public RefreshToken rotate(String currentToken, RefreshToken newToken, Instant revokedAt) {
        if (currentToken != null) {
            internalRevoke(currentToken, revokedAt != null ? revokedAt : clock.instant());
        }
        return save(newToken);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        Objects.requireNonNull(token, "token");
        Entry e = Entry.fromModel(token);
        byToken.put(e.token, e);
        tokensByUser.computeIfAbsent(e.userId, k -> new CopyOnWriteArraySet<>()).add(e.token);
        return e.toModel();
    }

    @Override
    public void revokeAllForUser(Long userId, String tenantId, Instant when) {
        if (userId == null) return;
        CopyOnWriteArraySet<String> set = tokensByUser.get(userId);
        if (set == null || set.isEmpty()) return;

        Instant ts = (when != null) ? when : clock.instant();
        for (String t : set) {
            internalRevoke(t, ts);
        }
    }

    @Override
    public void revokeByToken(String token, Instant revokedAt) {
        if (token == null) return;
        internalRevoke(token, revokedAt != null ? revokedAt : clock.instant());
    }

    @Override
    public long deleteExpired(Instant now) {
        Instant ref = now != null ? now : clock.instant();
        long count = 0L;

        Iterator<Map.Entry<String, Entry>> it = byToken.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> me = it.next();
            Entry e = me.getValue();
            if (e.expiresAt != null && e.expiresAt.isBefore(ref)) {
                it.remove();
                removeFromUserIndex(e.userId, e.token);
                count++;
            }
        }
        return count;
    }

    private void internalRevoke(String token, Instant when) {
        Entry e = byToken.get(token);
        if (e != null) {
            e.revokedAt = when;
        }
    }

    private void removeFromUserIndex(Long userId, String token) {
        if (userId == null) return;
        CopyOnWriteArraySet<String> set = tokensByUser.get(userId);
        if (set != null) {
            set.remove(token);
            if (set.isEmpty()) {
                tokensByUser.remove(userId, set);
            }
        }
    }

    private static final class Entry {
        String token;
        Long userId;
        String tenantId;
        Instant issuedAt;
        Instant expiresAt;
        Instant revokedAt;
        String userAgent;
        String ipAddress;

        static Entry fromModel(RefreshToken rt) {
            Entry e = new Entry();
            e.token = rt.getToken();
            e.userId = rt.getUserId();
            e.tenantId = rt.getTenantId();
            e.issuedAt = rt.getIssuedAt();
            e.expiresAt = rt.getExpiresAt();
            e.revokedAt = rt.getRevokedAt();
            e.userAgent = rt.getUserAgent();
            e.ipAddress = rt.getIpAddress();
            return e;
        }

        RefreshToken toModel() {
            RefreshToken rt = new RefreshToken();
            rt.setToken(token);
            rt.setUserId(userId);
            rt.setTenantId(tenantId);
            rt.setIssuedAt(issuedAt);
            rt.setExpiresAt(expiresAt);
            rt.setRevokedAt(revokedAt);
            rt.setUserAgent(userAgent);
            rt.setIpAddress(ipAddress);
            return rt;
        }
    }
}

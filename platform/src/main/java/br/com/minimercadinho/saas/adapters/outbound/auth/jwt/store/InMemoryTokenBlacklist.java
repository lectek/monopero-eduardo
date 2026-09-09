package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component(value="inMemoryTokenBlacklist")
@ConditionalOnProperty(prefix="jwt.blacklist", name={"strategy"}, havingValue="memory", matchIfMissing=true)
public class InMemoryTokenBlacklist
implements TokenBlacklist {
    private final Map<String, Instant> blacklist = new ConcurrentHashMap<String, Instant>();

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        Instant exp = this.blacklist.get(jti);
        if (exp == null) {
            return false;
        }
        if (Instant.now().isAfter(exp)) {
            this.blacklist.remove(jti);
            return false;
        }
        return true;
    }

    @Override
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) {
            return;
        }
        this.blacklist.put(jti, expiresAt);
    }

    @Override
    public void purgeExpired() {
        Instant now = Instant.now();
        this.blacklist.entrySet().removeIf(e -> now.isAfter((Instant)e.getValue()));
    }
}


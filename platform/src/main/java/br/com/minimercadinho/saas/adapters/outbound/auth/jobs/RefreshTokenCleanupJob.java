package br.com.minimercadinho.saas.adapters.outbound.auth.jobs;

import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.RefreshTokenStore;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);
    private final RefreshTokenStore store;

    public RefreshTokenCleanupJob(RefreshTokenStore store) {
        this.store = store;
    }

    @Scheduled(cron="${security.refresh.cleanup-cron:0 0/30 * * * *}")
    public void cleanup() {
        Instant now = Instant.now();
        long removed = this.store.deleteExpired(now);
        if (removed > 0L) {
            log.info("RefreshTokenCleanupJob: {} refresh tokens expirados removidos ({}).", (Object)removed, (Object)now);
        } else {
            log.debug("RefreshTokenCleanupJob: nenhum refresh token expirado para remover ({}).", (Object)now);
        }
    }
}


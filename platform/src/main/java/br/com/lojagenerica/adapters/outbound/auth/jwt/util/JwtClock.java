package br.com.lojagenerica.adapters.outbound.auth.jwt.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class JwtClock {
    private Clock clock;

    public JwtClock() {
        this.clock = Clock.systemDefaultZone();
    }

    public JwtClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(this.clock);
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public void resetToSystemDefault() {
        this.clock = Clock.systemDefaultZone();
    }

    public ZoneId getZone() {
        return this.clock.getZone();
    }
}


package br.com.lojagenerica.application.support;

import java.security.SecureRandom;

public final class DeliveryCodeGenerator {

    private static final int MIN_CODE = 100000;
    private static final int MAX_RANGE = 900000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeliveryCodeGenerator() {
    }

    public static String nextCode() {
        return String.valueOf(MIN_CODE + RANDOM.nextInt(MAX_RANGE));
    }
}


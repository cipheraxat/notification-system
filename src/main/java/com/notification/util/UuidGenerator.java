package com.notification.util;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public final class UuidGenerator {

    private UuidGenerator() {
    }

    public static UUID newV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}

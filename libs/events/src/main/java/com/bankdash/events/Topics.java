package com.bankdash.events;

/**
 * Topic names. The version suffix changes only with an incompatible payload change. Every topic, and the
 * {@code .DLT} of every consumed topic, must also be listed in {@code infra/kafka/topics.txt}.
 */
public final class Topics {

    /** Key: userId. Payload: {@link UserRegistered}. */
    public static final String AUTH_USER_REGISTERED = "auth.user-registered.v1";

    private Topics() {}

    /** The dead letter topic that consumers' failed records of {@code topic} go to. */
    public static String dlt(String topic) {
        return topic + ".DLT";
    }
}

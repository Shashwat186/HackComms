package com.shashwat.hackcomms.chat;

import lombok.Getter;

/** A Nexconn Server API call returned a non-zero code (or failed outright). */
@Getter
public class NexconnException extends RuntimeException {

    /** Documented codes we handle explicitly. */
    public static final int GROUP_ALREADY_EXISTS = 25409;
    public static final int ALREADY_MEMBER = 25412;
    public static final int NOT_MEMBER = 25418;

    private final int code;

    public NexconnException(int code, String message) {
        super("Nexconn error " + code + ": " + message);
        this.code = code;
    }
}

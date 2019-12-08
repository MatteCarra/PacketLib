package com.github.steveice10.packetlib.exception;

/**
 * Created by matte on 20/01/2016.
 */
public class SilentException extends RuntimeException {
    public SilentException(String reason) {
        super(reason);
    }
}

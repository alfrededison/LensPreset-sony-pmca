package com.lenspreset.core.profile;

/** A profile file that cannot be used. The message names the field, e.g. {@code sensor.width_mm}. */
public class ProfileException extends Exception {
    public ProfileException(String message) {
        super(message);
    }
}

package org.amrosman.jsmartclinic.service;

/** Thrown when the current user does not hold the permission required for an action. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}

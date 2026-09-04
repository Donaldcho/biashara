package com.biasharaai.desktop.v2;

final class PhoneAuthenticationException extends RuntimeException {
    private final int status;

    PhoneAuthenticationException(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}

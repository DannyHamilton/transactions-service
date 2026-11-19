package com.example.transactions;

class ConflictException extends RuntimeException {

    ConflictException(String message) {
        super(message);
    }
}

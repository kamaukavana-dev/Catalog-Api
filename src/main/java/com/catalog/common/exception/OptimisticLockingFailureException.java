package com.catalog.common.exception;

public class OptimisticLockingFailureException extends RuntimeException {
    public OptimisticLockingFailureException() { super(); }
    public OptimisticLockingFailureException(String message) { super(message); }
    public OptimisticLockingFailureException(String message, Throwable cause) { super(message, cause); }
}


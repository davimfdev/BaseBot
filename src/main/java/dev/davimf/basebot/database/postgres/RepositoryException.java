package dev.davimf.basebot.database.postgres;

/** Unchecked wrapper for data-access failures so callers aren't forced to handle SQLException. */
public class RepositoryException extends RuntimeException {
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}

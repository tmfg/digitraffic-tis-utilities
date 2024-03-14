package fi.digitraffic.tis.rules;

public class CorruptEntryException extends RuleException {
    public CorruptEntryException(String message) {
        super(message);
    }

    public CorruptEntryException(String message, Throwable cause) {
        super(message, cause);
    }

    public CorruptEntryException(Throwable cause) {
        super(cause);
    }

    protected CorruptEntryException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

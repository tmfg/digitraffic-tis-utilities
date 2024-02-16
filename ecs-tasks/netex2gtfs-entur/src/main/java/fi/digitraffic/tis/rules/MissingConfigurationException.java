package fi.digitraffic.tis.rules;

public class MissingConfigurationException extends ConfigurationException {
    public MissingConfigurationException(String message) {
        super(message);
    }

    public MissingConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public MissingConfigurationException(Throwable cause) {
        super(cause);
    }

    protected MissingConfigurationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}

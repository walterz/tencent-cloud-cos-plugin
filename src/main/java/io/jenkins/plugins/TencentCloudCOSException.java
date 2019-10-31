package io.jenkins.plugins;

public class TencentCloudCOSException extends Exception {
    private static final long serialVersionUID = 1582215285822395979L;

    public TencentCloudCOSException() {
        super();
    }

    public TencentCloudCOSException(final String message, final Throwable cause) {
        super(message,cause);
    }

    public TencentCloudCOSException(final String message) {
        super(message);
    }
}

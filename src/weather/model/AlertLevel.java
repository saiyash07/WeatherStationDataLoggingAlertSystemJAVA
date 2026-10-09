package weather.model;

/**
 * CONCEPT: enum implementing behaviour per constant (constant-specific bodies)
 * and natural ordering via ordinal (INFO &lt; WARNING &lt; CRITICAL).
 */
public enum AlertLevel {
    INFO {
        @Override
        public String icon() {
            return "i";
        }
    },
    WARNING {
        @Override
        public String icon() {
            return "!";
        }
    },
    CRITICAL {
        @Override
        public String icon() {
            return "!!";
        }
    };

    public abstract String icon();

    public boolean atLeast(AlertLevel other) {
        return compareTo(other) >= 0;
    }

    /** How far above its limit a reading is decides the severity. */
    public static AlertLevel fromOverage(double value, double limit) {
        if (value <= limit) {
            return INFO;
        }

        double ratio = (value - limit) / Math.max(Math.abs(limit), 1.0);

        if (ratio > 0.25) {
            return CRITICAL;
        }

        return WARNING;
    }
}

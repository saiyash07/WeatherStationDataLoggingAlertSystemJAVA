package weather.model;

import weather.exception.InvalidSensorException;
import java.util.Random;

/** Pressure does a slow random walk around 1013 hPa. State lives in the instance (transient). */
public final class PressureSensor extends Sensor {
    private static final long serialVersionUID = 1L;

    /** transient: not written by serialization, recomputed lazily after load. */
    private transient double current = 1013;

    public PressureSensor(String id, double limit, String location) throws InvalidSensorException {
        super(id, SensorType.PRESSURE, limit, location);
    }

    @Override
    protected synchronized double simulate(Random rnd, int hour) {
        current += rnd.nextGaussian() * 1.2;
        current += (1013 - current) * 0.05;   // gentle pull back to the mean

        return current;
    }

    @Override
    public String describe() {
        return "Barometer";
    }
}

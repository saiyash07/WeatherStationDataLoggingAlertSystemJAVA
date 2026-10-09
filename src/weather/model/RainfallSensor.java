package weather.model;

import weather.exception.InvalidSensorException;

import java.util.Random;

/**
 * Rainfall intensity in mm/h.
 * Most of the time it is dry (0); roughly one tick in three it rains, with an
 * exponential distribution so light rain is common and heavy rain is rare.
 */
public final class RainfallSensor extends Sensor {

    private static final long serialVersionUID = 1L;

    public RainfallSensor(String id, double limit, String location) throws InvalidSensorException {
        super(id, SensorType.RAINFALL, limit, location);
    }

    @Override
    protected double simulate(Random rnd, int hour) {
        if (rnd.nextInt(3) != 0) {
            return 0;
        }

        return -Math.log(1 - rnd.nextDouble()) * 8;
    }

    @Override
    public String describe() {
        return "Rain gauge";
    }
}

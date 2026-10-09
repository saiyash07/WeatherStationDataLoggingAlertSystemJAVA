package weather.model;

import weather.exception.InvalidSensorException;
import java.util.Random;

/** Humidity is highest at dawn and lowest in the afternoon (inverse of temperature). */
public final class HumiditySensor extends Sensor {
    private static final long serialVersionUID = 1L;

    public HumiditySensor(String id, double limit, String location) throws InvalidSensorException {
        super(id, SensorType.HUMIDITY, limit, location);
    }

    @Override
    protected double simulate(Random rnd, int hour) {
        double base = 60 - 20 * Math.sin((hour - 9) * Math.PI / 12.0);

        return base + rnd.nextGaussian() * 4;
    }

    @Override
    public String describe() {
        return "Hygrometer";
    }
}

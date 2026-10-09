package weather.model;

import weather.exception.InvalidSensorException;
import java.util.Random;

/** CONCEPT: inheritance + method overriding. Follows a daily sine wave peaking at 15:00. */
public final class TemperatureSensor extends Sensor {
    private static final long serialVersionUID = 1L;

    public TemperatureSensor(String id, double limit, String location) throws InvalidSensorException {
        super(id, SensorType.TEMPERATURE, limit, location);
    }

    @Override
    protected double simulate(Random rnd, int hour) {
        double base = 22 + 10 * Math.sin((hour - 9) * Math.PI / 12.0);

        return base + rnd.nextGaussian() * 1.5;
    }

    @Override
    public String describe() {
        return "Thermometer";
    }
}

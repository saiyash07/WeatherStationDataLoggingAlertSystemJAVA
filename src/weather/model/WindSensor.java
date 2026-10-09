package weather.model;

import weather.exception.InvalidSensorException;
import java.util.Random;

/** Wind speed is |gaussian| with occasional gusts. */
public final class WindSensor extends Sensor {
    private static final long serialVersionUID = 1L;

    public WindSensor(String id, double limit, String location) throws InvalidSensorException {
        super(id, SensorType.WIND, limit, location);
    }

    @Override
    protected double simulate(Random rnd, int hour) {
        double v = Math.abs(rnd.nextGaussian()) * 15 + 5;

        if (rnd.nextInt(20) == 0) v += 40 + rnd.nextDouble() * 40;   // gust

        return v;
    }

    @Override
    public String describe() {
        return "Anemometer";
    }
}

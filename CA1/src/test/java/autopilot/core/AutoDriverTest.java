package autopilot.core;

import autopilot.exception.SensorFailureException;
import autopilot.sensor.LaneSensor;
import autopilot.sensor.ObstacleSensor;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoDriverTest {
    private static final double CRUISING_SPEED = 80;
    private static final double CLEAR_DISTANCE = 100;
    private static final double CENTERED_LANE = 0;
    private static final double SAFE_BRAKE_PRESSURE = 30;
    private static final double TOLERANCE = 1e-9;
    private static final String SENSOR_FAILURE_MESSAGE = "Sensor unavailable";

    @Test
    void tick_atTargetSpeedOnClearCenteredRoad_holdsSpeedWithoutIntervention() {
        AutoDriver driver = driverWith(() -> CRUISING_SPEED, () -> CLEAR_DISTANCE, () -> CENTERED_LANE);

        AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

        AutoDriver.DriveResult expected = new AutoDriver.DriveResult(
                0, 0, AdaptiveCruise.Mode.HOLD, false, false);
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "lane offset {0} m -> steering {1} degrees")
    @CsvSource({"1, -45", "-1, 45"})
    void tick_laneDeviation_steersTowardLaneCenter(double offset, double expectedAngle) {
        AutoDriver driver = driverWith(() -> CRUISING_SPEED, () -> CLEAR_DISTANCE, () -> offset);

        AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

        assertEquals(expectedAngle, result.steeringAngleDegrees(), TOLERANCE);
    }

    @ParameterizedTest(name = "{0} sensor failure enters safe mode")
    @MethodSource("failedSensors")
    void tick_sensorFailure_returnsSafeModeCommand(FailedSensor failedSensor) {
        SpeedSensor speed = failedSensor == FailedSensor.SPEED
                ? () -> { throw new SensorFailureException(SENSOR_FAILURE_MESSAGE); }
                : () -> CRUISING_SPEED;
        ObstacleSensor obstacle = failedSensor == FailedSensor.OBSTACLE
                ? () -> { throw new SensorFailureException(SENSOR_FAILURE_MESSAGE); }
                : () -> CLEAR_DISTANCE;
        AutoDriver driver = driverWith(speed, obstacle, () -> CENTERED_LANE);

        AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

        AutoDriver.DriveResult expected = new AutoDriver.DriveResult(
                0, SAFE_BRAKE_PRESSURE, AdaptiveCruise.Mode.BRAKE, false, true);
        assertEquals(expected, result);
    }

    private static Stream<FailedSensor> failedSensors() {
        return Stream.of(FailedSensor.SPEED, FailedSensor.OBSTACLE);
    }

    private static AutoDriver driverWith(SpeedSensor speed, ObstacleSensor obstacle, LaneSensor lane) {
        return new AutoDriver(speed, obstacle, lane, CRUISING_SPEED);
    }

    private enum FailedSensor { SPEED, OBSTACLE }
}

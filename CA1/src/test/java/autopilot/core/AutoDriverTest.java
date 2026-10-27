package autopilot.core;

import autopilot.exception.SensorFailureException;
import autopilot.sensor.LaneSensor;
import autopilot.sensor.ObstacleSensor;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoDriverTest {
    private static final double CRUISING_SPEED = 80;
    private static final double CLEAR_DISTANCE = 100;
    private static final double CENTERED_LANE = 0;
    private static final double STRAIGHT_STEERING = 0;
    private static final double NO_BRAKE = 0;
    private static final double SAFE_BRAKE_PRESSURE = 30;
    private static final double TOLERANCE = 1e-9;
    private static final String SENSOR_FAILURE_MESSAGE = "Sensor unavailable";

    private static final SpeedSensor WORKING_SPEED = () -> CRUISING_SPEED;
    private static final ObstacleSensor WORKING_OBSTACLE = () -> CLEAR_DISTANCE;
    private static final LaneSensor WORKING_LANE = () -> CENTERED_LANE;
    private static final SpeedSensor FAILED_SPEED = () -> { throw new SensorFailureException(SENSOR_FAILURE_MESSAGE); };
    private static final ObstacleSensor FAILED_OBSTACLE = () -> { throw new SensorFailureException(SENSOR_FAILURE_MESSAGE); };
    private static final LaneSensor FAILED_LANE = () -> { throw new SensorFailureException(SENSOR_FAILURE_MESSAGE); };

    @Test
    void tick_atTargetSpeedOnClearCenteredRoad_holdsSpeedWithoutIntervention() {
        AutoDriver driver = new AutoDriver(WORKING_SPEED, WORKING_OBSTACLE, WORKING_LANE, CRUISING_SPEED);

        AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

        AutoDriver.DriveResult expected = new AutoDriver.DriveResult(
                STRAIGHT_STEERING, NO_BRAKE, AdaptiveCruise.Mode.HOLD, false, false);
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "lane offset {0} m -> steering {1} degrees")
    @CsvSource({"1, -45", "-1, 45"})
    void tick_laneDeviation_steersTowardLaneCenter(double offset, double expectedAngle) {
        AutoDriver driver = new AutoDriver(WORKING_SPEED, WORKING_OBSTACLE, () -> offset, CRUISING_SPEED);

        AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

        assertEquals(expectedAngle, result.steeringAngleDegrees(), TOLERANCE);
    }

    @ParameterizedTest(name = "{0} sensor failure enters safe mode")
    @MethodSource("failedSensors")
    void tick_sensorFailure_returnsSafeModeCommand(String failedSensor, SpeedSensor speed,
                                                   ObstacleSensor obstacle, LaneSensor lane) {
        AutoDriver driver = new AutoDriver(speed, obstacle, lane, CRUISING_SPEED);

        AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

        AutoDriver.DriveResult expected = new AutoDriver.DriveResult(
                STRAIGHT_STEERING, SAFE_BRAKE_PRESSURE, AdaptiveCruise.Mode.BRAKE, false, true);
        assertEquals(expected, result);
    }

    private static Stream<Arguments> failedSensors() {
        return Stream.of(
                Arguments.of("speed", FAILED_SPEED, WORKING_OBSTACLE, WORKING_LANE),
                Arguments.of("obstacle", WORKING_SPEED, FAILED_OBSTACLE, WORKING_LANE),
                Arguments.of("lane", WORKING_SPEED, WORKING_OBSTACLE, FAILED_LANE));
    }
}

package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SteeringControllerTest {
    private static final double LOW_SPEED = 50;
    private static final double HIGH_SPEED = 130;
    private static final double PHYSICAL_LIMIT = 450;
    private static final double HIGH_SPEED_LIMIT = 90;
    private static final double MAX_CHANGE_PER_TICK = 60;
    private static final int TICKS_TO_REACH_ANY_ANGLE = 8;
    private static final double TOLERANCE = 1e-9;
    private SteeringController steering;

    @BeforeEach
    void setUp() {
        steering = new SteeringController();
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "30, 30", "-30, -30", "60, 60", "60.01, 60", "-60.01, -60"})
    void update_fromCenter_limitsChangeToSixtyDegrees(double requested, double expected) {
        assertEquals(expected, steering.update(requested, LOW_SPEED), TOLERANCE);
    }

    @ParameterizedTest(name = "request {0} at {1} km/h settles at {2}")
    @CsvSource({"500, 120, 450", "-500, 120, -450", "500, 120.01, 90", "-500, 120.01, -90",
            "45, 150, 45"})
    void update_repeatedRequest_settlesAtBoundedAngle(double requested, double speed, double expected) {
        settleAt(requested, speed);

        assertEquals(expected, steering.getCurrentAngleDegrees(), TOLERANCE);
    }

    @ParameterizedTest(name = "at {0} then request {1} -> {2}")
    @CsvSource({"120, -120, 60", "120, 90, 90"})
    void update_changedTarget_movesFromCurrentAngle(double initial, double requested, double expected) {
        settleAt(initial, LOW_SPEED);

        assertEquals(expected, steering.update(requested, LOW_SPEED), TOLERANCE);
    }

    @Test
    void update_speedRisesAboveThresholdWithWideWheel_reducesAngleGradually() {
        settleAt(PHYSICAL_LIMIT, LOW_SPEED);

        assertEquals(PHYSICAL_LIMIT - MAX_CHANGE_PER_TICK, steering.update(PHYSICAL_LIMIT, HIGH_SPEED), TOLERANCE);
    }

    @Test
    void update_speedStaysAboveThresholdWithWideWheel_eventuallyReachesHighSpeedLimit() {
        settleAt(PHYSICAL_LIMIT, LOW_SPEED);
        settleAt(PHYSICAL_LIMIT, HIGH_SPEED);

        assertEquals(HIGH_SPEED_LIMIT, steering.getCurrentAngleDegrees(), TOLERANCE);
    }

    @ParameterizedTest(name = "wheel at {0} -> first step {1}")
    @CsvSource({"450, 390", "-450, -390", "60, 0"})
    void selfCenter_displacedWheel_movesAtMostSixtyDegreesPerTick(double initialAngle, double expected) {
        settleAt(initialAngle, LOW_SPEED);

        assertEquals(expected, steering.selfCenter(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-450, -60, -30, 0, 30, 60, 450})
    void selfCenter_displacedWheel_eventuallyReturnsToZero(double initialAngle) {
        settleAt(initialAngle, LOW_SPEED);
        for (int tick = 0; tick < TICKS_TO_REACH_ANY_ANGLE; tick++) {
            steering.selfCenter();
        }

        assertEquals(0, steering.getCurrentAngleDegrees(), TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({"NaN, 50", "Infinity, 50", "30, -1", "30, NaN", "30, Infinity"})
    void update_invalidInput_throwsIllegalArgument(double requested, double speed) {
        assertThrows(IllegalArgumentException.class, () -> steering.update(requested, speed));
    }

    private void settleAt(double requestedAngle, double speed) {
        for (int tick = 0; tick < TICKS_TO_REACH_ANY_ANGLE; tick++) {
            steering.update(requestedAngle, speed);
        }
    }
}

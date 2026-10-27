package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SteeringControllerTest {
    private static final double LOW_SPEED = 50;
    private static final double TOLERANCE = 1e-9;
    private SteeringController steering;

    @BeforeEach
    void setUp() {
        steering = new SteeringController();
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "30, 30", "-30, -30", "59.99, 59.99",
            "60, 60", "60.01, 60", "-60, -60", "-60.01, -60", "450, 60"})
    void update_fromCenter_limitsChangeToSixtyDegrees(double requested, double expected) {
        assertEquals(expected, steering.update(requested, LOW_SPEED), TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({"500, 120, 450", "-500, 120, -450", "450, 0, 450",
            "-450, 0, -450", "500, 120.01, 90", "-500, 120.01, -90",
            "45, 150, 45", "-45, 150, -45"})
    void update_repeatedRequest_reachesBoundedTarget(double requested, double speed, double expected) {
        for (int tick = 0; tick < 8; tick++) {
            steering.update(requested, speed);
        }
        assertAll(
                () -> assertEquals(expected, steering.getCurrentAngleDegrees(), TOLERANCE),
                () -> assertEquals(expected, steering.getTargetAngleDegrees(), TOLERANCE));
    }

    @ParameterizedTest
    @CsvSource({"120, -120, 60", "-120, 120, -60", "120, 90, 90"})
    void update_changedTarget_movesFromCurrentAngle(double initial, double requested, double expected) {
        steering.update(initial, LOW_SPEED);
        steering.update(initial, LOW_SPEED);
        assertEquals(expected, steering.update(requested, LOW_SPEED), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-450, -120, -60, -30, 0, 30, 60, 120, 450})
    void selfCenter_displacedWheel_returnsGraduallyToZero(double initialAngle) {
        for (int tick = 0; tick < 8; tick++) {
            steering.update(initialAngle, LOW_SPEED);
        }
        double expectedFirstStep = Math.copySign(Math.max(0, Math.abs(initialAngle) - 60), initialAngle);
        assertEquals(expectedFirstStep, steering.selfCenter(), TOLERANCE);
        for (int tick = 0; tick < 8; tick++) {
            steering.selfCenter();
        }
        assertEquals(0, steering.getCurrentAngleDegrees(), TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({"NaN, 50", "Infinity, 50", "-Infinity, 50",
            "30, -1", "30, NaN", "30, Infinity", "30, -Infinity"})
    void update_invalidInput_throwsWithoutMovingWheel(double requested, double speed) {
        steering.update(30, LOW_SPEED);
        assertThrows(IllegalArgumentException.class, () -> steering.update(requested, speed));
        assertEquals(30, steering.getCurrentAngleDegrees(), TOLERANCE);
    }
}

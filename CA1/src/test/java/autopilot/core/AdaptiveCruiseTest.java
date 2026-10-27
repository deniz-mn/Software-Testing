package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static autopilot.core.AdaptiveCruise.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * At the 80 km/h used by most scenarios the safe distance is 40 m,
 * the emergency boundary is 20 m and the hysteresis release boundary is 48 m.
 */
class AdaptiveCruiseTest {
    private static final double TARGET_SPEED = 100.0;
    private static final double SPEED = 80;
    private static final double EMERGENCY_DISTANCE = 20;
    private static final double BRAKING_DISTANCE = 30;
    private static final double RELEASE_DISTANCE = 48;
    private static final double INSIDE_HYSTERESIS_DISTANCE = 45;
    private static final double MAX_ACCELERATION_STEP = 5;
    private static final double FULL_BRAKE = 100;
    private static final double TOLERANCE = 1e-9;
    private AdaptiveCruise cruise;

    @BeforeEach
    void setUp() {
        cruise = new AdaptiveCruise(TARGET_SPEED);
    }

    @ParameterizedTest(name = "speed {0} km/h requires {1} m")
    @CsvSource({"0, 0", "1, 0.5", "80, 40"})
    void safeDistanceFor_validSpeed_returnsHalfSpeed(double speed, double expectedDistance) {
        assertEquals(expectedDistance, cruise.safeDistanceFor(speed), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, Double.NaN, Double.POSITIVE_INFINITY})
    void safeDistanceFor_invalidSpeed_throwsIllegalArgument(double speed) {
        assertThrows(IllegalArgumentException.class, () -> cruise.safeDistanceFor(speed));
    }

    @ParameterizedTest(name = "speed={0}, distance={1} -> {2}")
    @CsvSource({
            "80, 0, EMERGENCY_BRAKE",
            "80, 20, EMERGENCY_BRAKE",
            "80, 20.01, BRAKE",
            "80, 39.99, BRAKE",
            "80, 40, HOLD",
            "80, 40.01, ACCELERATE",
            "98, 100, ACCELERATE",
            "100, 100, HOLD",
            "0, 0, EMERGENCY_BRAKE",
            "0, 1, ACCELERATE"
    })
    void update_validReadings_selectsExpectedMode(double speed, double distance, AdaptiveCruise.Mode mode) {
        assertEquals(mode, cruise.update(speed, distance).mode());
    }

    @ParameterizedTest(name = "speed={0}, distance={1} -> amount {2}")
    @CsvSource({
            "80, 0, 100",
            "80, 20.01, 49.975",
            "80, 30, 25",
            "80, 39.99, 0.025",
            "80, 40.01, 5",
            "98, 100, 2",
            "100, 100, 0"
    })
    void update_validReadings_returnsExpectedAmount(double speed, double distance, double amount) {
        assertEquals(amount, cruise.update(speed, distance).amount(), TOLERANCE);
    }

    @ParameterizedTest(name = "braked at {0} m, then {1} m -> {2}")
    @CsvSource({"30, 40, BRAKE", "30, 47.99, BRAKE", "30, 48, ACCELERATE",
            "20, 45, BRAKE", "20, 48.01, ACCELERATE"})
    void update_afterBraking_releasesOnlyAtHysteresisBoundary(double initialDistance,
            double nextDistance, AdaptiveCruise.Mode expectedMode) {
        cruise.update(SPEED, initialDistance);

        assertEquals(expectedMode, cruise.update(SPEED, nextDistance).mode());
    }

    @ParameterizedTest(name = "braked at 30 m, then {0} m -> amount {1}")
    @CsvSource({"39, 2.5", "40, 10"})
    void update_afterBraking_returnsHysteresisBrakeAmount(double nextDistance, double expectedAmount) {
        cruise.update(SPEED, BRAKING_DISTANCE);

        assertEquals(expectedAmount, cruise.update(SPEED, nextDistance).amount(), TOLERANCE);
    }

    @Test
    void update_afterBrakeRelease_doesNotKeepHysteresisActive() {
        cruise.update(SPEED, BRAKING_DISTANCE);
        cruise.update(SPEED, RELEASE_DISTANCE);

        assertEquals(new AdaptiveCruise.Decision(ACCELERATE, MAX_ACCELERATION_STEP),
                cruise.update(SPEED, INSIDE_HYSTERESIS_DISTANCE));
    }

    @Test
    void update_whileBrakingAndObstacleBecomesCritical_prioritizesEmergencyBrake() {
        cruise.update(SPEED, BRAKING_DISTANCE);

        assertEquals(new AdaptiveCruise.Decision(EMERGENCY_BRAKE, FULL_BRAKE),
                cruise.update(SPEED, EMERGENCY_DISTANCE));
    }

    @ParameterizedTest
    @CsvSource({"-1, 100", "NaN, 100", "Infinity, 100", "80, -1", "80, NaN", "80, Infinity"})
    void update_invalidReading_throwsIllegalArgument(double speed, double distance) {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(speed, distance));
    }
}

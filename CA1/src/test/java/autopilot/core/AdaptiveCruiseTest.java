package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static autopilot.core.AdaptiveCruise.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveCruiseTest {
    private static final double TARGET_SPEED = 100.0;
    private static final double TOLERANCE = 1e-9;
    private AdaptiveCruise cruise;

    @BeforeEach
    void setUp() {
        cruise = new AdaptiveCruise(TARGET_SPEED);
    }

    @ParameterizedTest(name = "speed {0} km/h requires {1} m")
    @CsvSource({"0, 0", "1, 0.5", "80, 40", "120, 60"})
    void safeDistanceFor_validSpeed_returnsHalfSpeed(double speed, double expectedDistance) {
        assertEquals(expectedDistance, cruise.safeDistanceFor(speed), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void safeDistanceFor_invalidSpeed_throwsIllegalArgument(double speed) {
        assertThrows(IllegalArgumentException.class, () -> cruise.safeDistanceFor(speed));
    }

    // At 80 km/h the safe distance is 40 m and the emergency boundary is 20 m.
    @ParameterizedTest(name = "speed={0}, distance={1}: {2}, amount={3}")
    @CsvSource({
            "80, 0, EMERGENCY_BRAKE, 100",
            "80, 19.99, EMERGENCY_BRAKE, 100",
            "80, 20, EMERGENCY_BRAKE, 100",
            "80, 20.01, BRAKE, 49.975",
            "80, 30, BRAKE, 25",
            "80, 39.99, BRAKE, 0.025",
            "80, 40, HOLD, 0",
            "80, 40.01, ACCELERATE, 5",
            "95, 100, ACCELERATE, 5",
            "98, 100, ACCELERATE, 2",
            "100, 100, HOLD, 0",
            "110, 100, HOLD, 0",
            "0, 0, EMERGENCY_BRAKE, 100",
            "0, 1, ACCELERATE, 5"
    })
    void update_validReadings_returnsExpectedDecision(double speed, double distance,
                                                     AdaptiveCruise.Mode mode, double amount) {
        AdaptiveCruise.Decision decision = cruise.update(speed, distance);
        assertAll(
                () -> assertEquals(mode, decision.mode()),
                () -> assertEquals(amount, decision.amount(), TOLERANCE));
    }

    @ParameterizedTest
    @CsvSource({"30, 39, BRAKE, 2.5", "30, 40, BRAKE, 10",
            "30, 47.99, BRAKE, 10", "30, 48, ACCELERATE, 5",
            "20, 45, BRAKE, 10", "20, 48.01, ACCELERATE, 5"})
    void update_afterBraking_releasesOnlyAtHysteresisBoundary(double initialDistance,
            double nextDistance, AdaptiveCruise.Mode expectedMode, double expectedAmount) {
        cruise.update(80, initialDistance);
        AdaptiveCruise.Decision decision = cruise.update(80, nextDistance);
        assertAll(
                () -> assertEquals(expectedMode, decision.mode()),
                () -> assertEquals(expectedAmount, decision.amount(), TOLERANCE));
    }

    @Test
    void update_afterBrakeRelease_doesNotKeepHysteresisActive() {
        cruise.update(80, 30);
        cruise.update(80, 48);
        assertEquals(new AdaptiveCruise.Decision(ACCELERATE, 5), cruise.update(80, 45));
    }

    @Test
    void update_whileBrakingAndObstacleBecomesCritical_prioritizesEmergencyBrake() {
        cruise.update(80, 30);
        assertEquals(new AdaptiveCruise.Decision(EMERGENCY_BRAKE, 100), cruise.update(80, 20));
    }

    @ParameterizedTest
    @CsvSource({"-1, 100", "NaN, 100", "Infinity, 100", "-Infinity, 100",
            "80, -1", "80, NaN", "80, Infinity", "80, -Infinity"})
    void update_invalidReading_throwsIllegalArgument(double speed, double distance) {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(speed, distance));
    }
}

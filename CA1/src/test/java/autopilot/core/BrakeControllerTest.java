package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static autopilot.core.GearBox.Gear.*;
import static org.junit.jupiter.api.Assertions.*;

class BrakeControllerTest {
    private static final double TOLERANCE = 1e-9;
    private BrakeController brakes;

    @BeforeEach
    void setUp() {
        brakes = new BrakeController();
    }

    @ParameterizedTest(name = "pressure={0}, speed={1} -> {2}")
    @CsvSource({"0, 50, 0", "30, 50, 30", "80, 30.01, 80",
            "80.01, 30, 80.01", "80.01, 30.01, 90", "100, 0, 100",
            "150, 30, 100", "150, 31, 90"})
    void apply_validRequest_respectsPressureCapAndAbsBoundaries(double pressure,
                                                               double speed, double expected) {
        assertEquals(expected, brakes.apply(pressure, speed, D), TOLERANCE);
    }

    @Test
    void apply_continuousAbsRequest_alternatesHighAndLowPulses() {
        double[] actual = new double[4];
        for (int tick = 0; tick < actual.length; tick++) {
            actual[tick] = brakes.apply(100, 50, D);
        }
        assertArrayEquals(new double[]{90, 60, 90, 60}, actual, TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({"0, 50", "80, 50", "100, 30"})
    void apply_afterAbsStops_restartsWithHighPulse(double pressure, double speed) {
        brakes.apply(100, 50, D);
        brakes.apply(pressure, speed, D);
        assertEquals(90, brakes.apply(100, 50, D), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(value = GearBox.Gear.class, names = "P", mode = EnumSource.Mode.EXCLUDE)
    void emergencyBrake_nonParkGear_appliesFullPressureAndWarning(GearBox.Gear gear) {
        brakes.emergencyBrake(gear);
        assertAll(
                () -> assertEquals(100, brakes.getPressure(), TOLERANCE),
                () -> assertTrue(brakes.isWarningActive()));
    }

    @Test
    void emergencyBrake_duringAbs_resetsPulseSequence() {
        brakes.apply(100, 50, D);
        brakes.emergencyBrake(D);
        assertEquals(90, brakes.apply(100, 50, D), TOLERANCE);
    }

    @Test
    void apply_zeroPressure_releasesExistingBrake() {
        brakes.apply(50, 20, D);
        brakes.apply(0, 20, D);
        assertEquals(0, brakes.getPressure(), TOLERANCE);
    }

    @Test
    void apply_normalBraking_doesNotActivateWarning() {
        brakes.apply(100, 50, D);
        assertFalse(brakes.isWarningActive());
    }

    @ParameterizedTest
    @CsvSource({"-1, 50", "NaN, 50", "Infinity, 50", "-Infinity, 50",
            "30, -1", "30, NaN", "30, Infinity", "30, -Infinity"})
    void apply_invalidInput_throwsWithoutChangingPressure(double pressure, double speed) {
        brakes.apply(25, 20, D);
        assertThrows(IllegalArgumentException.class, () -> brakes.apply(pressure, speed, D));
        assertEquals(25, brakes.getPressure(), TOLERANCE);
    }

    @Test
    void apply_parkGear_throwsWithoutChangingPressure() {
        brakes.apply(25, 20, D);
        assertThrows(IllegalStateException.class, () -> brakes.apply(50, 20, P));
        assertEquals(25, brakes.getPressure(), TOLERANCE);
    }

    @Test
    void emergencyBrake_parkGear_throwsWithoutActivatingWarning() {
        assertThrows(IllegalStateException.class, () -> brakes.emergencyBrake(P));
        assertFalse(brakes.isWarningActive());
    }

    @Test
    void apply_nullGear_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> brakes.apply(20, 20, null));
    }

    @Test
    void emergencyBrake_nullGear_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> brakes.emergencyBrake(null));
    }
}

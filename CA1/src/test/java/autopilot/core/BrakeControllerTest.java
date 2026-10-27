package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static autopilot.core.GearBox.Gear.*;
import static org.junit.jupiter.api.Assertions.*;

class BrakeControllerTest {
    private static final double ABS_SPEED = 50;
    private static final double LOW_SPEED = 20;
    private static final double FULL_PRESSURE = 100;
    private static final double MODERATE_PRESSURE = 50;
    private static final double NO_PRESSURE = 0;
    private static final double HIGH_PULSE = 90;
    private static final double LOW_PULSE = 60;
    private static final double TOLERANCE = 1e-9;
    private BrakeController brakes;

    @BeforeEach
    void setUp() {
        brakes = new BrakeController();
    }

    @ParameterizedTest(name = "pressure={0}, speed={1} -> {2}")
    @CsvSource({"30, 50, 30", "80, 30.01, 80", "80.01, 30, 80.01", "80.01, 30.01, 90",
            "150, 30, 100", "150, 31, 90"})
    void apply_validRequest_respectsPressureCapAndAbsBoundaries(double pressure,
                                                               double speed, double expected) {
        assertEquals(expected, brakes.apply(pressure, speed, D), TOLERANCE);
    }

    @Test
    void apply_continuousAbsRequest_alternatesHighAndLowPulses() {
        double[] actual = {
                brakes.apply(FULL_PRESSURE, ABS_SPEED, D),
                brakes.apply(FULL_PRESSURE, ABS_SPEED, D),
                brakes.apply(FULL_PRESSURE, ABS_SPEED, D),
                brakes.apply(FULL_PRESSURE, ABS_SPEED, D)};

        assertArrayEquals(new double[]{HIGH_PULSE, LOW_PULSE, HIGH_PULSE, LOW_PULSE}, actual, TOLERANCE);
    }

    @ParameterizedTest(name = "ABS stopped by pressure={0}, speed={1}")
    @CsvSource({"0, 50", "80, 50", "100, 30"})
    void apply_afterAbsStops_restartsWithHighPulse(double pressure, double speed) {
        brakes.apply(FULL_PRESSURE, ABS_SPEED, D);
        brakes.apply(pressure, speed, D);

        assertEquals(HIGH_PULSE, brakes.apply(FULL_PRESSURE, ABS_SPEED, D), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(value = GearBox.Gear.class, names = "P", mode = EnumSource.Mode.EXCLUDE)
    void emergencyBrake_nonParkGear_appliesFullPressure(GearBox.Gear gear) {
        brakes.emergencyBrake(gear);

        assertEquals(FULL_PRESSURE, brakes.getPressure(), TOLERANCE);
    }

    @Test
    void emergencyBrake_nonParkGear_activatesWarning() {
        brakes.emergencyBrake(D);

        assertTrue(brakes.isWarningActive());
    }

    @Test
    void emergencyBrake_duringAbs_resetsPulseSequence() {
        brakes.apply(FULL_PRESSURE, ABS_SPEED, D);
        brakes.emergencyBrake(D);

        assertEquals(HIGH_PULSE, brakes.apply(FULL_PRESSURE, ABS_SPEED, D), TOLERANCE);
    }

    @Test
    void apply_zeroPressure_releasesExistingBrake() {
        brakes.apply(MODERATE_PRESSURE, LOW_SPEED, D);

        assertEquals(NO_PRESSURE, brakes.apply(NO_PRESSURE, LOW_SPEED, D), TOLERANCE);
    }

    @Test
    void apply_normalBraking_doesNotActivateWarning() {
        brakes.apply(FULL_PRESSURE, ABS_SPEED, D);

        assertFalse(brakes.isWarningActive());
    }

    @ParameterizedTest
    @CsvSource({"-1, 50", "NaN, 50", "Infinity, 50", "30, -1", "30, NaN", "30, Infinity"})
    void apply_invalidInput_throwsIllegalArgument(double pressure, double speed) {
        assertThrows(IllegalArgumentException.class, () -> brakes.apply(pressure, speed, D));
    }

    @Test
    void apply_parkGear_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> brakes.apply(MODERATE_PRESSURE, LOW_SPEED, P));
    }

    @Test
    void emergencyBrake_parkGear_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> brakes.emergencyBrake(P));
    }

    @Test
    void apply_nullGear_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> brakes.apply(MODERATE_PRESSURE, LOW_SPEED, null));
    }

    @Test
    void emergencyBrake_nullGear_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> brakes.emergencyBrake(null));
    }
}

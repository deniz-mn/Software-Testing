package autopilot.core;

import autopilot.exception.IllegalGearChangeException;
import autopilot.exception.SensorFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static autopilot.core.GearBox.Gear.*;
import static org.junit.jupiter.api.Assertions.*;

class GearBoxTest {
    private static final double STOPPED = 0;

    @ParameterizedTest(name = "{0} -> {1} at {2} km/h")
    @CsvFileSource(resources = "/autopilot/core/allowed-gear-transitions.csv", numLinesToSkip = 1)
    void changeTo_allowedTransition_switchesGear(GearBox.Gear from, GearBox.Gear to, double speed) {
        GearBox gearbox = gearboxIn(from);
        gearbox.setSpeedSensor(() -> speed);

        gearbox.changeTo(to);

        assertEquals(to, gearbox.getCurrentGear());
    }

    @ParameterizedTest(name = "{0} -> {1} at {2} km/h")
    @CsvFileSource(resources = "/autopilot/core/rejected-gear-transitions.csv", numLinesToSkip = 1)
    void changeTo_rejectedTransition_throwsIllegalGearChange(GearBox.Gear from, GearBox.Gear to, double speed) {
        GearBox gearbox = gearboxIn(from);
        gearbox.setSpeedSensor(() -> speed);

        assertThrows(IllegalGearChangeException.class, () -> gearbox.changeTo(to));
    }

    @Test
    void changeTo_rejectedTransition_keepsCurrentGear() {
        GearBox gearbox = gearboxIn(P);
        try {
            gearbox.changeTo(D);
        } catch (IllegalGearChangeException expected) {
            // the rejection itself is covered by the parameterized test above
        }

        assertEquals(P, gearbox.getCurrentGear());
    }

    @ParameterizedTest
    @EnumSource(GearBox.Gear.class)
    void changeTo_sameGear_doesNotReadSpeedSensor(GearBox.Gear gear) {
        GearBox gearbox = gearboxIn(gear);
        gearbox.setSpeedSensor(() -> { throw new AssertionError("Unexpected sensor read"); });

        gearbox.changeTo(gear);

        assertEquals(gear, gearbox.getCurrentGear());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, Double.NaN, Double.POSITIVE_INFINITY})
    void changeTo_invalidSpeed_throwsIllegalArgument(double speed) {
        GearBox gearbox = new GearBox(() -> speed);

        assertThrows(IllegalArgumentException.class, () -> gearbox.changeTo(R));
    }

    @Test
    void changeTo_failedSensor_propagatesSensorFailure() {
        GearBox gearbox = new GearBox(() -> { throw new SensorFailureException("Speed sensor unavailable"); });

        assertThrows(SensorFailureException.class, () -> gearbox.changeTo(R));
    }

    @Test
    void changeTo_nullTarget_throwsNullPointer() {
        GearBox gearbox = gearboxIn(D);

        assertThrows(NullPointerException.class, () -> gearbox.changeTo(null));
    }

    private static GearBox gearboxIn(GearBox.Gear gear) {
        GearBox gearbox = new GearBox(() -> STOPPED);
        if (gear != P) gearbox.changeTo(R);
        if (gear == N || gear == D) gearbox.changeTo(N);
        if (gear == D) gearbox.changeTo(D);
        return gearbox;
    }
}

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
    @ParameterizedTest(name = "{0} -> {1} at {2} km/h: allowed={3}")
    @CsvFileSource(resources = "/autopilot/core/gear-transitions.csv", numLinesToSkip = 1)
    void changeTo_transitionAndSpeed_obeysStateMachine(GearBox.Gear from, GearBox.Gear to,
                                                       double speed, boolean allowed) {
        GearBox gearbox = gearboxIn(from);
        gearbox.setSpeedSensor(() -> speed);
        if (allowed) {
            gearbox.changeTo(to);
            assertEquals(to, gearbox.getCurrentGear());
        } else {
            assertThrows(IllegalGearChangeException.class, () -> gearbox.changeTo(to));
            assertEquals(from, gearbox.getCurrentGear(), "Rejected shifts must preserve the gear");
        }
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
    @ValueSource(doubles = {-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void changeTo_invalidSpeed_throwsWithoutChangingGear(double speed) {
        GearBox gearbox = new GearBox(() -> speed);
        assertThrows(IllegalArgumentException.class, () -> gearbox.changeTo(R));
        assertEquals(P, gearbox.getCurrentGear());
    }

    @Test
    void changeTo_failedSensor_propagatesFailureWithoutChangingGear() {
        SensorFailureException failure = new SensorFailureException("Speed sensor unavailable");
        GearBox gearbox = new GearBox(() -> { throw failure; });
        assertSame(failure, assertThrows(SensorFailureException.class, () -> gearbox.changeTo(R)));
        assertEquals(P, gearbox.getCurrentGear());
    }

    @Test
    void changeTo_nullTarget_throwsWithoutChangingGear() {
        GearBox gearbox = gearboxIn(D);
        assertThrows(NullPointerException.class, () -> gearbox.changeTo(null));
        assertEquals(D, gearbox.getCurrentGear());
    }

    private static GearBox gearboxIn(GearBox.Gear gear) {
        GearBox gearbox = new GearBox(() -> 0);
        if (gear != P) gearbox.changeTo(R);
        if (gear == N || gear == D) gearbox.changeTo(N);
        if (gear == D) gearbox.changeTo(D);
        return gearbox;
    }
}

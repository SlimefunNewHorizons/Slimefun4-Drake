package io.github.thebusybiscuit.slimefun4.core.networks.cargo;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

class TestCargoFrequency {

    @ParameterizedTest
    @CsvSource({ "0,0", "7,7", "9,9", "10,10", "15,15", "007,7", "99999,99999" })
    void testValidFrequencies(String raw, int expected) {
        Assertions.assertEquals(expected, CargoNet.parseFrequency(raw));
    }

    @ParameterizedTest
    @CsvSource({ "abc", "-1", "1.5", "99999999999999999999", "'1 '" })
    void testInvalidFrequencies(String raw) {
        Assertions.assertEquals(-1, CargoNet.parseFrequency(raw));
    }

    @Test
    void testNullAndEmpty() {
        Assertions.assertEquals(-1, CargoNet.parseFrequency(null));
        Assertions.assertEquals(-1, CargoNet.parseFrequency(""));
    }

    @Test
    void testInvalidValueIsReportedOnlyOnce() {
        Map<Location, String> reported = new HashMap<>();
        Location node = Mockito.mock(Location.class);

        Assertions.assertTrue(CargoNet.shouldReportInvalidFrequency(reported, node, "abc"));
        Assertions.assertFalse(CargoNet.shouldReportInvalidFrequency(reported, node, "abc"));
        Assertions.assertTrue(CargoNet.shouldReportInvalidFrequency(reported, node, "xyz"));

        reported.remove(node);
        Assertions.assertTrue(CargoNet.shouldReportInvalidFrequency(reported, node, "xyz"));
    }
}

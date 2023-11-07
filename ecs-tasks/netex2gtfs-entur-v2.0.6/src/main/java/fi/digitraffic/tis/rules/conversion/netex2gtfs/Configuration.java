package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import org.immutables.value.Value;

import java.io.InputStream;

@Value.Immutable
public interface Configuration {
    Configuration DEFAULTS = ImmutableConfiguration.builder()
            .codespace("FIN")
            .build();

    /**
     * The NeTEx codespace of the timetable data provider.
     * @return The codespace.
     */
    String codespace();

    /**
     * A NeTEx dataset containing the timetable data.
     * @return Dataset as ready to use InputStream
     */
    InputStream timetableDataset();

    /**
     * A NeTEx dataset containing the full definition of the StopPlaces and Quays referred from the timetable data.
     * @return Dataset as ready to use InputStream
     */
    InputStream stopsAndQuaysDataset();
}

package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableConfiguration.class)
@JsonDeserialize(as = ImmutableConfiguration.class)
public interface Configuration {

    /**
     * The NeTEx codespace of the timetable data provider.
     * @return The codespace.
     */
    String codespace();

    /**
     * A NeTEx dataset containing the timetable data.
     * @return Dataset as ready to use InputStream
     */
    String timetableDataset();

    /**
     * A NeTEx dataset containing the full definition of the StopPlaces and Quays referred from the timetable data.
     * @return Dataset as ready to use InputStream
     */
    String stopsAndQuaysDataset();
}

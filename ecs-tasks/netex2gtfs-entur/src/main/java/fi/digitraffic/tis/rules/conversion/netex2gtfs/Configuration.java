package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

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
     * The input does not contain timetables only stops are to be converted.
     * @return True if convert stops only.
     */
    boolean stopsOnly();

    /**
     * A NeTEx dataset containing the timetable data.
     * @return Dataset as ready to use InputStream
     */
    @Nullable
    String timetableDataset();

    /**
     * A NeTEx dataset containing the full definition of the StopPlaces and Quays referred from the timetable data.
     * @return Dataset as ready to use InputStream
     */
    String stopsAndQuaysDataset();
}

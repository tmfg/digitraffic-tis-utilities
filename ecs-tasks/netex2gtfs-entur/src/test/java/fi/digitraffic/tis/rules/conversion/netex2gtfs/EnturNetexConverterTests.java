package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class EnturNetexConverterTests {

    @TempDir
    private Path tempDir;

    private ObjectMapper objectMapper;
    private Path input;
    private Path output;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        input = Files.createDirectories(tempDir.resolve("input"));
        output = Files.createDirectories(tempDir.resolve("output"));
    }

    @Test
    @Tag("longRunning")
    @Tag("integration")
    void canProcessNetexTimetableInput() throws URISyntaxException, IOException {
        // these files are produced by Entur AS
        Path timetable = loadResource("rb_vyg-aggregated-netex.zip");
        Path stopsAndQuays = loadResource("RailStations_latest.zip");

        String[] args = generateConfiguration(input, false, timetable, stopsAndQuays, output);

        EnturNetexConverter.main(args);

        assertThat(Files.exists(output.resolve("gtfs.zip")), equalTo(true));
    }

    @Test
    @Tag("longRunning")
    @Tag("integration")
    void canProcessNetexStopsInput() throws URISyntaxException, IOException {
        // these files are produced by Entur AS
        Path stopsAndQuays = loadResource("RailStations_latest.zip");

        String[] args = generateConfiguration(input, true, null, stopsAndQuays, output);

        EnturNetexConverter.main(args);

        assertThat(Files.exists(output.resolve("gtfs.zip")), equalTo(true));
    }

    @Test
    @Tag("integration")
    void failsGracefullyOnMissingStopData() throws URISyntaxException, IOException {
        Path timetable = loadResource("rb_vyg-aggregated-netex.zip");
        Path stopsAndQuays = loadResource("emptyStopsAndQuays.zip");

        String[] args = generateConfiguration(input, false, timetable, stopsAndQuays, output);

        EnturNetexConverter.main(args);

        assertThat(Files.exists(output.resolve("gtfs.zip")), equalTo(false));
    }

    private String[] generateConfiguration(Path input, boolean stopsOnly, Path timetable, Path stopsAndQuays, Path output) throws IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("codespace", "FTR");
        config.put("stopsOnly", stopsOnly);
        config.put("timetableDataset", timetable != null ? timetable.toString(): null);
        config.put("stopsAndQuaysDataset", stopsAndQuays.toString());

        Files.writeString(
                Files.createFile(input.resolve("config.json")),
                objectMapper.writeValueAsString(config));

        String[] args = {
                "--input", input.toString(),
                "--output", output.toString()};
        return args;
    }

    private static Path loadResource(String name) throws URISyntaxException {
        return Path.of(Thread.currentThread().getContextClassLoader().getResource(name).toURI());
    }
}

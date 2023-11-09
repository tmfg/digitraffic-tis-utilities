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
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class EnturNetexConverterTests {

    @TempDir
    private Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @Tag("longRunning")
    @Tag("integration")
    void canProcessNetexInput() throws URISyntaxException, IOException {
        // these files are produced by Entur AS
        Path timetable = loadResource("rb_vyg-aggregated-netex.zip");
        Path stopsAndQuaus = loadResource("RailStations_latest.zip");

        Path input = Files.createDirectories(tempDir.resolve("input"));
        Path output = Files.createDirectories(tempDir.resolve("output"));

        Path config = Files.writeString(
                Files.createFile(input.resolve("config.json")),
                objectMapper.writeValueAsString(
                        Map.of("codespace", "FTR",
                               "timetableDataset", timetable.toString(),
                               "stopsAndQuaysDataset", stopsAndQuaus.toString())));

        String[] args = {
                "--input", input.toString(),
                "--output", output.toString()};

        EnturNetexConverter.main(args);

        Path gtfs = output.resolve("gtfs.zip");
        assertThat(Files.exists(gtfs), equalTo(true));
    }

    private static Path loadResource(String name) throws URISyntaxException {
        return Path.of(Thread.currentThread().getContextClassLoader().getResource(name).toURI());
    }
}

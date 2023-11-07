package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.entur.netex.gtfs.export.DefaultGtfsExporter;
import org.entur.netex.gtfs.export.GtfsExporter;
import org.entur.netex.gtfs.export.stop.DefaultStopAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wrapper class for running Entur's NeTEx validator based on the unified file based interface of external rules.
 */
public class EnturNetexConverter {

    public static void main(String[] args) {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        EnturNetexConverter converter = new EnturNetexConverter();
        converter.run(arguments);
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    public EnturNetexConverter() {
        objectMapper = initObjectMapper();
    }

    private ObjectMapper initObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(new JavaTimeModule(), new Jdk8Module());
        return objectMapper;
    }

    private void run(Arguments arguments) {
        Configuration conf = validateConfiguration(arguments.inputPath.resolve("config.json"));
        Path netexSource = arguments.inputPath.resolve(
                arguments.fileName != null
                        ? arguments.fileName
                        : "netex.zip");
        convert(conf, netexSource, arguments.outputPath);
    }

    private Configuration validateConfiguration(Path configuration) {
        if (Files.exists(configuration)) {
            try {
                return objectMapper.readValue(configuration.toFile(), Configuration.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read in configuration class, possibly malformed input?", e);
            }
        } else {
            return Configuration.DEFAULTS;
        }
    }

    private void convert(Configuration configuration,
                         Path netexSource,
                         Path outputsDirectory) {
        // input stream pointing to a zip archive containing the NeTEX stops and quays definitions.
        InputStream stopsAndQuaysDataset = configuration.stopsAndQuaysDataset();
        DefaultStopAreaRepository defaultStopAreaRepository = new DefaultStopAreaRepository();
        defaultStopAreaRepository.loadStopAreas(stopsAndQuaysDataset);
        // input stream pointing to a zip archive containing the NeTEX timetable data.
        InputStream netexTimetableDataset = configuration.timetableDataset();
        // NeTEX codespace for the timetable data provider.
        String codespace = configuration.codespace();

        GtfsExporter gtfsExport = new DefaultGtfsExporter(codespace, defaultStopAreaRepository);

        // the returned Inputstream points to a GTFS zip archive
        InputStream exportedGtfs = gtfsExport.convertTimetablesToGtfs(netexTimetableDataset);

        try {
            Files.copy(exportedGtfs, outputsDirectory.resolve("gtfs.zip"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

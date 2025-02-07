package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fi.digitraffic.tis.rules.ConfigurationException;
import fi.digitraffic.tis.rules.InvalidConfigurationException;
import fi.digitraffic.tis.rules.MissingConfigurationException;
import fi.digitraffic.tis.rules.RuleException;
import fi.digitraffic.tis.rules.conversion.ConversionException;
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
        Configuration conf;
        try {
            conf = validateConfiguration(arguments.inputPath.resolve("config.json"));
        } catch (ConfigurationException e) {
            logger.error("Failed to validate configuration, cannot continue", e);
            return;
        }
        try {
            convert(conf, arguments.outputPath);
        } catch (RuleException e) {
            logger.error("Failed to run GTFS to NeTEx conversion for " + arguments, e);
        }
    }

    private Configuration validateConfiguration(Path configuration) throws ConfigurationException {
        if (Files.exists(configuration)) {
            try {
                return objectMapper.readValue(configuration.toFile(), Configuration.class);
            } catch (IOException e) {
                throw new InvalidConfigurationException("Failed to read in configuration class, possibly malformed input?", e);
            }
        } else {
            throw new MissingConfigurationException("Expected to find configuration from path '" + configuration + "'");
        }
    }

    private void convert(Configuration configuration,
                         Path outputsDirectory) throws ConversionException {
        // input stream pointing to a zip archive containing the NeTEX stops and quays definitions.
        InputStream stopsAndQuaysDataset;
        try {
            stopsAndQuaysDataset = Files.newInputStream(Path.of(configuration.stopsAndQuaysDataset()));
        } catch (IOException e) {
            throw new ConversionException("Could not read stops and quats dataset file from '" + configuration.stopsAndQuaysDataset() + "'", e);
        }
        DefaultStopAreaRepository defaultStopAreaRepository = new DefaultStopAreaRepository();
        defaultStopAreaRepository.loadStopAreas(stopsAndQuaysDataset);
        // input stream pointing to a zip archive containing the NeTEX timetable data.
        InputStream netexTimetableDataset;
        try {
            netexTimetableDataset = Files.newInputStream(Path.of(configuration.timetableDataset()));
        } catch (IOException e) {
            throw new ConversionException("Could not read timetable dataset file from '" + configuration.timetableDataset() + "'", e);
        }
        // NeTEX codespace for the timetable data provider.
        String codespace = configuration.codespace();

        GtfsExporter gtfsExport = new DefaultGtfsExporter(codespace, defaultStopAreaRepository);

        // the returned Inputstream points to a GTFS zip archive

        InputStream exportedGtfs;
        try {
            exportedGtfs = gtfsExport.convertTimetablesToGtfs(netexTimetableDataset);
        } catch (RuntimeException e) {
            throw new ConversionException("Failed to convert NeTEx timetable data to GTFS", e);
        }

        try {
            Files.copy(exportedGtfs, outputsDirectory.resolve("gtfs.zip"));
        } catch (IOException e) {
            throw new ConversionException("Cannot ouput exported GTFS to file", e);
        }
    }

}

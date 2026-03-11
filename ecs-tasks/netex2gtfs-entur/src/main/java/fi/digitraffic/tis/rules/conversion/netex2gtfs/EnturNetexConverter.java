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
import org.entur.netex.gtfs.export.GtfsExporter;
import org.entur.netex.gtfs.export.stop.DefaultStopAreaRepository;
import org.entur.netex.gtfs.export.stop.StopAreaRepository;
import org.rutebanken.netex.model.EntityStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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
            logger.error("Failed to run GTFS to NeTEx conversion for {}", arguments, e);
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

    private void loadStopAreasFromDataset(Path datasetPath, DefaultStopAreaRepository stopAreaRepository) throws ConversionException {
        try (InputStream stopsAndQuaysDataset = Files.newInputStream(datasetPath)) {
            logger.info("Loading stop areas from dataset '{}'", datasetPath);
            stopAreaRepository.loadStopAreas(stopsAndQuaysDataset);
        } catch (IOException e) {
            throw new ConversionException("Could not read stops and quays dataset file from '" + datasetPath + "'", e);
        }
    }

    private boolean isEmpty(StopAreaRepository stopAreaRepository) {
        try {
            return stopAreaRepository.getAllQuays().isEmpty();
        } catch (NullPointerException npe) {
            return true;
        }
    }

    private void checkStopAreas(StopAreaRepository stopAreaRepository) throws ConversionException {
        // Check that stop areas were loaded successfully, and they have coordinates, which are required for GTFS export.
        if (isEmpty(stopAreaRepository)) {
            throw new ConversionException("No stop areas were loaded from the provided datasets, cannot continue with GTFS export");
        }

        List<String> quaysWithoutCoords = stopAreaRepository.getAllQuays().stream()
                .filter(quay -> quay.getCentroid() == null)
                .map(EntityStructure::getId)
                .toList();

        List<String> stopPlacesWithoutCoords = stopAreaRepository.getAllQuays().stream()
                .map(quay -> stopAreaRepository.getStopPlaceByQuayId(quay.getId()))
                .filter(stopPlace -> stopPlace.getCentroid() == null)
                .map(EntityStructure::getId)
                .toList();

         if (!quaysWithoutCoords.isEmpty()) {
             for (int i = 0; i < quaysWithoutCoords.size(); i++) {
                 if (i >= 10) {
                     logger.warn("Found additional {} Quays without coordinates", quaysWithoutCoords.size() - 10);
                     break;
                 }
                 String quayId = quaysWithoutCoords.get(i);
                 logger.warn("Quay with id '{}' is missing coordinates", quayId);
             }
             for (int i = 0; i < stopPlacesWithoutCoords.size(); i++) {
                if (i >= 10) {
                    logger.warn("Found additional {} StopPlaces without coordinates", quaysWithoutCoords.size() - 10);
                    break;
                }
                String stopPlaceId = stopPlacesWithoutCoords.get(i);
                logger.warn("StopPlace with id '{}' is missing coordinates", stopPlaceId);
             }
             throw new ConversionException("Some stop areas are missing coordinates, cannot continue with GTFS export");
         }
    }

    private void convert(Configuration configuration, Path outputsDirectory) throws ConversionException {
        DefaultStopAreaRepository defaultStopAreaRepository = new DefaultStopAreaRepository();
        if (!configuration.stopsOnly()) {
            // Load stop areas from the timetable dataset if not in stopsOnly mode,
            // as it contains the stop areas referenced by the timetable data.
            // This ensures that all stop areas referenced by the timetable data are included in the GTFS export,
            // even if they are missing from the stops and quays dataset.
            loadStopAreasFromDataset(Path.of(Objects.requireNonNull(configuration.timetableDataset())), defaultStopAreaRepository);
        }
        if (isEmpty(defaultStopAreaRepository)) {
            loadStopAreasFromDataset(Path.of(configuration.stopsAndQuaysDataset()), defaultStopAreaRepository);
        }

        // Validate that stop areas were loaded successfully and have coordinates before attempting GTFS export,
        // as missing coordinates will cause the export to fail.
        checkStopAreas(defaultStopAreaRepository);

        // NeTEX codespace.
        String codespace = configuration.codespace();

        GtfsExporter gtfsExport = new VacoGtfsExporter(codespace, defaultStopAreaRepository);
        InputStream exportedGtfs;

        if (configuration.stopsOnly()) {
            try {
                exportedGtfs = gtfsExport.convertStopsToGtfs();
            } catch (Exception e) {
                throw new ConversionException("Failed to convert NeTEx stops to GTFS", e);
            }
        } else {
            // input stream pointing to a zip archive containing the NeTEX timetable data.
            try (InputStream netexTimetableDataset = Files.newInputStream(Path.of(configuration.timetableDataset()))) {
                exportedGtfs = gtfsExport.convertTimetablesToGtfs(netexTimetableDataset);
            } catch (IOException e) {
                throw new ConversionException("Could not read timetable dataset file from '" + configuration.timetableDataset() + "'", e);
            } catch (RuntimeException e) {
                throw new ConversionException("Failed to convert NeTEx timetable data to GTFS", e);
            }
        }

        try {
            Files.copy(exportedGtfs, outputsDirectory.resolve("gtfs.zip"));
        } catch (IOException e) {
            throw new ConversionException("Cannot output exported GTFS to file", e);
        }
    }
}

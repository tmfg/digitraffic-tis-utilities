package fi.digitraffic.tis.rules.validation.gbfs;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fi.digitraffic.tis.rules.CorruptEntryException;
import fi.digitraffic.tis.rules.RuleException;
import org.entur.gbfs.validation.GbfsValidator;
import org.entur.gbfs.validation.GbfsValidatorFactory;
import org.entur.gbfs.validation.model.FileValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Wrapper class for running Entur's GBFS validator based on the unified file based interface of external rules.
 */
public class EnturGbfsValidator {

    public static void main(String[] args) {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        EnturGbfsValidator validator = new EnturGbfsValidator();
        validator.run(arguments);
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    public EnturGbfsValidator() {
        objectMapper = initObjectMapper();
    }

    private ObjectMapper initObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(new JavaTimeModule(), new Jdk8Module());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    private void run(Arguments arguments) {
        Configuration conf = validateConfiguration(arguments.inputPath.resolve("config.json"));
        Path gbfsSource = arguments.inputPath.resolve(
                arguments.fileName != null
                        ? arguments.fileName
                        : "gbfs.zip");
        try {
            validateGbfs(conf, gbfsSource, arguments.outputPath);
        } catch (RuleException e) {
            logger.error("Failed to process provided file", e);
        }
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

    private void validateGbfs(Configuration configuration,
                              Path gbfsSource,
                              Path outputsDirectory) throws RuleException {

        GbfsValidator validator = GbfsValidatorFactory.getGbfsJsonValidator();
        try (ZipFile zipFile = toZipFile(gbfsSource)) {

            List<ImmutableReport> reports = zipFile.stream()
                    .filter(e -> !e.isDirectory())
                    .map(zipEntry -> {
                        logger.debug("Extracting ZIP entry {} from archive...", zipEntry);
                        ImmutableReport.Builder report = ImmutableReport.builder()
                                .entry(zipEntry.getName());
                        try {
                            InputStream contents = getEntryContents(zipFile, zipEntry);
                            FileValidationResult fvr = validateGbfsEntry(configuration, validator, zipEntry, contents);
                            report = report.fileValidationResult(fvr);
                        } catch (CorruptEntryException e) {
                            report = report.addErrors(serializeThrowable(e));
                        }
                        return report.build();
                    }).toList();
            produceReports(outputsDirectory, reports);
        } catch (IOException e) {
            throw new RuleException("Failed to close ZIP stream " + gbfsSource + " gracefully", e);
        }
    }

    private void produceReports(Path outputsDirectory, List<ImmutableReport> reports) throws RuleException {
        try {
            Files.writeString(outputsDirectory.resolve("reports.json"), objectMapper.writeValueAsString(reports));
        } catch (IOException e) {
            throw new RuleException("Failed to create file 'reports.json'", e);
        }
    }

    private ZipFile toZipFile(Path gbfsSource) throws RuleException {
        ZipFile zipFile;
        try {
            logger.debug("Processing {} as ZIP file", gbfsSource);
            zipFile = new ZipFile(gbfsSource.toFile());
        } catch (IOException e1) {
            throw new RuleException("Failed to unzip provided GBFS package " + gbfsSource, e1);
        }
        return zipFile;
    }

    private InputStream getEntryContents(ZipFile zipFile, ZipEntry zipEntry) throws CorruptEntryException {
        try {
            return zipFile.getInputStream(zipEntry);
        } catch (IOException e) {
            throw new CorruptEntryException("Failed to access file " + zipEntry.getName() + " within provided GBFS package " + zipFile.getName(), e);
        }
    }

    private FileValidationResult validateGbfsEntry(Configuration configuration,
                                                   GbfsValidator gbfsValidator,
                                                   ZipEntry zipEntry,
                                                   InputStream contents) {
        logger.info("Validating {}", zipEntry.getName());
        // feed name is file name without the .json
        String feedName = zipEntry.getName().substring(0, zipEntry.getName().length() - ".json".length());
        return gbfsValidator.validateFile(feedName, contents);
    }

    private static String serializeThrowable(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

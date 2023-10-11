package fi.digitraffic.tis.rules.validation.netex;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.entur.netex.validation.validator.NetexValidatorsRunner;
import org.entur.netex.validation.validator.ValidationReport;
import org.entur.netex.validation.validator.schema.NetexSchemaValidator;
import org.entur.netex.validation.xml.NetexXMLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Wrapper class for running Entur's NeTEx validator based on the unified file based interface of external rules.
 */
public class EnturNetexValidator {

    public static void main(String[] args) {
        Arguments arguments = new Arguments();
        JCommander.newBuilder()
                .addObject(arguments)
                .build()
                .parse(args);
        EnturNetexValidator validator = new EnturNetexValidator();
        validator.run(arguments);
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    public EnturNetexValidator() {
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
        validateNetex(conf, netexSource, arguments.outputPath);
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

    private void validateNetex(Configuration configuration,
                               Path netexSource,
                               Path outputsDirectory) {

        try (ZipFile zipFile = toZipFile(netexSource)) {
            List<ValidationReport> reports = zipFile.stream()
                    .filter(e -> !e.isDirectory())
                    .map(zipEntry -> {
                        logger.debug("Extracting ZIP entry {} from archive...", zipEntry);
                        byte[] bytes = getEntryContents(zipFile, zipEntry);
                        return validateNetexEntry(configuration, zipEntry, bytes);
                    }).toList();
            produceReports(outputsDirectory, reports);
        } catch (IOException e) {
            //errorHandlerService.reportError(
            //        ImmutableError.of(
            //                entry.publicId(),
            //                taskData.id(),
            //                rulesetRepository.findByName(RuleName.NETEX_ENTUR_1_0_1).orElseThrow().id(),
            //                getIdentifyingName(),
            //                message));
            throw new RuntimeException("Failed to close ZIP stream " + netexSource + " gracefully", e);
        }
    }

    private void produceReports(Path outputsDirectory, List<ValidationReport> reports) {
        try {
            Files.writeString(outputsDirectory.resolve("reports.json"), objectMapper.writeValueAsString(reports));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file 'reports.json'", e);
        }
    }

    private ZipFile toZipFile(Path netexSource) {
        ZipFile zipFile;
        try {
            logger.debug("Processing {} as ZIP file", netexSource);
            zipFile = new ZipFile(netexSource.toFile());
        } catch (IOException e1) {
            //errorHandlerService.reportError(
            //        ImmutableError.of(
            //                entry.publicId(),
            //                task.id(),
            //                rulesetRepository.findByName(RuleName.NETEX_ENTUR_1_0_1).orElseThrow().id(),
            //                getIdentifyingName(),
            //                message));
            throw new RuntimeException("Failed to unzip provided NeTEx package " + netexSource, e1);
        }
        return zipFile;
    }

    private byte[] getEntryContents(ZipFile zipFile, ZipEntry zipEntry) {
        byte[] bytes;
        try {
            bytes = zipFile.getInputStream(zipEntry).readAllBytes();
        } catch (IOException e) {
            //errorHandlerService.reportError(
            //        ImmutableError.of(
            //                entry.publicId(),
            //                task.id(),
            //                rulesetRepository.findByName(RuleName.NETEX_ENTUR_1_0_1).orElseThrow().id(),
            //                getIdentifyingName(),
            //                message));
            throw new RuntimeException("Failed to access file " + zipEntry.getName() + " within provided NeTEx package " + zipFile.getName(), e);
        }
        return bytes;
    }

    private ValidationReport validateNetexEntry(Configuration configuration, ZipEntry zipEntry, byte[] bytes) {
        logger.debug("Validating ZIP entry {}...", zipEntry);
        // TODO: accumulate max errors
        NetexXMLParser netexXMLParser = new NetexXMLParser(configuration.ignorableNetexElements());
        NetexSchemaValidator netexSchemaValidator = new NetexSchemaValidator(configuration.maximumErrors());
        NetexValidatorsRunner netexValidatorsRunner = new NetexValidatorsRunner(netexXMLParser, netexSchemaValidator, List.of());

        return netexValidatorsRunner.validate(
                configuration.codespace(),
                configuration.reportId(),
                zipEntry.getName(),
                bytes);
    }
}

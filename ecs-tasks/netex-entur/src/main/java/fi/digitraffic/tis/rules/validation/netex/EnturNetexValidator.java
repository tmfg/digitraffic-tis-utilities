package fi.digitraffic.tis.rules.validation.netex;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fi.digitraffic.tis.rules.CorruptEntryException;
import fi.digitraffic.tis.rules.RuleException;
import org.entur.netex.validation.validator.NetexValidatorsRunner;
import org.entur.netex.validation.validator.ValidationReport;
import org.entur.netex.validation.validator.schema.NetexSchemaValidator;
import org.entur.netex.validation.xml.NetexXMLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    private void run(Arguments arguments) {
        Configuration conf = validateConfiguration(arguments.inputPath.resolve("config.json"));
        Path netexSource = arguments.inputPath.resolve(
                arguments.fileName != null
                        ? arguments.fileName
                        : "netex.zip");
        try {
            validateNetex(conf, netexSource, arguments.outputPath);
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

    private void validateNetex(Configuration configuration,
                               Path netexSource,
                               Path outputsDirectory) throws RuleException {

        try (ZipFile zipFile = toZipFile(netexSource)) {
            NetexXMLParser netexXMLParser = new NetexXMLParser(configuration.ignorableNetexElements());
            NetexSchemaValidator netexSchemaValidator = new NetexSchemaValidator(configuration.maximumErrors());
            NetexValidatorsRunner netexValidatorsRunner = new NetexValidatorsRunner(netexXMLParser, netexSchemaValidator, List.of());

            List<ImmutableReport> reports = zipFile.stream()
                    .filter(e -> !e.isDirectory())
                    .map(zipEntry -> {
                        logger.debug("Extracting ZIP entry {} from archive...", zipEntry);
                        ImmutableReport.Builder report = ImmutableReport.builder()
                                .entry(zipEntry.getName());
                        try {
                            byte[] bytes = getEntryContents(zipFile, zipEntry);
                        ValidationReport vr = validateNetexEntry(configuration, netexValidatorsRunner, zipEntry, bytes);
                            report = report.validationReport(vr);
                        } catch (CorruptEntryException e) {
                            report.addErrors(serializeThrowable(e));
                        }
                        return report.build();
                    }).toList();
            produceReports(outputsDirectory, reports);
        } catch (IOException e) {
            throw new RuleException("Failed to close ZIP stream " + netexSource + " gracefully", e);
        }
    }

    private void produceReports(Path outputsDirectory, List<ImmutableReport> reports) throws RuleException {
        try {
            Files.writeString(outputsDirectory.resolve("reports.json"), objectMapper.writeValueAsString(reports));
        } catch (IOException e) {
            throw new RuleException("Failed to create file 'reports.json'", e);
        }
    }

    private ZipFile toZipFile(Path netexSource) throws RuleException {
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
            throw new RuleException("Failed to unzip provided NeTEx package " + netexSource, e1);
        }
        return zipFile;
    }

    private byte[] getEntryContents(ZipFile zipFile, ZipEntry zipEntry) throws CorruptEntryException {
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
            throw new CorruptEntryException("Failed to access file " + zipEntry.getName() + " within provided NeTEx package " + zipFile.getName(), e);
        }
        return bytes;
    }

    private ValidationReport validateNetexEntry(Configuration configuration,
                                                NetexValidatorsRunner netexValidatorsRunner,
                                                ZipEntry zipEntry,
                                                byte[] bytes) {
        return netexValidatorsRunner.validate(
                configuration.codespace(),
                configuration.reportId(),
                zipEntry.getName(),
                bytes);
    }

    private static String serializeThrowable(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

package fi.digitraffic.tis.rules.validation.netex;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fi.digitraffic.tis.rules.CorruptEntryException;
import fi.digitraffic.tis.rules.RuleException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmNode;
import org.entur.netex.validation.validator.NetexValidatorsRunner;
import org.entur.netex.validation.validator.Severity;
import org.entur.netex.validation.validator.SimpleValidationEntryFactory;
import org.entur.netex.validation.validator.ValidationIssue;
import org.entur.netex.validation.validator.ValidationReport;
import org.entur.netex.validation.validator.ValidationReportEntry;
import org.entur.netex.validation.validator.ValidationReportEntryFactory;
import org.entur.netex.validation.validator.ValidationRule;
import org.entur.netex.validation.validator.id.DefaultNetexIdRepository;
import org.entur.netex.validation.validator.id.IdVersion;
import org.entur.netex.validation.validator.id.NetexIdExtractorHelper;
import org.entur.netex.validation.validator.id.NetexIdUniquenessValidator;
import org.entur.netex.validation.xml.NetexXMLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    static final ValidationRule FINTRAFFIC_RULE_DUPLICATE_ID = new ValidationRule(
            "FINTRAFFIC_DUPLICATE_NETEX_ID",
            "NeTEx ID duplicated",
            "Duplicate element identifiers",
            Severity.ERROR
    );

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    private final ValidationReportEntryFactory validationReportEntryFactory = new SimpleValidationEntryFactory();

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
        Path netexSource = findInputFile(arguments);
        try {
            validateNetex(conf, netexSource, arguments.outputPath);
        } catch (RuleException e) {
            logger.error("Failed to process provided file", e);
        }
    }

    private static Path findInputFile(Arguments arguments) {
        // lookup alternatives: gtfs2netex may run this task as transitive, in which case we want to use its input
        Path converterInput = arguments.inputPath.resolve("gtfs2netex.fintraffic/result.zip");
        if (Files.exists(converterInput)) {
            return converterInput;
        }
        // use generic input
        String fileName = arguments.fileName != null
                ? arguments.fileName
                : "netex.zip";
        return arguments.inputPath.resolve(fileName);
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
            SimpleNetexSchemaValidator netexSchemaValidator = new SimpleNetexSchemaValidator(configuration.maximumErrors());
            NetexValidatorsRunner netexValidatorsRunner = NetexValidatorsRunner
                    .of()
                    .withNetexXMLParser(netexXMLParser)
                    .withNetexSchemaValidator(netexSchemaValidator)
                    .build();
            List<ImmutableReport> reports = zipFile.stream()
                    .filter(e -> !e.isDirectory())
                    .map(zipEntry -> {
                        logger.debug("Extracting ZIP entry {} from archive...", zipEntry);
                        ImmutableReport.Builder reportBuilder = ImmutableReport.builder()
                                .entry(zipEntry.getName());
                        try {
                            byte[] bytes = getEntryContents(zipFile, zipEntry);
                        ValidationReport vr = validateNetexEntry(netexXMLParser, configuration, netexValidatorsRunner, zipEntry, bytes);
                            reportBuilder = reportBuilder.validationReport(vr);
                        } catch (CorruptEntryException e) {
                            reportBuilder = reportBuilder.addErrors(serializeThrowable(e));
                        }
                        return reportBuilder.build();
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
            throw new RuleException("Failed to unzip provided NeTEx package " + netexSource, e1);
        }
        return zipFile;
    }

    private byte[] getEntryContents(ZipFile zipFile, ZipEntry zipEntry) throws CorruptEntryException {
        byte[] bytes;
        try {
            bytes = zipFile.getInputStream(zipEntry).readAllBytes();
        } catch (IOException e) {
            throw new CorruptEntryException("Failed to access file " + zipEntry.getName() + " within provided NeTEx package " + zipFile.getName(), e);
        }
        return bytes;
    }

    private ValidationReport validateNetexEntry(NetexXMLParser netexXMLParser,
                                                Configuration configuration,
                                                NetexValidatorsRunner netexValidatorsRunner,
                                                ZipEntry zipEntry,
                                                byte[] bytes) {
        ValidationReport report = netexValidatorsRunner.validate(
                configuration.codespace(),
                configuration.reportId(),
                zipEntry.getName(),
                bytes);

        report.addAllValidationReportEntries(validateDuplicateIds(
                netexXMLParser,
                bytes,
                zipEntry.getName()
        ));

        return report;
    }

    private static String serializeThrowable(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private List<ValidationReportEntry> validateDuplicateIds(NetexXMLParser netexXMLParser, byte[] fileContent, String filename) {
        XdmNode document = netexXMLParser.parseByteArrayToXdmNode(fileContent);
        XPathCompiler xPathCompiler = netexXMLParser.getXPathCompiler();
        List<IdVersion> localIds = NetexIdExtractorHelper.collectEntityIdentifiers(
                document,
                xPathCompiler,
                filename,
                Set.of("Codespace")
        );

        return duplicateIds(localIds)
                .map(idVersion -> new ValidationIssue(
                        FINTRAFFIC_RULE_DUPLICATE_ID,
                        idVersion.dataLocation()
                ))
                .map(validationReportEntryFactory::createValidationReportEntry)
                .toList();
    }

    private Stream<IdVersion> duplicateIds(List<IdVersion> idVersions) {
        Set<IdVersion> seen = new HashSet<>();
        return idVersions.stream().filter(idVersion -> !seen.add(idVersion));
    }
}

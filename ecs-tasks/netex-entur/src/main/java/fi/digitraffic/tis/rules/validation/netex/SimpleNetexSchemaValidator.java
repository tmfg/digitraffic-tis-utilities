package fi.digitraffic.tis.rules.validation.netex;

import org.entur.netex.validation.exception.NetexValidationException;
import org.entur.netex.validation.validator.DataLocation;
import org.entur.netex.validation.validator.Severity;
import org.entur.netex.validation.validator.ValidationIssue;
import org.entur.netex.validation.validator.ValidationRule;
import org.entur.netex.validation.validator.schema.NetexSchemaValidationContext;
import org.entur.netex.validation.validator.schema.NetexSchemaValidator;
import org.entur.netex.validation.xml.NetexSchemaRepository;
import org.rutebanken.netex.validation.NeTExValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleNetexSchemaValidator extends NetexSchemaValidator {
    private static final Logger logger = LoggerFactory.getLogger(
            SimpleNetexSchemaValidator.class
    );

    private final Map<NeTExValidator.NetexVersion, Schema> schemaCache = new ConcurrentHashMap<>();

    private final int maxValidationReportEntries;

    /**
     * @param maxValidationReportEntries the maximum number of entries reported. Additional entries are ignored.
     */
    public SimpleNetexSchemaValidator(int maxValidationReportEntries) {
        super(maxValidationReportEntries);
        this.maxValidationReportEntries = maxValidationReportEntries;
    }


    private Schema getSchema(NetexSchemaValidationContext validationContext) throws SAXException {
        NeTExValidator.NetexVersion schemaVersion = getNetexVersion(validationContext);
        return schemaCache.computeIfAbsent(schemaVersion, version -> {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            String resourceName = "xsd/"+ version +"/NeTEx_publication-NoConstraint.xsd";
            URL resource = NeTExValidator.class.getClassLoader().getResource(resourceName);
            try {
                return factory.newSchema(resource);
            } catch (SAXException e) {
                logger.error("Cannot load NeTEx schema {} {}", version, resourceName, e);
                throw new RuntimeException(e);
            }
        });
    }

    private NeTExValidator.NetexVersion getNetexVersion(NetexSchemaValidationContext validationContext) {
        NeTExValidator.NetexVersion version = NetexSchemaRepository.detectNetexSchemaVersion(validationContext.getFileContent());
        if (version != null) {
            return version;
        }
        logger.warn(
                "Could not detect schema version for file {}, defaulting to latest ({}})",
                validationContext.getFileName(),
                NeTExValidator.LATEST
        );
        return NeTExValidator.LATEST;
    }

    /***
     * Modifies original NetexSchemaValidator.validate -method so it resolves to schema version
     * without constraints.
     *
     * @param validationContext NetexSchemaValidationContext
     * @return List of validation issues
     */
    @Override
    public List<ValidationIssue> validate(NetexSchemaValidationContext validationContext) {
        logger.debug("Validating file {}", validationContext.getFileName());
        List<ValidationIssue> validationIssues = new ArrayList<>();
        try {

            Schema schema = getSchema(validationContext);
            Validator validator = schema.newValidator();
            validator.setErrorHandler(
                    new ErrorHandler() {
                        private int errorCount;

                        @Override
                        public void warning(SAXParseException exception) throws SAXParseException {
                            addValidationIssue(
                                    validationContext.getFileName(),
                                    exception,
                                    Severity.WARNING
                            );
                            errorCount++;
                        }

                        @Override
                        public void error(SAXParseException exception) throws SAXParseException {
                            addValidationIssue(
                                    validationContext.getFileName(),
                                    exception,
                                    Severity.CRITICAL
                            );
                            errorCount++;
                        }

                        @Override
                        public void fatalError(SAXParseException exception) throws SAXParseException {
                            error(exception);
                        }

                        private void addValidationIssue(
                                String fileName,
                                SAXParseException saxParseException,
                                Severity severity
                        ) throws SAXParseException {
                            if (errorCount < maxValidationReportEntries) {
                                String message = saxParseException.getMessage();
                                int line = saxParseException.getLineNumber();
                                int column = saxParseException.getColumnNumber();
                                DataLocation dataLocation = new DataLocation(null, fileName, line, column);
                                ValidationRule rule = severity == Severity.CRITICAL || severity == Severity.ERROR
                                        ? RULE_ERROR
                                        : RULE_WARNING;
                                validationIssues.add(new ValidationIssue(rule, dataLocation, message));
                            } else {
                                logger.warn(
                                        "File {} has too many schema validation errors (max is {}). Additional errors will not be reported.",
                                        fileName,
                                        maxValidationReportEntries
                                );
                                throw saxParseException;
                            }
                        }
                    }
            );
            validator.validate(new StreamSource(new ByteArrayInputStream(validationContext.getFileContent())));
        } catch (IOException e) {
            throw new NetexValidationException(e);
        } catch (SAXException saxException) {
            // Safe to ignore, errors are stored into validationIssues List
            logger.info("Found schema validation errors");
        }

        return validationIssues;
    }
}

package fi.digitraffic.tis.rules.validation.netex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.entur.netex.validation.validator.ValidationReportEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class EnturNetexValidatorTests {

    @Test
    void canValidateConvertedWalttiData() throws URISyntaxException, IOException {
        Path input = Path.of(Thread.currentThread().getContextClassLoader().getResource("waltti_netex_nordic.zip").toURI());

        Path output = Paths.get("/tmp", "/validatortests-1");

        Files.createDirectories(output);

        String[] args = {
                "--input", input.getParent().toString(),
                "--file", input.getFileName().toString(),
                "--output", output.toString()};
        EnturNetexValidator.main(args);
        cleanDirectory(output);
    }


    @Test
    void detectDuplicateId() throws URISyntaxException, IOException {
        Path input = Path.of(Thread.currentThread().getContextClassLoader().getResource("waltti-duplicate-id.zip").toURI());

        Path output = Paths.get("/tmp", "/validatortests-2");

        Files.createDirectories(output);

        String[] args = {
                "--input", input.getParent().toString(),
                "--file", input.getFileName().toString(),
                "--output", output.toString()};
        EnturNetexValidator.main(args);

        Path reportJson = output.resolve("reports.json");

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        ImmutableReport[] reports = mapper.readValue(Files.readAllBytes(reportJson), ImmutableReport[].class);

        Assertions.assertEquals(1, reports.length);
        Assertions.assertEquals(1, reports[0].validationReport().getValidationReportEntries().size());

        ValidationReportEntry entry = reports[0].validationReport().getValidationReportEntries().stream().findFirst().get();

        Assertions.assertEquals(28, entry.getLineNumber());
        Assertions.assertEquals("NeTEx ID duplicated", entry.getName());
        cleanDirectory(output);
    }

    private void cleanDirectory(Path dir) throws IOException {
        Files.walk(dir).forEach(path -> path.toFile().delete());
        dir.toFile().delete();
    }

}

package fi.digitraffic.tis.rules.validation.netex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.entur.netex.validation.validator.Severity;
import org.entur.netex.validation.validator.ValidationReportEntry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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

        ImmutableReport[] reports = readReports(output);
        // 1019 ZIP entries, 16 of them directories
        Assertions.assertEquals(1003, reports.length);
        Set<String> macMetadataEntries =
                Set.of("Jyväskylä/.DS_Store", "__MACOSX/Jyväskylä/._.DS_Store", "__MACOSX/Jyväskylä/._jyv_line_9011.xml");
        Assertions.assertEquals(
                macMetadataEntries,
                Arrays.stream(reports)
                        .filter(r -> !r.validationReport().getValidationReportEntries().isEmpty())
                        .map(r -> normalized(r.entry()))
                        .collect(Collectors.toSet()));
        for (ImmutableReport report : reports) {
            if (macMetadataEntries.contains(normalized(report.entry()))) {
                assertSingleCriticalSchemaError(
                        report.entry(), report.validationReport().getValidationReportEntries().stream().toList());
            }
        }
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

        ImmutableReport[] reports = readReports(output);

        Assertions.assertEquals(1, reports.length);
        Assertions.assertEquals(1, reports[0].validationReport().getValidationReportEntries().size());

        ValidationReportEntry entry = reports[0].validationReport().getValidationReportEntries().stream().findFirst().get();

        Assertions.assertEquals(28, entry.getLineNumber());
        Assertions.assertEquals("NeTEx ID duplicated", entry.getName());
        cleanDirectory(output);
    }

    @Test
    void reportsEntriesThatAreNotXmlWithoutAborting(@TempDir Path tempDir) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("valid.xml", validNetex());
        entries.put("notes.txt", "plain text".getBytes(StandardCharsets.UTF_8));
        entries.put(".DS_Store", new byte[] {0, 0, 0, 1, 'B', 'u', 'd', '1'});
        entries.put("__MACOSX/._x.xml", new byte[] {0x00, 0x05, 0x16, 0x07});

        ImmutableReport[] reports = validate(tempDir, entries);

        Assertions.assertEquals(4, reports.length);
        Assertions.assertTrue(findingsOf(reports, "valid.xml").isEmpty());
        for (String name : List.of("notes.txt", ".DS_Store", "__MACOSX/._x.xml")) {
            assertSingleCriticalSchemaError(name, findingsOf(reports, name));
        }
    }

    @Test
    void reportsMalformedXmlWithoutAborting(@TempDir Path tempDir) throws IOException {
        byte[] valid = validNetex();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("valid.xml", valid);
        entries.put("truncated.xml", Arrays.copyOf(valid, valid.length / 2));

        ImmutableReport[] reports = validate(tempDir, entries);

        Assertions.assertEquals(2, reports.length);
        Assertions.assertTrue(findingsOf(reports, "valid.xml").isEmpty());
        assertSingleCriticalSchemaError("truncated.xml", findingsOf(reports, "truncated.xml"));
    }

    @Test
    void detectsDuplicateIdInValidFileNextToUnparseableEntry(@TempDir Path tempDir) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("Joensuu/joe_line_01.xml", duplicateIdNetex());
        entries.put("notes.txt", "plain text".getBytes(StandardCharsets.UTF_8));

        ImmutableReport[] reports = validate(tempDir, entries);

        Assertions.assertEquals(2, reports.length);
        List<ValidationReportEntry> duplicateFindings = findingsOf(reports, "Joensuu/joe_line_01.xml");
        Assertions.assertEquals(1, duplicateFindings.size());
        Assertions.assertEquals("NeTEx ID duplicated", duplicateFindings.get(0).getName());
        assertSingleCriticalSchemaError("notes.txt", findingsOf(reports, "notes.txt"));
    }

    private static void assertSingleCriticalSchemaError(String entryName, List<ValidationReportEntry> findings) {
        Assertions.assertEquals(1, findings.size(), "findings for " + entryName);
        Assertions.assertEquals("NeTEx XML Schema validation error", findings.get(0).getName(), "finding name for " + entryName);
        Assertions.assertEquals(Severity.CRITICAL, findings.get(0).getSeverity(), "finding severity for " + entryName);
    }

    private static List<ValidationReportEntry> findingsOf(ImmutableReport[] reports, String entryName) {
        return Arrays.stream(reports)
                .filter(r -> r.entry().equals(entryName))
                .flatMap(r -> r.validationReport().getValidationReportEntries().stream())
                .toList();
    }

    private static String normalized(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC);
    }

    private static byte[] duplicateIdNetex() throws IOException {
        try (ZipFile zip = new ZipFile(fixture("waltti-duplicate-id.zip").toFile())) {
            return zip.getInputStream(zip.getEntry("Joensuu/joe_line_01.xml")).readAllBytes();
        }
    }

    /** The fixture file with its duplicated identifier removed, leaving valid NeTEx without findings. */
    private static byte[] validNetex() throws IOException {
        String xml = new String(duplicateIdNetex(), StandardCharsets.UTF_8);
        String[] lines = xml.split("\n", -1);
        // the duplicate is reported on line 28, so dropping that line's element is not safe: rename its id instead
        // (makes the id on line 28 unique)
        String original = lines[27];
        lines[27] = original.replaceFirst("id=\"([^\"]+)\"", "id=\"$1_unique\"");
        Assertions.assertNotEquals(original, lines[27], "fixture line 28 has no id attribute to rename");
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private ImmutableReport[] validate(Path tempDir, Map<String, byte[]> zipEntries) throws IOException {
        Path input = tempDir.resolve("input");
        Path output = tempDir.resolve("output");
        Files.createDirectories(input);
        Files.createDirectories(output);
        try (OutputStream out = Files.newOutputStream(input.resolve("netex.zip"));
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : zipEntries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        EnturNetexValidator.main(new String[] {
                "--input", input.toString(),
                "--file", "netex.zip",
                "--output", output.toString()});
        return readReports(output);
    }

    private static ImmutableReport[] readReports(Path output) throws IOException {
        Path reportJson = output.resolve("reports.json");
        Assertions.assertTrue(Files.exists(reportJson), "reports.json was not written (rule aborted?)");
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .readValue(Files.readAllBytes(reportJson), ImmutableReport[].class);
    }

    private static Path fixture(String name) throws IOException {
        try {
            return Path.of(Thread.currentThread().getContextClassLoader().getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private void cleanDirectory(Path dir) throws IOException {
        Files.walk(dir).forEach(path -> path.toFile().delete());
        dir.toFile().delete();
    }

}

package fi.digitraffic.tis.rules.validation.gbfs;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class EnturGbfsValidatorTests {

    @Disabled("GBFS is hard to test locally due to its autodiscovery component, so this test - which is a copy from Entur's NeTEx validator - is left as a marker for future")
    @Test
    void canValidateConvertedWalttiData() throws URISyntaxException, IOException {
        Path input = Path.of(Thread.currentThread().getContextClassLoader().getResource("waltti_netex_nordic.zip").toURI());

        Path output = Paths.get("/tmp", "/validatortests");

        Files.createDirectories(output);

        String[] args = {
                "--input", input.getParent().toString(),
                "--file", input.getFileName().toString(),
                "--output", output.toString()};
        EnturGbfsValidator.main(args);
    }

}

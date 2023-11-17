package fi.digitraffic.tis.rules.validation.netex;

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

        Path output = Paths.get("/tmp", "/validatortests");

        Files.createDirectories(output);

        String[] args = {
                "--input", input.getParent().toString(),
                "--file", input.getFileName().toString(),
                "--output", output.toString()};
        EnturNetexValidator.main(args);
    }

}

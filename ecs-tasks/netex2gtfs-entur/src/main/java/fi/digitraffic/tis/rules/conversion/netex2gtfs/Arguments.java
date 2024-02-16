package fi.digitraffic.tis.rules.conversion.netex2gtfs;

import com.beust.jcommander.Parameter;

import java.nio.file.Path;

/**
 * Command line arguments holder.
 */
class Arguments {
    @Parameter(names = {"-i", "--input"},
            required = true,
            description = "Local path for inputs")
    public Path inputPath;

    @Parameter(names = {"-o", "--output"},
            required = true,
            description = "Local path for outputs")
    public Path outputPath;

    @Override
    public String toString() {
        return "Arguments{" +
                "inputPath=" + inputPath +
                ", outputPath=" + outputPath +
                '}';
    }
}

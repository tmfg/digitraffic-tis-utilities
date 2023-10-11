package fi.digitraffic.tis.rules.validation.netex;

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

    @Parameter(names = {"-f", "--file"},
            required = false,
            description = "Exact name of the input file (without local path prefix)")
    public String fileName;

    @Parameter(names = {"-o", "--output"},
            required = true,
            description = "Local path for outputs")
    public Path outputPath;
}

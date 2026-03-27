package com.copybook;

import com.copybook.generator.JavaPojoGenerator;
import com.copybook.parser.CopybookParser;
import com.copybook.parser.model.CobolCopybook;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI entry point for the COBOL Copybook to Java POJO converter.
 *
 * <p>Usage:
 * <pre>
 *   copybook2pojo input.cpy -o ./output -p com.example.generated
 * </pre>
 */
@Command(
        name = "copybook2pojo",
        mixinStandardHelpOptions = true,
        version = "copybook2pojo 1.0.0",
        description = "Converts COBOL copybook files into Java POJO classes."
)
public class Copybook2Pojo implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to the COBOL copybook file (.cpy, .cbl, .txt)")
    private Path inputFile;

    @Option(names = {"-o", "--output"}, description = "Output directory for generated Java files (default: ./generated)",
            defaultValue = "./generated")
    private Path outputDir;

    @Option(names = {"-p", "--package"}, description = "Java package name for generated classes",
            defaultValue = "")
    private String packageName;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    @Override
    public Integer call() {
        try {
            if (!Files.exists(inputFile)) {
                System.err.println("Error: Input file not found: " + inputFile);
                return 1;
            }

            if (verbose) {
                System.out.println("Parsing copybook: " + inputFile);
            }

            // Parse the copybook
            CopybookParser parser = new CopybookParser();
            CobolCopybook copybook = parser.parse(inputFile);

            if (verbose) {
                System.out.println("Found " + copybook.getRecords().size() + " record(s)");
                System.out.println("Found " + copybook.getStandaloneItems().size() + " standalone item(s)");
            }

            // Generate Java POJOs
            JavaPojoGenerator generator = new JavaPojoGenerator(packageName);
            Map<String, String> files = generator.generate(copybook);

            // Write to disk
            generator.writeTo(outputDir);

            System.out.println("Generated " + files.size() + " Java file(s) in " + outputDir.toAbsolutePath());
            for (String fileName : files.keySet()) {
                System.out.println("  -> " + fileName);
            }

            return 0;

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 2;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Copybook2Pojo()).execute(args);
        System.exit(exitCode);
    }
}

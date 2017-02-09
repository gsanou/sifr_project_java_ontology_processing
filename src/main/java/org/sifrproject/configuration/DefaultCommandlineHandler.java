package org.sifrproject.configuration;


import org.apache.commons.cli.*;
import org.sifrproject.cli.OntologyCUIProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class DefaultCommandlineHandler implements CommandlineHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCommandlineHandler.class);

    private static final Options options; // Command line op
    private static final String OUTPUT_FILE_SUFFIX_OPTION = "o";
    private static final String DEFAULT_OUTPUT_FILE_SUFFIX = ".ttl";

    private static final String BY_DEFAULT = " by default.";
    public static final String CONFIG_OUTPUT_FILE_SUFFIX = "config.output_file_suffix";

    //Registering options for the posix command line parser
    private CommandLine commandLine; // Command Line arguments

    static {
        options = new Options();
        options.addOption("h", false, "Prints usage and exits. ");
        options.addOption(OUTPUT_FILE_SUFFIX_OPTION, true, "if present, use the specified value as the filename suffix for the output "
                + "." + DEFAULT_OUTPUT_FILE_SUFFIX + BY_DEFAULT);
    }


    /**
     * Print the command line usage information
     */
    private static void printUsage() {
        final HelpFormatter formatter = new HelpFormatter();
        final String help =
                "urlOrFile must point on an OWL ontology ";
        //noinspection HardcodedFileSeparator
        formatter.printHelp("java -cp /path/to/jar org.sifrproject.cli.OntologyCUIProcessor [OPTIONS] urlOrFile ...",
                "With OPTIONS in:", options,
                help, false);
    }

    /**
     * Validate the number of arguments and the presence of mandatory arguments
     */
    private void validateArguments() {
        if (commandLine.getArgs().length == 0) {
            logger.error("Missing model files or URL.");
            printUsage();
            System.exit(1);
        }

        if (commandLine.hasOption("h")) {
            printUsage();
            System.exit(0);
        }
    }

    /**
     * Load the input ontology to process in a Jena OntModel, supports local uncompressed files, bziped/gzipped files and
     * remote files over http
     */
    private String getOntologyURL() {
        String URL = "";
        if(commandLine.getArgs().length > 0) {
            URL =  commandLine.getArgs()[0];
        } else {
            printUsage();
            System.exit(1);
        }
        return URL;
    }

    @Override
    public void processCommandline(final String[] args, final Properties properties) {
        //Parse Command line options
        final CommandLineParser parser = new PosixParser();
        try {
            commandLine = parser.parse(options, args);
        } catch (final ParseException e) {
            logger.error("Error parsing arguments: {}", e.getLocalizedMessage());
            printUsage();
            System.exit(1);
        }

        //Validate number of arguments and help argument
        validateArguments();

        final String outputFileSuffix = commandLine.getOptionValue(OUTPUT_FILE_SUFFIX_OPTION, DEFAULT_OUTPUT_FILE_SUFFIX);
        final String ontologyURL = getOntologyURL();
        properties.put(OntologyCUIProcessor.CONFIG_SOURCE_ENDPOINT, ontologyURL);
        properties.put(CONFIG_OUTPUT_FILE_SUFFIX, outputFileSuffix);

    }
}

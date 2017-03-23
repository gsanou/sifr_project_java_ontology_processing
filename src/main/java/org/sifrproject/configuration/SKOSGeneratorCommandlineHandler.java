package org.sifrproject.configuration;


import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static org.sifrproject.configuration.ConfigurationConstants.CONFIG_OUTPUT_FILE_SUFFIX;
import static org.sifrproject.configuration.SKOSGeneratorConfigurationConstants.CONFIG_LANGUAGE;

public class SKOSGeneratorCommandlineHandler implements CommandlineHandler {

    private static final Logger logger = LoggerFactory.getLogger(SKOSGeneratorCommandlineHandler.class);

    private static final Options options; // Command line op
    private static final String OUTPUT_FILE_SUFFIX_OPTION = "o";
    private static final String DEFAULT_OUTPUT_FILE_SUFFIX = ".ttl";

    private static final String LANGUAGE_OPTION = "l";
    private static final String DEFAULT_LANGUAGE = "en";


    private static final String BY_DEFAULT = CUIProcessorCommandlineHandler.BY_DEFAULT;


    //Registering options for the posix command line parser
    private CommandLine commandLine; // Command Line arguments

    static {
        options = new Options();
        options.addOption("h", false, CUIProcessorCommandlineHandler.PRINTS_USAGE_AND_EXITS);
        options.addOption(LANGUAGE_OPTION,true,"Language of the dictionary (ISO 2 letter code)");
        options.addOption(OUTPUT_FILE_SUFFIX_OPTION, true, CUIProcessorCommandlineHandler.IF_PRESENT_USE_THE_SPECIFIED_VALUE_AS_THE_FILENAME_SUFFIX_FOR_THE_OUTPUT
                + "." + DEFAULT_OUTPUT_FILE_SUFFIX + BY_DEFAULT);
    }


    /**
     * Print the command line usage information
     */
    private static void printUsage() {
        final HelpFormatter formatter = new HelpFormatter();
        @SuppressWarnings("HardcodedFileSeparator") final String help =
                "dictionaryFile must point to a CSV dictionary file in the eHealthFormat";
        //noinspection HardcodedFileSeparator
        formatter.printHelp("java -cp /path/to/jar org.sifrproject.cli.generation.EHealth2017DictionaryToSkos [OPTIONS] dictionaryFile.csv ...",
                CUIProcessorCommandlineHandler.WITH_OPTIONS_IN, options,
                help, false);
    }

    /**
     * Validate the number of arguments and the presence of mandatory arguments
     */
    private void validateArguments() {
        if (commandLine.getArgs().length == 0) {
            logger.error("Missing Dictionary file");
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
    private String getDictionaryPath() {
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
            logger.error("Error parsing commandline arguments {}", e.getLocalizedMessage());
            printUsage();
            System.exit(1);
        }

        //Validate number of arguments and help argument
        validateArguments();

        final String outputFileSuffix = commandLine.getOptionValue(OUTPUT_FILE_SUFFIX_OPTION, DEFAULT_OUTPUT_FILE_SUFFIX);
        final String language = commandLine.getOptionValue(LANGUAGE_OPTION,DEFAULT_LANGUAGE);
        final String dictionaryPath = getDictionaryPath();
        properties.put(SKOSGeneratorConfigurationConstants.CONFIG_DICTIONARY_PATH, dictionaryPath);
        properties.put(CONFIG_OUTPUT_FILE_SUFFIX, outputFileSuffix);
        properties.put(CONFIG_LANGUAGE,language);
    }
}

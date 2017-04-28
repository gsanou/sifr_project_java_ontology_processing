package org.sifrproject.configuration;


import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static org.sifrproject.configuration.ConfigurationConstants.CONFIG_OUTPUT_FILE_SUFFIX;
import static org.sifrproject.configuration.SKOSGeneratorConfigurationConstants.*;

public class SKOSGeneratorCommandlineHandler implements CommandlineHandler {

    private static final Logger logger = LoggerFactory.getLogger(SKOSGeneratorCommandlineHandler.class);

    private static final Options options; // Command line op
    private static final String OUTPUT_FILE_SUFFIX_OPTION = "o";
    private static final String DEFAULT_OUTPUT_FILE_SUFFIX = ".ttl";

    private static final String LANGUAGE_OPTION = "l";
    private static final String DEFAULT_LANGUAGE = "en";

    private static final String DICTIONARY_OPTION = "d";
    private static final String CORPUS_RAW_OPTION = "cr";
    private static final String CORPUS_STD_OPTION = "cs"; //-cs
    private static final String CORPUS_ADAPTED_OPTION = "a"; //-a
    private static final String CORPUS_MOST_FREQUENT_CODE_OPTION = "mfc"; //-mfc


    private static final String BY_DEFAULT = CUIProcessorCommandlineHandler.BY_DEFAULT;


    //Registering options for the posix command line parser
    private CommandLine commandLine; // Command Line arguments

    static {
        options = new Options();
        options.addOption("h", false, CUIProcessorCommandlineHandler.PRINTS_USAGE_AND_EXITS);
        options.addOption(DICTIONARY_OPTION,false,"Include dictionary labels");
        options.addOption(CORPUS_RAW_OPTION,false,"Include raw corpus lines");
        options.addOption(CORPUS_STD_OPTION,false,"Include standardised corpus descriptions");
        options.addOption(CORPUS_ADAPTED_OPTION,false,"Smart selection between raw and standardised text from the " +
                "corpus, requires -"+CORPUS_RAW_OPTION+" and -"+CORPUS_STD_OPTION+" to be enabled");
        options.addOption(CORPUS_MOST_FREQUENT_CODE_OPTION,false,"Include text only for the most frequent codes, when several text segments share the same code");
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


    private String getCorpusPath() {
        String URL = "";
        if(commandLine.getArgs().length > 1) {
            URL =  commandLine.getArgs()[1];
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
        final String corpusPath = getCorpusPath();
        properties.put(CONFIG_DICTIONARY_PATH, dictionaryPath);
        properties.put(CONFIG_CORPUS_PATH, corpusPath);
        properties.put(CONFIG_OUTPUT_FILE_SUFFIX, outputFileSuffix);
        properties.put(CONFIG_LANGUAGE,language);

        if(commandLine.hasOption(DICTIONARY_OPTION)){
            properties.put(CONFIG_DICTIONARY,"true");
        }


        if(commandLine.hasOption(CORPUS_STD_OPTION)){
            properties.put(CONFIG_CORPUS_STD,"true");
        }


        if(commandLine.hasOption(CORPUS_RAW_OPTION)){
            properties.put(CONFIG_CORPUS_RAW,"true");
        }

        if(commandLine.hasOption(CORPUS_ADAPTED_OPTION)){
            if(!commandLine.hasOption(CORPUS_STD_OPTION) || ! commandLine.hasOption(CORPUS_RAW_OPTION)){
                logger.error("-a requires both -cr and -cs to be enabled");
                printUsage();
                System.exit(1);
            }
            if(commandLine.hasOption(CORPUS_MOST_FREQUENT_CODE_OPTION)){
                logger.error("-a is incompatible with -mfc");
                printUsage();
                System.exit(1);
            }
            properties.put(CONFIG_CORPUS_ADAPTED, "true");
        }

        if(commandLine.hasOption(CORPUS_MOST_FREQUENT_CODE_OPTION)){
            properties.put(CONFIG_MOST_FREQUENT_CODE, "true");
        }
    }
}

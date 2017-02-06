package org.sifrproject.cli;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import org.apache.commons.cli.*;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.sifrproject.ontology.OntologyDelegate;
import org.sifrproject.ontology.OntologyDelegateImpl;
import org.sifrproject.ontology.SQLUMLSDelegate;
import org.sifrproject.ontology.UMLSDelegate;
import org.sifrproject.stats.OntologyStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hello world!
 */
@SuppressWarnings("ClassWithTooManyFields")
public final class ProcessOntology {
    private static final Logger logger = LoggerFactory.getLogger(ProcessOntology.class);
    private static final Pattern URL_PATTERN = Pattern.compile("[^:]{2,6}:.*");

    private static final Options options; // Command line op
    private static final String RDF_FORMAT_OPTION = "f";
    private static final String DEFAULT_RDF_FORMAT = "turtle";
    private static final String OUTPUT_FILE_SUFFIX_OPTION = "o";
    private static final String DEFAULT_OUTPUT_FILE_SUFFIX = ".ttl";


    private static final String BY_DEFAULT = " by default.";
    private static final int AMBIGUITY_THRESHOLD = 2;

    private CommandLine commandLine; // Command Line arguments

    static {
        options = new Options();
        options.addOption("h", false, "Prints usage and exits. ");
        options.addOption(RDF_FORMAT_OPTION, true, "RDF file format (xmlrdf, turtle, n3, etc.). " + DEFAULT_RDF_FORMAT + BY_DEFAULT);
        options.addOption(OUTPUT_FILE_SUFFIX_OPTION, true, "if present, use the specified value as the filename suffix for the output "
                + "." + DEFAULT_OUTPUT_FILE_SUFFIX + BY_DEFAULT);
        //StoreHandler.DEBUG_ON = true;
    }

    private String rdfFormat;
    private String outputFileSuffix;
    private String ontologyName;
    @SuppressWarnings("InstanceVariableOfConcreteClass")
    private OntologyStats ontologyStats;
    private PrintStream statsOutput;

    private OntModel sourceModel;
    private OntologyDelegate ontologyDelegate;
    private UMLSDelegate umlsDelegate;

    private final Map<String, Collection<String>> cuisToAdd;
    private final Map<String, Collection<String>> tuisToAdd;

    private ProcessOntology() {
        cuisToAdd = new HashMap<>();
        tuisToAdd = new HashMap<>();
    }

    private static void printUsage() {
        final HelpFormatter formatter = new HelpFormatter();
        final String help =
                "urlOrFile must point on an OWL ontology ";
        //noinspection HardcodedFileSeparator
        formatter.printHelp("java -cp /path/to/jar org.sifrproject.cli.ProcessOntology [OPTIONS] urlOrFile ...",
                "With OPTIONS in:", options,
                help, false);
    }

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

    private void initializeStatsFile() {

            final String fileName = "stats_"+ontologyName+".csv";
            try {
                statsOutput = new PrintStream(fileName, "UTF-8");
            } catch (final FileNotFoundException e) {
                logger.error("Cannot output statistics to file {}", fileName);
                System.exit(1);
            } catch (final UnsupportedEncodingException e) {
                // Should never happen
                System.exit(1);
            }
            ontologyStats = OntologyStats.stats;
    }

    private void loadModel() {
        final String[] remainingArgs = commandLine.getArgs();
        for (final String arg : remainingArgs) {
            sourceModel = ModelFactory.createOntologyModel();
            try {
                logger.info("Reading ontology file: {}", arg);
                final Matcher matcher = URL_PATTERN.matcher(arg);
                if (matcher.matches()) {
                    // It's an URL
                    sourceModel.read(arg);
                } else {
                    // It's a file
                    if (arg.endsWith(".bz2")) {
                        final InputStreamReader modelReader = new InputStreamReader(new BZip2CompressorInputStream(new FileInputStream(arg)));
                        sourceModel.read(modelReader, null, rdfFormat);
                    } else {
                        final InputStreamReader modelReader = new InputStreamReader(new FileInputStream(arg));
                        sourceModel.read(modelReader, null, rdfFormat);
                    }
                    final Path ontologyPath = Paths.get(arg);
                    final Path filePath = ontologyPath.getFileName();
                    final String fileName = filePath.toString();
                    ontologyName = fileName.split("\\.")[0];
                }

            } catch (final FileNotFoundException e) {
                logger.error("Could not read {}", remainingArgs[0]);
                System.exit(1);
            } catch (final IOException e) {
                logger.error(e.getLocalizedMessage());
            }
        }
    }

    private void processCommandLineArguments(final String[] args) {
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

        //Process RDF format argument
        rdfFormat = commandLine.getOptionValue(RDF_FORMAT_OPTION, DEFAULT_RDF_FORMAT);
        rdfFormat = rdfFormat.toUpperCase();

        outputFileSuffix = commandLine.getOptionValue(OUTPUT_FILE_SUFFIX_OPTION, DEFAULT_OUTPUT_FILE_SUFFIX);

        loadModel();
        initializeStatsFile();
    }

    private void processConfiguration() {

        final JedisPool jedisPool = new JedisPool("localhost");
        try {
            final Properties properties = new Properties();
            properties.load(ProcessOntology.class.getResourceAsStream("/config.properties"));


            final String jdbc = properties.getProperty("config.umls_jdbc");
            final String dbUser = properties.getProperty("config.umls_user");
            final String dbpass = properties.getProperty("config.umls_password");
            final String dbumls = properties.getProperty("config.umls_db");
            umlsDelegate = new SQLUMLSDelegate(jdbc, dbUser, dbpass, dbumls, jedisPool);

            final String targetEndpoint = properties.getProperty("config.target_endpoint");
            final String mappingsEndpoint = properties.getProperty("config.mappings_endpoint");
            try {
                final OntModel targetModel = createModel(targetEndpoint);
                final OntModel mappingsModel = createModel(mappingsEndpoint);
                ontologyDelegate = new OntologyDelegateImpl(mappingsModel, targetModel, sourceModel, jedisPool);
            } catch (final IOException e) {
                logger.error("Cannot access target sparql endpoint: {}", e.getLocalizedMessage());
                System.exit(1);
            }


        } catch (final IOException e) {
            logger.error("Cannot find config.properties in classpath.");
            System.exit(1);
        }
    }

    private OntModel createModel(final String storeURI) throws IOException {
        final Path path = Paths.get(storeURI);
        final OntModel ontModel;
        if (Files.isDirectory(path)) {

            final Dataset dataset = TDBFactory.createDataset(path.toString());
            dataset.begin(ReadWrite.READ);
            final Model model = dataset.getDefaultModel();
            dataset.end();
            ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF, model);

        } else {
            ontModel = ModelFactory.createOntologyModel();
            ontModel.read(path.toString());

        }
        return ontModel;
    }

    private void output() {
        final String outputModelFileName = ontologyName+"_enriched" + outputFileSuffix;

        try {
            final OutputStream outputModelStream = new FileOutputStream(outputModelFileName);
            sourceModel.write(outputModelStream, rdfFormat);
            outputModelStream.close();

        } catch (final FileNotFoundException e) {
            logger.error("Could not create output stream: {}", e.getLocalizedMessage());
        } catch (final IOException e) {
            logger.error("IOException while creating output stream: {}", e.getLocalizedMessage());
        }
    }

    @SuppressWarnings({"HardcodedFileSeparator", "FeatureEnvy"})
    private void writeStatistics() {
        statsOutput.println("Ontology ,#Classes,w/o CUI,w/o TUI,CUI in altLabel,CUI in mappings, Ambiguous CUI, #Classes remaining without CUI, #Classes remaining without TUI");
        final StringBuilder builder = new StringBuilder();
        builder.append(ontologyName);
        builder.append(",");
        builder.append(ontologyStats.getTotalClassCount());
        builder.append(",");
        builder.append(ontologyStats.getClassesWithoutCUI());
        builder.append(",");
        builder.append(ontologyStats.getClassesWithoutTUI());
        builder.append(",");
        builder.append(ontologyStats.getClassesWithCUIInAltLabel());
        builder.append(",");
        builder.append(ontologyStats.getClassesWithCUIInMappings());
        builder.append(",");
        builder.append(ontologyStats.getClassesWithAmbiguousCUI());
        builder.append(",");
        builder.append(ontologyStats.getClassesRemainingWithoutCUI());
        builder.append(",");
        builder.append(ontologyStats.getClassesRemainingWithoutTUI());
        statsOutput.println(builder);
    }

    private void startProcessing() {
        final ExtendedIterator<OntClass> classes = sourceModel.listNamedClasses();
        final List<OntClass> classList = classes.toList();
        int currentClass = 0;
        for(final RDFNode thisClass: classList) {
            final double progress = (100d*currentClass)/(double)classList.size();
            ontologyStats.incrementTotalClassCount();
            logger.info(String.format("[%.2f] Processing: %s", progress  ,thisClass));
            final Collection<String> cuis = processCUIs(thisClass);
            processTUIs(thisClass, cuis);
            currentClass++;
        }
    }

    private void processTUIs(final RDFNode thisClass, final Collection<String> cuis) {
        Collection<String> tuis;
        if (cuis.isEmpty()) {
            tuis = new ArrayList<>();
            ontologyDelegate.tuisFromModel(sourceModel,thisClass.toString(),tuis);
            if (tuis.isEmpty()) {
                tuis = ontologyDelegate.findTUIsForMappings(thisClass.toString());
                if (tuis.isEmpty()) {
                    ontologyStats.incrementClassesRemainingWithoutTUI();
                }
            } else {
                logger.info("\t{} TUIs found!",cuis.size());
            }
        } else {
            tuis = umlsDelegate.getTUIsForCUIs(cuis);
            ontologyStats.incrementClassesWithoutTUI();
        }
        logger.info("\tAdded {} TUIs", tuis.size());
        tuisToAdd.put(thisClass.toString(), tuis);
    }

    private Collection<String> processCUIs(final RDFNode thisClass) {

        final Collection<String> cuis = new ArrayList<>();
        ontologyDelegate.cuisFromModel(sourceModel,thisClass.toString(),cuis);
        if (cuis.isEmpty()) {
            ontologyStats.incrementClassesWithoutCUI();
            logger.info("\tLooking for CUIs...");
            cuis.addAll(findCUIs(thisClass.toString()));
        } else {
            logger.info("\t{} CUIs found!",cuis.size());
        }
        if (cuis.isEmpty()) {
            logger.info("\tFalling back on UMLS to find CUIs...");
            //final List<CUITerm> conceptNameCUIMap = new ArrayList<>();
            //Collections.copy(conceptNameCUIMap, umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH));

            //final TermSimilarityRanker termSimilarityRanker = new TverskiTermSimilarityRanker();
            //termSimilarityRanker.rankBySimilarity(conceptNameCUIMap, "");

            //TODO: find candidates in UMLS
        }

        //Too many CUIs, trying to use UMLS to determine the most relevant ones
        if (cuis.size() > AMBIGUITY_THRESHOLD) {
            ontologyStats.incrementClassesWithAmbiguousCUI();
            logger.error("AMBIGUOUS!!!");
        }

        logger.info("\tAdding {} CUIs to model...", cuis.size());
        tuisToAdd.put(thisClass.toString(), cuis);
        return cuis;
    }

    @SuppressWarnings("FeatureEnvy")
    private Collection<String> findCUIs(final String classURI) {

        final List<String> cuis = new ArrayList<>(ontologyDelegate.findCUIsInAltLabel(classURI));
        logger.info("\t\tIn altLabel...");
        if (cuis.isEmpty()) {
            logger.info("\t\tIn mappings...");
            cuis.addAll(ontologyDelegate.findCUIsForMappings(classURI));
            if (cuis.isEmpty()) {
                ontologyStats.incrementClassesRemainingWithoutCUI();
            } else {
                ontologyStats.incrementClassesWithCUIInMappings();
            }
        } else {
            logger.info("\t\t Found {} CUIs in altLabel.", cuis.size());
            ontologyStats.incrementClassesWithCUIInAltLabel();
        }

        return cuis;
    }

    private void updateModel() {
        for (final Map.Entry<String, Collection<String>> tuiEntry : tuisToAdd.entrySet()) {
            for (final String tui : tuiEntry.getValue()) {
                ontologyDelegate.addTUIToModel(tuiEntry.getKey(),tui,sourceModel);
            }
        }

        for (final Map.Entry<String, Collection<String>> cuiEntry : cuisToAdd.entrySet()) {
            for (final String cui : cuiEntry.getValue()) {
                ontologyDelegate.addCUIToModel(cuiEntry.getKey(),cui,sourceModel);
            }
        }
    }


    public static void main(final String[] args) {
        @SuppressWarnings("LocalVariableOfConcreteClass") final ProcessOntology processOntology = new ProcessOntology();
        processOntology.processCommandLineArguments(args);
        processOntology.processConfiguration();
        processOntology.startProcessing();
        processOntology.updateModel();
        processOntology.writeStatistics();
        processOntology.output();
    }


}

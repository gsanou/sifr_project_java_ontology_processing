package org.sifrproject.cli;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.rdf.model.RDFNode;
import org.sifrproject.configuration.CommandlineHandler;
import org.sifrproject.configuration.DefaultCommandlineHandler;
import org.sifrproject.ontology.*;
import org.sifrproject.ontology.matching.CUITerm;
import org.sifrproject.ontology.matching.PooledTermSimilarityRanker;
import org.sifrproject.ontology.matching.TverskiTermSimilarityRanker;
import org.sifrproject.stats.CUIOntologyStats;
import org.sifrproject.stats.StatsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Hello world!
 */
@SuppressWarnings("ClassWithTooManyFields")
public final class OntologyCUIProcessor implements OntologyProcessor {
    private static final Logger logger = LoggerFactory.getLogger(OntologyCUIProcessor.class);

    //Configuration constants
    public static final String CONFIG_SOURCE_ENDPOINT = "config.source_endpoint";
    private static final String CONFIG_TARGET_ENDPOINT = "config.target_endpoint";
    private static final String CONFIG_MAPPINGS_ENDPOINT = "config.mappings_endpoint";
    private static final String CONFIG_UMLS_JDBC = "config.umls_jdbc";
    private static final String CONFIG_UMLS_USER = "config.umls_user";
    private static final String CONFIG_UMLS_PASSWORD = "config.umls_password";
    private static final String CONFIG_UMLS_DB = "config.umls_db";
    private static final String CONFIG_REDIS_HOST = "config.redis_host";
    private static final String CONFIG_REDIS_CLUSTER_BASE_PORT = "config.redis_cluster_base_port";
    private static final String CONFIG_REDIS_CLUSTER_NODES = "config.redis_cluster_nodes";

    private static final String CUI_ADDED_AUTOMATICALLY_NOTE = "The CUI(s) and TUI(s) have been added through an automated matching process";

    private final StatsHandler ontologyStats;

    private final OntologyDelegate ontologyDelegate;
    private final UMLSDelegate umlsDelegate;

    private final Map<String, Collection<String>> cuisToAdd;
    private final Map<String, Collection<String>> tuisToAdd;

    private final List<String> cuiAddedNotesToAdd;

    private final PooledTermSimilarityRanker termSimilarityRanker;

    private PrintWriter noCUIConceptsWriter;

    private boolean disambiguate;
    private boolean match;

    private final AtomicInteger progressCount;
    private int totalClasses;



    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private OntologyCUIProcessor(final Properties properties) {
        cuisToAdd = new HashMap<>();
        tuisToAdd = new HashMap<>();
        cuiAddedNotesToAdd = new ArrayList<>(10000);

        progressCount = new AtomicInteger();

        logger.info("Initializing UMLS SQL Interface...");

        final JedisCluster jedisCluster = initializeRedisCluster(
                properties.getProperty(CONFIG_REDIS_HOST),
                Integer.valueOf(properties.getProperty(CONFIG_REDIS_CLUSTER_BASE_PORT)),
                Integer.valueOf(properties.getProperty(CONFIG_REDIS_CLUSTER_NODES))
        );


        /*
         * Creating the UMLS delegate to access UMLS over SQL
         */
        final String jdbc = properties.getProperty(CONFIG_UMLS_JDBC);
        final String dbUser = properties.getProperty(CONFIG_UMLS_USER);
        final String dbpass = properties.getProperty(CONFIG_UMLS_PASSWORD);
        final String dbumls = properties.getProperty(CONFIG_UMLS_DB);
        umlsDelegate = new SQLUMLSDelegate(jdbc, dbUser, dbpass, dbumls, jedisCluster);


        logger.info("Initializing Source, Mappings and Target ontologies...");
            /*
             * Instantiating target and mappings models, can be a file or a Jena triple database (TDB)
             */
        final String sourceEndpoint = properties.getProperty(CONFIG_SOURCE_ENDPOINT);
        final String targetEndpoint = properties.getProperty(CONFIG_TARGET_ENDPOINT);
        final String mappingsEndpoint = properties.getProperty(CONFIG_MAPPINGS_ENDPOINT);

        final String outputFileSuffix = properties.getProperty(DefaultCommandlineHandler.CONFIG_OUTPUT_FILE_SUFFIX);

        ontologyDelegate = new OntologyDelegateImpl(mappingsEndpoint, targetEndpoint, sourceEndpoint, outputFileSuffix, jedisCluster);
        final String ontologyName = ontologyDelegate.getOntologyName();

        logger.info("Initializing similarity ranker...");
        termSimilarityRanker = new TverskiTermSimilarityRanker(jedisCluster);

        try {
            noCUIConceptsWriter = new PrintWriter(ontologyName + "_concepts_without_cui.txt");
        } catch (final FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
        }

        if (properties.containsKey(DefaultCommandlineHandler.CONFIG_DISAMBIGUATE)) {
            disambiguate = true;
        }
        if (properties.containsKey(DefaultCommandlineHandler.CONFIG_MATCH)) {
            match = true;
        }

        logger.info("Initializing statistics module...");
        ontologyStats = new CUIOntologyStats(ontologyName);
    }

    private JedisCluster initializeRedisCluster(final String host, final int startPort, final int numberOfNodes){
        final Set<HostAndPort> jedisClusterNodes = new HashSet<>();
        for(int i=1;i<=numberOfNodes;i++) {
            jedisClusterNodes.add(new HostAndPort(host, startPort+i));
        }
        return new JedisCluster(jedisClusterNodes);
    }


    /**
     * Process the ontology classes to look for CUIs and TUIs
     */
    @Override
    public void processOntology() {
        logger.info("Loading class list in memory...");
        final List<OntClass> classList = ontologyDelegate.getSourceClasses();
        totalClasses = classList.size();
        logger.info("Processing {} classes...", totalClasses);
        //Iterate over all classes in parallel, automatically dispatched by the java stream on all available CPUs
        final Stream<OntClass> ontClassStream = classList.parallelStream();
        ontClassStream.forEachOrdered(this::processClass);
        logger.info("Done!");
        logger.info("Writing statistics to file...");
        //Writing stats
        ontologyStats.writeStatistics();
    }

    private void processClass(final OntClass thisClass) {
        ontologyStats.incrementStatistic(CUIOntologyStats.TOTAL_CLASS_COUNT_STATISTIC);
        logger.debug(String.format("Processing: %s", thisClass));
        final Collection<String> cuis = processCUIs(thisClass);
        processTUIs(thisClass, cuis);
        final double progress = (progressCount.incrementAndGet()/(double) totalClasses)*100;
        //noinspection UseOfSystemOutOrSystemErr,HardcodedLineSeparator
        System.out.print(String.format("\rProcessing %.2f%%",progress));
    }

    /**
     * Process classes by successively looking for CUIs in the source model and if it isn't found tries to find them in altLabels and mappings.
     * Generates the corresponding statistics
     *
     * @param thisClass The current class to process
     * @return A collection of string containing the CUIs found
     */
    private Collection<String> processCUIs(final RDFNode thisClass) {

        final Collection<String> cuis = new ArrayList<>();
        ontologyDelegate.cuisFromSourceModel(thisClass.toString(), cuis);
        if (cuis.isEmpty()) {
            ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITHOUT_CUI_STATISTIC);
            logger.debug("\tLooking for CUIs...");
            cuis.addAll(findCUIs(thisClass.toString()));
        } else {
            logger.info("\t{} CUIs found!", cuis.size());
        }

        if (cuis.isEmpty() && match) {
            logger.debug("\tFalling back on UMLS to find CUIs...");
            disambiguate(cuis, thisClass);
            if (!cuis.isEmpty()) {
                cuiAddedNotesToAdd.add(thisClass.toString());
            }
        }

        //Too many CUIs, trying to use UMLS to determine the most relevant ones
        final Collection<String> ambiguousCuis = new ArrayList<>(cuis);
        if (cuis.size() > 1) {
            ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_AMBIGUOUS_CUI_STATISTIC);
            logger.debug("Ambiguous CUIs");

            disambiguate(ambiguousCuis, thisClass);
            if (disambiguate) {
                cuis.clear();
                cuis.addAll(ambiguousCuis);
            }
        }

        logger.debug("\tAdding {} CUIs to model...", cuis.size());
        synchronized (cuisToAdd) {
            cuisToAdd.put(thisClass.toString(), cuis);
        }
        return cuis;
    }

    @SuppressWarnings({"unused", "FeatureEnvy"})
    private void disambiguate(final Collection<String> cuis, final RDFNode thisClass) {

        final String conceptDescription = ontologyDelegate.conceptLabelFromSourceModel(thisClass.asResource());
        final List<CUITerm> conceptNameCUIMap = (cuis.isEmpty()) ?
                umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH) :
                umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH, cuis);

        if (!conceptNameCUIMap.isEmpty()) {
            termSimilarityRanker.rankBySimilarity(conceptNameCUIMap, conceptDescription);

            final CUITerm cuiTerm = conceptNameCUIMap.get(0);
            if (!cuis.isEmpty()) {
                final Collection<String> umlsCUIs = umlsDelegate.getUMLSCUIs(UMLSLanguageCode.FRENCH, cuiTerm.getCUI(), cuiTerm.getTerm());
                if(umlsCUIs.size()>conceptNameCUIMap.size()){
                    ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_LESS_CUIS_THAN_UMLS);
                } else {
                    ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_MORE_CUIS_THAN_UMLS);
                }
                logger.debug("{}", umlsCUIs.size());
            }

            cuis.clear();
            cuis.add(cuiTerm.getCUI());
        }
    }


    /**
     * Look for CUIs in altLabels and mappings
     *
     * @param classURI The class URI for which to look for the CUIs
     * @return A collection of retrieved CUIs
     */
    @SuppressWarnings({"FeatureEnvy", "LawOfDemeter"})
    private Collection<String> findCUIs(final String classURI) {

        logger.debug("\t\tIn altLabel...");
        final List<String> cuis = new ArrayList<>(ontologyDelegate.findCUIsInAltLabel(classURI));
        if (cuis.isEmpty()) {
            logger.debug("\t\tIn mappings...");
            cuis.addAll(ontologyDelegate.findCUIsFromMappings(classURI));
            if (cuis.isEmpty()) {
                //noinspection SynchronizeOnNonFinalField
                synchronized (noCUIConceptsWriter) {
                    noCUIConceptsWriter.println(classURI);
                    noCUIConceptsWriter.flush();
                }
                ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_REMAINING_WITHOUT_CUI_STATISTIC);
            } else {
                ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_CUI_IN_MAPPINGS_STATISTIC);
            }
        } else {
            logger.debug("\t\t Found {} CUIs in altLabel.", cuis.size());
            ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_CUI_IN_ALT_LABEL_STATISTIC);
        }

        return cuis;
    }


    /**
     * Process TUIs for a particular class of the ontology. If the TUI is not in the sourceModel, we look to find them
     * in the mappings. If the class has assocated CUIs, we immediately retrieve the corresponding TUIs from UMLS.
     *
     * @param thisClass The class for which we are looking to add TUIs to.
     * @param cuis      The CUIs found for the class
     */
    private void processTUIs(final RDFNode thisClass, final Collection<String> cuis) {
        Collection<String> tuis;
        if (cuis.isEmpty()) {
            tuis = new ArrayList<>();
            ontologyDelegate.tuisFromSourceModel(thisClass.toString(), tuis);
            if (tuis.isEmpty()) {
                tuis = ontologyDelegate.findTUIsForMappings(thisClass.toString());
                if (tuis.isEmpty()) {
                    ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_REMAINING_WITHOUT_TUI_STATISTIC);
                }
            } else {
                logger.debug("\t{} TUIs found!", cuis.size());
            }
        } else {
            tuis = umlsDelegate.getTUIsForCUIs(cuis);
            ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITHOUT_TUI_STATISTIC);
        }
        logger.debug("\tAdded {} TUIs", tuis.size());
        synchronized (tuisToAdd) {
            tuisToAdd.put(thisClass.toString(), tuis);
        }
    }

    /**
     * Update the source model by adding the new CUIs and TUIs
     */
    @Override
    public void updateModel() {
        progressCount.set(0);
        final List<OntClass> sourceClasses = ontologyDelegate.getSourceClasses();
        totalClasses = cuisToAdd.size() + cuiAddedNotesToAdd.size() + tuisToAdd.size() +
                sourceClasses.size();
        logger.info("Updating ontology model...");

        updateCUIs();
        cleanCUIsFromAltLabelAndSynonyms();
        updateTUIs();

        logger.info("Done!");

        logger.info("Writing processed ontology file...");
        //Writing enriched model
        ontologyDelegate.writeEnrichedModel();
    }

    private void updateCUIs() {
        for (final Map.Entry<String, Collection<String>> cuiEntry : cuisToAdd.entrySet()) {
            for (final String cui : cuiEntry.getValue()) {
                ontologyDelegate.addCUIToSourceModel(cuiEntry.getKey(), cui);
            }
            printUpdateProgress();
        }
        for (final String classURI : cuiAddedNotesToAdd) {
            ontologyDelegate.addSkosNote(classURI, CUI_ADDED_AUTOMATICALLY_NOTE);
            printUpdateProgress();
        }
    }

    private void updateTUIs() {
        for (final Map.Entry<String, Collection<String>> tuiEntry : tuisToAdd.entrySet()) {
            for (final String tui : tuiEntry.getValue()) {
                ontologyDelegate.addTUIToSourceModel(tuiEntry.getKey(), tui);
            }
            printUpdateProgress();
        }
    }

    private void cleanCUIsFromAltLabelAndSynonyms() {
        final List<OntClass> classes = ontologyDelegate.getSourceClasses();
        for (final OntClass ontClass : classes) {
            ontologyDelegate.purgeCUIsFromAltLabelAndSynonyms(ontClass.toString(),UMLSLanguageCode.FRENCH.getShortCode());
        }
    }

    private void printUpdateProgress(){
        final double progress = (progressCount.incrementAndGet()/(double) totalClasses)*100;
        //noinspection UseOfSystemOutOrSystemErr,HardcodedLineSeparator
        System.out.print(String.format("\rUpdating model %.2f%%",progress));
    }

    @Override
    public void cleanUp() throws IOException {
        termSimilarityRanker.release();
    }


    public static void main(final String[] args) throws IOException {

        final Properties properties = new Properties();
        properties.load(OntologyCUIProcessor.class.getResourceAsStream("/config.properties"));

        final CommandlineHandler commandlineHandler = new DefaultCommandlineHandler();
        commandlineHandler.processCommandline(args, properties);


        final OntologyProcessor ontologyCUIProcessor = new OntologyCUIProcessor(properties);
        ontologyCUIProcessor.processOntology();
        ontologyCUIProcessor.updateModel();
        ontologyCUIProcessor.cleanUp();
    }


}

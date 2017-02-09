package org.sifrproject.cli;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import org.sifrproject.configuration.CommandlineHandler;
import org.sifrproject.configuration.DefaultCommandlineHandler;
import org.sifrproject.ontology.*;
import org.sifrproject.ontology.matching.CUITerm;
import org.sifrproject.ontology.matching.TermSimilarityRanker;
import org.sifrproject.ontology.matching.TverskiTermSimilarityRanker;
import org.sifrproject.stats.CUIOntologyStats;
import org.sifrproject.stats.StatsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Hello world!
 */
@SuppressWarnings("ClassWithTooManyFields")
public final class OntologyCUIProcessor implements OntologyProcessor {
    private static final Logger logger = LoggerFactory.getLogger(OntologyCUIProcessor.class);


    private static final int AMBIGUITY_THRESHOLD = 1;

    //Configuration constants
    public static final String CONFIG_SOURCE_ENDPOINT = "config.source_endpoint";
    private static final String CONFIG_TARGET_ENDPOINT = "config.target_endpoint";
    private static final String CONFIG_MAPPINGS_ENDPOINT = "config.mappings_endpoint";
    private static final String CONFIG_UMLS_JDBC = "config.umls_jdbc";
    private static final String CONFIG_UMLS_USER = "config.umls_user";
    private static final String CONFIG_UMLS_PASSWORD = "config.umls_password";
    private static final String CONFIG_UMLS_DB = "config.umls_db";

    private final StatsHandler ontologyStats;

    private final OntologyDelegate ontologyDelegate;
    private final UMLSDelegate umlsDelegate;

    private final Map<String, Collection<String>> cuisToAdd;
    private final Map<String, Collection<String>> tuisToAdd;

    private final TermSimilarityRanker termSimilarityRanker;

    private PrintWriter noCUIConceptsWriter;



    private OntologyCUIProcessor(final Properties properties) {
        cuisToAdd = new HashMap<>();
        tuisToAdd = new HashMap<>();


        final JedisPool jedisPool = new JedisPool(properties.getProperty("config.redis_host"));
            /*
             * Creating the UMLS delegate to access UMLS over SQL
             */
        final String jdbc = properties.getProperty(CONFIG_UMLS_JDBC);
        final String dbUser = properties.getProperty(CONFIG_UMLS_USER);
        final String dbpass = properties.getProperty(CONFIG_UMLS_PASSWORD);
        final String dbumls = properties.getProperty(CONFIG_UMLS_DB);
        umlsDelegate = new SQLUMLSDelegate(jdbc, dbUser, dbpass, dbumls, jedisPool);

            /*
             * Instantiating target and mappings models, can be a file or a Jena triple database (TDB)
             */
        final String sourceEndpoint = properties.getProperty(CONFIG_SOURCE_ENDPOINT);
        final String targetEndpoint = properties.getProperty(CONFIG_TARGET_ENDPOINT);
        final String mappingsEndpoint = properties.getProperty(CONFIG_MAPPINGS_ENDPOINT);

        final String outputFileSuffix = properties.getProperty(DefaultCommandlineHandler.CONFIG_OUTPUT_FILE_SUFFIX);

        ontologyDelegate = new OntologyDelegateImpl(mappingsEndpoint, targetEndpoint, sourceEndpoint, outputFileSuffix, jedisPool);
        final String ontologyName = ontologyDelegate.getOntologyName();

        termSimilarityRanker = new TverskiTermSimilarityRanker(jedisPool);

        try {
            noCUIConceptsWriter = new PrintWriter(ontologyName+"_concepts_without_cui.txt");
        } catch (final FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
        }

        ontologyStats = new CUIOntologyStats(ontologyName);
    }



    /**
     * Process the ontology classes to look for CUIs and TUIs
     */
    @Override
    public void processOntology() {
        final ExtendedIterator<OntClass> classes = ontologyDelegate.getSourceClasses();
        final List<OntClass> classList = classes.toList();
        int currentClass = 0;
        for (final RDFNode thisClass : classList) {
            final double progress = (100d * currentClass) / (double) classList.size();
            ontologyStats.incrementStatistic(CUIOntologyStats.TOTAL_CLASS_COUNT_STATISTIC);
            logger.info(String.format("[%.2f] Processing: %s", progress, thisClass));
            final Collection<String> cuis = processCUIs(thisClass);
            processTUIs(thisClass, cuis);
            currentClass++;
        }
        ontologyStats.writeStatistics();
        ontologyDelegate.writeEnrichedModel();
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
            logger.info("\tLooking for CUIs...");
            cuis.addAll(findCUIs(thisClass.toString()));
        } else {
            logger.info("\t{} CUIs found!", cuis.size());
        }

        if (cuis.isEmpty()) {
            logger.info("\tFalling back on UMLS to find CUIs...");
            //disambiguate(cuis,thisClass);
        }

        //Too many CUIs, trying to use UMLS to determine the most relevant ones
        if (cuis.size() > AMBIGUITY_THRESHOLD) {
            logger.error("AMBIGUOUS!!!");
            ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_AMBIGUOUS_CUI_STATISTIC);
            //disambiguate(cuis, thisClass);
        }

        logger.info("\tAdding {} CUIs to model...", cuis.size());
        tuisToAdd.put(thisClass.toString(), cuis);
        return cuis;
    }

    @SuppressWarnings("unused")
    private void disambiguate(final Collection<String> cuis, final RDFNode thisClass) {

        final String conceptDescription = ontologyDelegate.conceptLabelFromSourceModel(thisClass.asResource());
        //This is unmodifiable, we need to make a copy to be able to exact changes
        final List<CUITerm> conceptNameCUIMap = (cuis.isEmpty()) ?
                umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH) :
                umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH, cuis);

        if(!conceptNameCUIMap.isEmpty()) {
            termSimilarityRanker.rankBySimilarity(conceptNameCUIMap, conceptDescription);
            cuis.clear();
            final CUITerm cuiTerm = conceptNameCUIMap.get(0);
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

        logger.info("\t\tIn altLabel...");
        final List<String> cuis = new ArrayList<>(ontologyDelegate.findCUIsInAltLabel(classURI));
        if (cuis.isEmpty()) {
            logger.info("\t\tIn mappings...");
            cuis.addAll(ontologyDelegate.findCUIsFromMappings(classURI));
            if (cuis.isEmpty()) {
                noCUIConceptsWriter.println(classURI);
                noCUIConceptsWriter.flush();
                ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_REMAINING_WITHOUT_CUI_STATISTIC);
            } else {
                ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_CUI_IN_MAPPINGS_STATISTIC);
            }
        } else {
            logger.info("\t\t Found {} CUIs in altLabel.", cuis.size());
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
                logger.info("\t{} TUIs found!", cuis.size());
            }
        } else {
            tuis = umlsDelegate.getTUIsForCUIs(cuis);
            ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITHOUT_TUI_STATISTIC);
        }
        logger.info("\tAdded {} TUIs", tuis.size());
        tuisToAdd.put(thisClass.toString(), tuis);
    }

    /**
     * Update the source model by adding the new CUIs and TUIs
     */
    private void updateModel() {
        for (final Map.Entry<String, Collection<String>> tuiEntry : tuisToAdd.entrySet()) {
            for (final String tui : tuiEntry.getValue()) {
                ontologyDelegate.addTUIToSourceModel(tuiEntry.getKey(), tui);
            }
        }
        for (final Map.Entry<String, Collection<String>> cuiEntry : cuisToAdd.entrySet()) {
            for (final String cui : cuiEntry.getValue()) {
                ontologyDelegate.addCUIToSourceModel(cuiEntry.getKey(), cui);
            }
        }
    }


    public static void main(final String[] args) throws IOException {

        final Properties properties = new Properties();
        properties.load(OntologyCUIProcessor.class.getResourceAsStream("/config.properties"));

        final CommandlineHandler commandlineHandler = new DefaultCommandlineHandler();
        commandlineHandler.processCommandline(args, properties);


        @SuppressWarnings("LocalVariableOfConcreteClass") final OntologyCUIProcessor ontologyCUIProcessor = new OntologyCUIProcessor(properties);
        ontologyCUIProcessor.processOntology();
        ontologyCUIProcessor.updateModel();
    }


}

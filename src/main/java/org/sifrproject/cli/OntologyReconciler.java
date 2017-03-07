package org.sifrproject.cli;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.rdf.model.RDFNode;
import org.sifrproject.cli.api.AbstractOntologyProcessor;
import org.sifrproject.cli.api.OntologyProcessor;
import org.sifrproject.configuration.CommandlineHandler;
import org.sifrproject.configuration.CUIProcessorCommandlineHandler;
import org.sifrproject.ontology.code.CodeFinder;
import org.sifrproject.ontology.code.CompositeCodeFinder;
import org.sifrproject.ontology.cuis.CUIOntologyDelegate;
import org.sifrproject.ontology.cuis.CUIOntologyDelegateImpl;
import org.sifrproject.ontology.mapping.DefaultOntologyMappingDelegate;
import org.sifrproject.ontology.mapping.OntologyMappingDelegate;
import org.sifrproject.ontology.umls.SQLUMLSDelegate;
import org.sifrproject.ontology.umls.UMLSDelegate;
import org.sifrproject.stats.CUIOntologyStats;
import org.sifrproject.stats.StatsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.sifrproject.configuration.CUIProcessorConfigurationConstants.*;

/**
 * Hello world!
 */
@SuppressWarnings("ClassWithTooManyFields")
public final class OntologyReconciler extends AbstractOntologyProcessor {
    private static final Logger logger = LoggerFactory.getLogger(OntologyReconciler.class);

    //Configuration constants



    private final UMLSDelegate umlsDelegate;

    private final Map<String, Collection<String>> cuisToAdd;
    private final Map<String, Collection<String>> tuisToAdd;

    private final List<String> cuiAddedNotesToAdd;

    //private final PooledTermSimilarityRanker termSimilarityRanker;


    private final AtomicInteger progressCount;
    private int totalClasses;

    private final CodeFinder codeFinder;

    private PrintWriter unmappedClasses;


    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed", "resource"})
    private OntologyReconciler(final Properties properties, final UMLSDelegate umlsDelegate, final CUIOntologyDelegate sourceDelegate, final CUIOntologyDelegate targetDelegate, final OntologyMappingDelegate mappingDelegate, final StatsHandler ontologyStats) {
        super(ontologyStats,sourceDelegate,targetDelegate, mappingDelegate);
        cuisToAdd = new HashMap<>();
        tuisToAdd = new HashMap<>();
        cuiAddedNotesToAdd = new ArrayList<>(10000);

        progressCount = new AtomicInteger();
        this.umlsDelegate = umlsDelegate;

        final String ontologyName = sourceDelegate.getOntologyName();

        codeFinder = new CompositeCodeFinder(sourceDelegate);

        try {
            unmappedClasses = new PrintWriter(ontologyName + "_unmapped_concepts.txt");
        } catch (final FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    /*private JedisCluster initializeRedisCluster(final String host, final int startPort, final int numberOfNodes){
        final Set<HostAndPort> jedisClusterNodes = new HashSet<>();
        for(int i=1;i<=numberOfNodes;i++) {
            jedisClusterNodes.add(new HostAndPort(host, startPort+i));
        }
        return new JedisCluster(jedisClusterNodes);
    }*/



    @Override
    protected void processSourceClass(final OntClass thisClass) {
        incrementStatistic(CUIOntologyStats.TOTAL_CLASS_COUNT_STATISTIC);
        logger.debug(String.format("Looking for mappings: %s", thisClass));
        final Collection<String> cuis = findSourceCodes(thisClass);
        final double progress = getPercentProgress();
        //noinspection UseOfSystemOutOrSystemErr,HardcodedLineSeparator
        System.out.print(String.format("\rProcessed mappings %.2f%%", progress));
    }

    @Override
    protected void processTargetClass(final OntClass thisClass) {
        incrementStatistic(CUIOntologyStats.TOTAL_CLASS_COUNT_STATISTIC);
        logger.debug(String.format("Looking for mappings: %s", thisClass));
        final Collection<String> cuis = findSourceCodes(thisClass);
        final double progress = getPercentProgress();
        //noinspection UseOfSystemOutOrSystemErr,HardcodedLineSeparator
        System.out.print(String.format("\rProcessed mappings %.2f%%", progress));
    }

    /**
     * Process classes by successively looking for CUIs in the source model and if it isn't found tries to find them in altLabels and mappings.
     * Generates the corresponding statistics
     *
     * @param thisClass The current class to process
     * @return A collection of string containing the CUIs found
     */
    private Collection<String> findSourceCodes(final RDFNode thisClass) {


        return Collections.emptyList();
    }

//    @SuppressWarnings("FeatureEnvy")
//    private void matchUMLSCodes(final RDFNode thisClass, final Collection<String> cuis) {
//        final String code = codeFinder.getCode(thisClass.toString());
//        if (code != null) {
//            incrementStatistic(CUIOntologyStats.UMLS_CODES_FOUND);
//            final Collection<String> umlsCUIs = umlsDelegate.getUMLSCUIs(code);
//            if (umlsCUIs.size() > cuis.size()) {
//                incrementStatistic(CUIOntologyStats.CLASSES_WITH_LESS_CUIS_THAN_UMLS);
//            } else if (umlsCUIs.size() < cuis.size()){
//                incrementStatistic(CUIOntologyStats.CLASSES_WITH_MORE_CUIS_THAN_UMLS);
//            }
//            logger.debug("{}", umlsCUIs.size());
//        }
//    }

//    @SuppressWarnings("FeatureEnvy")
//    private void disambiguate(final Collection<String> cuis, final RDFNode thisClass) {
//
//        final String conceptDescription = ontologyDelegate.getConceptLabel(thisClass.asResource());
//        final List<CUITerm> conceptNameCUIMap = (cuis.isEmpty()) ?
//                umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH) :
//                umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH, cuis);
//
//        if (!conceptNameCUIMap.isEmpty()) {
//            termSimilarityRanker.rankBySimilarity(conceptNameCUIMap, conceptDescription);
//
//            final CUITerm cuiTerm = conceptNameCUIMap.get(0);
//            cuis.clear();
//            cuis.add(cuiTerm.getCUI());
//        }
//    }


//    /**
//     * Look for CUIs in altLabels and mappings
//     *
//     * @param classURI The class URI for which to look for the CUIs
//     * @return A collection of retrieved CUIs
//     */
//    @SuppressWarnings({"FeatureEnvy", "LawOfDemeter"})
//    private Collection<String> findCUIs(final String classURI) {
//
//        logger.debug("\t\tIn altLabel...");
//        final List<String> cuis = new ArrayList<>(ontologyDelegate.findCUIsInAltLabel(classURI));
//        if (cuis.isEmpty()) {
//            logger.debug("\t\tIn mappings...");
//            cuis.addAll(ontologyDelegate.findCUIsForClasses(classURI));
//            if (cuis.isEmpty()) {
//                //noinspection SynchronizeOnNonFinalField
//                synchronized (unmappedClasses) {
//                    unmappedClasses.println(classURI);
//                    unmappedClasses.flush();
//                }
//                ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_REMAINING_WITHOUT_CUI_STATISTIC);
//            } else {
//                ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_CUI_IN_MAPPINGS_STATISTIC);
//            }
//        } else {
//            logger.debug("\t\t Found {} CUIs in altLabel.", cuis.size());
//            ontologyStats.incrementStatistic(CUIOntologyStats.CLASSES_WITH_CUI_IN_ALT_LABEL_STATISTIC);
//        }
//
//        return cuis;
//    }


    /**
     * Update the source model by adding the new CUIs and TUIs
     */
    private void updateMappingModel() {
        progressCount.set(0);
        final List<OntClass> sourceClasses = sourceDelegate.getClasses();
        totalClasses = cuisToAdd.size() + cuiAddedNotesToAdd.size() + tuisToAdd.size() +
                sourceClasses.size();
        logger.info("Populating mappings model");


        logger.info("Done!");

        logger.info("Writing mappings to file...");
        //Writing enriched model
        //TODO:Specialize Ontology Delegate interface
        sourceDelegate.writeModel();
    }



    private void printUpdateProgress() {
        final double progress = (progressCount.incrementAndGet() / (double) totalClasses) * 100;
        //noinspection UseOfSystemOutOrSystemErr,HardcodedLineSeparator
        System.out.print(String.format("\rUpdating model %.2f%%", progress));
    }

    @Override
    public void postProcess() {

    }

    @Override
    public void cleanUp() throws IOException {
        //termSimilarityRanker.release();
    }


    public static void main(final String[] args) throws IOException {

        final Properties properties = new Properties();
        properties.load(OntologyCUIProcessor.class.getResourceAsStream("/matcher_config.properties"));

        final CommandlineHandler commandlineHandler = new CUIProcessorCommandlineHandler();
        commandlineHandler.processCommandline(args, properties);

        final JedisPool jedisPool = new JedisPool(
                properties.getProperty(CONFIG_REDIS_HOST),
                Integer.valueOf(properties.getProperty(CONFIG_REDIS_PORT))
        );

        /*
         * Creating the UMLS delegate to access UMLS over SQL
         */
        final String jdbc = properties.getProperty(CONFIG_UMLS_JDBC);
        final String dbUser = properties.getProperty(CONFIG_UMLS_USER);
        final String dbpass = properties.getProperty(CONFIG_UMLS_PASSWORD);
        final String dbumls = properties.getProperty(CONFIG_UMLS_DB);
        final UMLSDelegate umlsDelegate = new SQLUMLSDelegate(jdbc, dbUser, dbpass, dbumls, jedisPool);


          /*
             * Instantiating target and mappings models, can be a file or a Jena triple database (TDB)
             */
        final String sourceEndpoint = properties.getProperty(CONFIG_SOURCE_ENDPOINT);
        final String targetEndpoint = properties.getProperty(CONFIG_TARGET_ENDPOINT);
        final String mappingsEndpoint = properties.getProperty(CONFIG_MAPPINGS_ENDPOINT);

        final String outputFileSuffix = properties.getProperty(CONFIG_OUTPUT_FILE_SUFFIX);

        final CUIOntologyDelegate sourceDelegate = new CUIOntologyDelegateImpl(sourceEndpoint, outputFileSuffix, jedisPool);
        final CUIOntologyDelegate targetDelegate = new CUIOntologyDelegateImpl(targetEndpoint, outputFileSuffix, jedisPool);

        final String sourceName = sourceDelegate.getOntologyName();
        final String targetName = targetDelegate.getOntologyName();

        final OntologyMappingDelegate mappingDelegate = new DefaultOntologyMappingDelegate(
                mappingsEndpoint,
                sourceName,
                targetName,
                jedisPool
        );


        final StatsHandler ontologyStats = new CUIOntologyStats(sourceName + "_" + targetName);


        final OntologyProcessor ontologyCUIProcessor = new OntologyReconciler(
                properties,
                umlsDelegate,
                sourceDelegate,
                targetDelegate,
                mappingDelegate,
                ontologyStats
        );
        ontologyCUIProcessor.processSourceOntology();
        ontologyCUIProcessor.postProcess();
        ontologyCUIProcessor.cleanUp();
    }


}

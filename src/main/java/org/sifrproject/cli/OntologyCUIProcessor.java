src/main/java/org/sifrproject/ontology/BaseOntologyDelegate.javapackage org.sifrproject.cli;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.rdf.model.RDFNode;
import org.getalp.lexsema.util.VisualVMTools;
import org.sifrproject.cli.api.AbstractOntologyProcessor;
import org.sifrproject.cli.api.OntologyProcessor;
import org.sifrproject.configuration.CUIProcessorCommandlineHandler;
import org.sifrproject.configuration.CommandlineHandler;
import org.sifrproject.ontology.code.CodeFinder;
import org.sifrproject.ontology.code.CompositeCodeFinder;
import org.sifrproject.ontology.code.SKOSNotationCodeFinder;
import org.sifrproject.ontology.cuis.CUIOntologyDelegate;
import org.sifrproject.ontology.cuis.CUIOntologyDelegateImpl;
import org.sifrproject.ontology.mapping.DefaultOntologyMappingDelegate;
import org.sifrproject.ontology.mapping.Mapping;
import org.sifrproject.ontology.mapping.OntologyMappingDelegate;
import org.sifrproject.ontology.matching.CUITerm;
import org.sifrproject.ontology.matching.PooledTermSimilarityRanker;
import org.sifrproject.ontology.matching.TverskiTermSimilarityRanker;
import org.sifrproject.ontology.umls.SQLUMLSDelegate;
import org.sifrproject.ontology.umls.UMLSDelegate;
import org.sifrproject.ontology.umls.UMLSLanguageCode;
import org.sifrproject.stats.CUIOntologyStats;
import org.sifrproject.stats.StatsHandler;
import redis.clients.jedis.JedisPool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.sifrproject.configuration.CUIProcessorConfigurationConstants.*;

/**
 * Hello world!
 */

@SuppressWarnings({"OverlyCoupledClass", "ClassWithTooManyFields"})
public final class OntologyCUIProcessor extends AbstractOntologyProcessor {

    private static final String CUI_ADDED_AUTOMATICALLY_NOTE = "Le LIRMM a enrichi ce concept en CUI et TUI par un processus automatique";

    private final UMLSDelegate umlsDelegate;

    private final Map<String, Collection<String>> cuisToAdd;
    private final Map<String, Collection<String>> tuisToAdd;
    private final Map<String, String> codesToAdd;

    private final Map<String, Collection<String>> cuisToPurgeFromAltLabel;

    private final Collection<Mapping> mappingsToAdd;

    private final List<String> cuiAddedNotesToAdd;

    private final PooledTermSimilarityRanker termSimilarityRanker;

    private PrintWriter noCUIConceptsWriter;

    private boolean disambiguate;
    private boolean match;

    private final AtomicInteger progressCount;
    private int totalClasses;

    private final CodeFinder codeFinder;


    @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed", "resource", "all"})
    private OntologyCUIProcessor(final Properties properties, final UMLSDelegate umlsDelegate, final CUIOntologyDelegate sourceDelegate, final CUIOntologyDelegate targetDelegate, final OntologyMappingDelegate mappingDelegate, final StatsHandler ontologyStats, final PooledTermSimilarityRanker termSimilarityRanker) {
        super(ontologyStats, sourceDelegate, targetDelegate, mappingDelegate);
        this.umlsDelegate = umlsDelegate;
        cuisToAdd = new HashMap<>();
        tuisToAdd = new HashMap<>();
        cuisToPurgeFromAltLabel = new HashMap<>();
        mappingsToAdd = new ArrayList<>();
        cuiAddedNotesToAdd = new ArrayList<>(10000);
        codesToAdd = new HashMap<>();

        progressCount = new AtomicInteger();

        this.termSimilarityRanker = termSimilarityRanker;

        final String ontologyName = sourceDelegate.getOntologyName();

        codeFinder = new CompositeCodeFinder(sourceDelegate);


        try {
            noCUIConceptsWriter = new PrintWriter(ontologyName + "_concepts_without_cui.txt");
        } catch (final FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
        }

        if (properties.containsKey(CONFIG_DISAMBIGUATE)) {
            disambiguate = true;
        }
        if (properties.containsKey(CONFIG_MATCH)) {
            match = true;
        }
    }


    @Override
    protected void processSourceClass(final OntClass thisClass) {
        incrementStatistic(CUIOntologyStats.TOTAL_CLASS_COUNT_STATISTIC);
        logger.debug(String.format("Processing: %s", thisClass));
        final Collection<String> cuis = processCUIs(thisClass);
        processTUIs(thisClass, cuis);
        final double progress = getPercentProgress();
        //noinspection UseOfSystemOutOrSystemErr,HardcodedLineSeparator
        System.out.print(String.format("\rProcessing %.2f%%", progress));
    }

    @Override
    protected void processTargetClass(final OntClass thisClass) {

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
        sourceDelegate.getCUIs(thisClass.toString(), cuis);

        final List<Mapping> mappings = mappingDelegate.sourceMappings(thisClass.toString());
        //Adding to mapping update list
        mappingsToAdd.addAll(mappings);

        final String code = codeFinder.getCode(thisClass.toString());
        if(code!=null) {
            codesToAdd.put(thisClass.toString(), code);
        }

        if (cuis.isEmpty()) {
            incrementStatistic(CUIOntologyStats.CLASSES_WITHOUT_CUI_STATISTIC);
            logger.debug("\tLooking for CUIs...");
            cuis.addAll(findCUIs(thisClass.toString(),mappings));
        } else {
            logger.debug("\t{} CUIs found!", cuis.size());
        }

        handleAmbiguity(cuis,code,thisClass);

        logger.debug("\tAdding {} CUIs to model...", cuis.size());
        synchronized (cuisToAdd) {
            if (!cuis.isEmpty()) {
                cuiAddedNotesToAdd.add(thisClass.toString());
            }
            cuisToAdd.put(thisClass.toString(), cuis);
        }
        return cuis;
    }

    private void handleAmbiguity(final Collection<String> cuis, final String code, final RDFNode thisClass){

        if (cuis.isEmpty() && match) {
            logger.debug("\tFalling back on UMLS to find CUIs...");
            disambiguate(cuis, thisClass);
        }

        //Too many CUIs, trying to use UMLS to determine the most relevant ones
        final Collection<String> ambiguousCuis = new ArrayList<>(cuis);
        if (cuis.size() > 1) {
            incrementStatistic(CUIOntologyStats.CLASSES_WITH_AMBIGUOUS_CUI_STATISTIC);
            logger.debug("Ambiguous CUIs");

            if (!cuis.isEmpty()) {
                compareCUIsToUMLS(code, cuis);
            }

            if (disambiguate) {
                disambiguate(ambiguousCuis, thisClass);
                cuis.clear();
                cuis.addAll(ambiguousCuis);
            }
        }
    }

    @SuppressWarnings("FeatureEnvy")
    private void compareCUIsToUMLS(final String code, final Collection<String> cuis) {
        if (code != null) {
            incrementStatistic(CUIOntologyStats.UMLS_CODES_FOUND);
            final Collection<String> umlsCUIs = umlsDelegate.getUMLSCUIs(code, UMLSLanguageCode.FRENCH);
            if (umlsCUIs.size() > cuis.size()) {
                incrementStatistic(CUIOntologyStats.CLASSES_WITH_LESS_CUIS_THAN_UMLS);
            } else if (umlsCUIs.size() < cuis.size()) {
                incrementStatistic(CUIOntologyStats.CLASSES_WITH_MORE_CUIS_THAN_UMLS);
            }
            logger.debug("{}", umlsCUIs.size());
        }
    }

    @SuppressWarnings("FeatureEnvy")
    private void disambiguate(final Collection<String> cuis, final RDFNode thisClass) {

        final String conceptDescription = sourceDelegate.getConceptLabel(thisClass.toString());
        final List<CUITerm> conceptNameCUIMap = (cuis.isEmpty()) ?
                umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH) :
                umlsDelegate.getCUIConceptNameMap(UMLSLanguageCode.FRENCH, cuis);

        if (!conceptNameCUIMap.isEmpty()) {
            termSimilarityRanker.rankBySimilarity(conceptNameCUIMap, conceptDescription);

            final CUITerm cuiTerm = conceptNameCUIMap.get(0);
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
    private Collection<String> findCUIs(final String classURI, final Collection<Mapping> mappings) {

        logger.debug("\t\tIn altLabel...");
        final List<String> cuis = new ArrayList<>(sourceDelegate.findCUIsInAltLabel(classURI));
        if (cuis.isEmpty()) {
            logger.debug("\t\tIn mappings...");


            final Stream<Mapping> stream = mappings.stream();
            final Stream<String> stringStream = stream.map(Mapping::getTargetClass);
            final Set<String> mappingClasses = stringStream.collect(Collectors.toSet());
            targetDelegate.getCUIs(mappingClasses, cuis);

            if (cuis.isEmpty()) {
                //noinspection SynchronizeOnNonFinalField
                synchronized (noCUIConceptsWriter) {
                    noCUIConceptsWriter.println(classURI);
                    noCUIConceptsWriter.flush();
                }
                incrementStatistic(CUIOntologyStats.CLASSES_REMAINING_WITHOUT_CUI_STATISTIC);
            } else {
                incrementStatistic(CUIOntologyStats.CLASSES_WITH_CUI_IN_MAPPINGS_STATISTIC);
            }
        } else {
            logger.debug("\t\t Found {} CUIs in altLabel.", cuis.size());
            cuisToPurgeFromAltLabel.put(classURI, cuis);
            incrementStatistic(CUIOntologyStats.CLASSES_WITH_CUI_IN_ALT_LABEL_STATISTIC);
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
        final Collection<String> tuis;
        if (cuis.isEmpty()) {
            tuis = new ArrayList<>();
            sourceDelegate.getTUIs(thisClass.toString(), tuis);
            if (tuis.isEmpty()) {
                final List<Mapping> mappings = mappingDelegate.sourceMappings(thisClass.toString());
                final Stream<Mapping> stream = mappings.stream();
                final Stream<String> stringStream = stream.map(Mapping::getSourceClass);
                final List<String> mappingClasses = stringStream.collect(Collectors.toList());
                targetDelegate.getTUIs(mappingClasses, tuis);
                if (tuis.isEmpty()) {
                    incrementStatistic(CUIOntologyStats.CLASSES_REMAINING_WITHOUT_TUI_STATISTIC);
                }
            } else {
                logger.debug("\t{} TUIs found!", cuis.size());
            }
        } else {
            tuis = umlsDelegate.getTUIsForCUIs(cuis);
            incrementStatistic(CUIOntologyStats.CLASSES_WITHOUT_TUI_STATISTIC);
        }
        logger.debug("\tAdded {} TUIs", tuis.size());
        synchronized (tuisToAdd) {
            tuisToAdd.put(thisClass.toString(), tuis);
        }
    }

    /**
     * Update the source model by adding the new CUIs and TUIs
     */
    @SuppressWarnings("FeatureEnvy")
    private void updateModel() {
        progressCount.set(0);
        final List<OntClass> sourceClasses = sourceDelegate.getClasses();
        totalClasses = cuisToAdd.size() + cuiAddedNotesToAdd.size() + tuisToAdd.size() +
                (2 * sourceClasses.size()) + mappingsToAdd.size() + codesToAdd.size();
        logger.info("Updating ontology model...");

        updateCUIs();
        cleanCUIsAltLabelsAndSynonyms(cuisToPurgeFromAltLabel.keySet());
        updateTUIs();
        updateMappings();
        updateCodeNotes();
        cleanAltLabelsSameAsPrefLabels();

        logger.info("Done!");

        logger.info("Writing processed ontology file...");
        //Writing enriched model
        sourceDelegate.writeModel();
    }

    @SuppressWarnings("FeatureEnvy")
    private void updateCUIs() {
        for (final Map.Entry<String, Collection<String>> cuiEntry : cuisToAdd.entrySet()) {
            for (final String cui : cuiEntry.getValue()) {
                sourceDelegate.addCUIToModel(cuiEntry.getKey(), cui);
            }
            printUpdateProgress();
        }

        for (final String classURI : cuiAddedNotesToAdd) {
            sourceDelegate.addSkosProperty(classURI, CUI_ADDED_AUTOMATICALLY_NOTE , "changeNote");
            printUpdateProgress();
        }
    }

    @SuppressWarnings("FeatureEnvy")
    private void updateMappings() {
        for (final Mapping mapping : mappingsToAdd) {
            sourceDelegate.addStatement(mapping.getSourceClass(), mapping.getProperty(), mapping.getTargetClass());
            printUpdateProgress();
        }
    }

    private void updateCodeNotes() {
        final CodeFinder skosCodeFinder = new SKOSNotationCodeFinder(sourceDelegate);
        for (final Map.Entry<String, String> stringStringEntry : codesToAdd.entrySet()) {
            final String classURI = stringStringEntry.getKey();
            final String code = stringStringEntry.getValue();
            sourceDelegate.purgeCodeFromAltLabel(classURI,code,"fr");
            if(skosCodeFinder.getCode(classURI)==null) {
                sourceDelegate.addSkosProperty(classURI, code, "notation");
            }
            printUpdateProgress();
        }
    }

    private void updateTUIs() {
        for (final Map.Entry<String, Collection<String>> tuiEntry : tuisToAdd.entrySet()) {
            for (final String tui : tuiEntry.getValue()) {
                sourceDelegate.addTUIToModel(tuiEntry.getKey(), tui);
            }
            printUpdateProgress();
        }
    }

    private void cleanCUIsAltLabelsAndSynonyms(final Iterable<String> cuisToPurge) {
        for (final String classURI : cuisToPurge) {
            sourceDelegate.purgeCUIsFromAltLabel(
                    classURI,
                    cuisToPurgeFromAltLabel.get(classURI),
                    UMLSLanguageCode.FRENCH.getShortCode()
            );
            printUpdateProgress();
        }
    }

    private void cleanAltLabelsSameAsPrefLabels() {
        for (final OntClass thisClass : sourceDelegate.getClasses()) {
            sourceDelegate.cleanSkosAltLabel(thisClass.toString());
            printUpdateProgress();
        }
    }

    private void printUpdateProgress() {
        final double progress = (progressCount.incrementAndGet() / (double) totalClasses) * 100;
        //noinspection UseOfSystemOutOrSystemErr,HardcodedLineSeparator
        System.out.print(String.format("\rUpdating model %.2f%%", progress));
    }

    @Override
    public void postProcess() {
        updateModel();
    }

    @Override
    public void cleanUp() throws IOException {
        termSimilarityRanker.release();
    }


    @SuppressWarnings("OverlyCoupledMethod")
    public static void main(final String[] args) throws IOException {

        VisualVMTools.delayUntilReturn();

        final Properties properties = new Properties();
        properties.load(OntologyCUIProcessor.class.getResourceAsStream("/cuiprocessor_config.properties"));

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

        final PooledTermSimilarityRanker termSimilarityRanker = new TverskiTermSimilarityRanker(jedisPool);

        final OntologyProcessor ontologyCUIProcessor = new OntologyCUIProcessor(
                properties,
                umlsDelegate,
                sourceDelegate,
                targetDelegate,
                mappingDelegate,
                ontologyStats,
                termSimilarityRanker
        );
        ontologyCUIProcessor.processSourceOntology();
        ontologyCUIProcessor.postProcess();
        ontologyCUIProcessor.cleanUp();
    }


}

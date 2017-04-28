package org.sifrproject.cli.generation;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.getalp.lexsema.util.dataitems.Pair;
import org.getalp.lexsema.util.dataitems.PairImpl;
import org.sifrproject.cli.api.generation.SKOSOntologyGenerator;
import org.sifrproject.configuration.CommandlineHandler;
import org.sifrproject.configuration.SKOSGeneratorCommandlineHandler;
import org.sifrproject.ontology.SKOSOntologyDelegate;
import org.sifrproject.ontology.SKOSOntologyDelegateImpl;
import org.sifrproject.ontology.prefix.OntologyPrefix;
import org.sifrproject.stats.SkosGeneratorOntologyStats;
import org.sifrproject.stats.StatsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.sifrproject.configuration.ConfigurationConstants.CONFIG_REDIS_HOST;
import static org.sifrproject.configuration.ConfigurationConstants.CONFIG_REDIS_PORT;
import static org.sifrproject.configuration.SKOSGeneratorConfigurationConstants.*;

@SuppressWarnings("ClassWithTooManyFields")
public final class EHealth2017DictionaryToSkos implements SKOSOntologyGenerator {

    private static final Logger logger = LoggerFactory.getLogger(EHealth2017DictionaryToSkos.class);
    private static final Pattern CODE_NORM_RELPLACE_PATTERN = Pattern.compile("([A-Z][0-9][0-9])([0-9][0-9]?)");
    private static final Pattern FILENAMEWITHEXT = Pattern.compile("\\.");
    private static final double HALF_PERCENT = 0.25d;
    private static final String FILE_NOT_FOUND_STRING = "File not found: {}";
    public static final double CUTOFF_CUMULATIVE_FREQ = .8d;
    private final StatsHandler statsHandler;

    private final Map<String, Integer> codeIndex;
    private final List<Set<String>> codeDescriptions;
    private final List<String> codeList;

    private final Map<String, Set<String>> decriptionCodesIndex;

    private final Map<String, Set<String>> stdDecriptionCodesIndex;
    private final Map<String, Set<String>> rawDecriptionCodesIndex;

    private final Map<String, Double> codeFrequencies;


    private final List<Pair<String, String>> codeRanges;
    private final List<String> codeRangeURIs;


    private final String separator;
    private final String dictionaryFileName;
    private final String corpusFileName;

    private final String baseURI;

    private final String languageCode;
    private static final int DICTIONARY_ID_INDEX = 1;
    private static final int DICTIONARY_LABEL_INDEX = 0;

    private static final int CORPUS_RAW_TEXT_INDEX = 6;
    private static final int CORPUS_STANDARD_TEXT_INDEX = 10;
    private static final int CORPUS_FIELD_NUMBER = 12;
    private static final int CORPUS_ID_INDEX = 11;

    private final boolean includeDictionary;
    private final boolean includeCorpusRaw;
    private final boolean includeCorpusStd;
    private final boolean mostFrequentCode;
    private final boolean adaptedCorpus;


    private EHealth2017DictionaryToSkos(final String baseURI, final String dictionaryFileName, final String corpusFileName, final String separator, final StatsHandler statsHandler, final String languageCode, final boolean includeDictionary, final boolean includeCorpusRaw, final boolean includeCorpusStd, final boolean mostFrequentCode, final boolean adaptedCorpus) {
        this.statsHandler = statsHandler;

        codeIndex = new HashMap<>();
        codeDescriptions = new ArrayList<>();
        codeList = new ArrayList<>();
        codeFrequencies = new HashMap<>();
        decriptionCodesIndex = new HashMap<>();
        stdDecriptionCodesIndex = new HashMap<>();
        rawDecriptionCodesIndex = new HashMap<>();

        this.separator = separator;
        this.dictionaryFileName = dictionaryFileName;
        this.corpusFileName = corpusFileName;

        this.baseURI = baseURI;

        codeRanges = new ArrayList<>();
        codeRangeURIs = new ArrayList<>();

        this.languageCode = languageCode;

        this.includeDictionary = includeDictionary;
        this.includeCorpusRaw = includeCorpusRaw;
        this.includeCorpusStd = includeCorpusStd;
        this.mostFrequentCode = mostFrequentCode;
        this.adaptedCorpus = adaptedCorpus;


    }

    private void loadChapters() {

        try (InputStream chaptersStream = EHealth2017DictionaryToSkos.class.getResourceAsStream("/ehealth/chapterRange.csv")) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(chaptersStream))) {
                final Stream<String> lines = reader.lines();
                for (final String line : lines.collect(Collectors.toList())) {
                    final String[] fields = line.split(",");
                    final String[] range = fields[1].split("-");
                    codeRanges.add(new PairImpl<>(range[0], range[1]));
                    codeRangeURIs.add(fields[0]);
                }
            }
        } catch (final IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    private void loadCorpus(final InputStream corpusIntputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(corpusIntputStream))) {
            int currentIndex = 0;
            final Stream<String> lines = reader.lines();
            for (final String line : lines.collect(Collectors.toList())) {

                final String[] fields = line.split(separator);
                if (!fields[0].equals("DocID") && (fields.length == CORPUS_FIELD_NUMBER)) {
                    final String rawLabel = fields[CORPUS_RAW_TEXT_INDEX];
                    final String standardLabel = fields[CORPUS_STANDARD_TEXT_INDEX];
                    final String id = normalizeCode(fields[CORPUS_ID_INDEX].trim());

                    currentIndex = processCorpusLine(id, rawLabel, standardLabel, currentIndex);
                }
            }
        } catch (final IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    private int processCorpusLine(final String code, final String rawLabel, final String standardLabel, final int currentIndex) {
        int newCurrentIndex = currentIndex;
        if (!code.isEmpty()) {
            updateCodeFrequencies(code);
            if (includeCorpusRaw) {
                newCurrentIndex = addCodeAndLabel(code, rawLabel, currentIndex);

                addToDescriptionCodeIndex(rawDecriptionCodesIndex, rawLabel, code);
            }
            if (includeCorpusStd) {
                newCurrentIndex = addCodeAndLabel(code, standardLabel, currentIndex);

                addToDescriptionCodeIndex(stdDecriptionCodesIndex, standardLabel, code);
            }
        }
        return newCurrentIndex;
    }

    private int addCodeAndLabel(final String code, final String label, final int currentIndex) {
        int ret = currentIndex;
        if (codeIndex.containsKey(code)) {
            final int index = codeIndex.get(code);
            final Set<String> strings = codeDescriptions.get(index);
            strings.add(label);
        } else {
            codeIndex.put(code, currentIndex);
            codeList.add(code);
            final Set<String> labelSet = new TreeSet<>();
            labelSet.add(label);
            codeDescriptions.add(labelSet);
            ret++;
        }

        addToDescriptionCodeIndex(decriptionCodesIndex, label, code);

        return ret;
    }

    void addToDescriptionCodeIndex(final Map<String, Set<String>> decriptionCodesIndex, final String label, final String code) {
        if (decriptionCodesIndex.containsKey(label)) {
            final Set<String> strings = decriptionCodesIndex.get(label);
            strings.add(code);
        } else {
            final Set<String> codeSet = new HashSet<>();
            codeSet.add(code);
            decriptionCodesIndex.put(label, codeSet);
        }
    }

    private void updateCodeFrequencies(final String code) {
        if (codeFrequencies.containsKey(code)) {
            codeFrequencies.put(code, codeFrequencies.get(code) + 1);
        } else {
            codeFrequencies.put(code, 1d);
        }
    }

    @SuppressWarnings("MethodWithMoreThanThreeNegations")
    private void loadDictionary(final InputStream dictionaryInputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(dictionaryInputStream))) {
            int currentIndex = 0;
            final Stream<String> lines = reader.lines();
            for (final String line : lines.collect(Collectors.toList())) {

                final String[] fields = line.split(separator);
                final String label = fields[DICTIONARY_LABEL_INDEX];
                final String code = normalizeCode(fields[DICTIONARY_ID_INDEX].trim());
                final String secondCode = normalizeCode(fields[DICTIONARY_ID_INDEX + 2].trim());

                if (!code.isEmpty()) {
                    currentIndex = addCodeAndLabel(code, label, currentIndex);
                    updateCodeFrequencies(code);
                    addToDescriptionCodeIndex(stdDecriptionCodesIndex, label, code);
                }
                if ((secondCode != null) && !secondCode.equals("NULL") && !secondCode.isEmpty()) {
                    currentIndex = addCodeAndLabel(secondCode, label, currentIndex);
                    updateCodeFrequencies(secondCode);
                    addToDescriptionCodeIndex(stdDecriptionCodesIndex, label, secondCode);
                }
            }
        } catch (final IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    private void loadResources() {
        if (includeDictionary) {
            try (final FileInputStream dictionaryInputStream = new FileInputStream(dictionaryFileName)) {
                logger.debug("Loading dictionary file...");
                loadDictionary(dictionaryInputStream);
            } catch (final FileNotFoundException e) {
                logger.error(FILE_NOT_FOUND_STRING, e.getLocalizedMessage());
                System.exit(1);
            } catch (final IOException e1) {
                logger.error(e1.getLocalizedMessage());
                System.exit(1);
            }
        }

        if ((includeCorpusRaw || includeCorpusStd) && !corpusFileName.isEmpty()) {
            try (final FileInputStream corpusInputStream = new FileInputStream(corpusFileName)) {
                logger.debug("Loading coprus file...");
                loadCorpus(corpusInputStream);
            } catch (final FileNotFoundException e) {
                logger.error(FILE_NOT_FOUND_STRING, e.getLocalizedMessage());
                System.exit(1);
            } catch (final IOException e1) {
                logger.error(e1.getLocalizedMessage());
                System.exit(1);
            }
        }

        loadChapters();
    }

    private String normalizeCode(final String code) {
        final Matcher matcher = CODE_NORM_RELPLACE_PATTERN.matcher(code);
        String finalCode = code;
        if (matcher.matches()) {
            finalCode = matcher.group(1) + "." + matcher.group(2);
        }
        return finalCode;
    }

    @SuppressWarnings("FeatureEnvy")
    @Override
    public void generate(final SKOSOntologyDelegate ontologyDelegate) {


        loadResources();

        logger.debug("Generating ontology...");

        final String schemeURI = baseURI + "ARBO";

        ontologyDelegate.addConceptScheme(schemeURI);
        String ontologyURI = baseURI;
        if (ontologyURI.endsWith("#")) {
            ontologyURI = ontologyURI.substring(0, ontologyURI.length() - 1);
        }
        ontologyDelegate.addStatement(ontologyURI, OntologyPrefix.getURI("rdf:type"), OntologyPrefix.getURI("owl:Ontology"));
        ontologyDelegate.addSkosProperty(ontologyURI, "This concept was generated from the eHealth2017 Task" +
                " 1 dictionaries for evaluation purposes and is subjected " +
                "to acceptance of the task's license agreement.", "note", "en");

        ontologyDelegate.setTopConcept(schemeURI, ontologyURI);

        final OntModel skosModel = ModelFactory.createOntologyModel();
        skosModel.read(EHealth2017DictionaryToSkos.class.getResourceAsStream("/ehealth/cim10chapters.rdf"), baseURI);
        ontologyDelegate.appendModel(skosModel);

        if (!mostFrequentCode && !adaptedCorpus) {
            for (int i = 0; i < codeList.size(); i++) {
                final String code = codeList.get(i);
                final String chapterURI = getChapterURI(code);

                final Set<String> labels = codeDescriptions.get(i);
                final String prefLabel = selectPrefLabel(labels);

                labels.remove(prefLabel);

                final String conceptURI = baseURI + code;
                ontologyDelegate.addConcept(conceptURI, schemeURI, chapterURI, prefLabel, labels, languageCode);


                statsHandler.incrementStatistic(SkosGeneratorOntologyStats.TOTAL_CLASS_COUNT_STATISTIC);
            }
        } else {
            final Map<String, Set<String>> finalCodeLabels = new HashMap<>();
            if (mostFrequentCode) {
                for (final Map.Entry<String, Set<String>> stringSetEntry : decriptionCodesIndex.entrySet()) {
                    final Set<String> codeSet = stringSetEntry.getValue();
                    final Stream<String> stream = codeSet.stream();
                    final Stream<PairImpl<String, Double>> pairStream = stream.map(code -> new PairImpl<>(code, codeFrequencies.get(code)));
                    List<Pair<String, Double>> codes = pairStream.collect(Collectors.toList());
                    codes = normalizeCodeFreqSet(codes);
                    codes.sort((o1, o2) -> Double.compare(o2.second(),o1.second()));
                    double cumulativeFreq = 0d;
                    final Iterator<Pair<String, Double>> iterator = codes.iterator();
//                    while ((cumulativeFreq < CUTOFF_CUMULATIVE_FREQ) && iterator.hasNext()) {
//                        final Pair<String, Double> codeFrequencyPair = iterator.next();
//                        final String code = codeFrequencyPair.first();
//
//                        if (!finalCodeLabels.containsKey(code)) {
//                            finalCodeLabels.put(code, new HashSet<>());
//                        }
//                        finalCodeLabels.get(code).add(stringSetEntry.getKey());
//                        cumulativeFreq += codeFrequencyPair.second();
//                    }
                    final String code = iterator.next().first();
                    if (!finalCodeLabels.containsKey(code)) {
                        finalCodeLabels.put(code, new HashSet<>());
                    }
                    finalCodeLabels.get(code).add(stringSetEntry.getKey());
                }

            } else {
                for (final Map.Entry<String, Set<String>> stringSetEntry : stdDecriptionCodesIndex.entrySet()) {
                    final Set<String> codeSet = stringSetEntry.getValue();
                    if (codeSet.size() == 1) {
                        final String code = codeSet.iterator().next();
                        if (!finalCodeLabels.containsKey(code)) {
                            finalCodeLabels.put(code, new HashSet<>());
                        }
                        finalCodeLabels.get(code).add(stringSetEntry.getKey());
                    }
                }

                for (final Map.Entry<String, Set<String>> stringSetEntry : rawDecriptionCodesIndex.entrySet()) {
                    final Set<String> codeSet = stringSetEntry.getValue();

                    for (final String code : codeSet) {
                        if (!finalCodeLabels.containsKey(code)) {
                            finalCodeLabels.put(code, new HashSet<>());
                        }
                        finalCodeLabels.get(code).add(stringSetEntry.getKey());
                    }
                }

            }
            for (final Map.Entry<String, Set<String>> stringSetEntry : finalCodeLabels.entrySet()) {
                final String chapterURI = getChapterURI(stringSetEntry.getKey());
                final Set<String> labels = stringSetEntry.getValue();
                final String prefLabel = selectPrefLabel(labels);
                labels.remove(prefLabel);
                final String conceptURI = baseURI + stringSetEntry.getKey();
                ontologyDelegate.addConcept(conceptURI, schemeURI, chapterURI, prefLabel, labels, languageCode);
                statsHandler.incrementStatistic(SkosGeneratorOntologyStats.TOTAL_CLASS_COUNT_STATISTIC);
            }
        }
    }

    private List<Pair<String, Double>> normalizeCodeFreqSet(final Iterable<Pair<String, Double>> codes) {
        double sum = 0d;
        for (final Pair<String, Double> code : codes) {
            sum += code.second();
        }
        final List<Pair<String, Double>> result = new ArrayList<>();
        for (final Pair<String, Double> code : codes) {
            result.add(new PairImpl<>(code.first(), code.second() / sum));
        }

        return result;
    }

    private String getChapterURI(final String code) {
        String rangeURI = codeRangeURIs.get(0);
        int position = 0;
        while ((position < codeRangeURIs.size()) && (codeRanges.get(position).second().compareTo(code) < 0)) {
            position++;
        }
        if (position < codeRanges.size()) rangeURI = codeRangeURIs.get(position);
        return rangeURI;
    }

    private String selectPrefLabel(final Collection<String> labels) {
        final Stack<String> labelStack = new Stack<>();
        labelStack.addAll(labels);
        labelStack.sort((o1, o2) -> Integer.compare(o2.length(), o1.length()));
        String choice = "";
        while (!labelStack.isEmpty() && choice.isEmpty()) {
            final String label = labelStack.pop();
            int upperCount = 0;
            for (int k = 0; k < label.length(); k++) {
                if (Character.isUpperCase(label.charAt(k))) upperCount++;
            }
            final double upperPercentage = (double) upperCount / (double) label.length();
            if (upperPercentage < HALF_PERCENT) {
                choice = label;
            }
        }
        if (choice.isEmpty() && !labels.isEmpty()) {
            final Iterator<String> iterator = labels.iterator();
            choice = iterator.next();
        }
        return choice;
    }


    public static void main(final String... args) throws IOException {
        final Properties properties = new Properties();
        properties.load(EHealth2017DictionaryToSkos.class.getResourceAsStream("/skos_generator_ehealth_config.properties"));

        OntologyPrefix.initialize(OntologyPrefix.class.getResourceAsStream("/ehealth/prefixes.ttl"));

        final CommandlineHandler commandlineHandler = new SKOSGeneratorCommandlineHandler();
        commandlineHandler.processCommandline(args, properties);

        final JedisPool jedisPool = new JedisPool(
                properties.getProperty(CONFIG_REDIS_HOST),
                Integer.valueOf(properties.getProperty(CONFIG_REDIS_PORT))
        );

        final String dictionaryPath = properties.getProperty(CONFIG_DICTIONARY_PATH);
        final String corpusPath = properties.getProperty(CONFIG_CORPUS_PATH);
        final Path path = Paths.get(dictionaryPath);
        final Path fileName = path.getFileName();
        final StatsHandler ontologyStats = new SkosGeneratorOntologyStats("SkosDictionaryGeneration_" + FILENAMEWITHEXT.split(fileName.toString())[0] + ".csv");


        final String language = properties.getProperty(CONFIG_LANGUAGE);

        boolean includeDictionary = false;

        final StringBuilder optionNameBuilder = new StringBuilder();

        if (properties.containsKey(CONFIG_DICTIONARY) && properties.getProperty(CONFIG_DICTIONARY).equals("true")) {
            includeDictionary = true;
            optionNameBuilder.append("_d");
        }

        boolean includeCorpusRaw = false;
        if (properties.containsKey(CONFIG_CORPUS_RAW) && properties.getProperty(CONFIG_CORPUS_RAW).equals("true")) {
            includeCorpusRaw = true;
            optionNameBuilder.append("_cr");
        }

        boolean includeCorpusStd = false;
        if (properties.containsKey(CONFIG_CORPUS_STD) && properties.getProperty(CONFIG_CORPUS_STD).equals("true")) {
            includeCorpusStd = true;
            optionNameBuilder.append("_cs");
        }

        boolean adaptedCorpus = false;
        if (properties.containsKey(CONFIG_CORPUS_ADAPTED) && properties.getProperty(CONFIG_CORPUS_ADAPTED).equals("true")) {
            adaptedCorpus = true;
            optionNameBuilder.append("_a");
        }

        boolean mostFrequentCode = false;
        if (properties.containsKey(CONFIG_MOST_FREQUENT_CODE) && properties.getProperty(CONFIG_MOST_FREQUENT_CODE).equals("true")) {
            mostFrequentCode = true;
            optionNameBuilder.append("_mfc");
        }

        final SKOSOntologyDelegate ontologyDelegate = new SKOSOntologyDelegateImpl(FILENAMEWITHEXT.split(fileName.toString())[0] + optionNameBuilder + ".owl", jedisPool);


        final SKOSOntologyGenerator SKOSOntologyGenerator =
                new EHealth2017DictionaryToSkos("http://chu-rouen.fr/cismef/CIM-10#", dictionaryPath, corpusPath,
                        ";", ontologyStats, language, includeDictionary, includeCorpusRaw,
                        includeCorpusStd, mostFrequentCode, adaptedCorpus);
        SKOSOntologyGenerator.generate(ontologyDelegate);
        ontologyDelegate.writeModel();

    }
}

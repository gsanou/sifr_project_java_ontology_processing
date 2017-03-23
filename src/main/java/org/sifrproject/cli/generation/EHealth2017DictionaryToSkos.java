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

import static org.sifrproject.configuration.ConfigurationConstants.CONFIG_REDIS_HOST;
import static org.sifrproject.configuration.ConfigurationConstants.CONFIG_REDIS_PORT;
import static org.sifrproject.configuration.SKOSGeneratorConfigurationConstants.CONFIG_DICTIONARY_PATH;
import static org.sifrproject.configuration.SKOSGeneratorConfigurationConstants.CONFIG_LANGUAGE;

@SuppressWarnings("ClassWithTooManyFields")
public final class EHealth2017DictionaryToSkos implements SKOSOntologyGenerator {

    private static final Logger logger = LoggerFactory.getLogger(EHealth2017DictionaryToSkos.class);
    private static final Pattern CODE_NORM_RELPLACE_PATTERN = Pattern.compile("([A-Z][0-9][0-9])([0-9][0-9]?)");
    private static final Pattern FILENAMEWITHEXT = Pattern.compile("\\.");
    private static final double HALF_PERCENT = 0.25d;
    private final StatsHandler statsHandler;

    private final Map<String, Integer> codeIndex;
    private final List<Set<String>> codeDescriptions;
    private final List<String> codeList;


    private final List<Pair<String,String>> codeRanges;
    private final List<String> codeRangeURIs;


    private final String separator;
    private final int labelIndex;
    private final int idIndex;

    private final String dictionaryFileName;

    private final String baseURI;

    private final String languageCode;

    private EHealth2017DictionaryToSkos(final String baseURI, final String dictionaryFileName, final String separator, final int labelIndex, final int idIndex, final StatsHandler statsHandler, final String languageCode) {
        this.statsHandler = statsHandler;

        codeIndex = new HashMap<>();
        codeDescriptions = new ArrayList<>();
        codeList = new ArrayList<>();

        this.separator = separator;
        this.dictionaryFileName = dictionaryFileName;
        this.labelIndex = labelIndex;
        this.idIndex = idIndex;

        this.baseURI = baseURI;

        codeRanges = new ArrayList<>();
        codeRangeURIs = new ArrayList<>();

        this.languageCode = languageCode;


    }

    private void loadSource(final InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            int currentIndex = 0;
            for (final String line : reader.lines().collect(Collectors.toList())) {

                final String[] fields = line.split(separator);
                final String label = fields[labelIndex];
                final String id = normalizeCode(fields[idIndex].trim());
                final String secondId = normalizeCode(fields[idIndex+2].trim());

                if (!id.isEmpty()) {
                    currentIndex = addCodeAndLabel(id, label, currentIndex);
                }
                if((secondId != null) && !secondId.equals("NULL") && !secondId.isEmpty()) {
                    currentIndex = addCodeAndLabel(secondId, label, currentIndex);
                }
            }
        } catch (final IOException e) {
            logger.error(e.getLocalizedMessage());
        }

        try (InputStream chaptersStream = EHealth2017DictionaryToSkos.class.getResourceAsStream("/ehealth/chapterRange.csv")) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(chaptersStream))) {
                for (final String line : reader.lines().collect(Collectors.toList())) {
                    final String[] fields = line.split(",");
                    final String[] range = fields[1].split("-");
                    codeRanges.add(new PairImpl<>(range[0],range[1]));
                    codeRangeURIs.add(fields[0]);
                }
            }
        } catch (final IOException e) {
            logger.error(e.getLocalizedMessage());
        }
    }

    private String normalizeCode(final String code) {
        final Matcher matcher = CODE_NORM_RELPLACE_PATTERN.matcher(code);
        String finalCode = code;
        if (matcher.matches()) {
            finalCode = matcher.group(1) + "." + matcher.group(2);
        }
        return finalCode;
    }

    private int addCodeAndLabel(final String code, final String label, final int currentIndex){
        int ret = currentIndex;
        if (codeIndex.containsKey(code)) {
            final int index = codeIndex.get(code);
            codeDescriptions.get(index).add(label);
        } else {
            codeIndex.put(code, currentIndex);
            codeList.add(code);
            final Set<String> labelSet = new TreeSet<>();
            labelSet.add(label);
            codeDescriptions.add(labelSet);
            ret++;
        }
        return ret;
    }

    @Override
    public void generate(final SKOSOntologyDelegate ontologyDelegate) {
        try (final FileInputStream fileInputStream = new FileInputStream(dictionaryFileName)){
            logger.debug("Loading dictionary file...");
            loadSource(fileInputStream);

            logger.debug("Generating ontology...");

            final String schemeURI = baseURI+"ARBO";

            ontologyDelegate.addConceptScheme(schemeURI);
            String ontologyURI = baseURI;
            if(ontologyURI.endsWith("#")){
                ontologyURI = ontologyURI.substring(0,ontologyURI.length()-1);
            }
            ontologyDelegate.addStatement(ontologyURI,OntologyPrefix.getURI("rdf:type"),OntologyPrefix.getURI("owl:Ontology"));

            ontologyDelegate.setTopConcept(schemeURI,ontologyURI);

            final OntModel skosModel = ModelFactory.createOntologyModel();
            skosModel.read(EHealth2017DictionaryToSkos.class.getResourceAsStream("/ehealth/cim10chapters.rdf"),baseURI);
            ontologyDelegate.appendModel(skosModel);

            for(int i = 0; i<codeList.size();i++) {
                final String code = codeList.get(i);
                final String chapterURI = getChapterURI(code);

                final Set<String> labels = codeDescriptions.get(i);
                final String prefLabel = selectPrefLabel(labels);

                labels.remove(prefLabel);

                final String conceptURI = baseURI + code;
                ontologyDelegate.addConcept(conceptURI,schemeURI,chapterURI,prefLabel,labels, languageCode);
                ontologyDelegate.addSkosProperty(conceptURI,"This concept was generated from the eHealth2017 Task" +
                        " 1 dictionaries for evaluation purposes and is subjected " +
                        "to acceptance of the task's license agreement.","note","en");


                statsHandler.incrementStatistic(SkosGeneratorOntologyStats.TOTAL_CLASS_COUNT_STATISTIC);
            }
        } catch (final FileNotFoundException e) {
            logger.error(e.getLocalizedMessage());
            System.exit(1);
        } catch (final IOException e) {
            logger.error(e.getLocalizedMessage());
        }


    }

    private String getChapterURI(final String code){
        String rangeURI = codeRangeURIs.get(0);
        int position = 0;
        while ((position < codeRangeURIs.size()) && (codeRanges.get(position).second().compareTo(code) < 0)) {
            position++;
        }
        if(position<codeRanges.size()) rangeURI = codeRangeURIs.get(position);
        return rangeURI;
    }

    private String selectPrefLabel(final Collection<String> labels){
        final Stack<String> labelStack = new Stack<>();
        labelStack.addAll(labels);
        labelStack.sort((o1, o2) -> Integer.compare(o2.length(),o1.length()));
        String choice = "";
        while(!labelStack.isEmpty() && choice.isEmpty()){
            final String label = labelStack.pop();
            int upperCount = 0;
            for (int k = 0; k < label.length(); k++) {
                if (Character.isUpperCase(label.charAt(k))) upperCount++;
            }
            final double upperPercentage = (double) upperCount / (double)label.length();
            if(upperPercentage< HALF_PERCENT){
                choice = label;
            }
        }
        if(choice.isEmpty() && !labels.isEmpty()){
            choice = labels.iterator().next();
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
        final Path path = Paths.get(dictionaryPath);
        final StatsHandler ontologyStats = new SkosGeneratorOntologyStats("SkosDictionaryGeneration_" +  FILENAMEWITHEXT.split(path.getFileName().toString())[0] + ".csv");

        final SKOSOntologyDelegate ontologyDelegate = new SKOSOntologyDelegateImpl(FILENAMEWITHEXT.split(path.getFileName().toString())[0]+".owl", jedisPool);

        final String language = properties.getProperty(CONFIG_LANGUAGE);

        final SKOSOntologyGenerator SKOSOntologyGenerator = new EHealth2017DictionaryToSkos("http://chu-rouen.fr/cismef/CIM-10#",dictionaryPath,";", 0, 1, ontologyStats,language);
        SKOSOntologyGenerator.generate(ontologyDelegate);
        ontologyDelegate.writeModel();

    }
}

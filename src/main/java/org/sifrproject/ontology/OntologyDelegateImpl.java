package org.sifrproject.ontology;


import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.sifrproject.cli.OWLOntologyCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OntologyDelegateImpl implements OntologyDelegate {
    private static final Logger logger = LoggerFactory.getLogger(OntologyDelegateImpl.class);

    private static final Pattern URL_PATTERN = Pattern.compile("[^:]{2,6}:.*");
    private static final Pattern CONTAINS_DOT_PATTERN = Pattern.compile("\\.");

    private static final String ALT_LABEL_PROPERTY = "http://www.w3.org/2004/02/skos/core#altLabel";
    private static final String CUI_PROPERTY_URI = "http://bioportal.bioontology.org/ontologies/umls/cui";
    private static final String TUI_PROPERTY_URI = "http://bioportal.bioontology.org/ontologies/umls/tui";
    private static final String HAS_STY_PROPERTY_URI = "http://bioportal.bioontology.org/ontologies/umls/hasSTY";
    private static final String STY_URL_BASE = "http://purl.lirmm.fr/ontology/STY/";
    private static final String MAPPINGS_PROPERTY_URI = "http://linguistics-ontology.org/gold/freeTranslation";
    private static final String TURTLE = "TURTLE";


    private final JedisPool jedisPool;

    private static final String MAPPING_PREFIX = "m_";
    private static final String CUI_PREFIX = "c_";
    private static final String TUI_PREFIX = "t_";
    private static final String ALTCUI_PREFIX = "ac_";
    private static final String PREFLABEL_PREFIX = "pl_";

    private final OntModel mappingsModel;
    private final OntModel targetModel;
    private final OntModel sourceModel;

    private String ontologyName = "";
    private final String outputFileSuffix;

    public OntologyDelegateImpl(final String mappingsModelURI, final String targetModelURI, final String sourceModelURI, final String outputFileSuffix, final JedisPool jedisPool) {
        this.jedisPool = jedisPool;

        final Matcher matcher = URL_PATTERN.matcher(sourceModelURI);
        if (matcher.matches()) {
            try {
                final URL url = new URL(sourceModelURI);
                ontologyName = url.getFile();
            } catch (final MalformedURLException e) {
                logger.error(e.getLocalizedMessage());
            }
        } else {
            final Path path = Paths.get(sourceModelURI);
            final Path fileName = path.getFileName();
            ontologyName = fileName.toString();
        }
        if (ontologyName.contains(".")) {
            ontologyName = CONTAINS_DOT_PATTERN.split(ontologyName)[0];
        }

        sourceModel = loadModel(sourceModelURI);
        targetModel = loadModel(targetModelURI);
        mappingsModel = loadModel(mappingsModelURI);


        this.outputFileSuffix = outputFileSuffix;
    }

    @Override
    public Collection<String> findCUIsInAltLabel(final String classURI) {
        Collection<String> cuis;
        try (Jedis jedis = jedisPool.getResource()) {

            cuis = jedis.lrange(ALTCUI_PREFIX + classURI, 0, -1);

            if (cuis.isEmpty()) {
                cuis = new ArrayList<>();
                synchronized (sourceModel) {
                    cuisFromAltLabel(sourceModel, classURI, cuis);
                }
                if (!cuis.isEmpty()) {
                    jedis.lpush(ALTCUI_PREFIX + classURI, cuis.toArray(new String[cuis.size()]));
                }
            }
        }
        return cuis;
    }


    @Override
    public Collection<String> findTUIsForMappings(final String classURI) {
        Collection<String> tuis = new ArrayList<>();
        final Collection<String> mappings = findMappings(classURI);
        if (!mappings.isEmpty()) {
            try (Jedis jedis = jedisPool.getResource()) {
                for (final String mappingURI : mappings) {
                    tuis = jedis.lrange(TUI_PREFIX + mappingURI, 0, -1);
                    if (tuis.isEmpty()) {
                        synchronized (targetModel) {
                            tuis = new ArrayList<>();
                            tuisFromModel(targetModel, mappingURI, tuis);
                        }
                    }
                }
            }
        }
        return tuis;
    }

    @Override
    public void cuisFromSourceModel(final String classURI, final Collection<String> cuis) {
        synchronized (sourceModel) {
            cuisFromModel(sourceModel, classURI, cuis);
        }
    }

    @Override
    public void tuisFromSourceModel(final String classURI, final Collection<String> tuis) {
        synchronized (sourceModel) {
            tuisFromModel(sourceModel, classURI, tuis);
        }
    }

    @Override
    public String conceptLabelFromSourceModel(final Resource thisClass) {
        final String prefLabel;
        try(Jedis jedis = jedisPool.getResource()) {
            final String cachedPrefLabel = jedis.get(PREFLABEL_PREFIX+thisClass.getURI());
            if(cachedPrefLabel == null) {
                synchronized (sourceModel) {
                    final StmtIterator stmtIterator = sourceModel.listStatements(thisClass, sourceModel.createOntProperty(OWLOntologyCleaner.SKOS_CORE_PREF_LABEL), (RDFNode) null);
                    final StringBuilder conceptDescription = new StringBuilder();
                    while (stmtIterator.hasNext()) {
                        final Statement statement = stmtIterator.next();
                        conceptDescription.append(statement.getString());
                    }
                    prefLabel = conceptDescription.toString();
                }
            } else {
                prefLabel = cachedPrefLabel;
            }
        }
        return prefLabel;
    }

    private void cuisFromAltLabel(final Model model, final String classURI, final Collection<String> cuis) {
        final StmtIterator stmtIterator = model.listStatements(
                ResourceFactory.createResource(classURI),
                model.createProperty(ALT_LABEL_PROPERTY),
                (RDFNode) null);

        while (stmtIterator.hasNext()) {
            final Statement statement = stmtIterator.nextStatement();
            final String altLabel = statement.getString();
            final Pattern pattern = Pattern.compile("(C[0-9][0-9][0-9][0-9][0-9][0-9][0-9])");
            final Matcher matcher = pattern.matcher(altLabel);
            if (matcher.matches()) {
                cuis.add(altLabel);
            }
        }
    }


    private void cuisFromModel(final Model model, final String classURI, final Collection<String> cuis) {
            final StmtIterator stmtIterator = model.listStatements(
                    ResourceFactory.createResource(classURI),
                    model.createProperty(CUI_PROPERTY_URI),
                    (RDFNode) null);

            while (stmtIterator.hasNext()) {
                final Statement statement = stmtIterator.nextStatement();
                cuis.add(statement.getString());
            }
    }


    private void tuisFromModel(final Model model, final String classURI, final Collection<String> tuis) {
        final StmtIterator stmtIterator = model.listStatements(
                ResourceFactory.createResource(classURI),
                model.createProperty(TUI_PROPERTY_URI),
                (RDFNode) null);

        while (stmtIterator.hasNext()) {
            final Statement statement = stmtIterator.nextStatement();
            tuis.add(statement.getString());
        }
    }

    @Override
    public void addTUIToSourceModel(final String classURI, final String tui) {
        synchronized (sourceModel) {
            sourceModel.add(ResourceFactory.createResource(classURI), sourceModel.createProperty(TUI_PROPERTY_URI), tui);
            sourceModel.add(ResourceFactory.createResource(classURI), sourceModel.createProperty(HAS_STY_PROPERTY_URI), sourceModel.createClass(STY_URL_BASE + tui));
        }
    }

    @Override
    public void addCUIToSourceModel(final String classURI, final String cui) {
        synchronized (sourceModel) {
            sourceModel.add(ResourceFactory.createResource(classURI), sourceModel.createProperty(CUI_PROPERTY_URI), cui);
        }
    }

    @Override
    public String getOntologyName() {
        return ontologyName;
    }

    @Override
    public ExtendedIterator<OntClass> getSourceClasses() {
        return sourceModel.listNamedClasses();
    }

    @Override
    @SuppressWarnings({"FeatureEnvy", "OverlyNestedMethod"})
    public Collection<String> findCUIsFromMappings(final String classURI) {
        Collection<String> cuis = new ArrayList<>();
        final Collection<String> mappings = findMappings(classURI);
        if (!mappings.isEmpty()) {
            try (Jedis jedis = jedisPool.getResource()) {
                for (final String mappingURI : mappings) {
                    cuis = jedis.lrange(CUI_PREFIX + mappingURI, 0, -1);
                    if (cuis.isEmpty()) {
                        synchronized (targetModel) {
                            cuisFromModel(targetModel, mappingURI, cuis);
                            if (cuis.isEmpty()) {
                                cuisFromAltLabel(targetModel, mappingURI, cuis);

                            }
                        }
                        if (!cuis.isEmpty()) {
                            jedis.lpush(CUI_PREFIX + mappingURI, cuis.toArray(new String[cuis.size()]));
                        }
                    }
                }
                logger.info("\t\t\t\t Found {} CUIs in mappings.", cuis.size());
            }
        }
        return cuis;
    }

    private Collection<String> findMappings(final String classURI) {
        Collection<String> mappings;

        try (Jedis jedis = jedisPool.getResource()) {
            mappings = jedis.lrange(MAPPING_PREFIX + classURI, 0, -1);
            if (mappings.isEmpty()) {
                mappings = new ArrayList<>();
                synchronized (mappingsModel) {
                    final StmtIterator stmtIterator = mappingsModel.listStatements(
                            ResourceFactory.createResource(classURI),
                            mappingsModel.createProperty(MAPPINGS_PROPERTY_URI),
                            (RDFNode) null);

                    while (stmtIterator.hasNext()) {
                        final Statement statement = stmtIterator.nextStatement();
                        final RDFNode object = statement.getObject();
                        mappings.add(object.toString());
                    }
                }

                if (!mappings.isEmpty()) {
                    final String[] strMapping = mappings.toArray(new String[mappings.size()]);
                    jedis.lpush(MAPPING_PREFIX + classURI, strMapping);
                }
            }
        }
        logger.info("Found {} mappings...", mappings.size());
        return mappings;
    }

    /**
     * Load the input ontology to process in a Jena OntModel, supports local uncompressed files, bziped/gzipped files and
     * remote files over http
     */
    private OntModel loadModel(final String modelURL) {
        final Path path = Paths.get(modelURL);
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF);
        try {
            logger.info("Reading ontology model...");
            final Matcher matcher = URL_PATTERN.matcher(modelURL);
            if (matcher.matches()) {
                // It's an URL
                ontModel.read(modelURL);
                logger.info("\tFrom URL: {}", modelURL);
            } else if (Files.isDirectory(path)) {
                logger.info("\tFrom TDB dataset: {}", modelURL);
                final Dataset dataset = TDBFactory.createDataset(path.toString());
                dataset.begin(ReadWrite.READ);
                final Model model = dataset.getDefaultModel();
                dataset.end();
                ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF,model);
            } else {
                logger.info("\tFrom File: {}", modelURL);
                // It's a file
                @SuppressWarnings("HardcodedFileSeparator") String rdfFormat = "RDF/XML";
                if (modelURL.contains(".ttl")) {
                    rdfFormat = TURTLE;
                }

                final InputStreamReader modelReader = getFileModelReader(modelURL);

                ontModel.read(modelReader, null, rdfFormat);
                modelReader.close();
            }

        } catch (final FileNotFoundException e) {
            logger.error("Could not read {}", modelURL);
            System.exit(1);
        } catch (final IOException e) {
            logger.error(e.getLocalizedMessage());
        }
        return ontModel;
    }

    @SuppressWarnings({"resource", "IOResourceOpenedButNotSafelyClosed"})
    private InputStreamReader getFileModelReader(final String modelURL) throws IOException {
        final InputStreamReader modelReader;
        if (modelURL.endsWith(".bz2")) {
            modelReader = new InputStreamReader(new BZip2CompressorInputStream(new FileInputStream(modelURL)));
        } else if (modelURL.endsWith(".gz")) {
            modelReader = new InputStreamReader(new GzipCompressorInputStream(new FileInputStream(modelURL)));
        } else {
            modelReader = new InputStreamReader(new FileInputStream(modelURL));
        }
        return modelReader;
    }

    /**
     * Output the enriched model to a file in the running directory of the project with "_enriched" appended to the
     * end.
     */
    @Override
    public void writeEnrichedModel() {
        final String outputModelFileName = ontologyName + "_enriched" + outputFileSuffix;

        try {
            final OutputStream outputModelStream = new FileOutputStream(outputModelFileName);
            sourceModel.write(outputModelStream, TURTLE);
            outputModelStream.close();

        } catch (final FileNotFoundException e) {
            logger.error("Could not create output stream: {}", e.getLocalizedMessage());
        } catch (final IOException e) {
            logger.error("IOException while creating output stream: {}", e.getLocalizedMessage());
        }
    }
}

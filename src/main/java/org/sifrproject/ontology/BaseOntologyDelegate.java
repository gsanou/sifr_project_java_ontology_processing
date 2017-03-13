package org.sifrproject.ontology;


import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import org.sifrproject.cli.OWLOntologyCleaner;
import org.sifrproject.ontology.prefix.OntologyPrefix;
import org.sifrproject.utils.CacheKeyPrefixes;
import org.sifrproject.utils.EmptyResultsCache;
import org.sifrproject.utils.OntologyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.sifrproject.utils.OntologyLoader.TURTLE;

public class BaseOntologyDelegate implements OntologyDelegate {

    private static final Logger logger = LoggerFactory.getLogger(BaseOntologyDelegate.class);

    protected static final String SKOS_CORE_PREF_LABEL_PROPERTY = OntologyPrefix.getURI(OWLOntologyCleaner.SKOS_PREF_LABEL);
    protected static final String SKOS_ALT_LABEL_PROPERTY = OntologyPrefix.getURI("skos:altLabel");

    private static final Pattern CONTAINS_DOT_PATTERN = Pattern.compile("\\.");
    private static final Pattern URL_PATTERN = Pattern.compile("[^:]{2,6}:.*");
    private static final Pattern BZ2PAttern = Pattern.compile(".bz2");

    protected final OntModel model;
    protected List<OntClass> classes;
    private String ontologyName = "";
    private String outputFileSuffix;
    protected final JedisPool jedisPool;
    private String outputFormat = TURTLE;

    private final Map<String, OntResource> resourceCache = new HashMap<>();
    private final Map<String, OntProperty> propertyCache = new HashMap<>();

    protected BaseOntologyDelegate(final String modelURI, final String outputFileSuffix, final JedisPool jedisPool) {
        model = OntologyLoader.loadModel(modelURI);
        loadPrefixes();

        this.jedisPool = jedisPool;
        this.outputFileSuffix = outputFileSuffix;

        final Matcher matcher = URL_PATTERN.matcher(modelURI);
        if (matcher.matches()) {
            try {
                final URL url = new URL(modelURI);
                ontologyName = url.getFile();
            } catch (final MalformedURLException e) {
                logger.error(e.getLocalizedMessage());
            }
        } else {
            final Path path = Paths.get(modelURI);
            final Path fileName = path.getFileName();
            ontologyName = fileName.toString();
        }
        if (ontologyName.contains(".")) {
            final String[] comps = CONTAINS_DOT_PATTERN.split(ontologyName);
            ontologyName = comps[0];
            comps[1] = BZ2PAttern.matcher(comps[1]).replaceAll("");
            switch (comps[1]){
                case "ttl":
                    outputFormat = TURTLE;
                    break;
                case "xml":
                case "owl":
                case "xrdf":
                default:
                    //noinspection HardcodedFileSeparator
                    outputFormat= "RDF/XML-ABBREV";
                    break;
            }
            this.outputFileSuffix+="."+comps[1];
        }
    }

    private void loadPrefixes() {
        final Map<String, String> nsPrefixMap = model.getNsPrefixMap();
        for (final String systemPrefix : OntologyPrefix.getPrefixes()) {
            if (!nsPrefixMap.containsKey(systemPrefix)) {
                model.setNsPrefix(systemPrefix, OntologyPrefix.getPrefixURI(systemPrefix));
            }
        }
    }

    @Override
    public String getConceptLabel(final String classURI) {
        final String prefLabel;
        try (Jedis jedis = jedisPool.getResource()) {
            final String key = CacheKeyPrefixes.PREFLABEL + classURI;
            final String cachedPrefLabel = jedis.get(key);
            if (cachedPrefLabel == null) {
                synchronized (model) {
                    final OntProperty ontProperty = getOrCreateProperty(SKOS_CORE_PREF_LABEL_PROPERTY);
                    final StmtIterator stmtIterator = model.listStatements(getOrCreateResource(classURI), ontProperty, (RDFNode) null);
                    final StringBuilder conceptDescription = new StringBuilder();
                    while (stmtIterator.hasNext()) {
                        final Statement statement = stmtIterator.next();
                        conceptDescription.append(statement.getString());
                    }
                    prefLabel = conceptDescription.toString();
                    jedis.set(key, prefLabel);
                }
            } else {
                prefLabel = cachedPrefLabel;
            }
        }
        return prefLabel;
    }

    @Override
    public List<OntClass> getClasses() {
        if (classes == null) {
            logger.info("Loading class list in memory...");
            final ExtendedIterator<OntClass> ontClassExtendedIterator = model.listNamedClasses();
            classes = ontClassExtendedIterator.toList();
        }
        return Collections.unmodifiableList(classes);
    }

    @Override
    public String getOntologyName() {
        return ontologyName;
    }


    /**
     * Output the enriched model to a file in the running directory of the project with "_enriched" appended to the
     * end.
     */
    @Override
    public void writeModel() {
        final String outputModelFileName = ontologyName + "_" + outputFileSuffix;

        try {
            final OutputStream outputModelStream = new FileOutputStream(outputModelFileName);
            model.write(outputModelStream, outputFormat);
            outputModelStream.close();

        } catch (final FileNotFoundException e) {
            logger.error("Could not create output stream: {}", e.getLocalizedMessage());
        } catch (final IOException e) {
            logger.error("IOException while creating output stream: {}", e.getLocalizedMessage());
        }
    }


    @Override
    public void addSkosProperty(final String classURI, final String value, final String propertyName, final String languageCode) {
        addLiteralStatement(classURI, model.expandPrefix("skos:" + propertyName), value,languageCode);
    }

    @Override
    public void addSkosProperty(final String classURI, final String value, final String propertyName) {
        addLiteralStatement(classURI, model.expandPrefix("skos:" + propertyName),value);
    }



    @Override
    public void addStatement(final String sourceURI, final String propertyURI, final String targetURI) {
        final OntResource subject = getOrCreateResource(sourceURI);
        final OntProperty property = getOrCreateProperty(propertyURI);
        final OntResource object = getOrCreateResource(targetURI);
        model.add(subject, property, object);
    }

    @Override
    public void addLiteralStatement(final String sourceURI, final String propertyURI, final String literal) {
        final OntResource subject = getOrCreateResource(sourceURI);
        final OntProperty property = getOrCreateProperty(propertyURI);
        model.add(subject, property, literal);
    }

    @Override
    public void addLiteralStatement(final String sourceURI, final String propertyURI, final String literal, final String languageCode) {
        final OntResource subject = getOrCreateResource(sourceURI);
        final OntProperty property = getOrCreateProperty(propertyURI);
        model.add(subject, property, model.createLiteral(literal,languageCode));
    }

    @Override
    public Collection<String> getObjectsThroughRelation(final String classURI, final String relationURI) {
        Collection<String> collection;
        try (Jedis jedis = jedisPool.getResource()) {
            final String key = CacheKeyPrefixes.CLASS_RELATION + classURI + "_" + relationURI;
            collection = jedis.lrange(key, 0, -1);
            if (collection.isEmpty() && !EmptyResultsCache.isEmpty(key, jedis)) {
                collection = new TreeSet<>();
                final OntProperty ontProperty = getOrCreateProperty(relationURI);
                final OntResource subject = getOrCreateResource(classURI);
                final StmtIterator stmtIterator =
                        model.listStatements(subject,
                                ontProperty,
                                (RDFNode) null);
                while (stmtIterator.hasNext()) {
                    final Statement statement = stmtIterator.nextStatement();
                    final RDFNode object = statement.getObject();
                    collection.add(object.toString());
                }
                if (collection.isEmpty()) {
                    EmptyResultsCache.markEmpty(key, jedis);
                } else {
                    jedis.lpush(key, collection.toArray(new String[collection.size()]));
                }
            }
        }
        return collection;
    }

    @Override
    public Collection<String> getObjectsThroughRelation(final Collection<String> classURIs, final String propertyURI) {
        final Collection<String> collection = new TreeSet<>();
        for (final String classURI : classURIs) {
            collection.addAll(getObjectsThroughRelation(classURI, propertyURI));
        }

        return collection;
    }

    protected OntResource getOrCreateResource(final String URI) {
        final OntResource resource;
        if (resourceCache.containsKey(URI)) {
            resource = resourceCache.get(URI);
        } else {
            //   resource = model.getOntResource(URI);
            // if (resource == null) {
            resource = model.createOntResource(URI);
            //}
            resourceCache.put(URI, resource);
        }
        return resource;
    }

    protected OntProperty getOrCreateProperty(final String URI) {
        final OntProperty property;

        if (propertyCache.containsKey(URI)) {
            property = propertyCache.get(URI);
        } else {
            //    property = model.getOntProperty(URI);
            //  if (property == null) {
            property = model.createOntProperty(URI);
            //}
            propertyCache.put(URI, property);
        }
        return property;
    }

    /*protected OntClass getOrCreateClass(final String URI){
        OntClass ontClass = model.getOntClass(URI);
        if(ontClass == null){
            ontClass = model.createClass(URI);
        }
        return ontClass;
    }*/

}

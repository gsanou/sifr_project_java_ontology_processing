package org.sifrproject.ontology.prefix;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.apache.jena.riot.RDFLanguages.TURTLE;

public final class OntologyPrefix {

    private static final Logger logger = LoggerFactory.getLogger(OntologyPrefix.class);
    private static final ThreadLocal<OntModel> prefixModel = new ThreadLocal<>();
    @SuppressWarnings("InstanceVariableOfConcreteClass")

    private OntologyPrefix() {
    }

    private static void checkInit(){
        if(prefixModel.get() ==null) {
            prefixModel.set(ModelFactory.createOntologyModel());
            try (InputStream prefixModelStream = OntologyPrefix.class.getResourceAsStream("/prefixes.ttl")) {
                prefixModel.get().read(prefixModelStream, null, TURTLE.getName());
            } catch (final IOException e) {
                logger.error(e.getLocalizedMessage());
            }
        }
    }


    public static synchronized String getURI(final String entityName) {
        //noinspection StaticVariableUsedBeforeInitialization
        checkInit();
        final OntModel ontModel = prefixModel.get();
        return ontModel.expandPrefix(entityName);
    }

    public static synchronized String getPrefixURI(final String prefix) {
        //noinspection StaticVariableUsedBeforeInitialization
        checkInit();
        final OntModel ontModel = prefixModel.get();
        return ontModel.getNsPrefixURI(prefix);
    }

    public static synchronized Set<String> getPrefixes(){
        checkInit();
        final OntModel ontModel = prefixModel.get();
        final Map<String, String> nsPrefixMap = ontModel.getNsPrefixMap();
        return nsPrefixMap.keySet();
    }

}

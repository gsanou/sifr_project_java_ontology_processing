package org.sifrproject.ontology.code;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

public class URICodeFinder implements CodeFinder {
    private static final Logger logger = LoggerFactory.getLogger(URICodeFinder.class);

    URICodeFinder() {
    }

    @Override
    public String getCode(final String classURI) {
        String code = null;
        try {
            final URI uri = new URI(classURI);
            code = uri.getFragment();
            if((code != null) && code.contains("_")){
                code = code.split("_")[0];
            }
        } catch (final URISyntaxException e) {
            logger.error(e.getLocalizedMessage());
        }
        return code;
    }
}

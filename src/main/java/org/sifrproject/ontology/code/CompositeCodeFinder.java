package org.sifrproject.ontology.code;

import org.sifrproject.ontology.cuis.CUIOntologyDelegate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompositeCodeFinder implements CodeFinder {

    private static final Pattern PATTERN = Pattern.compile("([^\\^]*)\\^\\^(.*)");
    private final CodeFinder skosFinder;
    private final CodeFinder icfFinder;
    private final CodeFinder icpcFinder;
    private final CodeFinder uriFinder;

    public CompositeCodeFinder(final CUIOntologyDelegate ontologyDelegate) {
        skosFinder = new SKOSNotationCodeFinder(ontologyDelegate);
        icfFinder = new ICDCodeFinder(ontologyDelegate);
        icpcFinder = new ICPC2PCodeFinder(ontologyDelegate);
        uriFinder = new URICodeFinder();
    }

    @Override
    public String getCode(final String classURI) {
        String code = skosFinder.getCode(classURI);
        if(code==null) {
            code = icfFinder.getCode(classURI);
        }
        if(code==null){
            code = icpcFinder.getCode(classURI);
        }
        if(code==null){
            code = uriFinder.getCode(classURI);
        }
        final Matcher matcher = PATTERN.matcher(code);
        if(matcher.matches()){
            code= matcher.group(1);
        }
        return code;
    }
}

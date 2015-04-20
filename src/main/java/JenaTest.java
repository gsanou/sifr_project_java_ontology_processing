/**
 * Created by Vincent on 20/04/2015.
 */

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;

public class JenaTest {

    // Prefix
    public static final String aofPrefix = "http://purl.org/ao/foaf/";
    public static final String aoPrefix = "http://purl.org/ao/";
    public static final String aotrPrefix = "http://purl.org/ao/types/";
    public static final String pavPrefix = "http://purl.org/pav/2.0/";
    public static final String annPrefix = "http://www.w3.org/2000/10/annotation-ns#";
    public static final String aosPrefix = "http://purl.org/ao/selectors/";
    public static final String aot = "http://purl.org/ao/types/";
    public static final String foafPrefix = "http://xmlns.com/foaf/0.1/";
    // URLs
    public static final String createdByURL = "http://bioportal.bioontology.org/annotator";
    public static final String contextURL = "http://my.example.org/se/10300";
    public static final String rootURL = "http://bioportal.bioontology.org/annotator/ann/";
    public static final String root2URL = "http://bioportal.bioontology.org/annotator/sel/";
    public static final String onDocumentURL = "http://data.bioontology.org/annotator?";

    public static void main(String [ ] args) {

        Model m = ModelFactory.createDefaultModel();


        Property exact = m.createProperty(aosPrefix + "exact");
        Property offset = m.createProperty(aosPrefix + "offset");
        Property range = m.createProperty(aosPrefix + "range");
        Resource r5 = m.createResource(aoPrefix + "Selector");
        Resource r6 = m.createResource(aosPrefix + "TextSelector");
        Resource r7 = m.createResource(aosPrefix + "OffsetRangeSelector");
        String text = "MÈLANOME";
        Property onDocument = m.createProperty(aofPrefix + "onDocument");
        Resource onDocumentResource = m.createResource("http://data.bioontology.org/annotator?4075");


        Long from = new Long(1);
        Long taill = new Long(8);

        for (int i = 1; i < 3; i++) {

            String selectorURI = getSelectorURI(from, taill, m);
            Resource root2;

            if (selectorURI.equals("")) {
                System.out.println("nooooo");
                root2 = m.createResource("http://bioportal.bioontology.org/annotator/sel/4075/0");
                m.add(root2, onDocument, onDocumentResource)
                        .add(root2, range, m.createTypedLiteral(taill.toString(), XSDDatatype.XSDinteger))
                        .add(root2, exact, text)
                        .add(root2, offset, m.createTypedLiteral(from.toString(), XSDDatatype.XSDinteger))
                        .add(root2, RDF.type, r7).add(root2, RDF.type, r6)
                        .add(root2, RDF.type, r5);
            } else {
                System.out.println(selectorURI);
                root2 = m.createResource(selectorURI);
            }
        }
    }

    private static String getSelectorURI (Long from, Long size, Model m) {
        String queryString = "select distinct ?sel where {?sel <" + aosPrefix + "range> ?range ; <" + aosPrefix + "offset> ?offset . FILTER (?range = " + size.toString() + " && ?offset = " + from.toString() + ") } LIMIT 10";


        Query query = QueryFactory.create(queryString) ;
        QueryExecution qexec = QueryExecutionFactory.create(query, m);
        ResultSet results = qexec.execSelect() ;
        Resource r = null;
        for ( ; results.hasNext() ; )
        {
            QuerySolution soln = results.nextSolution() ;
            r = soln.getResource("sel") ; // Get a result variable - must be a resource
            System.out.println(r.toString());
        }
        qexec.close() ;

        String selectorURI = "";
        if (r != null) {
            selectorURI = r.getURI();
        }

        return selectorURI;
    }


}

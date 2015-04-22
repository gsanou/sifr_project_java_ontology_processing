import java.io.File;
import java.util.ArrayList;
import java.util.List;


import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;


/**
 * Created by Vincent on 15/04/2015.
 */
public class OWLOntologyCleaner {

    static OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    static OWLDataFactory df = OWLManager.getOWLDataFactory();

    private OWLOntology ontology;
    private OWLOntology cleanedOntology;

    public OWLOntologyCleaner(String ontologyFileName) {
        File ontologyFile = new File(ontologyFileName);
        try {
            //Load ontology from file
            ontology = manager.loadOntologyFromOntologyDocument(ontologyFile);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
    }

    public static void main(String [ ] args) throws OWLOntologyCreationException {
        String ontologyFileName = "ontology_files/ONTOTOXNUC.owl";
        OWLOntologyCleaner oc = new OWLOntologyCleaner(ontologyFileName);

        oc.cleanMultilingualOntology("en");

        oc.printLabels();
        oc.outputOntology();
    }

    public void printLabels() {

        for (OWLClass cls : ontology.getClassesInSignature()) {
            // Get the annotations on the class that use the label property
            //System.out.println(cls);
            for (OWLAnnotation annotation : EntitySearcher.getAnnotations(cls.getIRI(), ontology)) {
                //System.out.println(annotation);
                if (annotation.getValue() instanceof OWLLiteral) {
                    OWLLiteral val = (OWLLiteral) annotation.getValue();
                    // look for french labels
                    if (val.hasLang("fr") || val.hasLang("en")) {
                        System.out.println(cls + " " + annotation.getProperty() + " " + val.getLiteral() + " " + val.getLang());
                        //System.out.println(annotation);
                    }
                }
            }
        }
    }

    public void outputOntology() {
        File outputFile = new File("ontology_files/ONTOTOXNUC_cleaned.owl");
        RDFXMLDocumentFormat rdfxmlFormat = new RDFXMLDocumentFormat();
        try {
            manager.saveOntology(ontology, rdfxmlFormat, IRI.create(outputFile.toURI()));
        } catch (OWLOntologyStorageException e) {
            e.printStackTrace();
        }
    }

    public void cleanMultilingualOntology(String lang) {
        //Clean the ontology by only keeping the lang asked for literals.
        List changeList = new ArrayList();
        for (OWLClass cls : ontology.getClassesInSignature()) {
            // Get the annotations on the class that use the label property
            //System.out.println(cls);
            for (OWLAnnotationAssertionAxiom annAx : EntitySearcher.getAnnotationAssertionAxioms(cls.getIRI(), ontology)) {
                //System.out.println(annAx);
                if (annAx.getValue() instanceof OWLLiteral) {
                    OWLLiteral val = (OWLLiteral) annAx.getValue();
                    // look for labels of the specific language
                    if (val.hasLang(lang)) {
                        //manager.removeAxiom(ontology, annAx);
                        RemoveAxiom rm = new RemoveAxiom(ontology, annAx);
                        changeList.add(rm);
                    }
                }
            }
            manager.applyChanges(changeList);
        }
    }



}

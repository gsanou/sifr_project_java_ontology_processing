import java.io.File;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;


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
        OWLOntologyCleaner ontology = new OWLOntologyCleaner(ontologyFileName);

        ontology.printLabels();
    }

    public void printLabels() {

        for (OWLClass cls : ontology.getClassesInSignature()) {
            // Get the annotations on the class that use the label property
            //System.out.println(cls);
            for (OWLAnnotation annotation : cls.getAnnotations(ontology)) {
                //System.out.println(annotation);
                if (annotation.getValue() instanceof OWLLiteral) {
                    OWLLiteral val = (OWLLiteral) annotation.getValue();
                    // look for french labels
                    if (val.hasLang("fr")) {
                        System.out.println(cls + " " + annotation.getProperty() + " " + val.getLiteral());
                        //System.out.println(annotation);
                    }
                }
            }
        }
    }

    public void cleanMultilingualOntology(String lang) {
        //Clean the ontology by only keeping the lang asked for literals.
        System.out.println(lang);
    }


}

import java.io.File;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.AutoIRIMapper;


/**
 * Created by Vincent on 15/04/2015.
 */
public class OWLOntologyLoader {

    //public static final IRI example_iri = IRI.create("http://www.semanticweb.org/ontologies/ont.owl");

    static OWLDataFactory df = OWLManager.getOWLDataFactory();

    public OWLOntologyLoader(String ontologyFile) {

    }

    public static void main(String [ ] args) throws OWLOntologyCreationException {

        //Create manager and load OWL file in an Ontology
        File fileBase = new File("ontology_files/ONTOTOXNUC.owl");
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(fileBase);

        //Print the number of axioms of this ontology
        System.out.println(ontology.getAxiomCount());

        //Print all the classes from this ontology
        /*
        for (OWLClass cls : ontology.getClassesInSignature()) {
            System.out.println(cls);
            System.out.println(cls.);
        }
        */


        for (OWLClass cls : ontology.getClassesInSignature()) {
            // Get the annotations on the class that use the label property
            //System.out.println(cls);
            for (OWLAnnotation annotation : cls.getAnnotations(ontology)) {
                //System.out.println(annotation);

                if (annotation.getValue() instanceof OWLLiteral) {
                    OWLLiteral val = (OWLLiteral) annotation.getValue();
                    // look for french labels
                    if (val.hasLang("fr")) {
                        System.out.println(cls + " labelled " + val.getLiteral());
                    }
                }
            }
        }
    }

    public void printLabel() {

    }


}

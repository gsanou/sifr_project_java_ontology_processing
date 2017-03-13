package org.sifrproject.cli.api;


import com.hp.hpl.jena.ontology.OntClass;
import org.sifrproject.ontology.cuis.CUIOntologyDelegate;
import org.sifrproject.ontology.mapping.OntologyMappingDelegate;
import org.sifrproject.stats.StatsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class AbstractOntologyProcessor implements OntologyProcessor {

    @SuppressWarnings("all")
    protected AbstractOntologyProcessor(final StatsHandler ontologyStats, final CUIOntologyDelegate sourceDelegate, final CUIOntologyDelegate targetDelegate, final OntologyMappingDelegate mappingDelegate) {
        this.ontologyStats = ontologyStats;
        this.sourceDelegate = sourceDelegate;
        this.targetDelegate = targetDelegate;
        this.mappingDelegate = mappingDelegate;
        progressCount = new AtomicInteger(0);
    }

    protected abstract void processSourceClass(final OntClass thisClass);
    protected abstract void processTargetClass(final OntClass thisClass);


    private final StatsHandler ontologyStats;
    protected final CUIOntologyDelegate sourceDelegate;
    protected final CUIOntologyDelegate targetDelegate;
    protected final OntologyMappingDelegate mappingDelegate;

    private final AtomicInteger progressCount;

    private int totalClasses;


    protected static final Logger logger = LoggerFactory.getLogger(AbstractOntologyProcessor.class);

    /**
     * Process the ontology classes to look for CUIs and TUIs
     */
    @Override
    public void processSourceOntology() {
        final List<OntClass> classList = sourceDelegate.getClasses();
        totalClasses = classList.size();
        logger.info("Processing {} source classes...", totalClasses);
        //Iterate over all classes in parallel, automatically dispatched by the java stream on all available CPUs
        final Stream<OntClass> ontClassStream = classList.parallelStream();
        ontClassStream.forEachOrdered(this::processSourceClass);
        logger.info("Done!");
        //Writing stats
        ontologyStats.writeStatistics();
    }

    /**
     * Process the ontology classes to look for CUIs and TUIs
     */
    @Override
    public void processTargetOntology() {
        final List<OntClass> classList = sourceDelegate.getClasses();
        totalClasses = classList.size();
        logger.info("Processing {} target classes...", totalClasses);
        //Iterate over all classes in parallel, automatically dispatched by the java stream on all available CPUs
        final Stream<OntClass> ontClassStream = classList.parallelStream();
        ontClassStream.forEachOrdered(this::processTargetClass);
        logger.info("Done!");
        //Writing stats
        ontologyStats.writeStatistics();
    }

    protected void incrementStatistic(final String name){
        ontologyStats.incrementStatistic(name);
    }

    protected double getPercentProgress(){
        return (progressCount.incrementAndGet() / (double) totalClasses) * 100;
    }

}

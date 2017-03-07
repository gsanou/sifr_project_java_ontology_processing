package org.sifrproject.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("PublicMethodNotExposedInInterface")
class OntologyStats implements StatsHandler {


    public static final String UMLS_CODES_FOUND = "umlsCodesFound";


    private PrintStream statsOutput;

    private final List<Statistic> statisticRegistry = new ArrayList<>();
    private final Map<String, Statistic> statisticNameMap = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(OntologyStats.class);

    OntologyStats(final String fileInfix) {
        logger.info("Initializing statistics module...");
        final String fileName = "stats_" + fileInfix + ".csv";
        try {
            statsOutput = new PrintStream(fileName, "UTF-8");
        } catch (final FileNotFoundException e) {
            logger.error("Cannot output statistics to file {}", fileName);
            System.exit(1);
        } catch (final UnsupportedEncodingException e) {
            // Should never happen
            System.exit(1);
        }
    }

    @Override
    @SuppressWarnings("LocalVariableOfConcreteClass")
    public final void incrementStatistic(final String statisticName) {
        if (statisticNameMap.containsKey(statisticName)) {
             final Statistic statistic = statisticNameMap.get(statisticName);
            statistic.increment();
        }
    }

    @SuppressWarnings("LocalVariableOfConcreteClass")
    final void registerStatistic(final String id, final String title){
        final Statistic statistic = new Statistic(title);
        statisticNameMap.put(id,statistic);
        statisticRegistry.add(statistic);
    }

    /**
     * Write statistics regarding the processing of the ontology
     */
    @SuppressWarnings({"HardcodedFileSeparator", "LocalVariableOfConcreteClass"})
    @Override
    public final void writeStatistics() {
        logger.info("Writing statistics to file...");
        //statsOutput.println("Ontology ,#Classes,w/o CUI,w/o TUI,CUI in altLabel,CUI in mappings, Ambiguous CUI, #Classes remaining without CUI, #Classes remaining without TUI");
        for(int i=0;i<statisticRegistry.size();i++){
            final Statistic statistic = statisticRegistry.get(i);
            statsOutput.print(statistic.getDescription());
            if(i < (statisticRegistry.size() - 1)){
                statsOutput.print(",");
            }
        }
        statsOutput.print(System.lineSeparator());

        final StringBuilder builder = new StringBuilder();
        for(int i=0;i<statisticRegistry.size();i++){
            final Statistic statistic = statisticRegistry.get(i);
            builder.append(statistic.getCount());
            if(i < (statisticRegistry.size() - 1)){
                builder.append(",");
            }
        }
        statsOutput.println(builder);
    }

    private static final class Statistic {
        private final String description;
        private int count;

        Statistic(final String description) {
            this.description = description;
        }

        void increment() {
            count++;
        }

        int getCount() {
            return count;
        }

        String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return String.format("%d -> '%s'}", count, description);
        }
    }
}

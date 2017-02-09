package org.sifrproject.stats;


public interface StatsHandler {
    void writeStatistics();
    void incrementStatistic(final String statisticName);
}

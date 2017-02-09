package org.sifrproject.configuration;


import java.util.Properties;

@FunctionalInterface
public interface CommandlineHandler {
    void processCommandline(final String[] args, final Properties properties);
}

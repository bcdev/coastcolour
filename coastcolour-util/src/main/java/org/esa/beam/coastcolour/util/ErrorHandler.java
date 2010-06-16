package org.esa.beam.coastcolour.util;

interface ErrorHandler {

    void warning(final Throwable t);

    void error(final Throwable t);
}

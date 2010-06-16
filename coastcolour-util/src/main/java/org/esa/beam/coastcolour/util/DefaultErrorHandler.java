package org.esa.beam.coastcolour.util;

class DefaultErrorHandler implements ErrorHandler {

    @Override
    public void warning(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void error(Throwable t) {
        t.printStackTrace();
        System.exit(1);
    }
}

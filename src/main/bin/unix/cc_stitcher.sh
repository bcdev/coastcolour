#! /bin/sh

export BEAM4_HOME=$(dirname $(dirname $0))

if [ -z "$BEAM4_HOME" ]; then
    echo
    echo Error:
    echo BEAM4_HOME does not exists in your environment. Please
    echo set the BEAM4_HOME variable in your environment to the
    echo location of your BEAM 4.x installation.
    echo
    exit 2
fi

JAVA_DIR=/usr/lib/jvm/default-java

"$JAVA_DIR/bin/java" \
    -Xmx1024M \
    -Dceres.context=beam \
    "-Dbeam.mainClass=org.esa.beam.coastcolour.util.ProductStitcherMain" \
    "-Dbeam.home=$BEAM4_HOME" \
    -jar "$BEAM4_HOME/modules/ceres-launcher-0.13.jar" "$@"

exit 0
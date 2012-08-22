#!/bin/bash
# export l1b_catalogue=hdfs:...
# coastcolour-bin-1.5-SNAPSHOT-stitching-call.bash hdfs://master00:9000/calvalus/projects/cc/l1p-nc/greatbarrierreef/2005/MER_FSG_CCL1P_20050614_001256_000001912038_00102_17190_0001.nc.gz hdfs://master00:9000/calvalus/projects/cc/test
set -e
set -m

inputURL=$1
outputURL=$2
inputFilename=$(basename $inputURL)
orbitNumber=${inputFilename:49:5}

regionDir=$(dirname $(dirname $inputURL))
inputFiles=$(hadoop fs -ls ${regionDir}/*/MER*_${orbitNumber}_*nc.gz |awk '{ print $8 }')
numberInputFiles=$(echo $inputFiles |wc -w)
firstInputFile="hdfs://master00:9000$(echo $inputFiles | tr ' ' '\n' | sort | head -n 1)"

if [ $numberInputFiles = 1 ]; then
    echo "copying single file in orbit $orbitNumber"
    /home/hadoop/opt/coastcolour/coastcolour-bin-1.5-SNAPSHOT/bin/reportprogress.sh &
    trap 'kill %1' EXIT
    if hadoop fs -ls ${outputURL}/$(basename ${inputFiles}) 2> /dev/null; then
        hadoop fs -rm ${outputURL}/$(basename ${inputFiles})
    fi
    #echo "hadoop fs -cp $inputFiles ${outputURL}/$(basename ${inputFiles})"
    hadoop fs -cp $inputFiles ${outputURL}/$(basename ${inputFiles})
elif [ "$numberInputFiles" -gt 1 -a "$inputURL" = "$firstInputFile" ]; then
    echo "found $numberInputFiles input files to stitch for orbit $orbitNumber"
    /home/hadoop/opt/coastcolour/coastcolour-bin-1.5-SNAPSHOT/bin/reportprogress.sh &
    trap 'kill %1' EXIT
    mkdir in
    for p in $inputFiles; do
	hadoop fs -get $p in
    done
    gunzip in/*gz
    mkdir out
    /home/hadoop/opt/coastcolour/coastcolour-bin-1.5-SNAPSHOT/bin/cc_stitcher.sh in/*.nc -o out
    gzip out/*.nc
    for resultFile in out/*.nc.gz; do
        if hadoop fs -ls ${outputURL}/$(basename ${resultFile}) 2> /dev/null; then
            hadoop fs -rm ${outputURL}/$(basename ${resultFile})
        fi
	#echo "hadoop fs -put ${resultFile} ${outputURL}/$(basename ${resultFile})"
	hadoop fs -put ${resultFile} ${outputURL}/$(basename ${resultFile})
    done
elif [ "$numberInputFiles" -gt 1 -a "$inputURL" != "$firstInputFile" ]; then
    echo "skipping because $firstInputFile is first" 	
    exit 0
else
    echo "Error: $inputURL not found."
    exit 1
fi

/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.coastcolour.processing;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.JobConfNames;
import com.bc.calvalus.processing.beam.BeamOpProcessingType;
import com.bc.calvalus.processing.hadoop.MultiFileSingleBlockInputFormat;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.util.logging.Logger;

import static com.bc.calvalus.processing.hadoop.HadoopProcessingService.*;

/**
 * Creates and runs Hadoop job for computing statistics for coastcolour products.
 *
 * @author MarcoZ
 * @author MarcoP
 */
public class CoastColourStatisticTool extends Configured implements Tool {

    private static final Logger LOG = CalvalusLogger.getLogger();

    private static Options options;

    static {
        options = new Options();
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new CoastColourStatisticTool(), args));
    }

    @Override
    public int run(String[] args) throws Exception {

        try {
            // parse command line arguments
            CommandLineParser commandLineParser = new PosixParser();
            final CommandLine commandLine = commandLineParser.parse(options, args);
            String[] remainingArgs = commandLine.getArgs();
            String inputPath = remainingArgs[0];
            String outputs = remainingArgs[1];

            LOG.info("start processing CC_stx " + inputPath + " to " + outputs);
            long startTime = System.nanoTime();

            // construct job and set parameters and handlers
            Job job = new Job(getConf(), "cc_stx");
            Configuration configuration = job.getConfiguration();
            configuration.set("mapred.job.priority", "HIGH");
            configuration.set("hadoop.job.ugi", "hadoop,hadoop");  // user hadoop owns the outputs
            configuration.set("mapred.map.tasks.speculative.execution", "false");
            configuration.set("mapred.reduce.tasks.speculative.execution", "false");
            configuration.set("mapred.child.java.opts", "-Xmx1024m");
            configuration.set("mapred.reduce.tasks", "1");
            configuration.setInt("mapred.max.map.failures.percent", 20);

            String inputs = BeamOpProcessingType.collectInputPaths(new String[]{inputPath}, ".*\\.seq", configuration);
            configuration.set(JobConfNames.CALVALUS_INPUT, inputs);
            configuration.set(JobConfNames.CALVALUS_INPUT_FORMAT, "HADOOP-STREAMING");
            configuration.set(JobConfNames.CALVALUS_OUTPUT, outputs);

            job.setInputFormatClass(MultiFileSingleBlockInputFormat.class);

            job.setMapperClass(CoastColourStatisticMapper.class);
            job.setMapOutputKeyClass(Text.class);
            job.setMapOutputValueClass(Text.class);

            job.setReducerClass(Reducer.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);

            job.setOutputFormatClass(TextOutputFormat.class);

            // clear output directory
            final Path outputPath = new Path(outputs);
            final FileSystem fileSystem = outputPath.getFileSystem(configuration);
            fileSystem.delete(outputPath, true);
            FileOutputFormat.setOutputPath(job, outputPath);

            // Add Calvalus modules to classpath of Hadoop jobs
            addBundleToClassPath(configuration.get(JobConfNames.CALVALUS_CALVALUS_BUNDLE, DEFAULT_CALVALUS_BUNDLE), configuration);
            // Add BEAM modules to classpath of Hadoop jobs
            addBundleToClassPath(configuration.get(JobConfNames.CALVALUS_BEAM_BUNDLE, DEFAULT_BEAM_BUNDLE), configuration);
            addBundleToClassPath("coastcolour-stx", configuration);

            int result = job.waitForCompletion(true) ? 0 : 1;

            long stopTime = System.nanoTime();
            LOG.info("stop processing CC_stx " + inputPath + " to " + outputPath + " after " + ((stopTime - startTime) / 1E9) +  " sec");
            return result;
        } catch (Exception e) {
            System.err.println("failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }
}

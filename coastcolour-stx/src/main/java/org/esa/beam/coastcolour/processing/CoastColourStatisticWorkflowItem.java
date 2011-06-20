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

import com.bc.calvalus.processing.JobConfNames;
import com.bc.calvalus.processing.JobUtils;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import com.bc.calvalus.processing.hadoop.HadoopWorkflowItem;
import com.bc.calvalus.processing.hadoop.MultiFileSingleBlockInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.esa.beam.util.StringUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * A workflow item for configuring a hadoop job, that computes a statistic,
 * generates a RGB quicklook and a worldmap using the {@code CoastColourStatisticMapper}.
 *
 * @author MarcoZ
 */
class CoastColourStatisticWorkflowItem extends HadoopWorkflowItem {

    private final String[] inputs;
    private final String outputPath;

    CoastColourStatisticWorkflowItem(HadoopProcessingService processingService, String[] inputs, String outputPath) {
        super(processingService);
        this.inputs = inputs;
        this.outputPath = outputPath;
    }

    @Override
    protected Job createJob() throws IOException {
        Job job = getProcessingService().createJob("cc-stx");
        Configuration configuration = job.getConfiguration();

        configuration.set("mapred.job.priority", "HIGH");
        configuration.set("mapred.child.java.opts", "-Xmx2000m");
        configuration.setInt("mapred.max.map.failures.percent", 20);

        configuration.set(JobConfNames.CALVALUS_INPUT, StringUtils.join(inputs, ","));
        configuration.set(JobConfNames.CALVALUS_INPUT_FORMAT, "HADOOP-STREAMING");
        configuration.set(JobConfNames.CALVALUS_OUTPUT, outputPath);

        Properties properties = new Properties();
        properties.setProperty("beam.pixelGeoCoding.useTiling", "true");
        String propertiesString = JobUtils.convertProperties(properties);
        configuration.set(JobConfNames.CALVALUS_SYSTEM_PROPERTIES, propertiesString);

        job.setInputFormatClass(MultiFileSingleBlockInputFormat.class);

        job.setMapperClass(CoastColourStatisticMapper.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        job.setReducerClass(Reducer.class);
        job.setNumReduceTasks(1);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        JobUtils.clearAndSetOutputDir(job, outputPath);

        HadoopProcessingService.addBundleToClassPath("coastcolour-stx-1.2", configuration);

        return job;
    }
}

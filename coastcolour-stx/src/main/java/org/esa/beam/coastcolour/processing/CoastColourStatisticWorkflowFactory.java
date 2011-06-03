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

import com.bc.calvalus.commons.WorkflowException;
import com.bc.calvalus.commons.WorkflowItem;
import com.bc.calvalus.processing.JobConfNames;
import com.bc.calvalus.processing.JobUtils;
import com.bc.calvalus.processing.beam.BeamUtils;
import com.bc.calvalus.processing.cli.WorkflowFactory;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;


/**
 * Creates and runs Hadoop job for computing statistics for coastcolour products.
 *
 * @author MarcoZ
 * @author MarcoP
 */
public class CoastColourStatisticWorkflowFactory implements WorkflowFactory {

    @Override
    public String getName() {
        return "cc-stx";
    }

    @Override
    public String getUsage() {
        return "cc-stx <inputPath> <outputPath> [<existingOutputPath>] -- computes statistics and quicklooks for CC products";
    }

    @Override
    public WorkflowItem create(HadoopProcessingService hps, String[] args) throws WorkflowException {
        String inputPath = args[0];
        System.out.println("inputPath = " + inputPath);
        String outputPath = args[1];
        String existingOutputPath = null;
        if (args.length == 3) {
            existingOutputPath = args[2];
        }
        String[] inputs = null;
        try {
            inputs = collectInputPaths(hps, inputPath, ".*\\.seq", existingOutputPath);
        } catch (IOException e) {
            throw new WorkflowException("Failed to collect inputs from: '" + inputPath + "' " + e.getMessage(), e);
        }
        System.out.println("inputs:");
        System.out.println(StringUtils.join(inputs, "\n"));
        System.out.println("--------------------------------------------------------");


//        debug(hps.getJobClient().getConf(), inputs);
//        return null;
        return new CoastColourStatisticWorkflowItem(hps, inputs, outputPath);
    }

    private void debug(Configuration conf, String[] inputs) {
        conf.set(JobConfNames.CALVALUS_INPUT_FORMAT, "HADOOP-STREAMING");

        Properties properties = new Properties();
        properties.setProperty("beam.pixelGeoCoding.useTiling", "true");
        String propertiesString = JobUtils.convertProperties(properties);
        conf.set(JobConfNames.CALVALUS_SYSTEM_PROPERTIES, propertiesString);

        BeamUtils.initGpf(conf);
        for (String input : inputs) {
            System.out.println("-------------------------------------------------------");
            System.out.println("input = " + input);
            String productName = null;
            String statisticalData = null;
            try {
                Path path = new Path(input);
                Product product = BeamUtils.readProduct(path, conf);

                productName = CoastColourStatisticMapper.createProductName(path.getName());

                FileOutputStream quicklookOutputStream = new FileOutputStream(productName + "_QL.png");
                FileOutputStream worldMapOutputStream = new FileOutputStream(productName + "_WM.png");
                try {
                    statisticalData = CoastColourStatisticMapper.createStatisticalData(product, quicklookOutputStream, worldMapOutputStream);
                } finally {
                    quicklookOutputStream.close();
                    worldMapOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println(productName + "\t" + statisticalData);
        }
    }

    static String[] collectInputPaths(HadoopProcessingService hps, String inputUrl, String filenamePattern, String existingPath) throws IOException {
        final Pattern filter = Pattern.compile(filenamePattern);
        List<String> collectedInputPaths = new ArrayList<String>();
        FileSystem fileSystem = FileSystem.get(hps.getJobClient().getConf());

        PathFilter pathFilter;
        if (existingPath != null) {
            final List<String> validWM = getValidFiles(existingPath, fileSystem, "_WM.png");
            final List<String> validQL = getValidFiles(existingPath, fileSystem, "_QL.png");
            pathFilter = new PathFilter() {
                @Override
                public boolean accept(Path path) {
                    String name = path.getName();
                    if (!filter.matcher(name).matches()) {
                        return false;
                    }
                    String productName = CoastColourStatisticMapper.createProductName(name);
                    if (validQL.contains(productName) && validWM.contains(productName)) {
                        return false;
                    }
                    return true;
                }
            };

        } else {
            pathFilter = new PathFilter() {
                @Override
                public boolean accept(Path path) {
                    return filter.matcher(path.getName()).matches();
                }
            };
        }

        FileStatus[] inputFileStatuses = fileSystem.listStatus(new Path(inputUrl), pathFilter);
        for (FileStatus inputFileStatus : inputFileStatuses) {
            collectedInputPaths.add(inputFileStatus.getPath().toString());
        }
        return collectedInputPaths.toArray(new String[collectedInputPaths.size()]);
    }

    private static List<String> getValidFiles(String existingPath, FileSystem fileSystem, final String suffix) throws IOException {
        PathFilter wmFilter = new PathFilter() {
            @Override
            public boolean accept(Path path) {

                return path.getName().endsWith(suffix);
            }
        };
        FileStatus[] existingWM = fileSystem.listStatus(new Path(existingPath), wmFilter);
        return getNonZeroFiles(existingWM);
    }

    private static List<String> getNonZeroFiles(FileStatus[] existing) {
        final List<String> valid = new ArrayList<String>();
        for (FileStatus fileStatus : existing) {
            if (fileStatus.getLen() > 0) {
                String name = fileStatus.getPath().getName();
                String productName = name.substring(0, name.length() - 7);
                valid.add(productName);
            }
        }
        return valid;
    }

}

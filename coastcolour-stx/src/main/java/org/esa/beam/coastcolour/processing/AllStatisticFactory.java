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

import com.bc.calvalus.commons.Workflow;
import com.bc.calvalus.commons.WorkflowException;
import com.bc.calvalus.commons.WorkflowItem;
import com.bc.calvalus.processing.cli.WorkflowFactory;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AllStatisticFactory implements WorkflowFactory{
    @Override
    public String getName() {
        return "cc-stx-all";
    }

    @Override
    public String getUsage() {
        return "cc-stx-all <inputPath> <outputPath> [<existingOutputPath>] -- computes statistics and quicklooks for CC products (including subdirs)";
    }

    @Override
    public WorkflowItem create(HadoopProcessingService hps, String[] args) throws WorkflowException {
        String inputPath = args[0];
        String outputPath = args[1];
        String existingOutputPath = "";
        if (args.length == 3) {
            existingOutputPath = args[2];
        }
        try {
            List<String> inputs = new ArrayList<String>();
            List<String> outputs = new ArrayList<String>();
            List<String> existings = new ArrayList<String>();


            String[] level1Strings = hps.listFilePaths(inputPath);
            for (String level1String : level1Strings) {
                String level1Dir = new Path(level1String).getName();
                if (level1Dir.equals("matchups")) {
                    inputs.add(level1String);
                    outputs.add(outputPath + "/" + level1Dir);
                    existings.add(existingOutputPath + "/" + level1Dir);
                } else {
                    String[] level2Strings = hps.listFilePaths(level1String);
                    for (String level2String : level2Strings) {
                        String level2Dir = new Path(level2String).getName();

                        inputs.add(level2String);
                        outputs.add(outputPath + "/" + level1Dir + "/" + level2Dir);
                        existings.add(existingOutputPath + "/" + level1Dir + "/" + level2Dir);
                    }
                }
            }
            CoastColourStatisticWorkflowFactory workflowFactory = new CoastColourStatisticWorkflowFactory();
            WorkflowItem[] workflowItems = new WorkflowItem[inputs.size()];
            for (int i = 0; i < workflowItems.length; i++) {
                String[] subArgs;
                if (args.length == 3) {
                    subArgs = new String[]{inputs.get(i), outputs.get(i), existings.get(i)};
                } else {
                    subArgs = new String[]{inputs.get(i), outputs.get(i)};
                }
                workflowItems[i] = workflowFactory.create(hps, subArgs);
            }
            return new Workflow.Parallel(workflowItems);
        } catch (IOException e) {
            throw new WorkflowException(e);
        }
    }
}

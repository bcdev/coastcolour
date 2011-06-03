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

import com.bc.calvalus.commons.WorkflowItem;
import com.bc.calvalus.processing.cli.WorkflowFactory;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;


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
        return "cc-stx <inputPath> <outputPath> -- computes statistics and quicklooks for CC products";
    }

    @Override
    public WorkflowItem create(HadoopProcessingService hps, String[] args) {
        return new CoastColourStatisticWorkflowItem(hps, args[0], args[1]);
    }

}

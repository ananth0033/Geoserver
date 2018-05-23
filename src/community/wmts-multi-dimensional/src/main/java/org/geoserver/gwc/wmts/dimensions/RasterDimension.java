/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.wmts.dimensions;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.DimensionInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.gwc.wmts.Tuple;
import org.geoserver.wms.WMS;
import org.geotools.data.Query;
import org.geotools.feature.FeatureCollection;
import org.opengis.filter.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Base class for raster based dimensions
 */
public abstract class RasterDimension extends Dimension {

    private final CoverageDimensionsReader.DataType dataType;

    public RasterDimension(WMS wms, String dimensionName, LayerInfo layerInfo, DimensionInfo dimensionInfo, CoverageDimensionsReader.DataType dataType) {
        super(wms, dimensionName, layerInfo, dimensionInfo);
        this.dataType = dataType;
    }

    @Override
    public List<Object> getDomainValues(Filter filter, boolean noDuplicates) {
        CoverageDimensionsReader reader = CoverageDimensionsReader.instantiateFrom((CoverageInfo) resourceInfo);
        if (noDuplicates) {
            // no duplicate values should be included
            Set<Object> values = reader.readWithoutDuplicates(getDimensionName(), filter, dataType);
            return new ArrayList<>(values);
        }
        // we need the duplicate values (this is useful for some operations like get histogram operation)
        return reader.readWithDuplicates(getDimensionName(), filter, dataType);
    }

    @Override
    protected DomainSummary getDomainSummary(Filter filter, int expandLimit) {
        CoverageDimensionsReader reader = CoverageDimensionsReader.instantiateFrom((CoverageInfo) resourceInfo);

        Tuple<String, FeatureCollection> values = reader.getValues(getDimensionName(), new Query(null, filter), dataType);
        return getDomainSummary(values.second, values.first, expandLimit);
    }

    @Override
    protected String getDimensionAttributeName() {
        CoverageDimensionsReader reader = CoverageDimensionsReader.instantiateFrom((CoverageInfo) resourceInfo);
        return reader.getDimensionAttributesNames(getDimensionName()).first;
    }

}

/* (c) 2016 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.wmts.dimensions;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.DimensionDefaultValueSetting;
import org.geoserver.catalog.DimensionInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.gwc.wmts.Tuple;
import org.geoserver.wms.WMS;
import org.geotools.data.Query;
import org.geotools.feature.FeatureCollection;
import org.opengis.filter.Filter;

import java.util.Date;

/**
 * Represents a time dimension of a raster.
 */
public class RasterTimeDimension extends RasterDimension {

    public RasterTimeDimension(WMS wms, LayerInfo layerInfo, DimensionInfo dimensionInfo) {
        super(wms, ResourceInfo.TIME, layerInfo, dimensionInfo, CoverageDimensionsReader.DataType.TEMPORAL);
    }

    @Override
    protected String getDefaultValueFallbackAsString() {
        return DimensionDefaultValueSetting.TIME_CURRENT;
    }

    @Override
    protected Class getDimensionType() {
        return Date.class;
    }

    @Override
    protected FeatureCollection getDomain(Query query) {
        CoverageDimensionsReader reader = CoverageDimensionsReader.instantiateFrom((CoverageInfo) resourceInfo);
        Tuple<String, FeatureCollection> values = reader.getValues(this.dimensionName, query,
                CoverageDimensionsReader.DataType.TEMPORAL);
        
        return values.second;
    }
}

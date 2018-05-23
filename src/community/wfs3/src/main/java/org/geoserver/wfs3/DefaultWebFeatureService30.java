/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs3;

import io.swagger.v3.oas.models.OpenAPI;
import net.opengis.wfs20.GetFeatureType;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.Response;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.WFSInfo;
import org.geoserver.wfs.WebFeatureService20;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geoserver.wfs3.response.CollectionsDocument;
import org.geoserver.wfs3.response.ConformanceDocument;
import org.geoserver.wfs3.response.LandingPageDocument;
import org.geotools.util.logging.Logging;
import org.opengis.filter.FilterFactory2;

import javax.xml.namespace.QName;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.geoserver.wfs3.response.ConformanceDocument.CORE;
import static org.geoserver.wfs3.response.ConformanceDocument.GEOJSON;
import static org.geoserver.wfs3.response.ConformanceDocument.GMLSF0;
import static org.geoserver.wfs3.response.ConformanceDocument.OAS30;

/** WFS 3.0 implementation */
public class DefaultWebFeatureService30 implements WebFeatureService30 {

    private static final Logger LOGGER = Logging.getLogger(DefaultWebFeatureService30.class);
    private FilterFactory2 filterFactory;
    private final GeoServer geoServer;
    private WebFeatureService20 wfs20;

    public DefaultWebFeatureService30(GeoServer geoServer, WebFeatureService20 wfs20) {
        this.geoServer = geoServer;
        this.wfs20 = wfs20;
    }

    public FilterFactory2 getFilterFactory() {
        return filterFactory;
    }

    public void setFilterFactory(FilterFactory2 filterFactory) {
        this.filterFactory = filterFactory;
    }

    @Override
    public LandingPageDocument landingPage(LandingPageRequest request) {
        LandingPageDocument contents = new LandingPageDocument(request, getService(), getCatalog());
        return contents;
    }

    @Override
    public Object collections(CollectionsRequest request) {
        if (request.getTypeName() == null) {
            // all collections
            return new CollectionsDocument(request, getService(), getCatalog());
        } else {
            // single collection
            QName typeName = request.getTypeName();
            NamespaceInfo ns = getCatalog().getNamespaceByURI(typeName.getNamespaceURI());
            FeatureTypeInfo featureType =
                    getCatalog().getFeatureTypeByName(ns, typeName.getLocalPart());
            if (featureType == null) {
                throw new ServiceException(
                        "Unknown collection " + typeName,
                        ServiceException.INVALID_PARAMETER_VALUE,
                        "typeName");
            } else {
                return new CollectionsDocument(request, getService(), getCatalog(), featureType);
            }
        }
    }

    @Override
    public ConformanceDocument conformance(ConformanceRequest request) {
        List<String> classes = Arrays.asList(CORE, OAS30, GEOJSON, GMLSF0);
        return new ConformanceDocument(classes);
    }

    private Catalog getCatalog() {
        return geoServer.getCatalog();
    }

    private WFSInfo getService() {
        return geoServer.getService(WFSInfo.class);
    }

    @Override
    public FeatureCollectionResponse getFeature(org.geoserver.wfs3.GetFeatureType request) {
        // If the server has any more results available than it returns (the number it returns is
        // less than or equal to the requested/default/maximum limit) then the server will include a
        // link to the next set of results.
        // This will make paging in WFS3 slower, as it always introduces sorting
        if (request.getStartIndex() == null && request.getCount() != null) {
            request.setStartIndex(BigInteger.ZERO);
        }

        // delegate execution to WFS 2.0
        FeatureCollectionResponse response = wfs20.getFeature(request);
        return response;
    }

    /**
     * Returns a selection of supported formats for a given response object
     *
     * <p>TODO: this should be moved in a more central place, as it's of general utility (maybe the
     * filtering part could be made customizable via a lambda)
     *
     * @return A list of MIME types
     */
    public static List<String> getAvailableFormats(Class responseType) {
        Set<String> formatNames = new LinkedHashSet<>();
        Collection responses = GeoServerExtensions.extensions(Response.class);
        for (Iterator i = responses.iterator(); i.hasNext(); ) {
            Response format = (Response) i.next();
            if (!responseType.isAssignableFrom(format.getBinding())) {
                continue;
            }
            // TODO: get better collaboration from content
            Set<String> formats = format.getOutputFormats();
            if (formats.isEmpty()) {
                continue;
            }
            // try to get a MIME type, otherwise pick the first available
            formats.stream().filter(f -> f.contains("/")).forEach(f -> formatNames.add(f));
        }
        return new ArrayList<>(formatNames);
    }

    @Override
    public OpenAPI api(APIRequest request) {
       return new OpenAPIBuilder().build(request, getService());
    }
   
}

/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs3.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.ows.URLMangler;
import org.geoserver.ows.util.ResponseUtils;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.WFSInfo;
import org.geoserver.wfs3.BaseRequest;
import org.geoserver.wfs3.DefaultWebFeatureService30;
import org.geoserver.wfs3.NCNameResourceCodec;
import org.opengis.filter.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A class representing the WFS3 server "collections" in a way that Jackson can easily translate to
 * JSON/YAML (and can be used as a Freemarker template model)
 */
@JacksonXmlRootElement(localName = "Collections")
@JsonPropertyOrder({"links", "links", "collections"})
public class CollectionsDocument {

    private final Catalog catalog;
    private final WFSInfo wfs;
    private final List<Link> links = new ArrayList<>();
    private final BaseRequest request;
    private final FeatureTypeInfo featureType;

    public CollectionsDocument(BaseRequest request, WFSInfo wfs, Catalog catalog) {
        this(request, wfs, catalog, null);
    }

    public CollectionsDocument(
            BaseRequest request, WFSInfo wfs, Catalog catalog, FeatureTypeInfo featureType) {
        this.wfs = wfs;
        this.catalog = catalog;
        this.request = request;
        this.featureType = featureType;

        // build the links
        List<String> formats =
                DefaultWebFeatureService30.getAvailableFormats(CollectionsDocument.class);
        String baseUrl = request.getBaseUrl();
        for (String format : formats) {
            String path =
                    "wfs3/collections/"
                            + (featureType != null ? NCNameResourceCodec.encode(featureType) : "");
            String apiUrl =
                    ResponseUtils.buildURL(
                            baseUrl,
                            path,
                            Collections.singletonMap("f", format),
                            URLMangler.URLType.SERVICE);
            String linkType = Link.REL_ALTERNATE;
            String linkTitle = "This document " + " as " + format;
            if (format.equals(request.getFormat())) {
                linkType = Link.REL_SELF;
                linkTitle = "This document";
            }
            links.add(new Link(apiUrl, linkType, format, linkTitle));
        }
    }

    @JacksonXmlProperty(localName = "Links")
    public List<Link> getLinks() {
        return links;
    }

    @JacksonXmlProperty(localName = "Collection")
    public Iterator<CollectionDocument> getCollections() {
        // single collection case
        if (featureType != null) {
            return Collections.singleton(new CollectionDocument(request, featureType)).iterator();
        }

        // full scan case
        CloseableIterator<FeatureTypeInfo> featureTypes =
                catalog.list(FeatureTypeInfo.class, Filter.INCLUDE);
        return new Iterator<CollectionDocument>() {

            CollectionDocument next;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }

                boolean hasNext = featureTypes.hasNext();
                if (!hasNext) {
                    featureTypes.close();
                    return false;
                } else {
                    try {
                        FeatureTypeInfo featureType = featureTypes.next();
                        CollectionDocument collection =
                                new CollectionDocument(request, featureType);

                        next = collection;
                        return true;
                    } catch (Exception e) {
                        featureTypes.close();
                        throw new ServiceException(
                                "Failed to iterate over the feature types in the catalog", e);
                    }
                }
            }

            @Override
            public CollectionDocument next() {
                CollectionDocument result = next;
                this.next = null;
                return result;
            }
        };
    }
}

/* (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs3.response;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Link {

    public static final String REL_SERVICE = "service";
    public static final String REL_SELF = "self";
    public static final String REL_ALTERNATE = "alternate";
    public static final String REL_ABOUT = "about";
    public static final String REL_DESCRIBEDBY = "describedBy";
    static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    
    String href;
    String rel;
    String type;
    String title;

    public Link() {
    }

    public Link(String href, String rel, String type, String title) {
        this.href = href;
        this.rel = rel;
        this.type = type;
        this.title = title;
    }

    @JacksonXmlProperty(namespace = ATOM_NS, isAttribute = true)
    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    @JacksonXmlProperty(namespace = ATOM_NS, isAttribute = true)
    public String getRel() {
        return rel;
    }

    public void setRel(String rel) {
        this.rel = rel;
    }

    @JacksonXmlProperty(namespace = ATOM_NS, isAttribute = true)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JacksonXmlProperty(namespace = ATOM_NS, isAttribute = true)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}

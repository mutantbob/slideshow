package com.purplefrog.slideshow;

import java.net.*;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: thoth
 * Date: 5/17/13
 * Time: 4:06 PM
 * To change this template use File | Settings | File Templates.
 */
public class Catalog
{
    public URL baseURL;
    public List<String> imgSrcs;
    public List<String> aHrefs;

    public Catalog(URL baseURL, List<String> imgSrcs, List<String> aHrefs)
    {

        this.baseURL = baseURL;
        this.imgSrcs = imgSrcs;
        this.aHrefs = aHrefs;
    }
}

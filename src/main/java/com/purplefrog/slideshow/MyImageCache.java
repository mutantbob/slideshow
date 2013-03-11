package com.purplefrog.slideshow;

import java.awt.*;
import java.lang.ref.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: thoth
 * Date: 11/6/12
 * Time: 4:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class MyImageCache
{
    final static Toolkit toolkit = Toolkit.getDefaultToolkit();

    public static Map<URL, SoftReference<Image>> cache = new HashMap<URL, SoftReference<Image>>();

    public static Image getImage2(URL u)
    {
        final SoftReference<Image> ref = cache.get(u);
        Image rval=null;
        if (ref!=null)
            rval = ref.get();
        if (rval==null) {
            rval =toolkit.getImage(u );
            cache.put(u, new SoftReference<Image>(rval));
        }
        return rval;
    }
}

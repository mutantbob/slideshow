package com.purplefrog.slideshow;

import java.awt.*;
import java.awt.image.*;
import java.lang.ref.*;
import java.util.*;

/**
 * This class is a fail.  Using {@link SoftReference} or {@link WeakReference} does not prevent {@link OutOfMemoryError}
 *
 * <p>Created with IntelliJ IDEA.
 * User: thoth
 * Date: 3/11/13
 * Time: 1:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class SizedImageCache
{
    protected int maxWidth;
    protected int maxHeight;

    Map<Image, Reference<Image>> cache = new HashMap<Image, Reference<Image>>();

    public SizedImageCache(int maxWidth, int maxHeight)
    {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }


    public Image getFor(Image srcImg, ImageObserver observer)
    {
        Reference<Image> ref = cache.get(srcImg);

        Image rval=null;
        if (ref!=null) {
            debugMsg("cache hit");
            rval = ref.get();
        }

        if (null == rval) {
            debugMsg("cache miss");
            rval =SlideShowPane.fitImageIn(observer, srcImg, maxWidth, maxHeight);
            if (null != rval)
                cache.put(srcImg, new WeakReference<Image>(rval));
        }
        return rval;

    }

    public static void debugMsg(String msg)
    {
        if (true)
            System.out.println(msg);
    }
}

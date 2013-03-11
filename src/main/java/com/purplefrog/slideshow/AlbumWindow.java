package com.purplefrog.slideshow;

import java.awt.*;
import java.net.*;
import java.util.*;

/**
 * This represents a span of ImageSets.
 *
 * Created with IntelliJ IDEA.
 * User: thoth
 * Date: 3/11/13
 * Time: 2:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class AlbumWindow
{
    ImageSet[] window;
    URL[] urls = null;

    Map<URL, ImageSet> perURL = new HashMap<URL, ImageSet>();

    public AlbumWindow(int count)
    {
        window = new ImageSet[count];
    }

    public ImageSet getIP(int albumOff)
    {
        return window[albumOff];
    }

    public void clearDisplayImages()
    {
        for (ImageSet imageSet : window) {
            if (imageSet != null)
                imageSet .clearDisplayImages();
        }
    }

    public int getCount()
    {
        return window.length;
    }

    public URL URLforImage(Image img)
    {
        int idx = indexForImage(img);
        return urls[idx];
    }

    public void setURLs(URL[] newURLs)
    {
        Map<URL, ImageSet> oldMap = new HashMap<URL, ImageSet>(perURL);

        Map<URL, ImageSet> newMap = new HashMap<URL, ImageSet>();

        ImageSet[] newWindow = new ImageSet[newURLs.length];

        for (int i = 0; i < newURLs.length; i++) {
            URL url = newURLs[i];

            ImageSet recovered;
            if (url == null) {
                recovered = new ImageSet();
            } else {
                recovered = oldMap.remove(url);
                if (recovered == null) {
                    recovered = new ImageSet();
                    recovered.baseImg = Toolkit.getDefaultToolkit().createImage(url);
                }
                newMap.put(url, recovered);
            }

            newWindow[i] = recovered;
        }

        window = newWindow;
        urls = newURLs;
        perURL = newMap;

        // discard unrecovered images
        for (ImageSet imageSet : oldMap.values()) {
            imageSet.flush();
        }
    }

    public void computeImages(ImageSet.Parameters params)
    {
        for (ImageSet imageSet : window) {
            imageSet.computeImages(0,null, params);
        }
    }

    public void resetBrokenImages(SlideShowPane ssp)
    {
        for (ImageSet imageSet : window) {
            imageSet.resetBrokenImages(ssp);
        }
    }

    public int indexForImage(Image img)
    {
        for (int i = 0; i < window.length; i++) {
            ImageSet imageSet = window[i];
            if (imageSet.matchesImage(img))
                return i;
        }

        return -1;
    }
}

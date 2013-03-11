package com.purplefrog.slideshow;

import java.awt.*;
import java.awt.image.*;

/**
 * Created with IntelliJ IDEA.
 * User: thoth
 * Date: 3/11/13
 * Time: 2:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class ImageSet
{
    Image baseImg;
    Image	img;
    Image bigImg;

    public ImageSet()
    {
        img = null;
        bigImg = null;
        baseImg = null;
    }

    public static Image fitImageIn(ImageObserver observer, Image baseImg, int w, int h)
    {
        if (null == baseImg)
            return null; // blacklisting makes this a little weird.

        if (baseImg == null)
            throw new NullPointerException("oh fuck!");

        if (observer == null)
            throw new NullPointerException("oh fuck!");

        int	iw = baseImg.getWidth(observer);
        int	ih = baseImg.getHeight(observer);
        if (iw <1 ||
            ih < 1) {
            return null;
        }

        float r = 1;
        if (w/(float)iw < r) r = w/(float)iw;
        if (h/(float)ih < r) r = h/(float)ih;

        int nw = Math.round(iw*r);
        int nh = Math.round(ih*r);
        if (nw<1) nw = 1;
        if (nh<1) nh = 1;
        //System.out.println("fitImageIn("+screenOff+","+w+","+h+") = "+nw+"x"+nh);
        return baseImg.getScaledInstance(nw, nh, Image.SCALE_FAST);
    }

    public synchronized void clearDisplayImages() {
        img = bigImg = null;
    }

    public Image dispImg(boolean isBig) {
        return isBig ? bigImg : img;
    }

    public synchronized void flush() {
        if (baseImg != null) baseImg.flush();
        if (img != null) img.flush();
        if (bigImg != null) bigImg.flush();
    }

    public synchronized void computeImages(int i, Dimension d, Parameters p2)
    {
        if (img == null) {
                img = fitImageIn(p2.observer, baseImg, p2.smallWidth, p2.smallHeight);
        }
        if (bigImg == null) {
                bigImg = fitImageIn(p2.observer, baseImg, p2.centerWidth, p2.centerHeight);

        }
    }

    public synchronized String toString()
    {
        return "base="+baseImg+" img="+img+" big="+bigImg;
    }

    public boolean matchesImage(Image other)
    {
        return other==img
            || other == bigImg
            || other == baseImg;
    }

    public void resetBrokenImages(SlideShowPane ssp)
    {
        if (ssp.imageHasProblem(img)) {
            img.flush();
            img = null;
        }
        if (ssp.imageHasProblem(baseImg)) {
            baseImg.flush();
            baseImg = null;
        }
        if (ssp.imageHasProblem(bigImg)) {
            bigImg.flush();
            bigImg = null;
        }
    }

    public static class Parameters
    {
        public int smallWidth;
        public int smallHeight;
        public int centerWidth;
        public int centerHeight;
        ImageObserver observer;

        public Parameters(int smallWidth, int smallHeight, int centerWidth, int centerHeight, ImageObserver observer)
        {
            this.smallWidth = smallWidth;
            this.smallHeight = smallHeight;
            this.centerWidth = centerWidth;
            this.centerHeight = centerHeight;
            this.observer = observer;
        }
    }
}

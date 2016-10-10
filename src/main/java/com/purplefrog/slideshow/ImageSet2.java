package com.purplefrog.slideshow;

import org.apache.commons.imaging.*;
import org.apache.commons.imaging.common.*;
import org.apache.commons.imaging.formats.jpeg.*;
import org.apache.commons.imaging.formats.tiff.*;
import org.apache.commons.imaging.formats.tiff.constants.*;

import javax.imageio.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;

/**
 * Created with IntelliJ IDEA.
 * User: thoth
 * Date: 3/11/13
 * Time: 2:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class ImageSet2
{
    public final URL url;
    BufferedImage baseImg;
    Image	img;
    Image bigImg;

    public ImageSet2()
    {
        this(null);
    }

    public ImageSet2(URL u)
    {
        this.url = u;
        img = null;
        bigImg = null;
        baseImg = null;
    }

    public synchronized void clearDisplayImages() {
        img = bigImg = null;
    }

    /**
     * This can take a while.  Don't do it on the UI thread
     */
    public synchronized void loadBaseImage()
        throws IOException, ImageReadException
    {
        if (null==url)
            return;
        debugPrint("load base "+this);
        long start = System.currentTimeMillis();
        byte[] buffer = slurp(url.openStream());
        long elapsed = System.currentTimeMillis()-start;
        System.out.println("slurp "+elapsed+"ms");
        BufferedImage i2;
        start = System.currentTimeMillis();
        if (false) {
            i2 = Imaging.getBufferedImage(buffer);
        } else {
            i2 = ImageIO.read(new ByteArrayInputStream(buffer));
        }
        elapsed = System.currentTimeMillis()-start;
        System.out.println("decode "+elapsed+"ms");
        ImageMetadata metadata = Imaging.getMetadata(buffer);
        i2 = maybeRotateImage(i2, metadata);
        baseImg = i2;
    }

    public synchronized void loadThumbnailImage(ImageSet.Parameters p2)
        throws IOException, ImageReadException
    {
        if (null==url)
            return;
        if (null==baseImg)
            loadBaseImage();
        debugPrint("load thumbnail "+this);
        img = fitImageIn(baseImg, p2.smallWidth, p2.smallHeight);
    }

    public synchronized void loadCenterImage(ImageSet.Parameters p2)
        throws IOException, ImageReadException
    {
        if (null==url)
            return;
        if (null==baseImg)
            loadBaseImage();
        debugPrint("load center " + this);
        bigImg = fitImageIn(baseImg, p2.centerWidth, p2.centerHeight);
    }

    public void debugPrint(String s)
    {
        if (false) {
            System.out.println(s);
        }
    }

    public static BufferedImage fitImageIn(BufferedImage baseImg, int w, int h)
    {
        if (null == baseImg)
            return null; // blacklisting makes this a little weird.

        if (baseImg == null)
            throw new NullPointerException("oh fuck!");

        int	iw = baseImg.getWidth();
        int	ih = baseImg.getHeight();
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

        if (nw*2 < iw) {
            baseImg = fitImageIn(baseImg, nw*2, nh*2);
        }

        BufferedImage rval = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = rval.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(baseImg, 0,0, nw, nh, null);
        return rval;
    }

    public Image dispImg(boolean isBig) {
        return isBig ? bigImg : img;
    }

    public synchronized void flush() {
        if (baseImg != null) baseImg.flush();
        if (img != null) img.flush();
        if (bigImg != null) bigImg.flush();
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

    public static BufferedImage maybeRotateImage(BufferedImage baseImage, ImageMetadata metadata)
        throws ImageReadException
    {
        TiffImageMetadata tiffMD;
        if (metadata instanceof JpegImageMetadata) {
            JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
            tiffMD = jpegMetadata.getExif();
        } else if (metadata instanceof TiffImageMetadata) {
            tiffMD = (TiffImageMetadata) metadata;
        } else {
            return baseImage;
        }

        TiffField orientation_ = tiffMD.findField(TiffTagConstants.TIFF_TAG_ORIENTATION);
        if (null == orientation_)
            return baseImage;

        int orientation = orientation_.getIntValue();

        BufferedImage i3;
        if (orientation > 4) {
            i3 = new BufferedImage(baseImage.getHeight(), baseImage.getWidth(), BufferedImage.TYPE_INT_ARGB);
        } else {
            // some form of rotation by 90 degrees
            i3 = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        }

        AffineTransform at = transformForEXIFOrientation(orientation, baseImage.getWidth(), baseImage.getHeight());

        i3.createGraphics().drawImage(baseImage, at, null);

        return i3;
    }

    public static byte[] slurp(InputStream inputStream)
        throws IOException
    {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        byte[] buffer = new byte[4<<10];

        while (true) {
            int n = inputStream.read(buffer);
            if (n<1)
                break;
            tmp.write(buffer, 0, n);
        }

        return tmp.toByteArray();
    }

    public static AffineTransform transformForEXIFOrientation(int orientation, int width, int height)
    {

        AffineTransform t = new AffineTransform();

        switch (orientation) {
        case 1:
            break;
        case 2: // Flip X
            t.scale(-1.0, 1.0);
            t.translate(-width, 0);
            break;
        case 3: // PI rotation
            t.translate(width, height);
            t.rotate(Math.PI);
            break;
        case 4: // Flip Y
            t.scale(1.0, -1.0);
            t.translate(0, -height);
            break;
        case 5: // - PI/2 and Flip X
            t.rotate(-Math.PI / 2);
            t.scale(-1.0, 1.0);
            break;
        case 6: // -PI/2 and -width
            t.translate(height, 0);
            t.rotate(Math.PI / 2);
            break;
        case 7: // PI/2 and Flip
            t.scale(-1.0, 1.0);
            t.translate(-height, 0);
            t.translate(0, width);
            t.rotate(  3 * Math.PI / 2);
            break;
        case 8: // PI / 2
            t.translate(0, width);
            t.rotate(  3 * Math.PI / 2);
            break;
        }

        return t;

    }
}

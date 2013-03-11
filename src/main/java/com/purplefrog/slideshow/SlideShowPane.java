package com.purplefrog.slideshow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class SlideShowPane
    extends JPanel
    implements MouseListener, MouseMotionListener
{
    protected Blacklist blacklist = new Blacklist();
    private boolean blacklistEnabled=false;
    //////////////////////////////////////////////////////////////////////

    protected class ImagePosition {
        URL u;

        Image	baseImg;

        Image	img, bigImg;
        int	x,y;


        protected ImagePosition() {
            baseImg = null;

            img = null;
            bigImg = null;
            x = y = 0;
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

        public synchronized void setImage(URL u)
        {
            this.u = u;
            this.baseImg = MyImageCache.getImage2(u);
            this.img = null;
            this.bigImg = null;
        }

        private synchronized void computeImages(int i, Dimension d)
        {
            if (img == null)
                img = fitImageIn(baseImg, smallWidth, smallHeight);
            if (bigImg == null)
                bigImg = fitImageIn(baseImg, centerWidth, d.height);
        }

        public synchronized String toString()
        {
            return "base="+baseImg+" img="+img+" big="+bigImg;
        }
    }

    /**/

    //////////////////////////////////////////////////////////////////////

    protected class BaseImageWatcher
        implements ImageObserver
    {
        //int urlOff;

        protected BaseImageWatcher() {
            //urlOff = i;
        }


        public boolean imageUpdate(Image img,
                                   int infoflags,
                                   int x, int y,
                                   int width, int height)
        {
            synchronized (SlideShowPane.this) {
//                System.out.println(img + " "+infoflags);
                if (0 != (infoflags & ImageObserver.ERROR) ) {
                    imageFailed(img);
                }
                if (0 != (infoflags & (ImageObserver.ABORT|
                    ImageObserver.ERROR))) {
                    return false;
                }
                if (0 != (infoflags & (ImageObserver.WIDTH|
                    ImageObserver.HEIGHT|
                    ImageObserver.PROPERTIES))) {
                    //System.out.println("dimensions for "+urlOff);
                    computeImages();
                }
            }
            if (0 != (infoflags&ALLBITS)) {
                Rectangle r = onScreenFor(img);
                if (null != r)
                    repaint(1, r.x, r.y, r.width, r.height);
                else
                    repaint(1);
            }
            return 0 == (infoflags&ALLBITS);
        }

    }

    protected class DispImageWatcher
        implements ImageObserver
    {
        //int urlOff;

        protected DispImageWatcher() {
            //urlOff = i;
        }


        public boolean imageUpdate(Image img,
                                   int infoflags,
                                   int x, int y,
                                   int width, int height)
        {
            synchronized (SlideShowPane.this) {

                int urlOff = -1;
                for (int i=0; i< images.length; i++) {
                    if (img == images[i].dispImg(isBig(i))) {
                        urlOff = screenIdxToIdx(i);
                        break;
                    }
                }

                Graphics g = null;
                if (0 != (infoflags & (ImageObserver.ABORT|
                    ImageObserver.ERROR))) {
                    return false;
                }
                if (0 != (infoflags & (ImageObserver.ALLBITS|
                    ImageObserver.FRAMEBITS
                    /*| ImageObserver.SOMEBITS*/))) {
                    /*
                    if (0 != (infoflags & (ImageObserver.ALLBITS))) {
                    System.out.println("all pixels for "+urlOff);
                    } else {
                    System.out.println("pixels for "+urlOff+" "+width+"x"+height+"+"+x+"+"+y);
                    }
                    */
                    if (g==null) g = getGraphics();
                    int screenOff = urlOff-currImageIdx + beforeCols*otherRows;
                    if (screenOff>=0 &
                        screenOff<countOnScreen()) {
                        ImagePosition curr = images[screenOff];
                        /*		    g.drawImage(curr.img,
                        curr.x+x, curr.y+y,curr.x+x+width-1, curr.y+y+height-1,
                        x, y, x+width-1, y+height-1, null);
                        */
                        if (true) {
                            repaint(1, curr.x, curr.y, img.getWidth(null), img.getHeight(null));
                        } else {
                            g.drawImage(curr.dispImg(urlOff==currImageIdx),
                                curr.x, curr.y, null);
                        }
                    }
                    drawStatusLEDs();
                }
            }
            return 0 == (infoflags&ALLBITS);
        }

    }

    protected class BackgroundImagePrefetcher
        implements ImageObserver, Runnable
    {
        Image target;

        public boolean pleaseStop;
        public boolean stopped;

        protected BackgroundImagePrefetcher() {
            target = null;
            pleaseStop = false;
            stopped = false;
        }

        public synchronized boolean imageUpdate(Image img,
                                                int infoflags,
                                                int x, int y,
                                                int width, int height)
        {
            if (img!=target) {
                this.notify();
                return false;
            }

            if (infoflagsDone(infoflags)) {
                //System.out.println("Done with "+img);
                this.notify();
                return false;
            }
            return true;
        }

        public boolean infoflagsDone(int infoflags) {
            return 0 != (infoflags & (ImageObserver.ABORT|
                ImageObserver.ERROR|
                ImageObserver.ALLBITS));
        }

        protected boolean groovy(Image img) {
            if (img == null)
                return true;
            if (!MyImageCache.toolkit.prepareImage(img, -1, -1, this)) {
                //System.out.println("waiting for "+img);
                target = img;
                try { this.wait(1000); } catch (InterruptedException e) { }
                return false;
            }
            return true;
        }

        protected synchronized void onepass() {
            int i;
            ImagePosition curr;

            drawStatusLEDs();

            final int n = countOnScreen();

            i=beforeCols*otherRows;
            curr = images[i];
            if (!groovy(curr.bigImg)) { return; }
            //imgStatusLED(i, Color.green);

            i++;
            curr = images[i];
            if (!groovy(curr.bigImg)) { return; }
            //imgStatusLED(i, Color.green);

            for (i=0; i<n; i++) {
                curr = images[i];
                if (!groovy(curr.img)) { return; }
                //imgStatusLED(i, Color.yellow);
            }
            for (i= 1; i<afterCols*otherRows; i++) {
                curr = images[beforeCols*otherRows+1+i];
                if (!groovy(curr.bigImg)) { return; }
                //imgStatusLED(i, Color.green);
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
        }

        public void run() {
            while (!pleaseStop) {
                onepass();
            }
            synchronized (this) {
                stopped = true;
                this.notifyAll();
            }
        }

    }

    //////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////

    ImagePosition[] images;

    int	beforeCols, otherRows;
    int	afterCols/*, afterRows*/;

    boolean	vertical;
    int	currImageIdx;

    List<URL> urls;

    //BaseImageWatcher [] baseWatchers;
    //DispImageWatcher [] dispWatchers;

    Thread bip_thread;
    BackgroundImagePrefetcher bip;

    /**/

    int	magX, magY;
    float magDivisor;
    int magRadius;

    /**/

    int	pad = 4;
    int	smallWidth, smallHeight, centerWidth;

    //////////////////////////////////////////////////////////////////////

    public SlideShowPane(List<URL> urls,
                         int beforeCols, int otherRows,
                         int afterCols /*int afterRows,*/)
    {

        this.beforeCols = beforeCols;
        this.otherRows = otherRows;
        this.afterCols = afterCols;
        /*this.afterRows = afterRows;*/
        currImageIdx = 0;

        {
            final int n = countOnScreen();
            images = new ImagePosition[ n ];
            for (int i=0; i<n; i++) {
                images[i] = new ImagePosition();
            }
        }
        setURLs(urls);

        {
            bip_thread = new Thread(bip = new BackgroundImagePrefetcher());
            bip_thread.setDaemon(true);
            bip_thread.start();
        }

        addComponentListener(new ComponentAdapter()
        { public void componentResized(ComponentEvent e)
        { componentResized_(e); }
        });
        addKeyListener(new KeyAdapter()
        { public void keyPressed(KeyEvent e)
        { keyPressed_(e); }
        });
        addMouseListener(this);
        addMouseMotionListener(this);
        setFocusable(true);
    }

    public SlideShowPane(List<URL> urls,
                         int cols, int rows)
    {
        this(urls, cols, rows, cols);
    }

    /**
     * you probably want to call {@link #setURLs(java.util.List)} at some point
     */
    public SlideShowPane()
    {
        this(new ArrayList<URL>(), 1, 4);
    }

    public void finalize() {
        bip.pleaseStop = true;
        /*
        synchronized (bip) {
        try { bip.wait(); } catch (InterruptedException e ) { }
        }
        */
    }

    private URL getURL(int idx)
    {
        return urls.get(idx);
    }

    protected void setURLs(List<URL> urls)
    {
        this.urls = new ArrayList<URL>();
        this.urls.addAll(urls);

        afterURLsChangeCompute();

        repaint(10);
    }

    protected void afterURLsChangeCompute()
    {
        magX = magY = 0;
        magRadius = 0;

        getImages();

        computeImages();
        computeImagePositions();
    }

    public synchronized void imageFailed(Image img)
    {
        System.out.println("failure "+img);
        for (int i = 0; i < images.length; i++) {
            //int urlIdx = screenIdxToIdx(i);
            //if (!validUrlsOff(urlIdx))
            //    continue;

            ImagePosition iwp = images[i];
            if (iwp.baseImg == img
                || iwp.img == img
                || iwp.bigImg == img) {
                System.out.println("smackdown "+iwp.u);

                if (i<beforeCols*otherRows) {
                    if (i>0) System.arraycopy(images, 0, images, 1, i);
                    images[0] = new ImagePosition();
                } else {
                    int last = countOnScreen()-1;
                    if (i<last)
                        System.arraycopy(images, i+1, images, i, last-i);
                    images[last] = new ImagePosition();
                }
                urls.remove(iwp.u);

                getImages();
                computeImages();
                computeImagePositions();
                repaint(100);
                return;
            }
        }
        img.flush();
    }

    private int screenIdxToIdx(int i)
    {
        return i + currImageIdx -beforeCols*otherRows;
    }

    //////////////////////////////////////////////////////////////////////

    protected int countOnScreen() {
        return 1+ beforeCols*otherRows + afterCols*otherRows;
    }

    protected boolean validUrlsOff(int i) {
        return i>=0 && i<urls.size();
    }
    //////////////////////////////////////////////////////////////////////

    protected synchronized void getImages() {

        PrintStream pw = System.err;
        pw.println("getImages()");
        if (blacklistEnabled) {
            
            int pivot = beforeCols * otherRows;

            putURLInImagePosition(pw, pivot, currImageIdx);
            
            int j=1;
            int urlsOff = currImageIdx-j;
            while (j<=beforeCols*otherRows) {
                if (urlsOff<0) {
                    putURLInImagePosition(pw, pivot-j, urlsOff);
                    j++;
                    continue;
                }
                
                if (blacklisted(getURL(urlsOff))) {
                    System.out.println("blacklisted");
                } else {
                    putURLInImagePosition(pw, pivot-j, urlsOff);
                    j++;
                }
                    urlsOff--;
            }
            
            j=1;
            urlsOff = currImageIdx+j;
            
            while (j<=afterCols*otherRows) {
                if (!validUrlsOff(urlsOff)) {
                    putURLInImagePosition(pw, pivot+j, urlsOff);
                    j++;
                    continue;
                }
                
                if (blacklisted(getURL(urlsOff))) {
                    System.out.println("blacklisted");
                } else {
                    putURLInImagePosition(pw, pivot+j, urlsOff);
                    j++;
                }
                    urlsOff++;
            }
                        
        } else {
            for (int j = -beforeCols*otherRows ; j<= afterCols*otherRows; j++) {
                int screenOff = j + beforeCols*otherRows;
                final int urlsOff=currImageIdx+j;
                pw.println(j+" urlsOff="+urlsOff);
                putURLInImagePosition(pw, screenOff, urlsOff);
            }
        }
    }

    private boolean blacklisted(URL url)
    {
        try {
            URI uri = url.toURI();
            return blacklist.isBlacklisted(uri);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return false;
        }
    }

    protected void putURLInImagePosition(PrintStream pw, int screenOff, int urlsOff)
    {
        if (!validUrlsOff(urlsOff)) {
            pw.println("OOB");
            images[screenOff] = new ImagePosition();
        } else if (images[screenOff].baseImg == null) {

            try {
                final URL url = getURL(urlsOff);
                pw.println("url["+urlsOff+"] = "+url);
                images[screenOff].setImage(url);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////

    protected synchronized void computeTableKerning() {
        Dimension d = getSize();

        int widthMinusPad = d.width - 4*(beforeCols+afterCols);

        float f = beforeCols / (float) otherRows + 1 + afterCols / (float)otherRows;
        smallWidth = (int)Math.round(Math.floor(widthMinusPad / f / otherRows));

        centerWidth = widthMinusPad - smallWidth*beforeCols - smallWidth*afterCols;
        smallHeight = (d.height-pad*(otherRows-1)) / otherRows;

        //System.out.println("computeTableKerning:  w"+d.width+" "+f+" "+smallWidth+" "+smallHeight);
    }

    //////////////////////////////////////////////////////////////////////

    protected synchronized void computeImages()
    {
        Dimension d = getSize();

        final int n = countOnScreen();
        for (int i=0; i<n; i++) {
            ImagePosition curr = images[i];
            curr.computeImages(i, d);
        }
    }

    public Image fitImageIn(Image baseImg, int w, int h)
    {
        if (null == baseImg)
            return null; // blacklisting makes this a little weird.

        if (baseImg == null)
            throw new NullPointerException("oh fuck!");
        ImageObserver observer = biw;
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

    //////////////////////////////////////////////////////////////////////

    protected synchronized void computeImagePositions()
    {
        int x;

        for (int i = -beforeCols; i<0; i++) {
            for (int j=0; j<otherRows; j++) {
                final int	screenOff = (i+beforeCols)*otherRows + j;
                images[screenOff].x = (i+beforeCols)*(smallWidth+pad);
                images[screenOff].y = j*(smallHeight+pad);
                //System.out.println("images["+screenOff+"] @"+images[screenOff].x+","+images[screenOff].y);
            }
        }

        x = beforeCols*(smallWidth+pad);

        images[beforeCols*otherRows].x = x;
        images[beforeCols*otherRows].y = 0;

        x += centerWidth+pad;

        for (int i = 0; i<afterCols; i++) {
            for (int j=0; j<otherRows; j++) {
                final int	screenOff = beforeCols*otherRows +1
                    +i*otherRows+ j;
                images[screenOff].x = i*(smallWidth+pad) + x;
                images[screenOff].y = j*(smallHeight+pad);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////

    public synchronized void setImageIdx(int newIdx) {
        if (!validUrlsOff(newIdx)) {
            throw new IllegalArgumentException("setImageIdx("+newIdx+")  ![0,"+urls.size()+")");
        }

        final int delta = newIdx - currImageIdx;
        boolean echidna = true;
        if (echidna) {
            for (int i = 0; i < images.length; i++) {
                images[i] = new ImagePosition();
            }
        } else {
            final int n = countOnScreen();
            int i;
            if (delta<0) {
                for (i=Math.max(delta, -n); i<0 && i<n; i++) {
                    images[n+i].flush();
                    System.out.println("flushing "+(n+i));
                }
                for (i=n-1+delta; i>=0; i--) {
                    images[i-delta] = images[i];
                }
                for (i=0; i<-delta; i++) {
                    images[i] = new ImagePosition();
                }
            } else if (delta>0) {
                for (i=0; i<delta && i<n; i++) {
                    images[i].flush();
                }
                for (i=0; i+delta<n; i++) {
                    images[i] = images[i+delta];
                }
                for (; i<n; i++) {
                    images[i] = new ImagePosition();
                }
            }
        }
        currImageIdx = newIdx;
        System.out.println(getURL(newIdx).toString());
        if (echidna || delta != 0) {
            getImages();
            computeImages();
            computeImagePositions();
            repaint();
        }
    }

    //////////////////////////////////////////////////////////////////////

    protected synchronized void resetBrokenImages()
    {

        final int n = countOnScreen();
        for (int i=0; i<n; i++) {
            ImagePosition curr = images[i];
            if (imageHasProblem(curr.baseImg) ) {
                System.err.println(i+".baseImg damaged, resetting");
                curr.baseImg.flush();
                curr.baseImg = null;
            }
            if (imageHasProblem(curr.img) ) {
                System.err.println(i+".img damaged, resetting");
                curr.img.flush();
                curr.img = null;
            }
            if (imageHasProblem(curr.bigImg) ) {
                System.err.println(i+".bigImg damaged, resetting");
                curr.bigImg.flush();
                curr.bigImg = null;
            }
        }
        getImages();
        computeImages();
    }

    private boolean imageHasProblem(Image img)
    {
        return img!= null &&
                        0 != ( (ImageObserver.ABORT|ImageObserver.ERROR)
                        & checkImage(img, -1, -1, null) );
    }

    //////////////////////////////////////////////////////////////////////

    protected synchronized int coordToScreenOff(int x, int y)
    {
        int	col;

        col = x/smallWidth;
        if (col < beforeCols) {
            return col*otherRows + y/smallHeight;
        }

        x -= smallWidth*beforeCols;

        if (x  < centerWidth)
            return beforeCols*otherRows;

        x -= centerWidth;
        col = x/smallWidth;

        return beforeCols*otherRows + 1 + col*otherRows + y/smallHeight;
    }

    //////////////////////////////////////////////////////////////////////

    protected final BaseImageWatcher biw = new BaseImageWatcher();
    protected synchronized ImageObserver imageWatcherForBase(int i) {
/*
        ImageObserver o = baseWatchers[i];
        if (o==null)
            o = baseWatchers[i] = new BaseImageWatcher(i);
        return o;
*/
        return biw;
    }

    protected final DispImageWatcher diw = new DispImageWatcher();
    protected synchronized ImageObserver imageWatcherForDisp(int i) {
/*
        ImageObserver o = dispWatchers[i];
        if (o==null)
            o = dispWatchers[i] = new DispImageWatcher(i);
        return o;
*/
        return diw;
    }

    //////////////////////////////////////////////////////////////////////

    public synchronized void paint(Graphics g) {
        paint_(g);

        super.paint(g);
    }

    @Override
    protected void paintComponent(Graphics g_)
    {
        Graphics g = g_.create();
        paint_(g);
    }

    public void paint_(Graphics g)
    {
        final int n = countOnScreen();
        for (int i=0; i<n; i++) {
            int urlsOff = i - beforeCols*otherRows + currImageIdx;
            //paintOneImage(g, i, imageWatcherForDisp(urlsOff));
            ImagePosition curr = images[i];

            boolean isBig = isBig(i) ;

            int w2 = isBig ? centerWidth : smallWidth;
            int h2 = isBig ? getHeight() : smallHeight;

            if (g.hitClip(curr.x, curr.y, w2, h2)) {

                g.fillRect(curr.x, curr.y, w2, h2);

                if (!validUrlsOff(urlsOff))
                    continue;

                if (curr.img != null) {
                    drawImageInRectangle(g, urlsOff, curr.dispImg(isBig), curr.x, curr.y, w2, h2);
                    //System.out.println("drawing ["+i+"] at +"+curr.x+"+"+curr.y);
                }
            }
        }

        drawTextReadouts(g);

        drawStatusLEDs();
        drawMag(g);
    }

    protected void drawTextReadouts(Graphics g)
    {
        g.setColor(Color.black);
        final FontMetrics fontMetrics = g.getFontMetrics();
        if (validUrlsOff(currImageIdx)) {
            final URL url = getURL(currImageIdx);

            g.drawString(url.toExternalForm(),
                smallWidth * beforeCols,
                getSize().height - fontMetrics.getDescent());

            if (blacklisted(url)) {
                g.setColor(Color.red);
                final int x = smallWidth * beforeCols + this.centerWidth - fontMetrics.stringWidth("B");
                final int y = getSize().height - fontMetrics.getDescent();
                g.drawString("B", x, y);
            }

        }
    }

    public void drawImageInRectangle(Graphics g, int urlsOff, Image img, int x, int y, int w, int h)
    {
        g.drawImage(img, x, y, imageWatcherForDisp(urlsOff));

        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw<0)
            iw=0;
        if (ih < 0)
            ih=0;
        g.setColor(getBackground());
        g.fillRect(x+iw, y, w - iw, ih);
        g.fillRect(x, y+ih, w, h-ih);
    }

    @Override
    public void update(Graphics g)
    {
        paint_(g);
    }

    public Rectangle onScreenFor(Image img)
    {
        for (ImagePosition ip : images) {
            if (img == ip.img) {
                return new Rectangle(ip.x, ip.y, img.getWidth(null), img.getHeight(null));
            } else if (img == ip.bigImg) {
                return new Rectangle(ip.x, ip.y, img.getWidth(null), img.getHeight(null));
            }
        }
        return null;
    }

    private boolean isBig(int screenIdx)
    {
        return screenIdx==beforeCols*otherRows;
    }

    protected void drawMag(Graphics g) {
        if (magRadius<=0)
            return;

        ImagePosition	bigun = images[beforeCols*otherRows];

        if (bigun.baseImg==null)
            return;

        if ( bigun.baseImg.getWidth(null)<=0 ||
            bigun.baseImg.getHeight(null)<=0 ||
            bigun.bigImg.getWidth(null)<=0 ||
            bigun.bigImg.getHeight(null)<=0)
            return;

        int x0,y0;
        x0 = magX*bigun.baseImg.getWidth(null) / bigun.bigImg.getWidth(null);
        y0 = magY*bigun.baseImg.getHeight(null) / bigun.bigImg.getHeight(null);

        x0 -= magRadius;
        if (x0<0) x0=0;

        y0 -= magRadius;
        if (y0<0) y0=0;

        int left, top;
        left = magX+bigun.x - magRadius;
        top = magY+bigun.y - magRadius;
        if (left<0) left=0;
        if (top<0) top=0;

        g.drawRect(left-1, top-1, 2*magRadius+1, 2*magRadius+1);
        // the magDivisor doesn't work
        int d;
        /* d = Math.round(2*magRadius/magDivisor);*/
        d = 2*magRadius;
        //System.out.println("diameter "+d);
        g.drawImage(bigun.baseImg,
            left, top, left+d, top+d,
            x0,y0, x0+2*magRadius, y0+2*magRadius,
            null);
    }

    protected static void imgStatusLED(Graphics g, int screenOff, Color color)
    {
        final int	n = 2;
        if (g==null) return;
        g.setColor(color);
        g.fillRect(screenOff*n, 0, n, n);
    }

    protected void drawStatusLEDs() {
        ImagePosition curr;
        Graphics g = getGraphics();
        final int n = countOnScreen();
        int	i;

        //System.out.println("scanning");
        for (i=0; i<n; i++) {
            curr = images[i];
            if (curr.img == null ||
                0 == (ALLBITS &checkImage(curr.img, -1,-1, null)) ) {
                imgStatusLED(g, i, Color.red);
            } else if (curr.bigImg == null ||
                0 == (ALLBITS &checkImage(curr.bigImg, -1,-1, null)) ) {
                imgStatusLED(g, i, Color.yellow);
            } else {
                imgStatusLED(g, i, Color.green);
            }
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(800,600);
    }

    //////////////////////////////////////////////////////////////////////

    // ComponentListener interface

    public void componentResized_(ComponentEvent e)
    {
        computeTableKerning();

        final int n = countOnScreen();
        for (int i=0; i<n; i++)
            images[i].clearDisplayImages();

        computeImages();
        computeImagePositions();
    }

    //////////////////////////////////////////////////////////////////////

    // KeyListener interface

    public void keyPressed_(KeyEvent e)
    {
        if (' ' == e.getKeyChar()) {
            //System.out.println("space");


            int newIdx;
            if (!blacklistEnabled) {
                newIdx = currImageIdx +1;
            } else {
                newIdx = urlIndexAfterBlacklisting(currImageIdx, 1);
            }
            if (validUrlsOff(newIdx))
                setImageIdx(newIdx);

        } else if ('\b' == e.getKeyChar()) {
            //System.out.println("backspace");

            int newIdx;
            if (!blacklistEnabled) {
                newIdx = currImageIdx -1;
            } else {
                newIdx = urlIndexAfterBlacklisting(currImageIdx, -1);
            }
            if (validUrlsOff(newIdx))
                setImageIdx(newIdx);

        } else if ('r' == e.getKeyChar()) {

            resetBrokenImages();
            repaint();

        } else if ('q' == e.getKeyChar()) {

            System.exit(0);

        } else if (KeyEvent.VK_PAGE_DOWN == e.getKeyCode()) {

            int newIdx = currImageIdx + countOnScreen();
            if (validUrlsOff(newIdx))
                setImageIdx(newIdx);
            else
                setImageIdx(urls.size()-1);

        } else if (KeyEvent.VK_PAGE_UP == e.getKeyCode()) {

            int newIdx = currImageIdx - countOnScreen();
            if (validUrlsOff(newIdx))
                setImageIdx(newIdx);
            else
                setImageIdx(0);

        } else if (KeyEvent.VK_B == e.getKeyCode()) {


            int mods = e.getModifiers();

            if (0 != (mods & KeyEvent.SHIFT_MASK)) {
                blacklistEnabled = !blacklistEnabled;

                for (int i = 0, imagesLength = images.length; i < imagesLength; i++) {
                    images[i] = new ImagePosition();
                }

                getImages();
                computeImages();
                computeImagePositions();
                repaint(1);

            } else {
                if (validUrlsOff(currImageIdx)) {
                    try {
                        URL url = getURL(currImageIdx);
                        URI uri =url.toURI();
                        if (blacklist.isBlacklisted(uri)) {
                            System.out.println("unblacklisting "+uri);
                            blacklist.unBlacklist(uri);
                        } else {
                            System.out.println("blacklisting "+uri);
                            blacklist.addBlacklist(uri);

                            if (blacklistEnabled) {
                                setImageIdx(urlIndexAfterBlacklisting(currImageIdx,0));
                            }
                        }
                        repaint(1);
                    } catch (URISyntaxException e1) {
                        e1.printStackTrace();
                    }
                }
            }

        } else {
            //System.out.println("key code "+ e.getKeyCode());
        }
    }

    public int urlIndexAfterBlacklisting(int startingPoint, int delta)
    {
        int i=0;
        int c=0;

        int newIdx = startingPoint;
        if (delta>=0) {
            while (true) {
                if (!validUrlsOff(newIdx)) {
                    return newIdx;
                }

                if (blacklisted(getURL(newIdx))) {

                } else {
                    if (c >=delta)
                        break;
                    c++;
                }
                i++;
                newIdx = startingPoint +i;
            }
        } else {
            while (true) {
                if (!validUrlsOff(newIdx)) {
                    return newIdx;
                }

                if (blacklisted(getURL(newIdx))) {

                } else {
                    if (c <= delta)
                        break;
                    c--;
                }
                i--;
                newIdx = startingPoint +i;
            }
        }
        return newIdx;
    }

    //////////////////////////////////////////////////////////////////////

    // MouseListener interface


    public void mouseClicked(MouseEvent e)
    {
        int screenOff = coordToScreenOff(e.getX(), e.getY());
        int delta = screenOff - beforeCols*otherRows;
        if (delta == 0) {
            return;
        }
        int newIdx = currImageIdx + delta;
        if (validUrlsOff(newIdx))
            setImageIdx(newIdx);
    }

    public void mousePressed(MouseEvent e)
    {
        int screenOff = coordToScreenOff(e.getX(), e.getY());
        int delta = screenOff - beforeCols*otherRows;
        if (delta != 0)
            return;

        mouseDragged(e);
    }

    public void mouseReleased(MouseEvent e)
    {
        if (magRadius != 0) {
            magRadius=0;
            repaint();
        }
    }

    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
    }

    //////////////////////////////////////////////////////////////////////

    // MouseMotionListener interface


    public void mouseDragged(MouseEvent e)
    {
        ImagePosition	bigun = images[beforeCols*otherRows];
        if (bigun.bigImg == null)
            return;

        // erase old mag window
        if (magRadius>0) {
            int left, top;
            left = magX+bigun.x - magRadius;
            top = magY+bigun.y - magRadius;

            Graphics g = getGraphics();
            g.setClip(left-1, top-1, 2*magRadius+2, 2*magRadius+2);

            //System.out.println("repainting at "+left+","+top);
            magRadius = 0; paint(g) ;
        }

        // compute new parameters

        int x = e.getX() - bigun.x;
        int y = e.getY() - bigun.y;

        int w = bigun.bigImg.getWidth(null);
        if (x >= w )
            x = bigun.bigImg.getWidth(null)-1;
        if (x<0)
            x = 0;

        int h = bigun.bigImg.getHeight(null);
        if (y >= h )
            y = h-1;
        if (y<0)
            y = 0;

        magX = x;
        magY = y;

        int modifiers = e.getModifiers();
        if (0 != (modifiers &e.BUTTON1_MASK)) {
            magDivisor=1;
            magRadius = 64;
        } else if (0 != (modifiers &e.BUTTON2_MASK)) {
            magDivisor=1;
            magRadius = 128;
        } else {
            magDivisor=1;
            magRadius = 192;
        }

        drawMag(getGraphics());
    }

    public void mouseMoved(MouseEvent e)
    {
    }

    //////////////////////////////////////////////////////////////////////
    //
    //////////////////////////////////////////////////////////////////////


    public void addURLs(List<URL> x)
    {
        System.out.println("adding "+x.size()+" URLs");
        urls.addAll(x);

        afterURLsChangeCompute();

        repaint(100);
    }

    public void addURL(URL url)
    {
        urls.add(url);

        afterURLsChangeCompute();
        
        repaint(100);
    }

    private static void addURL(List<URL> urls, String str)
        throws MalformedURLException
    {
        try {
            urls.add(new URL(str));
        } catch (MalformedURLException e) {
	    urls.add(new File(str).toURL());
        }
    }

}

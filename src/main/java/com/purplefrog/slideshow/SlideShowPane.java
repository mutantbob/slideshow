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
    extends JComponent
    implements MouseListener, MouseMotionListener
{
    protected Blacklist blacklist = new Blacklist();
    private boolean blacklistEnabled=false;
    //////////////////////////////////////////////////////////////////////

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

                if (0 != (infoflags & (ImageObserver.ABORT|
                    ImageObserver.ERROR))) {
                    return false;
                }
                if (0 != (infoflags & (ImageObserver.ALLBITS|
                    ImageObserver.FRAMEBITS
                    /*| ImageObserver.SOMEBITS*/))) {

                    int boxNum = images.indexForImage(img);

                    if (false) {
                        if (0 != (infoflags & (ImageObserver.ALLBITS))) {
                            System.out.println(boxNum);
                        } else {
                            System.out.println("pixels for "+boxNum + " " + width + "x" + height + "+" + x + "+" + y);
                        }
                    }

                    if (boxNum>=0) {
                        repaintBox(boxNum);
                    }

                    drawStatusLEDs();
                }
            }
            return 0 == (infoflags&ALLBITS);
        }

    }

    /**
     * schedule a repaint of the box specified.  Boxes are numbered from 0..(beforeCols*otherRows + 1 + afterCols*otherRows) .
     * @param boxNum
     */
    public void repaintBox(int boxNum)
    {
        Point curr = positions[boxNum];

                        /*		    g.drawImage(curr.img,
                        curr.x+x, curr.y+y,curr.x+x+width-1, curr.y+y+height-1,
                        x, y, x+width-1, y+height-1, null);
                        */
        boolean big = isBig(boxNum);

        repaint(1, curr.x, curr.y, big ? centerWidth:smallWidth , big ? getHeight() : smallHeight);
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
            ImageSet curr;

            drawStatusLEDs();

            final int n = countOnScreen();

            i=beforeCols*otherRows;
            curr = images.getIP(i);
            if (!groovy(curr.bigImg)) { return; }
            //imgStatusLED(i, Color.green);

            i++;
            curr = images.getIP(i);
            if (!groovy(curr.bigImg)) { return; }
            //imgStatusLED(i, Color.green);

            for (i=0; i<n; i++) {
                curr = images.getIP(i);
                if (!groovy(curr.img)) { return; }
                //imgStatusLED(i, Color.yellow);
            }
            for (i= beforeCols*otherRows+1; i<n; i++) {
                curr = images.getIP(i);
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

    AlbumWindow images;
    Point[] positions;

    int	beforeCols, otherRows;
    int	afterCols/*, afterRows*/;

    boolean	vertical;
    int	currImageIdx;

    List<URL> urls;

    Thread bip_thread;
    BackgroundImagePrefetcher bip;

    /**/

    int	magX, magY;
    float magDivisor;
    int magRadius;

    /**/

    int	pad = 4;
    int	smallWidth, smallHeight, centerWidth;

    SizedImageCache smallImageCache;
    SizedImageCache bigImageCache;

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

        images = new AlbumWindow(countOnScreen());
        positions = new Point[countOnScreen()];
        for (int i=0; i<positions.length; i++)
            positions[i] = new Point();

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

        computeOnScreenURLs();

        computeImages();
        computeImagePositions();
    }

    public synchronized void imageFailed(Image img)
    {
        System.out.println("failure "+img);
        URL u = images.URLforImage(img);
        if (u!=null) {
            System.out.println("smackdown "+u);
            urls.remove(u);
            computeOnScreenURLs();
            computeImages();
            computeImagePositions();
            repaint(100);
        }

        img.flush();
    }

    //////////////////////////////////////////////////////////////////////

    protected int countOnScreen() {
        return 1+ beforeCols*otherRows + afterCols*otherRows;
    }

    protected boolean validUrlsOff(int i) {
        return i>=0 && i<urls.size();
    }
    //////////////////////////////////////////////////////////////////////

    protected synchronized void computeOnScreenURLs() {

//        PrintStream pw = System.err;
//        pw.println("getImages()");

        URL[] imageURLs = new URL[beforeCols*otherRows + 1 + afterCols*otherRows];

        if (blacklistEnabled) {
            
            int pivot = beforeCols * otherRows;

            maybeSetURL(imageURLs, pivot, currImageIdx);

            int j=1;
            int urlsOff = currImageIdx-j;
            while (j<=beforeCols*otherRows) {
                if (urlsOff<0) {
                    imageURLs[pivot-j] = null;
                    j++;
                    continue;
                }
                
                if (blacklisted(getURL(urlsOff))) {
                    System.out.println("blacklisted");
                } else {
                    maybeSetURL(imageURLs, pivot-j, urlsOff);
                    j++;
                }
                urlsOff--;
            }
            
            j=1;
            urlsOff = currImageIdx+j;
            
            while (j<=afterCols*otherRows) {
                if (!validUrlsOff(urlsOff)) {
                    imageURLs[pivot+j] = null;
                    j++;
                    continue;
                }
                
                if (blacklisted(getURL(urlsOff))) {
                    System.out.println("blacklisted");
                } else {
                    maybeSetURL(imageURLs, pivot+j, urlsOff);
                    j++;
                }
                urlsOff++;
            }
                        
        } else {
            for (int j = -beforeCols*otherRows ; j<= afterCols*otherRows; j++) {
                int screenOff = j + beforeCols*otherRows;
                final int urlsOff=currImageIdx+j;
//                pw.println(j+" urlsOff="+urlsOff);
                maybeSetURL(imageURLs, screenOff, urlsOff);
            }
        }

        images.setURLs(imageURLs);
    }

    private void maybeSetURL(URL[] imageURLs, int screenOff, int urlsOff)
    {
        imageURLs[screenOff] =  validUrlsOff(urlsOff)
            ? urls.get(urlsOff)
            : null ;
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

    //////////////////////////////////////////////////////////////////////

    protected synchronized void computeTableKerning() {
        Dimension d = getSize();

        int widthMinusPad = d.width - 4*(beforeCols+afterCols);

        float f = beforeCols / (float) otherRows + 1 + afterCols / (float)otherRows;
        smallWidth = (int)Math.round(Math.floor(widthMinusPad / f / otherRows));

        centerWidth = widthMinusPad - smallWidth*beforeCols - smallWidth*afterCols;
        smallHeight = (d.height-pad*(otherRows-1)) / otherRows;

        smallImageCache = new SizedImageCache(smallWidth, smallHeight);
        bigImageCache = new SizedImageCache(centerWidth, d.height);

        //System.out.println("computeTableKerning:  w"+d.width+" "+f+" "+smallWidth+" "+smallHeight);
    }

    //////////////////////////////////////////////////////////////////////

    protected synchronized void computeImages()
    {
        Dimension d = getSize();

        ImageSet.Parameters params = new ImageSet.Parameters(smallWidth, smallHeight, centerWidth, d.height, biw);

        images.computeImages(params);

    }

    //////////////////////////////////////////////////////////////////////

    protected synchronized void computeImagePositions()
    {
        int x;

        for (int i = -beforeCols; i<0; i++) {
            for (int j=0; j<otherRows; j++) {
                final int	screenOff = (i+beforeCols)*otherRows + j;
                positions[screenOff].x = (i+beforeCols)*(smallWidth+pad);
                positions[screenOff].y = j*(smallHeight+pad);
                //System.out.println("images["+screenOff+"] @"+images[screenOff].x+","+images[screenOff].y);
            }
        }

        x = beforeCols*(smallWidth+pad);

        positions[beforeCols*otherRows].x = x;
        positions[beforeCols*otherRows].y = 0;

        x += centerWidth+pad;

        for (int i = 0; i<afterCols; i++) {
            for (int j=0; j<otherRows; j++) {
                final int	screenOff = beforeCols*otherRows +1
                    +i*otherRows+ j;
                positions[screenOff].x = i*(smallWidth+pad) + x;
                positions[screenOff].y = j*(smallHeight+pad);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////

    /**
     * pick which of the images refered to by our URL array will be the big center image.
     * @param newIdx
     */
    public synchronized void setImageIdx(int newIdx) {
        if (!validUrlsOff(newIdx)) {
            throw new IllegalArgumentException("setImageIdx("+newIdx+")  ![0,"+urls.size()+")");
        }

        final int delta = newIdx - currImageIdx;

        currImageIdx = newIdx;
        System.out.println(getURL(newIdx).toString());
        if (delta != 0) {
            computeOnScreenURLs();
            computeImages();
            computeImagePositions();
            repaint();
        }
    }

    //////////////////////////////////////////////////////////////////////

    protected synchronized void resetBrokenImages()
    {
        images.resetBrokenImages(null);

        computeOnScreenURLs();
        computeImages();
    }

    public boolean imageHasProblem(Image img)
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

    protected final DispImageWatcher diw = new DispImageWatcher();

    //////////////////////////////////////////////////////////////////////
/*

    public synchronized void paint(Graphics g) {
        paint_(g);

        super.paint(g);
    }
*/

    @Override
    protected void paintComponent(Graphics g_)
    {
        Graphics g = g_.create();

        g.setColor(getBackground());

        paint_(g);
    }

    public void paint_(Graphics g)
    {
        final int n = countOnScreen();

        paintGutters(g);

        for (int i=0; i<n; i++) {
            int urlsOff = i - beforeCols * otherRows + currImageIdx;
            //paintOneImage(g, i, imageWatcherForDisp(urlsOff));


            Point curr =positions[i];

            ImageSet qq=    images.getIP(i);

            boolean isBig = isBig(i) ;

            int w2 = isBig ? centerWidth : smallWidth;
            int h2 = isBig ? getHeight() : smallHeight;

            if (g.hitClip(curr.x, curr.y, w2, h2)) {

                if (validUrlsOff(urlsOff) && qq.img != null) {
                    drawImageInRectangle(g, qq.dispImg(isBig), curr.x, curr.y, w2, h2);
                    //System.out.println("drawing ["+i+"] at +"+curr.x+"+"+curr.y);
                } else {
                    g.fillRect(curr.x, curr.y, w2, h2);
                }
            }
        }

        drawTextReadouts(g);

        drawStatusLEDs();
        drawMag(g);
    }

    private void paintGutters(Graphics g)
    {
//        g.setColor(Color.RED);
        int x2 = beforeCols*(smallWidth+pad) + centerWidth;
        int w1 = beforeCols * (smallWidth + pad);
        int w2 = afterCols * (smallWidth + pad);
        for (int j=1; j<otherRows; j++) {
            int y = (smallHeight + pad) * j - pad;
            // left horizontal stripe gutter
            g.fillRect(0, y, w1, pad);

            // right horizontal stripe gutter
            g.fillRect(x2, y, w2, pad);
        }

        int height = getHeight();

        // left vertical stripe gutter
        for (int j=0; j<beforeCols; j++) {
            int x = j*(smallWidth+pad) + smallWidth;
            g.fillRect(x, 0, pad, height);
        }

        // right vertical stripe gutter
        for (int j=0; j<afterCols; j++) {
            int x = beforeCols*(smallWidth+pad)
                + centerWidth
                + j * (smallWidth + pad);
            g.fillRect(x, 0, pad, height);
        }
//        g.setColor(getBackground());
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

    public void drawImageInRectangle(Graphics g, Image img, int x, int y, int w, int h)
    {
        boolean buggy=false;
        ImageObserver result;
        synchronized (this) {
            result = diw;
        }
        if (!buggy)
            g.fillRect(x,y, w, h);
        g.drawImage(img, x, y, result);

        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw<0)
            iw=0;
        if (ih < 0)
            ih=0;

        if (buggy) {
            // now fill in the parts of the box that aren't painted with image pixels
            if (ih>0)
                g.fillRect(x+iw, y, w - iw, ih);

            g.fillRect(x, y+ih, w, h-ih);
        }
    }

    @Override
    public void update(Graphics g)
    {
        paint_(g);
    }

    public Rectangle onScreenFor(Image img)
    {
        for (int i=0; i<images.getCount(); i++) {
            ImageSet is = images.getIP(i);

            if (is.matchesImage(img)) {
                Point pos = positions[i];
                return new Rectangle(pos.x, pos.y, img.getWidth(null), img.getHeight(null));
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

        int offOfBig = beforeCols * otherRows;
        ImageSet	bigun = images.getIP(offOfBig);

        Point pos = positions[offOfBig];

        if (bigun.baseImg==null)
            return;

        int baseIW = bigun.baseImg.getWidth(null);
        int baseIH = bigun.baseImg.getHeight(null);
        if ( baseIW <=0 ||
            baseIH <=0 ||
            bigun.bigImg.getWidth(null)<=0 ||
            bigun.bigImg.getHeight(null)<=0)
            return;

        int x0,y0;
        x0 = magX* baseIW / bigun.bigImg.getWidth(null);
        y0 = magY* baseIH / bigun.bigImg.getHeight(null);

        int d;
        /* d = Math.round(2*magRadius/magDivisor);*/
        d = 2*magRadius;

        x0 -= magRadius;
        if (x0+d > baseIW)
            x0 = baseIW-d;
        if (x0<0) x0=0;

        y0 -= magRadius;
        if (y0+d>baseIH)
            y0 = baseIH-d;
        if (y0<0) y0=0;

        int left, top;
        left = magX+pos.x - magRadius;
        top = magY+pos.y - magRadius;

        int panelWidth = getWidth();
        int panelHeight = getHeight();
        if (left+d> panelWidth) {
            left = panelWidth -d;
        }
        if (top+d> panelHeight) {
            top = panelHeight -d;
        }

        if (left<0) left=0;
        if (top<0) top=0;

        g.drawRect(left-1, top-1, 2*magRadius+1, 2*magRadius+1);
        // the magDivisor doesn't work

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

        Graphics g = getGraphics();
        final int n = countOnScreen();
        int	i;

        //System.out.println("scanning");
        for (i=0; i<n; i++) {
            ImageSet curr = images.getIP(i);
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

        images.clearDisplayImages();

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

                computeOnScreenURLs();
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
        int newIdx = //currImageIdx + delta;
        urlIndexAfterBlacklisting(currImageIdx, delta);
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
        int offOfBig = beforeCols * otherRows;
        ImageSet	bigun = images.getIP(offOfBig);
        Point pos = positions[offOfBig];
        if (bigun.bigImg == null)
            return;

        // erase old mag window
        if (magRadius>0) {
            int left, top;
            left = magX+pos.x - magRadius;
            top = magY+pos.y - magRadius;

            Graphics g = getGraphics();
            g.setClip(left-1, top-1, 2*magRadius+2, 2*magRadius+2);

            //System.out.println("repainting at "+left+","+top);
            magRadius = 0; paint(g) ;
        }

        // compute new parameters

        int x = e.getX() - pos.x;
        int y = e.getY() - pos.y;

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


    public void addURLs(Collection<URL> x)
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

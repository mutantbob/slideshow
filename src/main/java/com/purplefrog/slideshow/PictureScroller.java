package com.purplefrog.slideshow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.lang.ref.*;
import java.net.*;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: thoth
 * Date: Apr 26, 2011
 * Time: 4:40:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class PictureScroller
    extends JComponent
{
    Gob[] images;
    boolean paused = false;
    private int scrolled=0;
    protected double scrollTime=0;
    private int padding=1;
    private double pixelsPerSecond=0;
    private int imageCursor = 0;

    private boolean vertical = false;
    Geometry geom = vertical ? new Vertical(): new Horizontal();

    public PictureScroller(URL... urls)
    {
        this(Arrays.asList(urls));
    }

    public PictureScroller(Collection<URL> urls)
    {

        images = new Gob[urls.size()];
        int i=0;
        for ( Iterator<URL> iterator = urls.iterator(); iterator.hasNext(); i++) {
            URL url = iterator.next();
            images[i] = new Gob(url);
        }

        addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent mouseEvent)
            {
                if (!isFocusOwner()) {
                    System.out.println("requestFocus()");
                    requestFocus(); // XXX work around bugs in java, or fvwm, not sure which
                }

            }
        });


        addKeyListener(new ClicheKeyListener());

    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(800, 600);
    }

    @Override
    public boolean isFocusable()
    {
        return true;
    }

    @Override
    public void paint(Graphics g)
    {
        super.paint(g);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        int i = vertical ? paintForVertical(g) : paintForHorizontal(g);

        {
            // start loading the next image off screen
            int j = (i + imageCursor) % images.length;
            images[j].getSize();
        }
    }

    private int paintForVertical(Graphics g)
    {
        int uncertain=0;
        int z = -scrolled;
        Dimension sz = getSize();
        Rectangle r = g.getClipBounds();
        int limit = geom.limit(sz, r);
        int i;
        for (i=0; i<images.length && uncertain<10 && z < limit; i++) {
            int j = (i + imageCursor) % images.length;
            Image img = images[j].getImage();

            if (img==null) {
                uncertain++;
                continue;
            }

            Dimension isz = images[j].getSize();
            if (null == isz) {
                if ( ! images[j].isBad())
                    uncertain++;
                continue;
            }

            Dimension adjusted = geom.fitToScreen(isz, sz);

            Point xy = geom.positionFor(z, sz, adjusted);

            g.drawImage(img, xy.x, xy.y, adjusted.width, adjusted.height, this);

            z += geom.advance(adjusted, padding); //+= adjusted.height + padding;
        }
        return i;
    }

    private int paintForHorizontal(Graphics g)
    {
        int uncertain=0;
        int x = -scrolled;
        Dimension sz = getSize();
        Rectangle r = g.getClipBounds();
        int maxX = Math.max(sz.width, r.x  + r.width);
        int i;
        for (i=0; i<images.length && uncertain<10 && x < maxX; i++) {
            int j = (i + imageCursor) % images.length;
            Image img = images[j].getImage();

            if (img==null) {
                uncertain++;
                continue;
            }

            Dimension isz = images[j].getSize();
            if (null == isz) {
                if ( ! images[j].isBad())
                    uncertain++;
                continue;
            }

            Dimension adjusted = geom.fitToScreen(isz, sz);

            int y = (sz.height - adjusted.height)/2;
            g.drawImage(img, x, y, adjusted.width, adjusted.height, this);

            x += adjusted.width + padding;
        }
        return i;
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h)
    {
        return super.imageUpdate(img, infoflags, x, y, w, h);
    }

    public void startScrolling(double speed)
    {
        this.pixelsPerSecond = speed;
        scrollTime = System.currentTimeMillis();

        Thread t = new ScrollThread();
        t.start();
    }

    public void pause()
    {
        paused=true;
    }

    protected synchronized void checkScrollProgress()
    {
        if (scrolled<0) {
            // this won't happen unless the user jumps backward

            int prev = previous(imageCursor, images.length);

            Dimension psz = images[prev].getSize();
            if (psz != null) {
                imageCursor = prev;
                Dimension adjusted = geom.fitToScreen(psz, getSize());
                scrolled += geom.advance(adjusted,padding);
                repaint(1);
            } else if (images[prev].isBad()) {
                imageCursor = prev;
            }


            return;
        }

        Dimension isz = images[imageCursor].getSize();

        if (isz != null) {
            Dimension adjusted = geom.fitToScreen(isz, getSize());
            if (scrolled > geom.advance(adjusted,0) ) {
                scrolled -= geom.advance(adjusted,padding);

                images[imageCursor].soften();

                incrementImageCursor();
            }
        } else if (images[imageCursor].isBad()) {
            incrementImageCursor();
        }
    }

    protected synchronized void incrementImageCursor()
    {
        imageCursor++;

        if (imageCursor >= images.length) {
            imageCursor = 0;
        }
        debugPrint2("imageCursor++;  == "+imageCursor);
    }

    private static void debugPrint(String message)
    {
//        System.out.println(message);
    }

    private static void debugPrint2(String message)
    {
//        System.out.println(message);
    }

    private static void debugPrint3(String message)
    {
//        System.out.println(message);
    }

    public static List<URL> parse(String[] argv)
    {
        List<URL> rval = new ArrayList<URL>(argv.length);

        for (int i = 0, argvLength = argv.length; i < argvLength; i++) {
            String arg = argv[i];

            if (arg.equals("-find")) {
                final List<URL> tmp = URLExtractor.recurseDirectory(new File(argv[++i]));
                Collections.sort(tmp, new URLExtractor.WackyURLComparator());
                rval.addAll(tmp);
            } else {
                URL u;
                try {
                    u = new URL(arg);
                } catch (MalformedURLException e) {
                    try {
                        u = new File(arg).toURI().toURL();
                    } catch (MalformedURLException e1) {
                        e1.printStackTrace();
                        continue;
                    }
                }

                if (arg.endsWith(".spider")) {

                    try {
                        rval.addAll(HTMLHarvester.extractSpider(u));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    rval.add(u);
                }
            }
        }

        return rval;
    }

    public static int previous(int n, int modulus)
    {
        return (n -1 + modulus)% modulus;
    }

    public static class Gob
        implements ImageObserver
    {
        URL u;
        Image img;
        SoftReference<Image> img_;
        private boolean bad = false;
        private long badTime;

        public Gob(URL url)
        {
            this.u = url;
        }


        public Image getImage()
        {
            if (img==null && img_ != null) {
                img = img_.get();
                if (img==null) {
                    debugPrint3("SoftReference had been recycled");
                }
            }

            if (img==null) {
                img = Toolkit.getDefaultToolkit().createImage(u);
                img_ = new SoftReference<Image>(img);
            }

            return img;
        }

        public void soften()
        {
            img = null;
        }

        public Dimension getSize()
        {
            if (isBad())
                return null;

            int w = getImage().getWidth(this);
            int h = getImage().getHeight(this);

            if (w>0 && h>0) {
                bad = false;
                return new Dimension(w,h);
            } else
                return null;
        }

        public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height)
        {
            if (0 != (infoflags & ImageObserver.ERROR)) {
                bad = true;
                badTime = System.currentTimeMillis();
                debugPrint2("bad image "+u);
            }

            return 0 == (infoflags & (ImageObserver.ALLBITS|ImageObserver.ERROR));
        }

        public boolean isBad()
        {
            return bad
                && 60*1000 + badTime > System.currentTimeMillis();
        }
    }

    public static void main(String[] argv)
    {
        cliche(parse(argv));
    }

    public static void cliche(Collection<URL> urls)
    {
        JFrame fr = new JFrame();
        final PictureScroller x = new PictureScroller(urls);
        x.scrolled = 0;
        fr.getContentPane().add(x);
        fr.pack();
        fr.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        fr.setVisible(true);

        x.startScrolling(40.0);
        x.pause();
    }

    private class ScrollThread
        extends Thread
    {
        protected int millis_fast = 16;

        @Override
        public void run()
        {
            PictureScroller psthis = PictureScroller.this;
            while (true) {
                // limit our refresh rate to 60Hz
                double nextTime = scrollTime + Math.max(millis_fast, 1000.0/pixelsPerSecond);

                long now = System.currentTimeMillis();
                synchronized (psthis) {
                    if (paused) {
                        checkScrollProgress();

                        try {
                            psthis.wait(scrolled<0 ? millis_fast : 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                        continue;
                    }
                    if (now >= nextTime) {
                        int dx = Math.max(1, (int) Math.floor( (now - scrollTime) * pixelsPerSecond/1000 ));
                        if (scrollTime==0)
                            dx=0; // bootstrap without insanity

                        debugPrint("scrolling by "+dx);

                        scrolled += dx;
                        scrollTime += dx * 1000.0 / pixelsPerSecond;

                        checkScrollProgress();

                        repaint(1);

                    } else {

                        long toSleep = (long) Math.ceil( nextTime - now);

                        debugPrint("sleeping "+toSleep+"ms");
                        try {
                            psthis.wait(toSleep);
                        } catch (InterruptedException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                }
            }
        }
    }

    public class ClicheKeyListener
        implements KeyListener
    {
        public ClicheKeyListener()
        {

        }

        public void keyTyped(KeyEvent e)
        {
        }

        public void keyPressed(KeyEvent e)
        {

        }

        public void keyReleased(KeyEvent e)
        {
            debugPrint2("key code = "+e.getKeyCode()+"; modifiers = 0x"+Integer.toHexString(e.getModifiers()));
            if (KeyEvent.VK_SPACE == e.getKeyCode()) {
                paused = !paused;
                scrollTime = System.currentTimeMillis();
                if (!paused) {
                    psNotifyAll();
                }
            } else if (KeyEvent.VK_OPEN_BRACKET == e.getKeyCode()) {
                paused = false;
                boolean shift = 0 != (e.getModifiers() & KeyEvent.SHIFT_MASK);
                pixelsPerSecond /= shift ? 2.0: 1.1;
            } else if (KeyEvent.VK_CLOSE_BRACKET == e.getKeyCode()) {
                paused = false;
                boolean shift = 0 != (e.getModifiers() & KeyEvent.SHIFT_MASK);
                pixelsPerSecond *= shift ? 2.0: 1.1;
            } else if (KeyEvent.VK_RIGHT== e.getKeyCode()) {
                scrolled += 0.9*geom.pgDown(getSize());
                repaint();
            } else if (KeyEvent.VK_LEFT== e.getKeyCode()) {

                if (false) {
                    scrolled = 0;
                    while (true) {
                        imageCursor = previous(imageCursor, images.length);
                        if (!images[imageCursor].isBad())
                            break;
                    }
                } else {
                    scrolled -= 0.9*geom.pgDown(getSize());
                    psNotifyAll();
                }
                repaint();
            } else if (KeyEvent.VK_H == e.getKeyCode()) {
                vertical = false;
                geom = new Horizontal();
                repaint(10);
            } else if (KeyEvent.VK_V == e.getKeyCode()) {
                vertical = true;
                geom = new Vertical();
                repaint(10);
            } else {
                return;
            }
            debugPrint2("new pixels/s = "+pixelsPerSecond);
        }

        public void psNotifyAll()
        {
            PictureScroller x = PictureScroller.this;
            synchronized(x) {
                x.notifyAll();
            }
        }

    }

    public static interface Geometry
    {

        Dimension fitToScreen(Dimension isz, Dimension sz);

        int limit(Dimension sz, Rectangle r);

        Point positionFor(int z, Dimension sz, Dimension adjusted);

        int advance(Dimension adjusted, int padding);

        int pgDown(Dimension size);
    }

    public static class Vertical
        implements Geometry
    {
        public Dimension fitToScreen(Dimension isz, Dimension sz)
        {
            int w2 = isz.width;
            int h2 = isz.height;

            if (w2 > sz.width) {
                h2 = h2 * sz.width / w2;
                w2 = sz.width;
            }

            return new Dimension(w2, h2);
        }

        public int limit(Dimension sz, Rectangle r)
        {
            return Math.min(sz.height, r.y + r.height);
        }

        public Point positionFor(int z, Dimension sz, Dimension adjusted)
        {
            return new Point((sz.width - adjusted.width)/2, z);
        }

        public int advance(Dimension adjusted, int padding)
        {
            return adjusted.height+padding;
        }

        public int pgDown(Dimension size)
        {
            return size.height;
        }
    }

    public static class Horizontal
        implements Geometry
    {
        public Dimension fitToScreen(Dimension isz, Dimension sz)
        {
            int h2 = sz.height;
            int w2 = isz.width * sz.height/ isz.height;

            if (w2 > sz.width) {
                w2 = sz.width;
                h2 = isz.height * sz.width / isz.width;
            }

            return new Dimension(w2, h2);
        }

        public int limit(Dimension sz, Rectangle r)
        {
            return Math.min(sz.width,  r.x+r.width);
        }

        public Point positionFor(int z, Dimension sz, Dimension adjusted)
        {
            return new Point( z, (sz.height - adjusted.height )/2);
        }

        public int advance(Dimension adjusted, int padding)
        {
            return adjusted.width +padding;
        }

        public int pgDown(Dimension size)
        {
            return size.width;
        }
    }
}

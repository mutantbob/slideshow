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

    public PictureScroller(URL[] urls)
    {
        images = new Gob[urls.length];
        for (int i = 0; i < urls.length; i++) {
            images[i] = new Gob(urls[i]);
        }
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

            Dimension adjusted = fitToScreen(isz, sz);

            int y = (sz.height - adjusted.height)/2;
            g.drawImage(img, x, y, adjusted.width, adjusted.height, this);

            x += adjusted.width + padding;
        }

        {
            // start loading the next image off screen
            int j = (i + imageCursor) % images.length;
            images[j].getSize();
        }
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h)
    {
        return super.imageUpdate(img, infoflags, x, y, w, h);
    }

    private Dimension fitToScreen(Dimension imgSize, Dimension componentSize)
    {
        int h2 = componentSize.height;
        int w2 = imgSize.width * componentSize.height/imgSize.height;

        if (w2 > componentSize.width) {
            w2 = componentSize.width;
            h2 = imgSize.height * componentSize.width / imgSize.width;
        }

        return new Dimension(w2, h2);
    }

    private void startScrolling(double speed)
    {
        this.pixelsPerSecond = speed;
        scrollTime = System.currentTimeMillis();

        Thread t = new ScrollThread();
        t.start();
    }

    protected synchronized void checkScrollProgress()
    {
        if (scrolled<0) {
            // this won't happen unless the user jumps backward

            int prev = previous(imageCursor, images.length);

            Dimension psz = images[prev].getSize();
            if (psz != null) {
                imageCursor = prev;
                Dimension adjusted = fitToScreen(psz, getSize());
                scrolled += adjusted.width+padding;
                repaint();
            } else if (images[prev].isBad()) {
                imageCursor = prev;
            }


            return;
        }

        Dimension isz = images[imageCursor].getSize();

        if (isz != null) {
            Dimension adjusted = fitToScreen(isz, getSize());
            if (scrolled > adjusted.width) {
                scrolled -= adjusted.width + padding;

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

    public static URL[] parse(String[] argv)
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
                rval.add(u);
            }
        }

        return rval.toArray(new URL[rval.size()]);
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
        JFrame fr = new JFrame();
        PictureScroller x = new PictureScroller(parse(argv));
        x.scrolled = 50;
        fr.getContentPane().add(x);
        fr.pack();
        fr.setVisible(true);

        x.addKeyListener(x.new ClicheKeyListener());

        x.startScrolling(40.0);
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
                        debugPrint("scrolling by "+dx);

                        scrolled += dx;
                        scrollTime += dx * 1000.0 / pixelsPerSecond;

                        checkScrollProgress();

                        repaint();

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
                scrolled += getSize().width;
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
                    scrolled -= getSize().width;
                    psNotifyAll();
                }
                repaint();
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
}

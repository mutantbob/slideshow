package com.purplefrog.slideshow;

import javax.swing.*;
import java.applet.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: thoth
 * Date: 11/6/12
 * Time: 5:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class SlideShow
    extends Applet
{

    SlideShowPane2 pane;

    public SlideShow()
    {
        pane = new SlideShowPane2();
        add(pane);
    }

    public static List<URL> maybeParseHTML(String spec, String imgMatch)
        throws IOException
    {
        try {
            URL u = new URL(spec);

            if (null != imgMatch) {
                URLConnection conn = u.openConnection();
                InputStream istr = conn.getInputStream();
                try {
                    if (conn instanceof HttpURLConnection) {
                        HttpURLConnection hconn = (HttpURLConnection) conn;
                        String mime = hconn.getContentType();
                        if (mime.toLowerCase().startsWith("text/html")) {
                            return URLExtractor.extractImages(istr, imgMatch, u);
                        }
                    }
                } finally {
                    istr.close();
                }
            }

            return Arrays.asList(u);
        } catch (MalformedURLException e) {
            // not a URL, probably just a path

            final URL u = new File(spec).toURI().toURL();

            if (null != imgMatch) {
                if (spec.endsWith(".html") || spec.endsWith(".htm")) {
                    InputStream istr = new FileInputStream(spec);
                    return URLExtractor.extractImages(istr, imgMatch, u);
                }
            }

            return Arrays.asList(u);
        }

    }

    public static URL urlOrFile(String spec)
        throws MalformedURLException
    {
        try {
            return new URL(spec);
        } catch (MalformedURLException e) {
            return new File(spec).toURI().toURL();
        }
    }

    public void init()
    {
        try {
            String whereUrlList = getParameter("imagelist");

            URL u = new URL(getDocumentBase(), whereUrlList);

            InputStream istr = u.openStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(istr));

            ArrayList<URL> newURLs = new ArrayList<URL>();
            String urlStr;
            while (null != (urlStr = r.readLine())) {
                newURLs.add(new URL(urlStr));
            }

            pane.setURLs(newURLs);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] argv)
    {

        Dimension	geometry = null;

        String imgMatch=null;

        int i;
        {

            for (i=0; i<argv.length; i++) {
                if (argv[i].equals("-geom")) {
                    final String errmsg = "-geom requires a widthxheight parameter (i.e. 800x600)";
                    if (i+1<argv.length) {
                        i++;
                        StringTokenizer st = new StringTokenizer(argv[i], "x");
                        if (!st.hasMoreTokens()) { throw new IllegalArgumentException(errmsg); }
                        String wstr = st.nextToken();
                        if (!st.hasMoreTokens()) { throw new IllegalArgumentException(errmsg); }
                        String hstr = st.nextToken();
                        if (st.hasMoreTokens()) { throw new IllegalArgumentException(errmsg); }
                        geometry = new Dimension(Integer.parseInt(wstr),
                            Integer.parseInt(hstr));
                    } else {
                        throw new IllegalArgumentException(errmsg);
                    }
                } else {
                    break;
                }
            }
        }

        JFrame f = new JFrame("SlideShow");

        SlideShowPane2 ss = new SlideShowPane2(new ArrayList(), 1,4);
        ss.setDoubleBuffered(true);

        f.getContentPane().add(ss);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.pack();
        if (geometry!=null) f.setSize(geometry);
        f.setVisible(true);

        {
            for (; i<argv.length; i++) {
                if ("-file".equals(argv[i])) {
                    i++;
                    String fname = argv[i];
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(fname));
                        String line;
                        while (null != (line=br.readLine())) {
                            try {
                                ss.addURL(urlOrFile(line));
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                } else if ("-html".equals(argv[i])) {
                    i++;
                    try {
                        List<URL> x = URLExtractor.parse(new URL(argv[i]));
                        ss.addURLs(x);
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                } else if ("-zip".equals(argv[i]))  {
                    i++;
                    try {
                        List<URL> x = URLExtractor.scanZip(argv[i]);
                        ss.addURLs(x);
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                } else if ("-find".equals(argv[i])) {
                    i++;
                    List<URL> x = URLExtractor.recurseDirectory(new File(argv[i]));
                    Collections.sort(x, new URLExtractor.WackyURLComparator());
                    ss.addURLs(x);
                } else if ("-spider".equals(argv[i])) {
		    i++;
		    try {
			ss.addURLs(HTMLHarvester.extractSpider(new URL(argv[i])));
		    } catch (IOException e) {
			e.printStackTrace();
		    }
                } else if ("-imgmatch".equals(argv[i])) {
                    i++; // supporting code is unfinished
                    imgMatch = argv[i];
                } else {

                    try {
                        ss.addURLs(maybeParseHTML(argv[i], imgMatch));
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            }
        }

    }

}

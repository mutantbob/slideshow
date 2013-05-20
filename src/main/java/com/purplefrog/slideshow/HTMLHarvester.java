package com.purplefrog.slideshow;

import com.purplefrog.stupidXML.*;
import org.apache.log4j.*;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 * Created with IntelliJ IDEA.
 * User: thoth
 * Date: 5/17/13
 * Time: 4:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class HTMLHarvester
{
    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(HTMLHarvester.class);

    public static Collection<URL> recurse( Pattern htmlPattern, Pattern imgPattern, URL... start)
    {
        return recurse(htmlPattern, imgPattern, Arrays.asList(start));
    }

    /**
     *
     * @param start the URL of the first HTML document to scan
     * @param htmlPattern a pattern for HTML documents to recurse into
     * @param imgPattern a pattern for images we want returned
     * @return
     */
    public static Collection<URL> recurse( Pattern htmlPattern, Pattern imgPattern, List<URL> start)
    {
        Set<URL> toParse = new HashSet<URL>(start);

        Map<URL, Catalog> parsed = new HashMap<URL, Catalog>();

        while (!toParse.isEmpty()) {
            Iterator<URL> iter = toParse.iterator();
            URL u = iter.next();
            iter.remove();

            logger.debug("harvesting from "+u);

            try {
                InputStream istr = u.openStream();
                Catalog cat = harvestFromHTML(new InputStreamReader(istr), u);

                parsed.put(u, cat);

                for (String aHref : cat.aHrefs) {
                    URL u2 = new URL(cat.baseURL, aHref);
                    if (parsed.containsKey(u2))
                        continue;
                    Matcher m = htmlPattern.matcher(u2.toString());
                    if (m.find()) {
                        logger.debug("spider to " + u2);
                        toParse.add(u2);
                    }
                }

            } catch (IOException e) {
                logger.warn("malfunction fetching URL "+start);
            }
        }

        Set<URL> repeats = new HashSet<URL>();
        List<URL> rval = new ArrayList<URL>();

        List<URL> htmlPages = new ArrayList<URL>(parsed.keySet());
        Collections.sort(htmlPages, new URLExtractor.WackyURLComparator());

        for (URL key : htmlPages) {
            Catalog cat = parsed.get(key);
            for (String imgSrc : cat.imgSrcs) {
                try {
                    URL u = new URL(cat.baseURL, imgSrc);
                    Matcher m = imgPattern.matcher(u.toString());
                    if (m.find()) {
                        logger.debug(cat.baseURL+" has acceptable image "+u);
                        if (repeats.add(u))
                            rval.add(u);
                    }
                } catch (MalformedURLException e) {
                    logger.warn("", e);
                }
            }
        }

        return rval;
    }

    public static Catalog harvestFromHTML(Reader r, URL baseURL)
        throws MalformedURLException
    {
        TagParser parser = new TagParser(r);

        List<String> imgSrcs = new ArrayList<String>();
        List<String> aHrefs = new ArrayList<String>();
        while (parser.hasNext()) {
            Object x = parser.next();
            if (x instanceof TagParser.Tag) {
                TagParser.Tag tag = (TagParser.Tag) x;

//                System.out.println(tag.name);

                if (tag.name .equalsIgnoreCase("img")) {
                    String src = tag.getAttributeValue("src");
                    imgSrcs .add(src);
                } else if (tag.name.equals("a")) {
                    String href = tag.getAttributeValue("href");
                    aHrefs.add(href);
                }

            }
        }
        return new Catalog(baseURL, imgSrcs, aHrefs);
    }

    public static void main(String[] argv)
    {
        BasicConfigurator.configure();

        List<URL> urls = new ArrayList<URL>();

        int q=21;
        if (argv.length ==0) {
            try {
                urls.add(new URL("http://drmcninja.com/archives/comic/" + q + "p1/"));
            } catch (MalformedURLException e) {
                logger.warn("", e);
            }
        } else {
        for (String s : argv) {
            try {
                urls.add(new URL(s));
            } catch (MalformedURLException e) {
                logger.warn("", e);
            }
        }
        }

        Collection<URL> x = recurse(Pattern.compile("^http://drmcninja.com/archives/comic/" + q + "p"), Pattern.compile("comics/\\d{4}-\\d\\d-\\S*\\.(gif|jpg|png)$"), urls);

        for (URL url : x) {
            System.out.println(url);
        }


        if (true) {
            PictureScroller.cliche(x);
        } else {
            JFrame f = new JFrame("SlideShow");

            SlideShowPane ss = new SlideShowPane(new ArrayList(), 1,4);
            ss.setDoubleBuffered(true);

            f.getContentPane().add(ss);
            f.pack();
            f.setVisible(true);

            ss.addURLs(x);
        }

    }

    public static Collection<URL> extractSpider(URL spiderURL)
        throws IOException
    {
        Properties props = new Properties();
        props.load(spiderURL.openStream());

        String htmlCriteria = props.getProperty("htmlCriteria");
        String imageCriteria = props.getProperty("imageCriteria");
        String start = props.getProperty("start");

        return recurse(Pattern.compile(htmlCriteria),
            Pattern.compile(imageCriteria),
            new URL(start));
    }
}

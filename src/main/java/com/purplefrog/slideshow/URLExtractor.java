/*
 * Copyright 2002
 * Robert Forsman <thoth@purplefrog.com>
 */
package com.purplefrog.slideshow;

import sun.security.util.*;

import java.math.*;
import java.net.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.regex.*;
import java.io.*;

public class URLExtractor
{

    public static List<URL> parse(URL u)
	    throws IOException
    {
	URLConnection conn = u.openConnection();
	String contentType = conn.getContentType();
	System.out.println("content-type: "+contentType);
	Reader r = new InputStreamReader(conn.getInputStream());
	String con = slurp(r);

	Pattern p = Pattern.compile("<a\\s+href=(?:\"([^\"]*)\"|(\\S*))", Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
	Matcher m = p.matcher(con);

	List<URL> rval = new ArrayList<URL>();
	while (m.find()) {
	    String href = m.group(1);
	    if (null == href)
		href = m.group(2);
	    rval.add(new URL(u, href));
	}

	return rval;
    }

    public static List<URL> extractImages(URL u, String rawPattern)
        throws IOException
    {
        URLConnection conn = u.openConnection();
        String contentType = conn.getContentType();

	System.out.println("content-type: "+contentType);
        return extractImages(conn.getInputStream(), rawPattern, u);
    }

    public static List<URL> extractImages(InputStream inputStream, String rawPattern, URL context)
        throws IOException
    {
        Reader r = new InputStreamReader(inputStream);
        String con = slurp(r);

        final ArrayList<URL> rval = new ArrayList<URL>();

        Pattern p = Pattern.compile("src=(\"(.*?" + rawPattern+".*?)\"" +
            "|(.*?" + rawPattern + ".*?))");
        Matcher m = p.matcher(con);
        while (m.find()) {
            String src = null==m.group(3) ? m.group(2) : m.group(3);

            if (src.length()>0) {
                 rval.add(new URL(context, src));
            }
        }

        return rval;
    }

    private static String slurp(Reader r)
	    throws IOException
    {
	StringWriter sw = new StringWriter();
	char[] buf = new char[64<<10];
	int n;
	while (true) {
	    n = r.read(buf);
	    if (n<1)
		break;
	    sw.write(buf, 0, n);
	}

	String con = sw.toString();
	return con;
    }


    public static void main(String[] argv)
	    throws IOException
    {
        URL u = String.class.getResource("String.class");
        System.out.println(u);
        List<URL> strings;
        if (true) {
            strings = extractImages(new URL(argv[0]), "\\.jpg");
        } else if (true) {
            strings = scanZip(argv[0]);
        } else {
            strings = parse(new URL(argv[0]));
        }
        for (URL s : strings) {
            System.out.println(s);
        }
    }

    public static List<URL> scanZip(String s) throws IOException
    {
        ZipFile zf = new ZipFile(s);
        Enumeration<? extends ZipEntry> it = zf.entries();

        URL base = new File(s).toURI().toURL();

        List<String> inZip = new ArrayList<String>();
        while (it.hasMoreElements()) {
            ZipEntry entry = it.nextElement();

            inZip.add(entry.getName());
        }

        Collections.sort(inZip, new WackyNameComparator());

        ArrayList<URL> rval = new ArrayList<URL>();
        for (String suffix : inZip) {
            URL u = new URL("jar:"+base+"!/"+suffix);
            rval.add(u);
        }
        return rval;
    }

    public static List<URL> recurseDirectory(File dir)
    {
        List<URL> rval = new ArrayList<URL>();

        File[] listing = dir.listFiles();
        if (null == listing)
            return rval;

        for (File file : listing) {
//            System.out.println("checking "+file);
            if (file.isDirectory()) {
//                System.out.println("directory");
                rval.addAll(recurseDirectory(file));
            } else if (file.isFile()) {
//                System.out.println("directory");
                try {
                    rval.add(file.toURI().toURL());
                } catch (MalformedURLException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            } else {
                System.out.println(file+" is weird");
            }
        }

        return rval;
    }

    public static class WackyURLComparator
        implements Comparator<URL>
    {
        WackyNameComparator cmp = new WackyNameComparator();

        public int compare(URL o1, URL o2)
        {
            return cmp.compare(o1.toString(), o2.toString());
        }
    }

    public static class WackyNameComparator
        implements Comparator<String>
    {
        public static Pattern DIGITS = Pattern.compile("\\d+");

        public int compare(String s1, String s2)
        {
            Matcher m1 = DIGITS.matcher(s1);
            Matcher m2 = DIGITS.matcher(s2);

            int i1 = 0;
            int i2 = 0;
            while (i1<s1.length() || i2<s2.length()) {

                String q1;
                String q2;
                BigInteger x1;
                BigInteger x2;
                int nexti1;
                int nexti2;
                if (m1.find(i1)) {
                    q1 =  s1.substring(i1, m1.start());
                    x1 =  new BigInteger(m1.group());
                    nexti1 = m1.end();
                } else {
                    q1 = s1.substring(i1);
                    x1 = BigInteger.valueOf(0);
                    nexti1 = s1.length();
                }
                if (m2.find(i2)) {
                    q2 = s2.substring(i2, m2.start());
                    x2 = new BigInteger(m2.group());
                    nexti2 = m2.end();
                } else {
                    q2 = s2.substring(i2);
                    x2 = BigInteger.valueOf(0);
                    nexti2 = s2.length();
                }

                int r = q1.toLowerCase().compareTo(q2.toLowerCase());
                if (r!=0)
                    return r;

                r = q1.compareTo(q2);
                if (r != 0)
                    return r;

                r = x1.compareTo(x2);
                if (r!=0)
                    return r;

                i1 = nexti1;
                i2 = nexti2;
            }

            return 0;
        }
    }
}

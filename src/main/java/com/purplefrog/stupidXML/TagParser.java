package com.purplefrog.stupidXML;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: thoth
 * Date: 5/17/13
 * Time: 3:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class TagParser
    implements Iterator<Object>
{
    private PushbackReader r;
    private Object cache;

    public TagParser(Reader r)
    {
        this.r = new PushbackReader(r);
    }

    public TagParser(PushbackReader r)
    {
        this.r = r;
    }

    public boolean hasNext()
    {
        try {
            if (null==cache)
                cache = manufactureForCache();
        } catch (IOException e) {
            throw new UndeclaredThrowableException(e, "malfunction reading from source");
        }

        return null!=cache;
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    public Object next()
    {
        try {
            if (cache==null)
                cache = manufactureForCache();
            if (cache==null)
                throw new NoSuchElementException();
            Object rval = cache;
            cache = null;
            return rval;
        } catch (IOException e) {
            throw new UndeclaredThrowableException(e, "malfunction reading from source");
        }
    }

    private Object manufactureForCache()
        throws IOException
    {
        int ch = r.read();

        if (ch=='<') {
            return parseRemainingTag();

        } else if (ch<0) {
            return null;

        } else {
            StringBuilder rval = new StringBuilder();
            rval.append((char)ch);

            while (true) {
                ch = r.read();
                if (ch<0)
                    return rval;

                if (ch=='<') {
                    r.unread(ch);
                    return rval;
                }

                rval.append((char)ch);
            }
        }
    }

    private Tag parseRemainingTag()
        throws IOException
    {
        eatWS();

        String name = getWord();

        if (name.startsWith("!--")) {
            return parseRemainingComment(name.substring(3));
        }

        List<Attribute> attrs = new ArrayList<Attribute>();

        while (true) {
            eatWS();
            int ch = r.read();
            if (ch<0)
                break;
            if (ch == '>')
                break;

            r.unread(ch);
            attrs.add(parseAttribute());
        }

        return new Tag(name, attrs);
    }

    public Tag parseRemainingComment(String partial)
        throws IOException
    {
        StringBuilder payload = new StringBuilder(partial);
        while (true) {
            int ch = r.read();
            if (ch<0) {
                break;
            } else if (ch== '>') {
                int len = payload.length();
                if (len>1
                    && '-'==payload.charAt(len-1)
                    &&'-'==payload.charAt(len-2)
                    ) {
                    payload.delete(len-2, len);
                    break;
                }
            }
            payload.append((char)ch);
        }
        return new Tag("!--", new Attribute("", payload.toString()));
    }

    private Attribute parseAttribute()
        throws IOException
    {
        String name = getWord();

        eatWS();
        int ch = r.read();
        if (ch<0)
            return new Attribute(name, null);

        if ('='==ch) {
            String value = parseAttributeValue();
            return new Attribute(name, value);
        } else {
            r.unread(ch);
            return new Attribute(name, null);
        }
    }

    private String parseAttributeValue()
        throws IOException
    {
        eatWS();
        int ch = r.read();

        if (ch=='"') {
            return parseQuotedString();
        } else if (ch=='>') {
            r.unread(ch);
            return "";
        } else {
            StringBuilder rval = new StringBuilder();
            rval.append((char)ch);
            while (true) {
                ch = r.read();
                if (ch<0)
                    break;
                if ('>'==ch || Character.isWhitespace(ch)) {
                    r.unread(ch);
                    break;
                }
            }

            return rval.toString();
        }
    }

    private String parseQuotedString()
        throws IOException
    {
        StringBuilder rval = new StringBuilder();

        while (true) {
            int ch = r.read();
            if (ch < 0 || ch == '"')
                break;

            rval.append((char)ch);
        }
        return rval.toString();
    }

    private String getWord()
        throws IOException
    {
        StringBuilder rval = new StringBuilder();
        while (true) {
            int ch = r.read();
            if (ch<0)
                break;

            if (ch=='=' || Character.isWhitespace(ch) || ch=='>') {
                r.unread(ch);
                break;
            }
            rval.append((char)ch);
        }

        return rval.toString();
    }

    private void eatWS()
        throws IOException
    {
        while (true) {
            int ch = r.read();
            if (ch<0)
                return;
            if (!Character.isWhitespace(ch)) {
                r.unread(ch);
                return;
            }
        }
    }

    public static class Tag
    {
        public String name;
        public List<Attribute> attrs;

        public Tag(String name, List<Attribute> attrs)
        {

            this.name = name;
            this.attrs = attrs;
        }

        public Tag(String name , Attribute... attrs)
        {
            this.name = name;
            this.attrs = Arrays.asList(attrs);
        }

        public String getAttributeValue(String attrName)
        {
            for (Attribute attr : attrs) {
                if (attrName.equalsIgnoreCase(attr.name))
                    return attr.value;
            }
            return null;
        }
    }

    public static class Attribute
    {
        public String name, value;

        public Attribute(String name, String value)
        {
            this.name = name;
            this.value = value;
        }
    }
}

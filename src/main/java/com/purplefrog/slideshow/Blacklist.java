package com.purplefrog.slideshow;

import java.io.*;
import java.lang.ref.*;
import java.net.*;
import java.security.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: thoth
 * Date: 11/6/12
 * Time: 1:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class Blacklist
{

    private Map<File, SoftReference<OneDir>> blacklists = new HashMap<File, SoftReference<OneDir>>();
    private OneDir remoteBlacklist;

    public boolean isBlacklisted(URI uri)
    {
        if (isLocalFile(uri)) {
            String path = uri.getPath();
            File f = new File(path);
            return getBlacklistForFile(f).isBlacklisted(f.getName());
        } else {
            return getRemoteBlacklist().isBlacklisted(uri.toString());
        }
    }

    public void addBlacklist(URI uri)
    {
        if (isLocalFile(uri)) {
            final File f = new File(uri.getPath());
            getBlacklistForFile(f).addBlacklist(f.getName());
        } else {
            getRemoteBlacklist().addBlacklist(uri.toString());
        }
    }

    public void unBlacklist(URI uri)
    {
        if (isLocalFile(uri)) {
            final File f = new File(uri.getPath());
            getBlacklistForFile(f).unBlacklist(f.getName());
        } else {
            getRemoteBlacklist().unBlacklist(uri.toString());
        }
    }

    public OneDir getBlacklistForFile(File f)
    {
        File p = f.getParentFile();
        return getBlacklistForDirectory(p);
    }

    private OneDir getRemoteBlacklist()
    {
        if (remoteBlacklist==null) {
            final String home_ = System.getProperty("user.home");

            File home = null;
            if (home_!=null) {
                home = new File(home_);
            }
            if (home==null || !home.exists()) {
                home = File.listRoots()[0];
            }
            File hashes = new File(home, ".slideshow.remote.blacklist");
            remoteBlacklist = new OneDir(hashes);
        }
        return remoteBlacklist;
    }

    public static boolean isLocalFile(URI uri)
    {
        final String scheme = uri.getScheme();
        return null==scheme || "file".equals(scheme);
    }

    private OneDir getBlacklistForDirectory(File directory)
    {
        try {
            directory = directory.getCanonicalFile();
        } catch (IOException e) {
            log("unable to canonicalize "+directory, e);
        }
        SoftReference<OneDir> rval_ = blacklists.get(directory);

        OneDir rval=null;
        if (rval_!=null) {
            rval = rval_.get();
        }

        if (null==rval) {
            File hashes = new File(directory, ".slideshow.blacklist");

            rval = new OneDir(hashes);
            blacklists.put(directory, new SoftReference<OneDir>(rval));
        }

        return rval;
    }

    public static void log(String msg, Exception e)
    {
        System.err.println(msg);
        if (null != e)
            e.printStackTrace();
    }

    //

    public static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    public static byte[] unhex(String hexdigits)
    {
        final byte[] rval = new byte[hexdigits.length() / 2];
        for (int i=0; i<rval.length; i++) {
            rval[i] = (byte) Integer.parseInt(hexdigits.substring(i*2, i*2+2), 16);
        }
        return rval;
    }

    public static String hex(byte[] stuff)
    {
        StringBuilder rval = new StringBuilder();
        for (byte b : stuff) {
            rval.append(HEX_DIGITS[0xf & (b >> 4)]);
            rval.append(HEX_DIGITS[0xf & b]);
        }

        return rval.toString();
    }

    //

    public static class OneDir
    {
        byte[] salt;
        List<byte[]> blacklisted;
        private File hashFile;

        public OneDir(File hashes)
        {
            hashFile = hashes;
            blacklisted = new ArrayList<byte[]>();
            try {
                FileReader r = new FileReader(hashes);
                BufferedReader br = new BufferedReader(r);

                salt = unhex(br.readLine());

                while (true) {
                    String line = br.readLine();
                    if (null==line)
                        break;

                    blacklisted.add(unhex(line));
                }
            } catch (FileNotFoundException e) {
                // no big deal
            } catch (IOException e) {
                log("malfunction parsing " + hashes, e);
            }

            if (null==salt) {
                salt = randomBytes(7);
            }
        }

        public static byte[] randomBytes(int count)
        {
            final byte[] rval = new byte[count];

            Random r = new SecureRandom();

            for (int i=0; i<count; i++) {
                rval[i] = (byte) r.nextInt(256);
            }
            return rval;
        }

        public boolean isBlacklisted(String name)
        {
            try {
                byte[] digest = digested(name);

                if (found(digest))
                    return true;
            } catch (Exception e) {
                log("malfunction digesting '" + name + "'", e);
            }

            return false;
        }

        public boolean found(byte[] digest)
        {
            for (byte[] bytes : blacklisted) {
                if (Arrays.equals(bytes, digest))
                    return true;
            }
            return false;
        }

        public byte[] digested(String name)
            throws NoSuchAlgorithmException, UnsupportedEncodingException
        {
            MessageDigest md = MessageDigest.getInstance("SHA-1");

            md.update(salt);
            md.update(name.getBytes("UTF-8"));
            return md.digest();
        }

        public void addBlacklist(String name)
        {
            byte[] digest = new byte[0];
            try {
                digest = digested(name);
            } catch (Exception e) {
                log("filed to digest '" + name + "'", e);
            }

            if (!found(digest)) {
                blacklisted.add(digest);
                try {
                    writeBlacklist();
                } catch (IOException e) {
                    log("malfunction writing blacklist", e);
                }
            }
        }

        public void writeBlacklist()
            throws IOException
        {
            File replacement = new File(hashFile+".new");
            FileWriter fw = new FileWriter(replacement);
            fw.write(hex(salt)+"\n");
            for (byte[] digest : blacklisted) {
                fw.write(hex(digest)+"\n");
            }
            fw.close();

            File old = new File(hashFile+".old");
            old.delete();
            if (hashFile.exists() && !hashFile.renameTo(old)) {
                log("unable to rename "+hashFile+" to "+old, null);
            }
            if (! replacement.renameTo(hashFile)) {
                log("Unable to rename "+replacement + " to " + hashFile, null);
            }

        }

        public void unBlacklist(String name)
        {
            try {
                byte[] digest = digested(name);
                for (Iterator<byte[]> iterator = blacklisted.iterator(); iterator.hasNext(); ) {
                    byte[] bytes = iterator.next();

                    if (Arrays.equals(digest, bytes)) {
                        iterator.remove();
                        writeBlacklist();
                        return;
                    }
                }
            } catch (Exception e) {
                log("malfunction removing "+name+" from blacklist", e);
            }
        }
    }

    public static void main(String[] argv)
        throws IOException
    {
        Blacklist bl = new Blacklist();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        while (true) {

            String line = br.readLine();

            if (null==line)
                break;

            try {

                if (line.startsWith("+")) {
                    String uri_ = line.substring(1);
                    bl.addBlacklist(new URI(uri_));
                } else {
                    boolean result = bl.isBlacklisted(new URI(line));
                    System.out.println(result);
                }
            } catch (URISyntaxException e) {
                log("bad URI '" + line + "'", e);
            }
        }
    }

}

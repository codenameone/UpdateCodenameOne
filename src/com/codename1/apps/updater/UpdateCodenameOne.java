/*
 * Copyright (c) 2012, Codename One and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Codename One designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *  
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Please contact Codename One through http://www.codenameone.com/ if you 
 * need additional information or have any questions.
 */

package com.codename1.apps.updater;

import com.codename1.impl.javase.JavaSEPort;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import java.util.prefs.Preferences;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A tool meant to update the main files of a Codename One project if they need
 * updating. It also dynamically fetches updated resources from the Codename One
 * website
 *
 * @author Shai Almog
 */
public class UpdateCodenameOne {
    private final String UPDATER_VERSION = "4";
    
    private static final long DAY = 24 * 60 * 60000;
    private final File PROP_FILE = new File(System.getProperty("user.home") + File.separator + ".codenameone" + File.separator + "UpdateStatus.properties");
    private final File LOCK_FILE = new File(System.getProperty("user.home") + File.separator + ".codenameone" + File.separator + "UpdateStatus.lock");
    private final File UPDATER_JAR = new File(System.getProperty("user.home") + File.separator + ".codenameone" + File.separator + "UpdateCodenameOne.jar");
    private final File SKIN_DIR = new File(System.getProperty("user.home") + File.separator + ".codenameone");
    
    private static final String KEY_JAVA_SE_JAR = "JavaSEJar";
    private static final String KEY_BUILD_CLIENT = "CodeNameOneBuildClientJar";
    private static final String KEY_CLDC = "CLDC11Jar";
    private static final String KEY_CODENAME_ONE_JAR = "CodenameOneJar";
    
    private static final String[] KEYS = {
        KEY_JAVA_SE_JAR,
        KEY_BUILD_CLIENT,
        KEY_CLDC,
        KEY_CODENAME_ONE_JAR,
        "CodenameOne_SRCzip",
        "designer",
        "guiBuilder"
    };
    
    private static final String SKIN_BASE_URL = "https://www.codenameone.com/OTA";
    private static final String SKIN_XML_URL = SKIN_BASE_URL + "/Skins.xml";
    
    private static final String BASE_URL = "https://www.codenameone.com/files/updates/";
    private static final String[] URLS = {
        BASE_URL + "JavaSE.jar",
        BASE_URL + "CodeNameOneBuildClient.jar",
        BASE_URL + "CLDC11.jar",
        BASE_URL + "CodenameOne.jar",
        BASE_URL + "CodenameOne_SRC.zip",
        BASE_URL + "designer_1.jar",
        BASE_URL + "guibuilder.jar"
    };
    
    private static final String[] RELATIVE_PATHS = {
        "JavaSE.jar",
        "CodeNameOneBuildClient.jar",
        "CLDC11.jar",
        "CodenameOne.jar",
        "CodenameOne_SRC.zip",
        "designer_1.jar",
        "guibuilder.jar"
    };
    
    private boolean checkAndDownloadFile(String url, String localVersion, String remoteVersion, 
            File destination) throws Exception {
        if(!localVersion.equals(remoteVersion)) {
            System.out.println("Updating...");
            if(!destination.getParentFile().exists()) {
                destination.getParentFile().mkdirs();
            }
            URL u = new URL(url);
            HttpURLConnection con = (HttpURLConnection)u.openConnection();
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 2.2; en-us; Nexus One Build/FRF91) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1");
            int length  = con.getContentLength();
            System.out.println("Downloading " + length + " bytes");
            byte[] data = new byte[length];
            try(DataInputStream dis = new DataInputStream(con.getInputStream())) {
                dis.readFully(data);
            }
            
            try(FileOutputStream dos = new FileOutputStream(destination)) {
                dos.write(data);
            } catch(IOException err) {
                // probably a file lock... we need to save the file as a "new" file
                String path = destination.getAbsolutePath();
                path = path.substring(0, path.length() - 3) + "new";
                System.out.println("File is locked writing new file as " + path);
                try(FileOutputStream dos = new FileOutputStream(path)) {
                    dos.write(data);
                } 
                if(!path.contains("UpdateCodenameOne")) {
                    // rename the file in a separate process
                    String javaCommand = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                    if(new File(javaCommand + "w.exe").exists()) {
                        javaCommand += "w.exe";
                    }
                    ProcessBuilder pb = new ProcessBuilder(javaCommand, "-classpath", 
                            UPDATER_JAR.getAbsolutePath(), 
                            "com.codename1.apps.updater.Renamer", 
                            path, destination.getAbsolutePath());
                    pb.redirectErrorStream(true);
                    pb.redirectOutput(File.createTempFile("UpdaterLog", ".log"));
                    pb.start();
                }
            }
            
            return true;
        }
        return false;
    }
    
    private void fetchSystemLibraries(Properties updateStatus) throws Exception {
        URL updateVersions = new URL(BASE_URL + "UpdateStatus.properties");
        HttpURLConnection con = (HttpURLConnection)updateVersions.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 2.2; en-us; Nexus One Build/FRF91) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1");
        Properties serverUpdateStatus = new Properties();
        try(InputStream is = con.getInputStream()) {
            serverUpdateStatus.load(is);            
        }
        
        checkAndDownloadFile(BASE_URL + "UpdateCodenameOne.jar", 
                    UPDATER_VERSION, 
                    serverUpdateStatus.getProperty("Updater", "0"), 
                    new File(PROP_FILE.getParentFile(), "UpdateCodenameOne.new"));
        
        for(int iter = 0 ; iter < KEYS.length ; iter++) {
            File destination = new File(PROP_FILE.getParentFile(), RELATIVE_PATHS[iter]);
            System.out.println("Checking: " + RELATIVE_PATHS[iter]);
            if(checkAndDownloadFile(URLS[iter], 
                    updateStatus.getProperty(KEYS[iter], "0"), 
                    serverUpdateStatus.getProperty(KEYS[iter], "0"), 
                    destination)) {
                updateStatus.setProperty(KEYS[iter], serverUpdateStatus.getProperty(KEYS[iter], "0"));
                updateStatus.setProperty("lastUpdate", "" + System.currentTimeMillis());
                try(FileOutputStream fos = new FileOutputStream(PROP_FILE)) {
                    updateStatus.store(fos, "");
                }
            }
        }
    }
    
    private Properties loadSystemUpdateStatus() throws Exception {
        Properties updateStatus = new Properties();
        if(PROP_FILE.exists()) {
            try(FileInputStream fis = new FileInputStream(PROP_FILE)) {
                updateStatus.load(fis);
            }
        } 
        
        return updateStatus;
    }
    
    private void runUpdate(File projectPath, boolean force) throws Exception {
        // check that a lock file exists but also use a 20 minute timeout to ignore an old lock file
        if(LOCK_FILE.exists() && LOCK_FILE.lastModified() < System.currentTimeMillis() + 20 * 60000) {
            System.out.println("Update process in progress lock file exists at: " + LOCK_FILE.getAbsolutePath());
            return;
        }
        LOCK_FILE.delete();
        LOCK_FILE.createNewFile();
        LOCK_FILE.deleteOnExit();
        Properties updateStatus = loadSystemUpdateStatus();
        String lastUpdate = updateStatus.getProperty("lastUpdate", "0");
        if(force || Long.parseLong(lastUpdate) < System.currentTimeMillis() - DAY) {
            fetchSystemLibraries(updateStatus);
        }
        
        File projectUpdateProperties = new File(projectPath, "Versions.properties");
        Properties projectVersions = new Properties();
        if(projectUpdateProperties.exists()) {
            try(FileInputStream fis = new FileInputStream(projectUpdateProperties)) {
                projectVersions.load(fis);
            }
        }
        
        File[] projectPaths = new File[] {
            new File(projectPath, "JavaSE.jar"),
            new File(projectPath, "CodeNameOneBuildClient.jar"),
            new File(projectPath, "lib" + File.separator + "CLDC11.jar"),
            new File(projectPath, "lib" + File.separator + "CodenameOne.jar"),
            new File(projectPath, "lib" + File.separator + "CodenameOne_SRC.zip")
        };
        
        boolean updateFile = false;
        for(int iter = 0 ; iter < projectPaths.length ; iter++) {
            String updatedVersion = updateStatus.getProperty(KEYS[iter], "0");
            if(!projectVersions.getProperty(KEYS[iter], "0").equals(updatedVersion)) {
                File from = new File(PROP_FILE.getParentFile(), RELATIVE_PATHS[iter]);
                if(!from.exists()) {
                    System.out.println("File not found: " + projectPaths[iter].getAbsolutePath());
                    continue;
                }
                System.out.println("Updating the file: " + projectPaths[iter].getAbsolutePath());
                byte[] data = new byte[(int)from.length()];
                try(DataInputStream dis = new DataInputStream(new FileInputStream(from))) {
                    dis.readFully(data);
                }
                try(FileOutputStream fos = new FileOutputStream(projectPaths[iter])) {
                    fos.write(data);
                }
                projectVersions.setProperty(KEYS[iter], updatedVersion);
                updateFile = true;
            }
        }
        
        long lastSkinUpdate = Long.parseLong(updateStatus.getProperty("lastSkinUpdate", "0"));
        if(lastSkinUpdate < System.currentTimeMillis() - DAY) {
            updateSkins();
            updateStatus.setProperty("lastSkinUpdate", "" + System.currentTimeMillis());
            updateFile = true;
        }
        
        if(updateFile) {
            System.out.println("Updated project files");
            try(FileOutputStream fos = new FileOutputStream(projectUpdateProperties)) {
                projectVersions.store(fos, "");
            }
        } else {
            System.out.println("Project files are up to date");
        }
    }
    
    private void updateSkins() throws Exception {
        URL skinXML = new URL(SKIN_XML_URL);
        HttpURLConnection con = (HttpURLConnection)skinXML.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 2.2; en-us; Nexus One Build/FRF91) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1");
        Preferences pref = Preferences.userNodeForPackage(JavaSEPort.class);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document d = null;
        try(InputStream is = con.getInputStream()) {
            d = db.parse(is);
        }
        NodeList skins = d.getElementsByTagName("Skin");
        for (int i = 0; i < skins.getLength(); i++) {
            Node skin = skins.item(i);
            NamedNodeMap attr = skin.getAttributes();
            String url = attr.getNamedItem("url").getNodeValue();
            if(!new File(SKIN_DIR, url).exists()) {
                continue;
            }

            int ver = 0;
            Node n = attr.getNamedItem("version");
            if(n != null){
                ver = Integer.parseInt(n.getNodeValue());
            }

            int currentVersion = Integer.parseInt(pref.get(url, "0"));
            if(currentVersion != ver) {
                HttpURLConnection skinDownload = (HttpURLConnection)new URL(SKIN_BASE_URL + url).openConnection();
                skinDownload.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; U; Android 2.2; en-us; Nexus One Build/FRF91) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1");
                int length  = skinDownload.getContentLength();
                System.out.println("Downloading skin " + url + " of " + length + " bytes");
                byte[] data = new byte[length];
                try(DataInputStream dis = new DataInputStream(skinDownload.getInputStream())) {
                    dis.readFully(data);
                }

                try(FileOutputStream dos = new FileOutputStream(new File(SKIN_DIR, url))) {
                    dos.write(data);
                } 
                pref.putInt(url, ver);
            }
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        boolean force = false;
        if(args.length > 1) {
            force = args[1].equalsIgnoreCase("force");
        }
        
        new UpdateCodenameOne().runUpdate(new File(args[0]), force);
    }
    
}

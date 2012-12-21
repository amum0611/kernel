/*
*  Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.server.util;

import org.wso2.carbon.server.LauncherConstants;
import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Utils {

    public static final String JAR_TO_BUNDLE_DIR =
            System.getProperty("java.io.tmpdir").endsWith(File.separator) ?
                    System.getProperty("java.io.tmpdir") + "jarsToBundles" :
                    System.getProperty("java.io.tmpdir") + File.separator + "jarsToBundles";
    private static File bundleBackupDir;
    
    static {
        File jarsToBundlesDir = new File(JAR_TO_BUNDLE_DIR);
        if (jarsToBundlesDir.exists()) {
            deleteDir(jarsToBundlesDir);
        }
    }

    public static File getCarbonComponentRepo() {
        String carbonRepo = System.getenv("CARBON_REPOSITORY");
        if (carbonRepo == null) {
            carbonRepo = System.getProperty("carbon.repository");
        }
        if (carbonRepo == null) {
            carbonRepo = System.getProperty("carbon.home") + File.separator + "repository";
        }
        String carbonComponentsRepository = carbonRepo + File.separator + "components";
        File componentRepo = new File(carbonComponentsRepository);
        if (!componentRepo.exists() && !componentRepo.mkdirs()) {
            System.err.println("Fail to create the directory: " + componentRepo.getAbsolutePath());
        }
        return componentRepo;
    }

    public static void printUsages() {
        String osName = System.getProperty("os.name");

        System.out.println("Usage: wso2server." + ((osName.toLowerCase().indexOf("win") == -1) ? "sh" : "bat ") +
                " [command] [system-properties]");
        System.out.println();
        System.out.println("command:");
        System.out.println("\t--debug <port> \tStart the server in remote debugging mode." +
                "\n\t\t\tport: The remote debugging port.");
        if (osName.toLowerCase().indexOf("win") == -1) {
            System.out.println("\t--start\t\tStart Carbon using nohup in the background");
            System.out.println("\t--stop\t\tStop the Carbon server process");
            System.out.println("\t--restart\tRestart the Carbon server process");

        }
        System.out.println("\t--cleanRegistry\t\t\tClean registry space. [CAUTION] All Registry data will be lost.");
        System.out.println("\t--version\t\t\tWhat version of the product are you running?");
        System.out.println();
        System.out.println("system-properties:");
        System.out.println("\t-DhttpPort=<httpPort>\t\tOverrides the HTTP port defined in the mgt-transports.xml file");
        System.out.println("\t-DhttpsPort=<httpsPort>\t\tOverrides the HTTPS port defined in the mgt-transports.xml file");
        System.out.println("\t-DosgiConsole=[port]\t\tStart Carbon with Equinox OSGi console. " +
                "\n\t\t\t\t\tIf the optional 'port' parameter is provided, a telnet port will be opened");
        System.out.println("\t-DosgiDebugOptions=[options-file]" +
                "\n\t\t\t\t\tStart Carbon with OSGi debugging enabled. " +
                "\n\t\t\t\t\tIf the optional 'options-file is provided, the OSGi debug options will be loaded from it.");
        System.out.println("\t-Dsetup\t\t\t\tClean the Registry & other configuration, recreate DB, re-populate the configuration, and start Carbon");
        System.out.println("\t-Dwso2.transports.xml=<transportXmlPath>\tLocation of the mgt-transports.xml file.");
        System.out.println("\t-DserverRoles=<roles>\t\tA comma separated list of roles. Used in deploying cApps");
        System.out.println("\t-Dcarbon.use.registry.repo\tUse registry based repository.");
        System.out.println("\t-DdisableHttpLog\t\tDisable HTTP access logging");
        System.out.println("\t-DapplyPatches\t\t\tUse this system property when there are patches to be applied/reverted to/from the system. ");
        System.out.println("\t\t\t\t\tDrop the patches to $CARBON_HOME/repository/componenets/patches directory and restart the system with this property. ");
        System.out.println("\t\t\t\t\tWhen you want to revert a patch, delete it from the patches directory and restart the system with this property");
        System.out.println("\t-DworkerNode\t\t\tSet this system property when starting as a worker node.");
        System.out.println("\t\t\t\t\tThis is required in clustered setups with master and worker nodes.");
        System.out.println();
    }

    public static String searchFor(final String target, String start) {
        FileFilter filter = new FileFilter() {
            public boolean accept(File candidate) {
                return candidate.getName().equals(target) ||
                        candidate.getName().startsWith(target + "_");
            }
        };
        File[] candidates = new File(start).listFiles(filter);
        if (candidates == null) {
            return null;
        }
        String[] arrays = new String[candidates.length];
        for (int i = 0; i < arrays.length; i++) {
            arrays[i] = candidates[i].getName();
        }
        int result = findMax(arrays);
        if (result == -1) {
            return null;
        }
        return candidates[result].getAbsolutePath().replace(File.separatorChar, '/') +
                (candidates[result].isDirectory() ? "/" : "");
    }

    static int findMax(String[] candidates) {
        int result = -1;
        Object maxVersion = null;
        for (int i = 0; i < candidates.length; i++) {
            String name = candidates[i];
            String version =
                    ""; // Note: directory with version suffix is always > than directory without version suffix
            int index = name.indexOf('_');
            if (index != -1) {
                version = name.substring(index + 1);
            }
            Object currentVersion = getVersionElements(version);
            if (maxVersion == null) {
                result = i;
                maxVersion = currentVersion;
            } else {
                if (compareVersion((Object[]) maxVersion, (Object[]) currentVersion) < 0) {
                    result = i;
                    maxVersion = currentVersion;
                }
            }
        }
        return result;
    }

    static int compareVersion(Object[] left, Object[] right) {

        int result = ((Integer) left[0]).compareTo((Integer) right[0]); // compare major
        if (result != 0) {
            return result;
        }

        result = ((Integer) left[1]).compareTo((Integer) right[1]); // compare minor
        if (result != 0) {
            return result;
        }

        result = ((Integer) left[2]).compareTo((Integer) right[2]); // compare service
        if (result != 0) {
            return result;
        }

        return ((String) left[3]).compareTo((String) right[3]); // compare qualifier
    }

    static Object[] getVersionElements(String version) {
        if (version.endsWith(".jar")) {
            version = version.substring(0, version.length() - 4);
        }
        Object[] result = {0, 0, 0, ""};
        StringTokenizer t = new StringTokenizer(version, ".");
        String token;
        int i = 0;
        while (t.hasMoreTokens() && i < 4) {
            token = t.nextToken();
            if (i < 3) {
                // major, minor or service ... numeric values
                try {
                    result[i++] = new Integer(token);
                } catch (Exception e) {
                    // invalid number format - use default numbers (0) for the rest
                    break;
                }
            } else {
                // qualifier ... string value
                result[i++] = token;
            }
        }
        return result;
    }

    public static String[] getArgs() {
        List<String> args = new ArrayList<String>();

        // Enable osgi console
        // First try to get from the System property
        String enableOsgiConsole = System.getProperty(LauncherConstants.ENABLE_OSGI_CONSOLE);

        if (enableOsgiConsole != null && !enableOsgiConsole.toLowerCase().equals("true")) {
            try {
                enableOsgiConsole =
                        "-console " + String.valueOf(Integer.parseInt(enableOsgiConsole));
            } catch (NumberFormatException ignored) {
                enableOsgiConsole = "-console";
            }
        } else if (enableOsgiConsole != null) {
            enableOsgiConsole = "-console";
        }


        if (enableOsgiConsole != null) {
            StringTokenizer tokenizer = new StringTokenizer(enableOsgiConsole, LauncherConstants.WS_DELIM);
            while (tokenizer.hasMoreTokens()) {
                String arg = tokenizer.nextToken();
                if (arg.startsWith("\"")) {
                    if (arg.endsWith("\"")) {
                        if (arg.length() >= 2) {
                            // strip the beginning and ending quotes
                            arg = arg.substring(1, arg.length() - 1);
                        }
                    } else {
                        String remainingArg = tokenizer.nextToken("\"");
                        arg = arg.substring(1) + remainingArg;
                        // skip to next whitespace separated token
                        tokenizer.nextToken(LauncherConstants.WS_DELIM);
                    }
                } else if (arg.startsWith("'")) {
                    if (arg.endsWith("'")) {
                        if (arg.length() >= 2) {
                            // strip the beginning and ending quotes
                            arg = arg.substring(1, arg.length() - 1);
                        }
                    } else {
                        String remainingArg = tokenizer.nextToken("'");
                        arg = arg.substring(1) + remainingArg;
                        // skip to next whitespace separated token
                        tokenizer.nextToken(LauncherConstants.WS_DELIM);
                    }
                }
                args.add(arg);
            }
            System.out.println("OSGi console has been enabled with options: " + enableOsgiConsole);
        }

        // Enable osgi debug
        // First try to get from the System property
        String enableOsgiDebug = System.getProperty(LauncherConstants.ENABLE_OSGI_DEBUG);
        if (enableOsgiDebug != null && enableOsgiDebug.toLowerCase().equals("true")) {
            String carbonRepo = System.getProperty(LauncherConstants.CARBON_HOME)+File.separator + "repository";
            enableOsgiDebug = carbonRepo + File.separator + "conf" + File.separator + "osgi-debug.options";
            args.add("-debug");
            args.add(enableOsgiDebug);
            System.out.println("OSGi debugging has been enabled with options: " + enableOsgiDebug);
        }

        return args.toArray(new String[]{});
    }

    /**
     * Here is the patch applying algorithm.
     * 1) Creates a patch0000 (if it does not exist) inside the patches directory. Backup all the bundles in the plugins
     * directory
     * 2) Then copy all the patchxxxx to the plugins folder.
     *
     * @param patchesDir
     * @param pluginsDir
     * @throws IOException
     */
    public static void applyPatches(File patchesDir, File pluginsDir) throws IOException {
        bundleBackupDir = new File(patchesDir, LauncherConstants.BUNDLE_BACKUP_DIR);
        if (!bundleBackupDir.exists()) {
            //We need to backup the plugins in the components/repository/plugins folder.
            FileUtils.copyDirectory(pluginsDir, bundleBackupDir);
        }
        //Now lets apply patches.
        copyPatches(patchesDir, pluginsDir);
    }

    /**
     * copying jars inside patches-xxx directories to components/plugins
     *
     * @param source folder which contains the patches.
     * @param target target
     * @throws java.io.IOException
     */
	private static void copyPatches(File source, File target) throws IOException {
		// Sorting patch folders.
		File[] files = source.listFiles();
		Arrays.sort(files);
		for (File file : files) {
			if (file.isDirectory()) {
				// if it's the patch0000 directory, copy all the files in
				// patch0000 directory to plugins.
				// bundlefileName verification is not required for patch0000
				if (file.equals(bundleBackupDir)) {
					FileUtils.copyDirectory(file, target);
				} else {
					// verify bundleFileName before copying the files to plugins
					File[] patchFiles = file.listFiles();
					for (File patch : patchFiles) {
						String patchFileName = verifyBundleFileName(patch);
						File copiedFile = new File(target, patchFileName);
						FileUtils.copyFile(patch, copiedFile, true);
					}
				}
			}
		}
	}

    /**
     * Create & return the bundle directory
     *
     * @param bundleDir The relative path of directory which contains the jars to be made into bundles
     * @return The bundle directory
     */
    public static File getBundleDirectory(String bundleDir) {
        //TODO The name of this utils class is incorrect - Refactor - Sameera
        String carbonHome = System.getProperty("carbon.home");

        if (carbonHome == null) {
            carbonHome = System.getenv("CARBON_HOME");
        }

        if (carbonHome == null || carbonHome.length() == 0) {
            throw new RuntimeException("CARBON_HOME not found");
        }
        File dir = new File(carbonHome, bundleDir);
        if (!dir.exists() && !dir.mkdirs()) {
            System.out.println("Fail to create the directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    /**
     * Create an OSGi bundle out of a JAR file
     *
     * @param jarFile         The jarfile to be bundled
     * @param targetDir       The directory into which the created OSGi bundle needs to be placed into.
     * @param mf              The bundle manifest file
     * @param extensionPrefix Prefix, if any, for the bundle
     * @throws java.io.IOException If an error occurs while reading the jar or creating the bundle
     */
    public static void createBundle(File jarFile,
                                    File targetDir,
                                    Manifest mf,
                                    String extensionPrefix) throws IOException {
        if (mf == null) {
            mf = new Manifest();
        }
        String exportedPackages = Utils.parseJar(jarFile);

        String fileName = jarFile.getName();
        fileName = fileName.replaceAll("-", "_");
        if (fileName.endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        String symbolicName = extensionPrefix + fileName;
        String pluginName = extensionPrefix + fileName + "_1.0.0.jar";
        File extensionBundle = new File(targetDir, pluginName);

        Attributes attribs = mf.getMainAttributes();
        attribs.putValue(LauncherConstants.MANIFEST_VERSION, "1.0");
        attribs.putValue(LauncherConstants.BUNDLE_MANIFEST_VERSION, "2");
        attribs.putValue(LauncherConstants.BUNDLE_NAME, fileName);
        attribs.putValue(LauncherConstants.BUNDLE_SYMBOLIC_NAME, symbolicName);
        attribs.putValue(LauncherConstants.BUNDLE_VERSION, "1.0.0");
        attribs.putValue(LauncherConstants.EXPORT_PACKAGE, exportedPackages);
        attribs.putValue(LauncherConstants.BUNDLE_CLASSPATH, ".," + jarFile.getName());

        Utils.createBundle(jarFile, extensionBundle, mf);
    }

    /**
     * Create an OSGi bundle out of a JAR file
     *
     * @param jarFile  The jarfile to be bundled
     * @param bundle   The bundle to be created
     * @param manifest The manifest file
     * @throws IOException If an error occurs while reading the jar or creating the bundle
     */
    public static void createBundle(File jarFile,
                                    File bundle,
                                    Manifest manifest) throws IOException {
        String extractedDirPath = JAR_TO_BUNDLE_DIR + File.separator +
                System.currentTimeMillis() + Math.random();
        File extractedDir = new File(extractedDirPath);
        if (!extractedDir.mkdirs()) {
            throw new IOException("Fail to create the directory: " + extractedDir.getAbsolutePath());
        }
        FileOutputStream mfos = null;
        FileOutputStream p2InfOs = null;
        try {
            Utils.copyFileToDir(jarFile, extractedDir);
            String metaInfPath = extractedDirPath + File.separator + "META-INF";
            if (!new File(metaInfPath).mkdirs()) {
                throw new IOException("Failed to create the directory: " + metaInfPath);
            }
            mfos = new FileOutputStream(metaInfPath + File.separator + "MANIFEST.MF");
            manifest.write(mfos);

            File p2InfFile = new File(metaInfPath + File.separator + "p2.inf");
            if (!p2InfFile.createNewFile()) {
                throw new IOException("Fail to create the file: " + p2InfFile.getAbsolutePath());
            }
            p2InfOs = new FileOutputStream(p2InfFile);
            p2InfOs.write("instructions.configure=markStarted(started:true);".getBytes());
            p2InfOs.flush();

            Utils.archiveDir(bundle.getAbsolutePath(), extractedDirPath);
            Utils.deleteDir(extractedDir);

        } finally {
            try {
                if (mfos != null) {
                    mfos.close();
                }
            } catch (IOException e) {
                System.out.println("Unable to close the OutputStream " + e.getMessage());
                e.printStackTrace();
            }

            try {
                if (p2InfOs != null) {
                    p2InfOs.close();
                }
            } catch (IOException e) {
                System.out.println("Unable to close the OutputStream " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Deletes all files and subdirectories under dir.
     * Returns true if all deletions were successful.
     * If a deletion fails, the method stops attempting to delete and returns false.
     */
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String aChildren : children) {
                    boolean success = deleteDir(new File(dir, aChildren));
                    if (!success) {
                        return false;
                    }
                }
            }
        }

        // The directory is now empty so delete it
        if (!dir.delete()) {
            dir.deleteOnExit();
        }
        return true;
    }

    /**
     * Archive a directory
     *
     * @param destArchive
     * @param sourceDir
     * @throws java.io.IOException
     */
    public static void archiveDir(String destArchive, String sourceDir) throws IOException {
        File zipDir = new File(sourceDir);
        if (!zipDir.isDirectory()) {
            throw new RuntimeException(sourceDir + " is not a directory");
        }

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destArchive));
        zipDir(zipDir, zos, sourceDir);
        zos.close();
    }

    protected static void zipDir(File zipDir, ZipOutputStream zos, String archiveSourceDir)
            throws IOException {
        //get a listing of the directory content
        String[] dirList = zipDir.list();
        byte[] readBuffer = new byte[40960];
        int bytesIn = 0;
        //loop through dirList, and zip the files
        for (String aDirList : dirList) {
            File f = new File(zipDir, aDirList);
            //place the zip entry in the ZipOutputStream object
            zos.putNextEntry(new ZipEntry(getZipEntryPath(f, archiveSourceDir)));
            if (f.isDirectory()) {
                //if the File object is a directory, call this
                //function again to add its content recursively
                zipDir(f, zos, archiveSourceDir);
                //loop again
                continue;
            }
            //if we reached here, the File object f was not a directory
            //create a FileInputStream on top of f
            FileInputStream fis = new FileInputStream(f);
            try {
                //now write the content of the file to the ZipOutputStream
                while ((bytesIn = fis.read(readBuffer)) != -1) {
                    zos.write(readBuffer, 0, bytesIn);
                }
            } finally {
                try {
                    //close the Stream
                    fis.close();
                } catch (IOException e) {
                    System.out.println("Unable to close the InputStream " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    protected static String getZipEntryPath(File f, String archiveSourceDir) {
        String entryPath = f.getPath();
        entryPath = entryPath.substring(archiveSourceDir.length() + 1);
        if (File.separatorChar == '\\') {
            entryPath = entryPath.replace(File.separatorChar, '/');
        }
        if (f.isDirectory()) {
            entryPath += "/";
        }
        return entryPath;
    }

    /**
     * Copies src file to dst directory.
     * If the dst directory does not exist, it is created
     */
    public static void copyFileToDir(File src, File dst) throws IOException {
        String dstAbsPath = dst.getAbsolutePath();
        String dstDir = dstAbsPath.substring(0, dstAbsPath.lastIndexOf(File.separator));
        File dir = new File(dstDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Fail to create the directory: " + dir.getAbsolutePath());
        }

        File file = new File(dstAbsPath + File.separator + src.getName());
        copyFile(src, file);
    }


    /**
     * Copies src file to dst file.
     * If the dst file does not exist, it is created
     */
    public static void copyFile(File src, File dst) throws IOException {
        OutputStream out = null;
        InputStream in = new FileInputStream(src);

        try {
            String dstAbsPath = dst.getAbsolutePath();
            String dstDir = dstAbsPath.substring(0, dstAbsPath.lastIndexOf(File.separator));
            File dir = new File(dstDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Fail to create the directory: " + dir.getAbsolutePath());
            }

            out = new FileOutputStream(dst);
            // Transfer bytes from in to out
            byte[] buf = new byte[10240];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                System.out.println("Unable to close the InputStream " + e.getMessage());
                e.printStackTrace();
            }

            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                System.out.println("Unable to close the OutputStream " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void extract(String archive, String extractDir) throws IOException {
        FileInputStream inputStream = new FileInputStream(archive);
        extractFromStream(inputStream, extractDir);
    }

    public static void extractFromStream(InputStream inputStream, String extractDir)
            throws IOException {
        ZipInputStream zin = null;
        try {
            File unzipped = new File(extractDir);
            // Open the ZIP file
            zin = new ZipInputStream(inputStream);
            if (!unzipped.mkdirs()) {
                throw new IOException("Fail to create the directory: " + unzipped.getAbsolutePath());
            }
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String entryName = entry.getName();
                File f = new File(extractDir + File.separator + entryName);

                if (entryName.endsWith("/") && !f.exists()) { // this is a
                    // directory
                    if (!f.mkdirs()) {
                        throw new IOException("Fail to create the directory: " + f.getAbsolutePath());
                    } else {
                        continue;
                    }
                }

                // This is a file. Carry out File processing
                int lastIndexOfSlash = entryName.lastIndexOf('/');
                String dirPath = "";
                if (lastIndexOfSlash != -1) {
                    dirPath = entryName.substring(0, lastIndexOfSlash);
                    File dir = new File(extractDir + File.separator + dirPath);
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IOException("Failed to create the directory: " + dir.getAbsoluteFile());
                    }
                }

                if (!f.isDirectory()) {
                    OutputStream out = new FileOutputStream(f);
                    byte[] buf = new byte[40960];

                    // Transfer bytes from the ZIP file to the output file
                    int len;
                    while ((len = zin.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.close();
                }
            }
        } catch (IOException e) {
            String msg = "Cannot unzip archive. It is probably corrupt";
            System.out.println(msg);
            throw e;
        } finally {
            try {
                if (zin != null) {
                    zin.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param jarFile jar file location
     * @return package name list separated by ","
     * @throws IOException IOException
     */
    public static String parseJar(File jarFile) throws IOException {
        List<String> exportedPackagesList = new ArrayList<String>();
        ZipInputStream zipInputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(jarFile)));
        List<ZipEntry> entries = populateList(zipInputStream);
        zipInputStream.close();

        for (ZipEntry entry : entries) {
            String path = entry.getName();
            if (!path.endsWith("/") && path.endsWith(".class")) {
                //This is package that contains classes. Thus, exportedPackagesList
                int index = path.lastIndexOf('/');
                if (index != -1) {
                    path = path.substring(0, index);
                    path = path.replaceAll("/", ".");
                    if (!exportedPackagesList.contains(path)) {
                        exportedPackagesList.add(path);
                    }
                }
            }
        }

        String[] packageArray =
                exportedPackagesList.toArray(new String[exportedPackagesList.size()]);
        StringBuffer exportedPackages = new StringBuffer();
        for (int i = 0; i < packageArray.length; i++) {
            exportedPackages.append(packageArray[i]);
            if (i != (packageArray.length - 1)) {
                exportedPackages.append(",");
            }
        }
        return exportedPackages.toString();
    }


    /**
     * @param zipInputStream zipInputStream
     * @return return zipetry map
     * @throws IOException IOException
     */
    private static List<ZipEntry> populateList(ZipInputStream zipInputStream) throws IOException {
        List<ZipEntry> listEntry = new ArrayList<ZipEntry>();
        while (zipInputStream.available() == 1) {
            ZipEntry entry = zipInputStream.getNextEntry();
            if (entry == null) {
                break;
            }
            listEntry.add(entry);
        }
        return listEntry;
    }

    public static class JarFileFilter implements FileFilter {
        public boolean accept(File pathname) {
            return pathname.getName().endsWith(".jar");
        }
    }

    /**
     * clearPrefixedSystemProperties clears System Properties by writing null properties in
     * the targetPropertyMap that match a prefix
     *
     * @param prefix            prefix
     * @param targetPropertyMap targetPropertyMap
     */
    public static void clearPrefixedSystemProperties(String prefix,
                                                     Map<String, String> targetPropertyMap) {
        for (Object o : System.getProperties().keySet()) {
            String propertyName = (String) o;
            if (propertyName.startsWith(prefix) && !targetPropertyMap.containsKey(propertyName)) {
                targetPropertyMap.put(propertyName, null);
            }
        }
    }

    /**
     * loadProperties is a convenience method to load properties from a servlet context resource
     *
     * @param filePath - path to load properties from
     * @return the properties
     */
    public static Properties loadProperties(String filePath) {
        Properties properties = new Properties();
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(filePath);
            properties.load(fileInputStream);

        } catch (MalformedURLException e) {
            // no url to load from
        } catch (IOException e) {
            // its ok if there is no file
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return properties;
    }

	/**
	 * Verifies bundle file name against the naming convention:
	 * bundleName_bundleVersion.jar
	 * 
	 * @param bundle file
	 * @return verified bundle-fileName
	 * @throws IOException
	 */
	public static String verifyBundleFileName(File file) throws IOException {
		String newFileName = file.getName();
		if (file.getName().endsWith(".jar")) {
			JarFile jar = new JarFile(file);
			Attributes attributes = jar.getManifest().getMainAttributes();
			String name = attributes.getValue(LauncherConstants.BUNDLE_SYMBOLIC_NAME);
			String version = attributes.getValue(LauncherConstants.BUNDLE_VERSION);
			if (name != null && version != null) {
				String bundleFileName = name + "_" + version + ".jar";
				// verify and correct the bundle filename
				if (!(file.getName().equals(bundleFileName))) {
					newFileName = bundleFileName;
				}
			}
		}
		return newFileName;
	}
    	
}

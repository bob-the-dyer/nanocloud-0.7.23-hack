/**
 * Copyright 2012 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.vicluster.telecontrol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Classpath {

	private static final Logger LOGGER = LoggerFactory.getLogger(Classpath.class);
	private static final String DIGEST_ALGO = "SHA-1";

	private static WeakHashMap<ClassLoader, List<ClasspathEntry>> CLASSPATH_CACHE = new WeakHashMap<ClassLoader, List<ClasspathEntry>>();
	private static WeakHashMap<URL, WeakReference<ClasspathEntry>> CUSTOM_ENTRIES = new WeakHashMap<URL, WeakReference<ClasspathEntry>>();

	public static synchronized List<ClasspathEntry> getClasspath(ClassLoader classloader) {
		List<ClasspathEntry> classpath = CLASSPATH_CACHE.get(classloader);
		if (classpath == null) {
			classpath = new ArrayList<Classpath.ClasspathEntry>();
            if (classloader instanceof URLClassLoader) {
                fillClasspath(classpath, ClasspathUtils.listCurrentClasspath(((URLClassLoader) classloader)));
            } else { //workaround for com.intellij.util.lang.UrlClassLoader which extends java.lang.ClassLoader, bob-the-dyer hack
                try {
                    List<URL> urls = (List<URL>) classloader.getClass().getMethod("getUrls").invoke(classloader);
                    URLClassLoader classLoaderAdapter = new URLClassLoader(urls.toArray(new URL[0]), classloader.getParent());
                    fillClasspath(classpath, ClasspathUtils.listCurrentClasspath((classLoaderAdapter)));
                } catch (Exception e) {
                    LOGGER.error("ClassLoader of type " + classloader.getClass().getName() + " doesn't have method getUrls()", e.getMessage());
                }
            }
			classpath = Collections.unmodifiableList(classpath);
			CLASSPATH_CACHE.put(classloader, classpath);
		}
		return classpath;
	}

	public static synchronized ClasspathEntry getLocalEntry(String path) throws IOException {
		try {
			URL url = new File(path).toURI().toURL();
			WeakReference<ClasspathEntry> wr = CUSTOM_ENTRIES.get(url);
			if (wr != null) {
				ClasspathEntry entry = wr.get();
				return entry;
			}
			ClasspathEntry entry = newEntry(url);
			CUSTOM_ENTRIES.put(url, new WeakReference<ClasspathEntry>(entry));
			return entry;
		} catch (MalformedURLException e) {
			throw new IOException(e);
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}

    private static void fillClasspath(List<ClasspathEntry> classpath, Collection<URL> urls) {
        for(URL url: urls) {
            if (!isIgnoredJAR(url)) {
                try {
                    ClasspathEntry entry = newEntry(url);
                    if (entry == null) {
                        LOGGER.warn("Cannot copy URL content: " + url.toString());
                        continue;
                    }
                    classpath.add(entry);
                }
                catch(Exception e) {
                    LOGGER.warn("Cannot copy URL content: " + url.toString(), e);
                    continue;
                }
            }
        }
    }

    private static boolean isIgnoredJAR(URL url) {
        try {
            if ("file".equals(url.getProtocol())) {
                File f = new File(url.toURI());
                if (f.isFile()) {
                    if (belongs(JRE_ROOT, url) || belongs(JDK_LIB, url)) {
                        // ignore JRE based jars, e.g. tools.jar
                        return true;
                    }
                    else if (f.getName().startsWith("surefire") && isManifestOnly(f)) {
                        // surefirebooter will interfere with classpath tweaking, exclude it
                        return true;
                    } else if (f.getName().startsWith("drozd-idea-plugin")) { // bob-the-dyer hack
                        return true;
                    }
                }
            }
        } catch (URISyntaxException e) {
            // ignore
        }
        return false;
    }

    private static boolean isManifestOnly(File f) {
        JarFile jar = null;
        try {
            jar = new JarFile(f);
            Enumeration<JarEntry> en = jar.entries();
            if (!en.hasMoreElements()) {
                return false;
            }
            JarEntry je = en.nextElement();
            if ("META-INF/".equals(je.getName())) {
                if (!en.hasMoreElements()) {
                    return false;
                }
                je = en.nextElement();
            }
            if (!"META-INF/MANIFEST.MF".equals(je.getName())) {
                return false;
            }
            return !en.hasMoreElements();
        } catch (IOException e) {
            return false;
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static URL JRE_ROOT = getJreRoot();
    private static URL JDK_LIB = getJdkLib();

    private static boolean belongs(URL base, URL derived) {
        // TODO not exactly correct, but should work
        return derived.toString().startsWith(base.toString());
    }

    private static URL getJavaHome() throws MalformedURLException {
        return new File(System.getProperty("java.home")).toURI().toURL();
    }

    // See Isolate
    static URL getJreRoot() {
        try {
            String jlo = ClassLoader.getSystemResource("java/lang/Object.class").toString();
            String root = jlo;
            if (root.startsWith("jar:")) {
                root = root.substring("jar:".length());
                int n = root.indexOf('!');
                root = root.substring(0, n);

                if (root.endsWith("/rt.jar")) {
                    root = root.substring(0, root.lastIndexOf('/'));
                    if (root.endsWith("/lib")) {
                        root = root.substring(0, root.lastIndexOf('/'));
                        return new URL(root);
                    }
                }
                /*bob-the-dyer hack, Mac OS jre 1.6*/
                if (root.endsWith("/classes.jar")) {
                    root = root.substring(0, root.lastIndexOf('/'));
                    if (root.endsWith("/Classes")) {
                        root = root.substring(0, root.lastIndexOf('/'));
                        return new URL(root);
                    }
                }
            }
            // fall back
            return getJavaHome();
        }
        catch(MalformedURLException e) {
            return null;
        }
    }

    static URL getJdkLib() {
        try {
            String jdkLib = System.getProperty("java.home");
            if (jdkLib.endsWith("jre")){
                jdkLib = jdkLib.substring(0, jdkLib.lastIndexOf("jre")).concat("lib");
                return new File(jdkLib).toURI().toURL();
            }
            if (System.getProperty("os.name").toLowerCase().startsWith("mac os") && //for preinstalled 1.6 jre on Mac OS, bob-the-dyer hack
                    jdkLib.startsWith(File.separator + "System") &&
                    System.getProperty("java.version").startsWith("1.6.0") &&
                    jdkLib.endsWith("Home")) {
                jdkLib = jdkLib.substring(0, jdkLib.lastIndexOf("Home")).concat("Classes");
                return new File(jdkLib).toURI().toURL();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    private static ClasspathEntry newEntry(URL url) throws IOException, URISyntaxException {
		ClasspathEntry entry = new ClasspathEntry();
		entry.url = url;
		File file = uriToFile(url.toURI());
		if (file.isFile()) {
			entry.file = file;
			entry.filename = file.getName();
		}
		else {
			String lname = file.getName();
			if ("classes".equals(lname)) {
				lname = file.getParentFile().getName();
			}
			if ("target".equals(lname)) {
				lname = file.getParentFile().getParentFile().getName();
			}
			lname += ".jar";
			entry.filename = lname;
			entry.data = ClasspathUtils.jarFiles(file.getPath());
			if (entry.data == null) {
				LOGGER.warn("Classpath entry is empty: " + file.getCanonicalPath());
				return null;
			}
		}
		return entry;
	}

	private static File uriToFile(URI uri) {
		if ("file".equals(uri.getScheme())) {
			if (uri.getAuthority() == null) {
				return new File(uri);
			}
			else {
				// try to fix broken windows network path
				String path = "file:////" + uri.getAuthority() + "/" + uri.getPath();
				try {
					return new File(new URI(path));
				} catch (URISyntaxException e) {
					return new File(uri);
				}
			}
		}
		return new File(uri);
	}

	public static class ClasspathEntry implements FileBlob {

		private URL url;
		private String filename;
		private String hash;
		private File file;
		private byte[] data;

		public URL getUrl() {
			return url;
		}

		@Override
		public String getFileName() {
			return filename;
		}

		@Override
		public synchronized String getContentHash() {
			if (hash == null) {
				hash = StreamHelper.digest(getData(), DIGEST_ALGO);
			}
			return hash;
		}

		@Override
		public synchronized InputStream getContent() {
			try {
				return (InputStream) (data != null ? new ByteArrayInputStream(data) : new FileInputStream(file));
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e.getMessage());
			}
		}

		@Override
		public long size() {
			return data != null ? data.length : file.length();
		}

		public synchronized byte[] getData() {
			if (data != null) {
				return data;
			}
			else {
				// do not cache jar content in memory
				return StreamHelper.readFile(file);
			}
		}
	}
}

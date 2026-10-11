/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package me.ahoo.cosid.accessor.scanner;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Finds the names of the top-level classes in a package and its sub-packages, in directories and jars of a class loader.
 *
 * <p>Jars are found through the package's resources and, since a jar may have no directory entries, also through the
 * class path: the URLs of each {@link URLClassLoader} up the parent chain and the {@code java.class.path} property.</p>
 */
final class ClassFinder {
    private static final String CLASS_SUFFIX = ".class";

    private ClassFinder() {
    }

    static Set<String> findTopLevelClasses(ClassLoader classLoader, String basePackage) throws IOException {
        String packagePath = basePackage.replace('.', '/');
        Set<String> classNames = new TreeSet<>();
        Set<Path> scannedJars = new HashSet<>();
        Enumeration<URL> resources = classLoader.getResources(packagePath);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            URLConnection connection = resource.openConnection();
            if (connection instanceof JarURLConnection jarConnection) {
                addIfFile(jarConnection.getJarFileURL(), scannedJars);
                findInJar(jarConnection, packagePath, classNames);
            } else if ("file".equals(resource.getProtocol())) {
                findInDirectory(toPath(resource), basePackage, classNames);
            }
        }
        for (Path jar : classPathJars(classLoader)) {
            if (scannedJars.add(jar)) {
                try (JarFile jarFile = new JarFile(jar.toFile())) {
                    findInJar(jarFile, packagePath, classNames);
                }
            }
        }
        return classNames;
    }

    private static Set<Path> classPathJars(ClassLoader classLoader) {
        Set<Path> jars = new LinkedHashSet<>();
        for (ClassLoader current = classLoader; current != null; current = current.getParent()) {
            if (current instanceof URLClassLoader urlClassLoader) {
                for (URL url : urlClassLoader.getURLs()) {
                    addIfJar(url, jars);
                }
            }
        }
        if (classLoader == ClassLoader.getSystemClassLoader() || isAncestor(classLoader, ClassLoader.getSystemClassLoader())) {
            String classPath = System.getProperty("java.class.path", "");
            for (String entry : classPath.split(File.pathSeparator)) {
                if (!entry.isEmpty() && entry.endsWith(".jar")) {
                    Path jar = Paths.get(entry).toAbsolutePath().normalize();
                    if (Files.isRegularFile(jar)) {
                        jars.add(jar);
                    }
                }
            }
        }
        return jars;
    }

    private static boolean isAncestor(ClassLoader child, ClassLoader ancestor) {
        for (ClassLoader current = child.getParent(); current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static void addIfJar(URL url, Set<Path> jars) {
        if (url.getPath().endsWith(".jar")) {
            addIfFile(url, jars);
        }
    }

    private static void addIfFile(URL url, Set<Path> files) {
        if (!"file".equals(url.getProtocol())) {
            return;
        }
        try {
            Path file = Paths.get(url.toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(file)) {
                files.add(file);
            }
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            // Not a local file: only reachable through the package resources.
        }
    }

    private static void findInJar(JarURLConnection connection, String packagePath, Set<String> classNames) throws IOException {
        connection.setUseCaches(false);
        try (JarFile jarFile = connection.getJarFile()) {
            findInJar(jarFile, packagePath, classNames);
        }
    }

    private static void findInJar(JarFile jarFile, String packagePath, Set<String> classNames) {
        String prefix = packagePath + "/";
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith(prefix) && isTopLevelClass(name)) {
                classNames.add(name.substring(0, name.length() - CLASS_SUFFIX.length()).replace('/', '.'));
            }
        }
    }

    private static void findInDirectory(Path directory, String basePackage, Set<String> classNames) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String relative = directory.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                if (isTopLevelClass(relative)) {
                    String simplePath = relative.substring(0, relative.length() - CLASS_SUFFIX.length());
                    classNames.add(basePackage + "." + simplePath.replace('/', '.'));
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static boolean isTopLevelClass(String path) {
        if (!path.endsWith(CLASS_SUFFIX)) {
            return false;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.indexOf('$') < 0 && !"package-info.class".equals(fileName) && !"module-info.class".equals(fileName);
    }

    private static Path toPath(URL resource) throws IOException {
        try {
            return Paths.get(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }
}

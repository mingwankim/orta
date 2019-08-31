package org.orta.core.type.locator;

/*-
 * #%L
 * orta-core
 * %%
 * Copyright (C) 2019 https://github.com/rts-orta
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the Team ORTA nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */



import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class ClassSourceLocator {

  @NonNull
  private static final Logger logger = LoggerFactory.getLogger(
          ClassSourceLocator.class);
  @NonNull
  private final ImmutableList<ClassPathLike> classPaths;
  private final ImmutableSet<Pattern> excludedPackages;

  public ClassSourceLocator(@NonNull Set<Path> classPaths, Set<String> excludedPackages,
                            boolean excludeJVM) throws IOException {
    this.excludedPackages = excludedPackages.stream()
            .map(Pattern::compile)
            .collect(ImmutableSet.toImmutableSet());

    ImmutableList.Builder<ClassPathLike> builder = ImmutableList.builder();

    builder.add(new JarClassPath(prepareWALAJar(), true));
    for (Path classPath : classPaths) {
      addClassPath(builder, classPath);
    }

    if (!excludeJVM) {
      builder.add(new InMemoryClassPath());
    }

    this.classPaths = builder.build();
  }

  private static void addClassPath(ImmutableList.Builder<ClassPathLike> builder,
                                   @NonNull Path classPath) throws IOException {
    if (Files.notExists(classPath)) {
      throw new IOException("The classpath " + classPath + " does not exist");
    }

    if (classPath.toString().endsWith(".jar")) {
      builder.add(new JarClassPath(classPath, false));
    } else if (Files.isDirectory(classPath)) {
      builder.add(new DirectoryPath(classPath));
    } else {
      logger.warn("Unrecognized classpath: {}", classPath);
    }
  }

  private static Path prepareWALAJar() throws IOException {
    Path jar = Files.createTempDirectory("asdf").resolve("wala.jar");
    try (InputStream is = ClassSourceLocator.class.getResourceAsStream("/primordial.jar")) {
      try (OutputStream os = Files.newOutputStream(jar)) {
        IOUtils.copy(is, os);
      }
    }

    return jar;
  }

  public void close() {
    for (ClassPathLike classPath : classPaths) {
      try {
        classPath.close();
      } catch (Exception e) {
      }
    }
  }

  @Nullable
  public ClassSource lookupSource(@NonNull String className) throws IOException {
    if (excludedPackages.stream().anyMatch(x -> x.matcher(className).matches())) {
      return null;
    }

    for (ClassPathLike classPath : classPaths) {
      ClassSource source = classPath.locateClassSource(className);
      if (source != null) {
        return source;
      }
    }

    if (className.equals("java/lang/Object")) {
      Class<?> cls = Object.class;
      try (InputStream s = cls.getResourceAsStream("Object.class")) {
        return new AsmClassSource(s);
      }
    }

    if (className.equals("com/ibm/wala/Malleable")) {
      return EmptyClassSource.get(className, null);
    }

    throw new NoSuchFileException(className);
  }

  private interface ClassPathLike extends AutoCloseable {

    @Nullable ClassSource locateClassSource(String className) throws IOException;
  }

  private static class InMemoryClassPath implements ClassPathLike {
    @Override
    public String toString() {
      return "InMemoryClassPath";
    }

    @Override
    @Nullable
    public ClassSource locateClassSource(String className) throws IOException {
      final String[] names = className.split("/");
      final String fileName = names[names.length - 1] + ".class";

      try {
        Class<?> cls = Class.forName(className.replace("/", "."));
        Package pkg = cls.getPackage();
        String title = pkg.getImplementationTitle();
        if (!Objects.equals(title, "Java Runtime Environment")) {
          // Runtime environment is not JDK8.
          if (!pkg.getName().startsWith("javax.") && !pkg.getName().startsWith("com.sun.") && !pkg
                  .getName().startsWith("java.") && !pkg.getName().startsWith("sun.") && !pkg.getName()
                  .startsWith("jdk.")) {
            return null;
          } else {
//            logger.warn("This package may not JCL: {}", cls.getName());
          }
        }

        try (InputStream s = cls.getResourceAsStream(fileName)) {
          return new AsmClassSource(s);
        }
      } catch (ClassNotFoundException ignored) {
      }

      return null;
    }

    @Override
    public void close() {

    }
  }

  private static class JarClassPath implements ClassPathLike {

    private final Path jarPath;
    private boolean shouldClaer;

    JarClassPath(Path jarPath, boolean shoudlClear) {
      this.jarPath = jarPath;
      this.shouldClaer = shoudlClear;
    }

    @Override
    public String toString() {
      return jarPath.toString();
    }

    @Override
    @Nullable
    public ClassSource locateClassSource(String className) throws IOException {
      final String fileName = className + ".class";
      try (JarFile file = new JarFile(jarPath.toFile())) {
        JarEntry entry = file.getJarEntry(fileName);
        if (entry == null) {
          return null;
        }

        try (InputStream jarStream = file.getInputStream(entry)) {
          return new AsmClassSource(jarStream);
        }
      }
    }

    @Override
    public void close() {
      if (shouldClaer) {
        try {
          Files.deleteIfExists(jarPath);
          Files.deleteIfExists(jarPath.getParent());
        } catch (IOException e) {
        }
      }
    }
  }

  private static class DirectoryPath implements ClassPathLike {

    private final Path path;

    DirectoryPath(Path path) {
      this.path = path;
    }

    @Override
    public String toString() {
      return path.toString();
    }

    @Override
    @Nullable
    public ClassSource locateClassSource(String className) throws IOException {
      final String fileName = className + ".class";
      Path classpath = path.resolve(fileName);

      if (Files.exists(classpath)) {
        try (InputStream stream = Files.newInputStream(classpath, StandardOpenOption.READ)) {
          return new AsmClassSource(stream);
        }
      }

      return null;
    }

    @Override
    public void close() {

    }
  }
}

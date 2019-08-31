package org.orta.diff;

/*-
 * #%L
 * orta-diff
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



import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

public class ClassHash {

  private static Gson gson = new Gson();

  public final String name;
  private final byte[] classLevelHash;
  private final Map<String, byte[]> methods = new HashMap<>();

  public ClassHash(Path path) throws IOException, NoSuchAlgorithmException {
    this(read(path));
  }

  public ClassHash(ClassNode node) throws NoSuchAlgorithmException {
    Preconditions.checkState(node.name.length() > 0);
    this.name = node.name.replace("/", ".");
    MessageDigest md = MessageDigest.getInstance("MD5");
    node.interfaces.sort(String::compareTo);
    for (String name : node.interfaces) {
      md.update(name.getBytes());
    }

    if (node.superName != null) {
      md.update(node.superName.getBytes());
    }

    updateDigestWithAnnotationNode(md, node.visibleAnnotations);
    updateDigestWithAnnotationNode(md, node.visibleTypeAnnotations);

    visitFields(node, md);

    node.methods.sort(ASMComparators.methodComparator);
    for (MethodNode m : node.methods) {
      boolean isVirtual = (m.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0;
      // Consider the signatures of virtual methods as the class header.
      // Private methods assumed to be invoked in the methods of this class only. Therefore, any changes in the private method influences to only the methods of the same class.
      // This assumption is useful not to overestimate the changes in a lambda within a method as a class-level change.
      // Static methods and constructors assumed to be independent to this class. Therefore, we do not need to consider the changes in these method as class-level changes.

      if (isVirtual && !m.name.endsWith("init>")) {
        md.update(m.name.getBytes());
        md.update(m.desc.getBytes());
        md.update(Ints.toByteArray(m.access));

        updateDigestWithAnnotationNode(md, m.visibleAnnotations);
        updateDigestWithAnnotationNode(md, m.visibleTypeAnnotations);
      }

      String signature = m.name + m.desc;
      methods.put(signature, InsnVisitor.compute(m).digest());
    }

    this.classLevelHash = md.digest();
  }

  private static ClassNode read(Path item) throws IOException {
    try (InputStream is = Files.newInputStream(item)) {
      ClassNode classNode = new ClassNode();
      ClassReader reader = new ClassReader(is);
      reader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return classNode;
    }
  }

  public static ClassHash load(InputStream is) {
    return gson.fromJson(new BufferedReader(new InputStreamReader(is)), ClassHash.class);
  }

  public static Map<String, ClassHash> loadSet(Path p) throws IOException {
    if (Files.notExists(p)) {
      throw new IOException(p.toString());
    }

    Map<String, ClassHash> prevHashes = new TreeMap<>();
    try (TarArchiveInputStream is = new TarArchiveInputStream(
            new GzipCompressorInputStream(
                    Files.newInputStream(p)
            ))) {

      TarArchiveEntry entry;
      while ((entry = is.getNextTarEntry()) != null) {
        if (!entry.isDirectory()) {
          ClassHash hash = ClassHash.load(is);
          prevHashes.put(hash.name, hash);
        }
      }
    }

    return prevHashes;
  }

  private static void updateDigestWithAnnotationNode(MessageDigest md,
                                                     List<? extends AnnotationNode> nodes) {
    if (nodes == null) {
      return;
    }

    if (nodes.size() > 1) {
      nodes.sort(ASMComparators.annotationComparator);
    }

    for (AnnotationNode node : nodes) {
      md.update(node.desc.getBytes());
      if (node.values != null) {
        Queue<List<Object>> list = new LinkedList<>();
        list.add(node.values);
        while (!list.isEmpty()) {
          @SuppressWarnings("nullness")
          List<Object> values = list.poll();
          for (Object value : values) {
            if (value instanceof String[]) {
              String[] v = (String[]) value;
              md.update(v[0].getBytes());
              md.update(v[1].getBytes());
            } else if (value instanceof AnnotationNode) {
              updateDigestWithAnnotationNode(md, ImmutableList.of((AnnotationNode) value));
            } else if (value instanceof List) {
              list.add((List) value);
            } else {
              String strValue = value.toString();
              md.update(strValue.getBytes());
            }
          }
        }
      }
    }
  }

  private static void visitFields(ClassNode node, MessageDigest md) {
    node.fields.sort(Comparator.comparing(x -> x.name));
    for (FieldNode f : node.fields) {
      md.update(Ints.toByteArray(f.access));
      md.update(f.desc.getBytes());
      md.update(f.name.getBytes());
      if (f.signature != null) {
        md.update(f.signature.getBytes());
      }

      Object value = f.value;
      if (value != null) {
        // Do not care what the value is.
        md.update(value.toString().getBytes());
      }

      updateDigestWithAnnotationNode(md, f.visibleAnnotations);
      updateDigestWithAnnotationNode(md, f.visibleTypeAnnotations);
    }
  }

  public static void dumpSets(Path out, Iterable<ClassHash> hashes) throws IOException {
    try {
      Files.createDirectories(out.getParent());
    } catch (IOException e) {
    }

    try (TarArchiveOutputStream os = new TarArchiveOutputStream(
            new GzipCompressorOutputStream(Files.newOutputStream(out)))) {
      os.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      for (ClassHash hash : hashes) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        hash.writeTo(buffer);
        String entryPath = hash.name.replace(".", "/");
        TarArchiveEntry entry = new TarArchiveEntry(entryPath);
        entry.setSize(buffer.size());
        os.putArchiveEntry(entry);
        os.write(buffer.toByteArray());
        os.closeArchiveEntry();
      }
    }
  }

  public void writeTo(OutputStream os) throws IOException {
    try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(os))) {
      gson.toJson(this, ClassHash.class, writer);
    }
  }

  public boolean hasClassLevelChanges(ClassHash prev) {
    Preconditions.checkArgument(prev.name.equals(name));
    return !Arrays.equals(classLevelHash, prev.classLevelHash);
  }

  public Set<String> computeChangedMethodFromPrevious(ClassHash prev) {
    Set<String> result = new HashSet<>(Sets.difference(prev.methods.keySet(), methods.keySet()));
    Set<String> common = Sets.intersection(prev.methods.keySet(), methods.keySet());

    for (String key : common) {
      if (!Arrays.equals(prev.methods.get(key), methods.get(key))) {
        result.add(key);
      }
    }

    return result;
  }
}

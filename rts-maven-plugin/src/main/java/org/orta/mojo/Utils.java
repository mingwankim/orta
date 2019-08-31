package org.orta.mojo;

/*-
 * #%L
 * rts-maven-plugin
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



import com.google.common.collect.HashBasedTable;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import experiments.commons.artifacts.ArtifactHandler;
import org.orta.diff.ClassHash;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static experiments.commons.artifacts.Artifact.Hashes;

public final class Utils {
  private Utils() {

  }

  public static Set<String> computeAffectedTests(Log log, Map<String, ClassHash> curHashes, File oldArtifactRoot, ArtifactHandler<Table<String, String, Set<String>>> reachablesArtifact, Set<String> testClasses) throws IOException {
    if (oldArtifactRoot == null) {
      return testClasses;
    }

    Path oldReach = reachablesArtifact.resolvePath(oldArtifactRoot);
    if (Files.notExists(oldReach)) {
      return testClasses;
    }

    Reachables prevReachables = new Reachables(oldReach, testClasses);
    Set<String> affected = new HashSet<>(Sets.difference(testClasses, prevReachables.testClasses));
    log.info("Compute impacted classes");
    StringJoiner j = new StringJoiner("\n");
    affected.forEach(j::add);
    log.info("Select newly added:\n" + j.toString());
    if (affected.size() != testClasses.size()) {
      Map<String, ClassHash> prevHashes = Hashes.loadArtifact(oldArtifactRoot);
      for (ClassHash prev : prevHashes.values()) {
        String className = prev.name;
        ClassHash cur = curHashes.remove(className);
        if (cur == null || cur.hasClassLevelChanges(prev)) {
          Set<String> impacted = prevReachables.classes.get(className);
          log.info("Found class-level change" + className);
          StringJoiner joiner = new StringJoiner("\n");
          for (String s : impacted) {
            affected.add(s);
            joiner.add(s);
          }
          log.info("Affects to:\n" + joiner.toString());
        } else {
          Set<String> changedSignatures = cur.computeChangedMethodFromPrevious(prev);
          if (!changedSignatures.isEmpty()) {
            log.info("Found method-level change in " + className + "\n" + changedSignatures);
            for (String selector : changedSignatures) {
              Set<String> aff = prevReachables.methods.get(className, selector);
              if (aff != null) {
                affected.addAll(aff);
              }
            }
          }
        }
      }
    }

    return affected;
  }

  private static class Reachables {
    SetMultimap<String, String> classes = MultimapBuilder.hashKeys().hashSetValues().build();
    Table<String, String, Set<String>> methods = HashBasedTable.create();
    Set<String> testClasses = new HashSet<>();

    Reachables(Path p, Set<String> currentTestClasses) throws IOException {
      try (TarArchiveInputStream is = new TarArchiveInputStream(
              new GzipCompressorInputStream(
                      Files.newInputStream(p)
              ))) {

        ArchiveEntry entry;
        while ((entry = is.getNextEntry()) != null) {
          String testName = entry.getName();
          if (!currentTestClasses.contains(testName)) {
            continue;
          }

          testClasses.add(testName);
          JsonReader r = new JsonReader(new InputStreamReader(is));
          r.beginObject();
          while (r.peek() != JsonToken.END_OBJECT) {
            String className = r.nextName();
            classes.put(className, testName);
            r.beginArray();
            while (r.peek() != JsonToken.END_ARRAY) {
              String selector = r.nextString();
              Set<String> tests = methods.get(className, selector);
              if (tests == null) {
                tests = new HashSet<>();
                methods.put(className, selector, tests);
              }

              tests.add(testName);
            }
            r.endArray();
          }
          r.endObject();
        }
      }
    }
  }
}

package experiments.commons.artifacts;

/*-
 * #%L
 * exp-commons
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


import com.google.common.collect.Table;
import org.orta.diff.ClassHash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface Artifact {
  ArtifactHandler<Map<String, ClassHash>> Hashes = new HashArtifactHandler("hashes.tar.gz");
  ArtifactHandler<Path> FailedBuildLog = new LogArtifactHandler("error.log");
  ArtifactHandler<Path> PassedBuildLog = new LogArtifactHandler("passed.log");
  ArtifactHandler<Set<String>> ClassPaths = new SetArtifactHandler.PathSetArtifactHandler("classpaths.list");
  ArtifactHandler<Set<String>> TestClasses = new SetArtifactHandler.ClassSetArtifactHandler("testclasses.list");
  ArtifactHandler<Set<String>> HyRTS = new SetArtifactHandler.ClassSetArtifactHandler("hyrts.list");
  ArtifactHandler<int[]> Edges = new ArtifactHandler<int[]>("edges") {

    @Override
    public boolean verify(Path filePath) throws IOException {
      try (Stream<String> lines = Files.lines(filePath)) {
        Iterator<String> line = lines.iterator();
        if (!line.hasNext()) return false;
        try {
          Integer.parseUnsignedInt(line.next());
          if (!line.hasNext()) return false;
          Integer.parseUnsignedInt(line.next());
          if (!line.hasNext()) return false;
          Integer.parseUnsignedInt(line.next());
          if (!line.hasNext()) return false;
          Integer.parseUnsignedInt(line.next());
          if (line.hasNext()) return false;
        } catch (NumberFormatException e) {
          logger.error("Unexpected number", e);
          return false;
        }
      }

      return true;
    }

    @Override
    protected void dumpArtifact(int[] obj, Path artifactPath) throws IOException {
      if (obj.length != 4) {
        throw new IllegalArgumentException();
      }

      try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(artifactPath))) {
        writer.println(obj[0]);
        writer.println(obj[1]);
        writer.println(obj[2]);
        writer.println(obj[3]);
      }
    }

    @Override
    protected int[] readArtifact(Path artifactsRoot) throws IOException {
      int[] result = new int[4];
      try (BufferedReader reader = Files.newBufferedReader(artifactsRoot)) {
        result[0] = Integer.parseInt(reader.readLine().trim());
        result[1] = Integer.parseInt(reader.readLine().trim());
        result[2] = Integer.parseInt(reader.readLine().trim());
        result[3] = Integer.parseInt(reader.readLine().trim());
      }

      return result;
    }
  };

  static void dumpSet(Set<String> elements, Path dumpPath, Predicate<String> p) throws IOException {
    try (PrintWriter writer = new PrintWriter(Files.newOutputStream(dumpPath))) {
      for (String element : elements) {
        writer.println(element);
      }
    }
  }

  static boolean isNotClassName(String l) {
    return !l.isEmpty() && !l.matches("([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*");
  }

  interface SeparateArtifact extends Artifact {
    ArtifactHandler<Set<String>> SelectedTests = new SetArtifactHandler.ClassSetArtifactHandler("sep_selected.list");
    ArtifactHandler<Set<String>> ExcludedTests = new SetArtifactHandler.ClassSetArtifactHandler("sep_excluded.list");
    ArtifactHandler<Set<String>> AffectedTests = new SetArtifactHandler.ClassSetArtifactHandler("sep_affected.list");
    ArtifactHandler<Table<String, String, Set<String>>> Reachables = new ReachablesArtifactHandler("separate.tar.gz");
  }

  interface SingleArtifact extends Artifact {
    ArtifactHandler<Set<String>> SelectedTests = new SetArtifactHandler.ClassSetArtifactHandler("selected.list");
    ArtifactHandler<Set<String>> AffectedTests = new SetArtifactHandler.ClassSetArtifactHandler("single_affected.list");
    ArtifactHandler<Set<String>> ExcludedTests = new SetArtifactHandler.ClassSetArtifactHandler("excluded.list");
    ArtifactHandler<Table<String, String, Set<String>>> Reachables = new ReachablesArtifactHandler("single.tar.gz");
  }
}

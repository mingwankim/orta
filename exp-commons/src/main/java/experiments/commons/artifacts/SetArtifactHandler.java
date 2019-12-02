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


import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

abstract class SetArtifactHandler extends ArtifactHandler<Set<String>> {
  SetArtifactHandler(String filename) {
    super(filename);
  }

  protected abstract boolean isCorrupted(String line);

  @Override
  public boolean verify(Path filePath) throws IOException {
    try (Stream<String> lines = Files.lines(filePath).flatMap(x -> Arrays.stream(x.split(",")))) {
      Iterator<String> iterator = lines.iterator();
      while (iterator.hasNext()) {
        String line = iterator.next();
        if (isCorrupted(line)) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  protected void dumpArtifact(Set<String> elements, Path artifactPath) throws IOException {
    Artifact.dumpSet(elements, artifactPath, this::isCorrupted);
  }

  @Override
  protected Set<String> readArtifact(Path artifactPath) throws IOException {
    try (Stream<String> lines = Files.lines(artifactPath)) {
      return lines.flatMap(x -> Arrays.stream(x.split(",")))
              .map(String::trim)
              .peek(x -> {
                if (isCorrupted(x)) {
                  throw new IllegalStateException(x);
                }
              }).filter(x -> !x.isEmpty())
              .collect(ImmutableSet.toImmutableSet());
    }
  }

  static class ClassSetArtifactHandler extends SetArtifactHandler {

    ClassSetArtifactHandler(String filename) {
      super(filename);
    }

    @Override
    protected boolean isCorrupted(String line) {
      return Artifact.isNotClassName(line);
    }
  }

  static class PathSetArtifactHandler extends SetArtifactHandler {

    PathSetArtifactHandler(String filename) {
      super(filename);
    }

    @Override
    protected boolean isCorrupted(String line) {
      try {
        // Current experiments do not include third-party libraries.
        Paths.get(line);
        return false;
//                return Files.notExists(Paths.get(line));
      } catch (InvalidPathException e) {
        return true;
      }

    }
  }
}

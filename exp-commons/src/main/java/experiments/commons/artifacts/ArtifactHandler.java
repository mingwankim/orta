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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class ArtifactHandler<T> {
  protected static final Logger logger = LoggerFactory.getLogger(Artifact.class);
  private final String filename;

  ArtifactHandler(String filename) {
    this.filename = filename;
  }

  protected abstract boolean verify(Path filePath) throws IOException;

  public void acceptArtifact(T obj, Path artifactRoot) throws IOException {
    Path artifactPath = resolvePath(artifactRoot);
    Files.deleteIfExists(artifactPath);
    dumpArtifact(obj, artifactPath);
  }

  protected abstract void dumpArtifact(T obj, Path artifactFpath) throws IOException;

  public boolean isReady(Path artifactRoot) throws IOException {
    Path artifactPath = resolvePath(artifactRoot);
    if (Files.notExists(artifactPath)) return false;
    return verify(artifactPath);
  }

  public Path resolvePath(Path artifactPath) {
    return artifactPath.resolve(filename);
  }

  public Path resolvePath(File artifactFile) {
    return resolvePath(artifactFile.toPath());
  }

  public T loadArtifact(Path artifactsRoot) throws IOException {
    Path artifactPath = resolvePath(artifactsRoot);
    if (Files.notExists(artifactPath)) return null;
    try {
      return readArtifact(artifactPath);
    } catch (Throwable e) {
      e.printStackTrace();
      return null;
    }
  }

  protected abstract T readArtifact(Path artifactsRoot) throws IOException;

  public void acceptArtifact(T obj, File commitPathFile) throws IOException {
    acceptArtifact(obj, commitPathFile.toPath());
  }

  public void deleteIfExists(File artifactRoot) {
    try {
      Files.deleteIfExists(resolvePath(artifactRoot.toPath()));
    } catch (IOException e) {

    }
  }

  public T loadArtifact(File artifactFile) throws IOException {
    if (artifactFile == null) return null;
    else return loadArtifact(artifactFile.toPath());
  }

}

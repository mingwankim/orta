package experiments.utils;

/*-
 * #%L
 * experiments
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



import experiments.commons.artifacts.Artifact;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Commit implements Comparable<Commit> {
  private static final Logger logger = LoggerFactory.getLogger(Commit.class);
  private final RevCommit c;
  private final Path artifactsRoot;

  public Commit(Path projRootPath, RevCommit c) {
    this.c = c;
    this.artifactsRoot = getArtifactRoot(projRootPath, c.name());
    try {
      Files.createDirectories(artifactsRoot);
    } catch (IOException e) {
    }
  }

  public static Path getArtifactRoot(Path projPath, String c) {
    return projPath.resolve(c);
  }

  public RevCommit getRevCommit() {
    return c;
  }

  @Override
  public int hashCode() {
    return c.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (obj instanceof Commit) {
      return ((Commit) obj).c.equals(c);
    }

    return false;
  }

  @Override
  public int compareTo(Commit rev) {
    return c.getCommitTime() - rev.c.getCommitTime();
  }

  @Override
  public String toString() {
    return c.name();
  }

  public Status checkArtifacts() throws IOException {
    Path artifactRoot = getArtifactsRoot();
    if (Artifact.FailedBuildLog.isReady(artifactRoot)) {
      return Status.Unavailable;
    }

    if (!Artifact.PassedBuildLog.isReady(artifactRoot)) {
      return Status.Unknown;
    }

    if (!Artifact.ClassPaths.isReady(artifactRoot)) {
      return Status.Unknown;
    }

    if (!Artifact.Hashes.isReady(artifactRoot)) {
      return Status.Unknown;
    }

    if (!Artifact.TestClasses.isReady(artifactRoot)) {
      return Status.Unknown;
    }

    return Status.Available;
  }

  public Path getArtifactsRoot() {
    return artifactsRoot;
  }

  public enum Status {
    Unknown,
    Available,
    Unavailable
  }
}

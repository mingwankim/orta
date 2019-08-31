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



import experiments.commons.PathUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class GitHelper implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(GitHelper.class);
  private final Path gitHome;
  private final Path projRootPath;
  private final Map<RevCommit, Commit> commits = new HashMap<>();
  private final String url;
  private final String projName;
  private Repository repo;

  public GitHelper(String projName, Path projRoot, String url)
          throws IOException {
    this.gitHome = Files.createTempDirectory("rtscg_git");
    this.projRootPath = projRoot != null ? projRoot : gitHome;
    this.url = url;
    this.projName = projName;
  }

  private Repository getRepo() throws GitHelperException {
    if (this.repo == null) {
      cloneRepository();
    }

    return repo;
  }

  public void cloneRepository()
          throws GitHelperException {
    try {
      try (Git git = Git.cloneRepository()
              .setDirectory(gitHome.toFile())
              .setURI(url)
              .setNoCheckout(true)
              .call()) {
        repo = git.getRepository();
      }
    } catch (GitAPIException e) {
      throw new GitHelperException(e);
    }
  }

  public Commit getOrCreateCommit(@NonNull String c) throws GitHelperException {
    return getOrCreateCommit(parseCommit(c));
  }

  Commit getOrCreateCommit(@NonNull RevCommit c)
          throws GitHelperException {
    RevCommit rc = parseCommit(c);
    return commits.computeIfAbsent(rc, cc -> new Commit(projRootPath, cc));
  }

  public Iterator<Commit> iterateParents(Commit c) throws GitHelperException {
    Iterator<RevCommit> iterator = Arrays.asList(parseCommit(c.getRevCommit()).getParents())
            .iterator();
    return new Iterator<Commit>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public Commit next() {
        try {
          return getOrCreateCommit(iterator.next());
        } catch (GitHelperException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  public MavenHelper checkout(String commitString, Path excPath, MavenPluginType type) throws GitHelperException, IOException, MavenHelper.MavenException {
    try (Git git = Git.wrap(getRepo())) {
      return checkout(git, commitString, excPath, type);
    }
  }

  @NonNull
  public RevCommit parseCommit(RevCommit commitStr) throws GitHelperException {
    try {
      return getRepo().parseCommit(commitStr);
    } catch (IOException e) {
      throw new GitHelperException(e);
    }
  }

  @NonNull
  public RevCommit parseCommit(String commitStr) throws GitHelperException {
    try {
      ObjectId id = getRepo().resolve(commitStr);
      return getRepo().parseCommit(id);
    } catch (IOException e) {
      throw new GitHelperException(e);
    }
  }

  private void reset(Git git) throws GitHelperException {
    try {
      git.reset()
              .setMode(ResetType.HARD)
              .call();
    } catch (GitAPIException e) {
      throw new GitHelperException(e);
    }
  }

  private void _checkout(Git git, String commitString) throws GitHelperException {
    try {
      git.checkout()
              .setName(commitString)
              .call();
    } catch (GitAPIException e) {
      throw new GitHelperException(e);
    }
  }

  private MavenHelper checkout(Git git, String commitString, Path excPath, MavenPluginType type) throws GitHelperException, IOException, MavenHelper.MavenException {
    logger.info("{}: Checkout {}", projName, commitString);
    try {
      git.checkout()
              .setName(commitString)
              .call();
    } catch (CheckoutConflictException e) {
      reset(git);
      _checkout(git, commitString);
    } catch (JGitInternalException e) {
      Throwable reason = e.getCause();
      if (reason instanceof LockFailedException) {
        LockFailedException cause = (LockFailedException) reason;
        Path lockedFiled = cause.getFile().toPath();
        Path fileName = lockedFiled.getFileName();
        Path locker = lockedFiled.resolveSibling(fileName.toString() + ".lock");
        try {
          Files.deleteIfExists(locker);
        } catch (IOException e1) {
          throw new GitHelperException(e);
        }

        _checkout(git, commitString);
      } else {
        throw e;
      }
    } catch (GitAPIException e) {
      throw new GitHelperException(e);
    }

    MavenHelper mvn = new MavenHelper(gitHome, projName, excPath, type);
    mvn.clean();
    return mvn;
  }

  public void close() {
    if (Files.exists(gitHome)) {
      try {
        PathUtils.recursiveDelete(gitHome);
      } catch (IOException e) {
      }
    }

    if (repo != null) {
      repo.close();
    }
  }

  public Path getRootPath() {
    return gitHome;
  }

  public static class GitHelperException extends Exception {

    private GitHelperException(Exception e) {
      super(e);
    }
  }
}

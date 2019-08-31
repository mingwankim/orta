package experiments;

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



import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import experiments.commons.artifacts.Artifact;
import experiments.utils.GitHelper;
import experiments.utils.MavenHelper;
import experiments.utils.MavenPluginType;
import org.orta.diff.ClassHash;
import org.apache.commons.cli.ParseException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

public class ChainCreator {

  private static final Logger logger = LoggerFactory.getLogger(ChainCreator.class);

  public static void main(@NonNull String... args) throws ParseException, IOException, SQLException, GitHelper.GitHelperException {
    try (ChainTaskOption opts = new ChainTaskOption(args); GitHelper git = opts.createGit()) {
      git.cloneRepository();

      try (CommitSet commits = new CommitSet(git, opts)) {
        while (commits.hasNext()) {
          RevCommit nextCommit = commits.next();
          String headCommitId = nextCommit.name();

          RevCommit[] children = git.parseCommit(nextCommit).getParents();
          if (children.length == 0) {
            continue;
          }

          if (commits.isNotTestable(git, headCommitId)) {
            // This head is not connectible, but its children could be head commits.
            commits.addQueue(Arrays.asList(children));
            continue;
          }

          RevCommit tailCommit = children[0];
          String tailCommitId = tailCommit.name();
          commits.addQueue(Arrays.asList(children).subList(1, children.length));
          if (commits.isNotTestable(git, tailCommitId)) {
            // This preceding is not connectible, but its children could be head commits.
            commits.addQueue(Arrays.asList(git.parseCommit(tailCommit).getParents()));
          } else {
            commits.addQueue(tailCommit);
            // This preceiding could be a head commit.
            if (commits.isCandidate(headCommitId, tailCommitId)) {
              // These commits can be a chain.
              if (commits.hasCodeChanges(headCommitId, tailCommitId)) {
                commits.register(headCommitId, tailCommitId);
              } else {
                commits.exclude(headCommitId, tailCommitId);
              }
            }
          }
        }
      }
    }
  }

  private static class CommitInfo {
    private final int id;
    private boolean isAvailable;

    CommitInfo(int id, boolean available) {
      this.id = id;
      this.isAvailable = available;
    }
  }

  private static class CommitSet implements AutoCloseable {
    private final PriorityQueue<RevCommit> queue = new PriorityQueue<>(Comparator.comparingInt(RevCommit::getCommitTime).reversed());
    private final Map<String, CommitInfo> commits;
    private final Table<Integer, Integer, Boolean> edges;
    private final Path outDir;
    private final PreparedStatement insertCommit;
    private final PreparedStatement insertEdge;
    private final PreparedStatement insertExcludedEdge;
    private final int targetCount;
    private int errorCount = 0;
    private int selectCount = 0;


    CommitSet(GitHelper git, ChainTaskOption opts) throws IOException, GitHelper.GitHelperException, SQLException {
      addQueue(git.parseCommit(opts.head));

      Connection conn = opts.conn;
      int repoId = opts.repoId;

      this.commits = loadCommits(conn, repoId);
      this.edges = loadEdges(conn, repoId);
      this.outDir = opts.outDirectory;
      this.targetCount = opts.targetCount;

      insertCommit = conn.prepareStatement("insert into commits(repo_id, name, is_available) values (" + repoId + ",?,?)", Statement.RETURN_GENERATED_KEYS);
      insertEdge = conn.prepareStatement("insert into edges(repo_id, head_commit, tail_commit) values (" + repoId + ",?,?)");
      insertExcludedEdge = conn.prepareStatement("insert into excluded_edges(repo_id, head_commit, tail_commit) values (" + repoId + ",?,?)");
    }

    private static boolean hasValidEdge(Map<Integer, Boolean> edges) {
      for (Entry<Integer, Boolean> entry : edges.entrySet()) {
        if (entry.getValue()) {
          return true;
        }
      }

      return false;
    }

    private static Map<String, CommitInfo> loadCommits(Connection conn, int repoId) throws SQLException {
      Map<String, CommitInfo> commits = new HashMap<>();
      try (Statement stmt = conn.createStatement()) {
        ResultSet result = stmt.executeQuery("select id, name, is_available from commits where repo_id=" + repoId);
        while (result.next()) {
          int id = result.getInt(1);
          String name = result.getString(2);
          boolean isAvailable = result.getBoolean(3);
          commits.put(name, new CommitInfo(id, isAvailable));
        }

        return commits;
      }
    }

    private void insertEdge(String head, String tail, boolean selected) throws SQLException {
      int head_id = commits.get(head).id;
      int tail_id = commits.get(tail).id;
      Boolean current = edges.get(head_id, tail_id);
      if (current != null) {
        if (!current.equals(selected)) {
          throw new RuntimeException();
        }
      } else {
        if (selected) {
          if (hasValidEdge(edges.row(head_id)) || hasValidEdge(edges.column(tail_id))) {
            throw new RuntimeException();
          }

          selectCount += 1;
        }

        edges.put(head_id, tail_id, selected);
        PreparedStatement stmt = selected ? insertEdge : insertExcludedEdge;
        stmt.setInt(1, head_id);
        stmt.setInt(2, tail_id);
        stmt.execute();
      }
    }

    private CommitInfo insertCommit(String name, boolean available) throws SQLException {
      CommitInfo info = commits.get(name);
      if (info != null) {
        return info;
      }

      insertCommit.setString(1, name);
      insertCommit.setBoolean(2, available);
      insertCommit.execute();
      try (ResultSet result = insertCommit.getGeneratedKeys()) {
        result.next();
        info = new CommitInfo(result.getInt(1), available);
        commits.put(name, info);
      }

      return info;
    }

    private Table<Integer, Integer, Boolean> loadEdges(Connection conn, int projId) throws SQLException {
      Table<Integer, Integer, Boolean> table = HashBasedTable.create();
      try (Statement stmt = conn.createStatement()) {
        try (ResultSet result = stmt.executeQuery(
                String.format("(select head_commit, tail_commit, TRUE from edges where repo_id=%d)" +
                        " union " +
                        "(select head_commit, tail_commit, FALSE from excluded_edges where repo_id=%d)", projId, projId))) {
          while (result.next()) {
            int head = result.getInt(1);
            int tail = result.getInt(2);
            boolean available = result.getBoolean(3);
            table.put(head, tail, available);
            if (available) {
              selectCount += 1;
            }
          }
        }

        return table;
      }
    }

    public void close() {
      try {
        insertEdge.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        insertCommit.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }

      try {
        insertExcludedEdge.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }

    boolean hasNext() {
      return !queue.isEmpty() && selectCount < targetCount && errorCount < 10;
    }

    RevCommit next() {
      return queue.poll();
    }

    void addQueue(Collection<RevCommit> commits) {
      queue.addAll(commits);
    }

    boolean isCandidate(String head, String tail) {
      int head_id = commits.get(head).id;
      int tail_id = commits.get(tail).id;
      return !edges.contains(head_id, tail_id);
    }

    void register(String head, String tail) throws SQLException {
      insertEdge(head, tail, true);
    }

    void exclude(String head, String tail) throws SQLException {
      insertEdge(head, tail, false);
    }

    boolean isNotTestable(GitHelper git, String c) throws IOException, GitHelper.GitHelperException, SQLException {
      CommitInfo info = commits.get(c);
      if (info != null) {
        if (info.isAvailable) {
          Path outRoot = outDir.resolve(c);
          if (!Artifact.Hashes.isReady(outRoot)) {
            try (MavenHelper mvn = git.checkout(c, null, MavenPluginType.HASH)) {
              mvn.collectHash(outRoot, false);
            } catch (MavenHelper.MavenException e) {
              throw new RuntimeException(e);
            }
          }
        }

        return !info.isAvailable;
      }

      info = initializeCommit(git, c);
      return !info.isAvailable;
    }

    private CommitInfo initializeCommit(GitHelper git, String c) throws GitHelper.GitHelperException, SQLException {
      Path outRoot = outDir.resolve(c);
      boolean isTestable;
      try (MavenHelper mvn = git.checkout(c, null, MavenPluginType.HASH)) {
        try {
          mvn.runTest(outRoot, false);
          isTestable = true;
        } catch (MavenHelper.MavenException | IOException e) {
          try {
            mvn.checkTestResult();
            isTestable = true;
          } catch (MavenHelper.MavenException | IOException ee) {
            isTestable = false;
          }
        }
      } catch (MavenHelper.MavenException | IOException e) {
        // Failed to prepare maven.
        isTestable = false;
      }

      if (isTestable) {
        errorCount = 0;
      } else {
        errorCount += 1;
      }
      return insertCommit(c, isTestable);
    }

    void addQueue(RevCommit preceding) {
      queue.add(preceding);
    }

    private boolean hasCodeChanges(String previous, String current) throws IOException {
      Map<String, ClassHash> currentHashes = Artifact.Hashes.loadArtifact(outDir.resolve(current));
      Map<String, ClassHash> prevHashes = Artifact.Hashes.loadArtifact(outDir.resolve(previous));

      for (Entry<String, ClassHash> entry : prevHashes.entrySet()) {
        String className = entry.getKey();
        ClassHash prevHash = entry.getValue();
        ClassHash curHash = currentHashes.remove(className);
        if (curHash == null) {
          // a class is deleted.
          return true;
        } else if (curHash.hasClassLevelChanges(prevHash) && !curHash.computeChangedMethodFromPrevious(prevHash).isEmpty()) {
          return true;
        }
      }

      // Some classes are newly added.
      return !currentHashes.isEmpty();
    }
  }
}

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



import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import experiments.commons.artifacts.Artifact;
import experiments.commons.artifacts.ArtifactHandler;
import experiments.utils.GitHelper;
import experiments.utils.MavenHelper;
import experiments.utils.MavenPluginType;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

public class Benchmark {
  private static final Logger logger = LoggerFactory.getLogger(Benchmark.class);

  public static void main(String... args)
          throws IOException, ParseException, SQLException, GitHelper.GitHelperException, MavenHelper.MavenException {
    try (BenchTaskOption opt = new BenchTaskOption(args)) {
      Updater sep = new SeparateUpdater(opt);
      Updater single = new SingleUpdater(opt);
      single.prepareArtifact();
      sep.prepareArtifact();

      single.update();
      sep.update();
      testAll(opt);
      opt.updateAllTests();
    }
  }

  private static void testAll(BenchTaskOption opt) throws IOException, GitHelper.GitHelperException, SQLException, MavenHelper.MavenException {
    if (!opt.isConfident("all")) {
      try (MavenHelper mvn = opt.checkoutHead(MavenPluginType.NONE)) {
        do {
          int[] times = mvn.profileTestAll(false);
          if (times == null) {
            continue;
          }

          opt.add("all", times[1]);
        } while (!opt.isConfident("all"));
      }
    }
  }

  abstract static class Updater {
    final ArtifactHandler<Set<String>> selectedArtifact;
    final ArtifactHandler<Set<String>> affectedArtifact;
    final ArtifactHandler<?> reachableArtifacts;
    final BenchTaskOption opt;

    Updater(BenchTaskOption opt, ArtifactHandler<Set<String>> selectedTests, ArtifactHandler<Set<String>> affectedTests, ArtifactHandler<Table<String, String, Set<String>>> reachables) {
      this.opt = opt;
      this.selectedArtifact = selectedTests;
      this.affectedArtifact = affectedTests;
      this.reachableArtifacts = reachables;
    }

    boolean isInconsistentResult() throws IOException, SQLException {
      if (isInconsistentResult(selectedArtifact.loadArtifact(opt.headRoot), affectedArtifact.loadArtifact(opt.headRoot))) {
        opt.markAsProblem();
        return true;
      }
      return false;
    }

    abstract boolean isAllConfident();

    boolean hasMissedArtifacts(Path artifactRoot) throws IOException {
      return !selectedArtifact.isReady(artifactRoot) || !affectedArtifact.isReady(artifactRoot) || !reachableArtifacts.isReady(artifactRoot);
    }

    abstract void runForConsistencyTest() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException, SQLException;

    boolean isInconsistentResult(Set<String> selected, Set<String> affected) {
      Preconditions.checkState(selected != null && affected != null, "Could not load artifacts");
      Set<String> storedSelected = getSelected();
      Set<String> storedAffected = getAffected();
      return !(storedAffected != null && storedSelected != null && storedAffected.equals(affected) && storedSelected.equals(selected));
    }

    void update() throws IOException, GitHelper.GitHelperException, MavenHelper.MavenException, SQLException {
      Files.createDirectories(this.opt.headRoot);

      runForConsistencyTest();
      Set<String> selected = selectedArtifact.loadArtifact(this.opt.headRoot);
      Set<String> affected = affectedArtifact.loadArtifact(this.opt.headRoot);
      if (isInconsistentResult(selected, affected)) {
        updateForConsistency(selected, affected);
      }


      if (!isAllConfident()) {
        runBenchmark();
      }
    }

    protected abstract void updateForConsistency(Set<String> selected, Set<String> affected) throws SQLException;

    protected abstract void runBenchmark() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException, SQLException;

    protected abstract void prepareArtifact() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException;

    protected abstract Set<String> getAffected();

    protected abstract Set<String> getSelected();
  }

  private static class SingleUpdater extends Updater {
    private ImmutableSet<String> selected;
    private ImmutableSet<String> affected;

    public SingleUpdater(BenchTaskOption opt) throws SQLException {
      super(opt, Artifact.SingleArtifact.SelectedTests, Artifact.SingleArtifact.AffectedTests, Artifact.SingleArtifact.Reachables);
      selected = opt.singleSelected;
      affected = opt.singleAffected;
    }

    @Override
    boolean isAllConfident() {
      return opt.isConfident("singleRTA") && opt.isConfident("singleOnline");
    }

    @Override
    void runForConsistencyTest() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException {
      try (MavenHelper mvn = opt.checkoutHead(MavenPluginType.SINGLE_AEC)) {
        mvn.select(this.opt.headRoot, this.opt.tailRoot, false);
      }
    }

    @Override
    protected void updateForConsistency(Set<String> selected, Set<String> affected) throws SQLException {
      this.affected = ImmutableSet.copyOf(affected);
      this.selected = ImmutableSet.copyOf(selected);
      try (Statement stmt = opt.conn.createStatement()) {
        String selectInsert = DBConnector.listToString(selected);
        String affectInsert = DBConnector.listToString(affected);
        stmt.execute("update edges set single_selected=" + selectInsert + ", single_affected=" + affectInsert + " where id=" + opt.edgeId);
      }

      opt.resetBenchmark("singleOnline", "singleRTA");
    }

    @Override
    protected void runBenchmark() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException, SQLException {
      try (MavenHelper mvn = opt.checkoutHead(MavenPluginType.SINGLE_AEC)) {
        while (!opt.isConfident("singleOnline")) {
          int[] times = mvn.profileTest(this.opt.headRoot, this.opt.tailRoot, false);
          if (times == null) {
            continue;
          }
          if (isInconsistentResult()) {
            return;
          }
          opt.add("singleOnline", times[1] + times[0]);
        }

        while (!opt.isConfident("singleRTA")) {
          int[] times = mvn.profileSelect(this.opt.headRoot, this.opt.tailRoot, false);
          if (times == null) {
            continue;
          }
          if (isInconsistentResult()) {
            return;
          }
          opt.add("singleRTA", times[2]);
        }
      }
    }

    @Override
    protected void prepareArtifact() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException {
      if (hasMissedArtifacts(opt.tailRoot)) {
        try (MavenHelper mvn = opt.checkoutTail(MavenPluginType.SINGLE_AEC)) {
          mvn.select(opt.tailRoot, null, false);
        }
      }
    }

    @Override
    protected Set<String> getAffected() {
      return affected;
    }

    @Override
    protected Set<String> getSelected() {
      return selected;
    }
  }


  static class SeparateUpdater extends Updater {
    private ImmutableSet<String> selected;
    private ImmutableSet<String> affected;

    SeparateUpdater(BenchTaskOption opt) {
      super(opt, Artifact.SeparateArtifact.SelectedTests, Artifact.SeparateArtifact.AffectedTests, Artifact.SeparateArtifact.Reachables);
      selected = opt.sepSelected;
      affected = opt.sepAffected;
    }

    @Override
    boolean isAllConfident() {
      return opt.isConfident("sepRTA") && opt.isConfident("sepORTA") && opt.isConfident("sepOnline");
    }

    @Override
    void runForConsistencyTest() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException, SQLException {
      try (MavenHelper mvn = opt.checkoutHead(MavenPluginType.SEPARATE_RTA)) {
        mvn.countEdges(this.opt.headRoot, this.opt.tailRoot, false);
      }

      int[] edges = Artifact.Edges.loadArtifact(this.opt.headRoot);
      try (Statement stmt = this.opt.conn.createStatement()) {
        stmt.execute("update edges set sep_edge_count=" + edges[2] + ", sep_dups_count=" + edges[3] + ", single_edge_count=" + edges[0] + ", single_dups_count=" + edges[1] + " where id=" + opt.edgeId);
      }
    }

    @Override
    protected void updateForConsistency(Set<String> selected, Set<String> affected) throws SQLException {
      this.affected = ImmutableSet.copyOf(affected);
      this.selected = ImmutableSet.copyOf(selected);
      try (Statement stmt = opt.conn.createStatement()) {
        String selectInsert = DBConnector.listToString(selected);
        String affectInsert = DBConnector.listToString(affected);
        stmt.execute("update edges set sep_selected=" + selectInsert + ", sep_affected=" + affectInsert + " where id=" + opt.edgeId);
      }

      opt.resetBenchmark("sepOnline", "sepRTA", "sepORTA");
    }

    @Override
    protected void runBenchmark() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException, SQLException {
      try (MavenHelper mvn = opt.checkoutHead(MavenPluginType.SEPARATE_RTA)) {
        while (!opt.isConfident("sepOnline")) {
          int[] times = mvn.profileTest(this.opt.headRoot, this.opt.tailRoot, false);
          if (times == null) {
            continue;
          }
          if (isInconsistentResult()) {
            return;
          }

          opt.add("sepOnline", times[1] + times[0]);
        }

        while (!opt.isConfident("sepRTA")) {
          int[] times = mvn.profileSelect(this.opt.headRoot, this.opt.tailRoot, false);
          if (times == null) {
            continue;
          }

          if (isInconsistentResult()) {
            return;
          }
          opt.add("sepRTA", times[2]);
        }
      }

      if (!opt.isConfident("sepORTA")) {
        try (MavenHelper mvn = opt.checkoutHead(MavenPluginType.ORTA)) {
          do {
            int[] times = mvn.profileSelect(this.opt.headRoot, this.opt.tailRoot, false);
            if (times == null) continue;
            if (isInconsistentResult()) {
              return;
            }

            opt.add("sepORTA", times[3]);
          } while (!opt.isConfident("sepORTA"));
        }
      }
    }

    @Override
    protected void prepareArtifact() throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException {
      if (hasMissedArtifacts(opt.tailRoot)) {
        try (MavenHelper mvn = opt.checkoutTail(MavenPluginType.SEPARATE_RTA)) {
          mvn.select(opt.tailRoot, null, false);
        }
      }
    }

    @Override
    protected Set<String> getAffected() {
      return affected;
    }

    @Override
    protected Set<String> getSelected() {
      return selected;
    }
  }
}

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


import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import experiments.commons.PathUtils;
import experiments.commons.artifacts.Artifact;
import experiments.utils.GitHelper;
import experiments.utils.MavenHelper;
import experiments.utils.MavenPluginType;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

public class BenchTaskOption implements AutoCloseable {
  public final String url;
  public final Connection conn;
  final public Path excludesFile;
  final public Path tailRoot;
  final int edgeId;
  final Path outDirectory;
  final String projName;
  final String head;
  final String tail;
  final ImmutableSet<String> hyRTS;
  final ImmutableSet<String> singleSelected;
  final ImmutableSet<String> singleAffected;
  final ImmutableSet<String> sepAffected;
  final ImmutableSet<String> sepSelected;
  final ImmutableSet<String> allTests;
  final PreparedStatement inserter;
  final Path headRoot;
  final GitHelper git;
  final ListMultimap<String, Integer> values = MultimapBuilder.hashKeys().linkedListValues().build();

  public BenchTaskOption(String... args) throws ParseException, SQLException, IOException {
    Options options = new Options();

    options.addRequiredOption("id", "id", true, "edgeId");
    CommandLineParser parser = new DefaultParser();
    CommandLine cmds = parser.parse(options, args);
    this.edgeId = Integer.parseInt(cmds.getOptionValue("id"));
    this.excludesFile = Files.createTempFile("excludes", ".list");
    this.conn = DBConnector.connect();
    this.inserter = conn.prepareStatement("insert into benchmark(edge_id, type, value) values (?,?,?)");

    int repoId;
    try (Statement stmt = conn.createStatement()) {
      ResultSet result = stmt.executeQuery("select e.repo_id, url, head.name, tail.name, e.hyRTS_selected, e.sep_affected, e.sep_selected, e.single_selected, e.single_affected, e.all_tests from (select * from edges where id=" + edgeId + ") as e join commits as head on (head.id=e.head_commit) join commits as tail on (tail.id=e.tail_commit) join repositories on (e.repo_id=repositories.id)");
      if (!result.next()) {
        throw new IllegalArgumentException("Could not found the url from the database");
      }

      this.url = result.getString(2);
      this.head = result.getString(3);
      this.tail = result.getString(4);
      this.hyRTS = setOrNull(result, 5);
      sepAffected = setOrNull(result, 6);
      sepSelected = setOrNull(result, 7);
      singleSelected = setOrNull(result, 8);
      singleAffected = setOrNull(result, 9);
      allTests = setOrNull(result, 10);
      if (result.next()) {
        throw new IllegalStateException();
      }
    }

    try (Statement stmt = conn.createStatement()) {
      ResultSet result = stmt.executeQuery("select type, value from benchmark where edge_id=" + edgeId);
      while (result.next()) {
        String type = result.getString(1);
        int value = result.getInt(2);
        values.put(type, value);
      }
    }

    this.outDirectory = Files.createTempDirectory("gitf");
    Files.createDirectories(outDirectory);
    this.projName = url.substring(url.lastIndexOf('/') + 1);
    this.tailRoot = outDirectory.resolve(tail);
    this.headRoot = outDirectory.resolve(head);
    this.git = new GitHelper(projName, outDirectory, url);
  }

  private static ImmutableSet<String> setOrNull(ResultSet resultSet, int index) throws SQLException {
    String s = resultSet.getString(index);
    if (s == null) {
      return null;
    }

    return ImmutableSet.copyOf(s.split(","));
  }

  private static double confidenceRatio(List<Integer> values) {
    if (values.size() < 5) {
      return 1;
    }
    SummaryStatistics stat = new SummaryStatistics();
    values.forEach(stat::addValue);

    double criVal = new TDistribution(stat.getN() - 1)
            .inverseCumulativeProbability(1.0 - 0.95 / 2);
    double ci = criVal * stat.getStandardDeviation() / Math.sqrt(stat.getN());
    return ci * 2 / stat.getMean();
  }

  void resetBenchmark(String... type) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement("delete from benchmark where edge_id=? and type=?")) {
      for (String s : type) {
        stmt.setInt(1, edgeId);
        stmt.setString(2, s);
        stmt.addBatch();
        values.removeAll(s);
      }

      stmt.executeBatch();
    }

  }

  @Override
  public void close() {
    try {
      if (conn != null) {
        conn.close();
      }
    } catch (Exception e) {

    }

    try {
      if (excludesFile != null) {
        Files.deleteIfExists(excludesFile);
      }
    } catch (Exception e) {

    }

    try {
      if (git != null) {
        git.close();
      }
    } catch (Exception e) {

    }

    try {
      PathUtils.recursiveDelete(outDirectory);
    } catch (IOException e) {
    }
  }

  public void updateAllTests() throws IOException, SQLException {
    if (allTests == null) {
      Set<String> classes = Artifact.TestClasses.loadArtifact(headRoot);
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("update edges set all_tests=" + DBConnector.listToString(classes) + " where id=" + edgeId);
      }
    }
  }

  public boolean add(String type, int v) throws SQLException {
    List<Integer> values = this.values.get(type);
    values.add(v);
    inserter.setInt(1, edgeId);
    inserter.setString(2, type);
    inserter.setInt(3, v);
    inserter.execute();
    return isConfident(values);
  }

  public boolean isConfident(String type) {
    List<Integer> values = this.values.get(type);
    return isConfident(values);
  }

  boolean isConfident(List<Integer> values) {
    int size = values.size();
    if (size < 5) {
      return false;
    }
    return size >= 30 || confidenceRatio(values) <= 0.03;
  }

  MavenHelper checkoutHead(MavenPluginType type) throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException {
    return git.checkout(head, excludesFile, type);
  }

  public MavenHelper checkoutTail(MavenPluginType type) throws MavenHelper.MavenException, IOException, GitHelper.GitHelperException {
    return git.checkout(tail, excludesFile, type);
  }

  public void markAsProblem() throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute("update edges set has_problem=true where id=" + edgeId);
    }
  }
}

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



import com.google.common.base.Preconditions;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import experiments.commons.MavenConstants;
import experiments.commons.PathUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.stream.Stream;

public class MavenHelper implements AutoCloseable {

  @NonNull
  private static final Logger logger = LoggerFactory.getLogger(MavenHelper.class);
  private static final EnumMap<MavenPluginType, List<Param>> requires = new EnumMap<>(MavenPluginType.class);
  private static final String MVN_PLUGIN_NAME = "rts-maven-plugin";
  private static final String MVN_PLUGIN_GROUP = "kr.ac.korea.esel";
  private static final File javaHome;
  private static final Invoker invoker;

  static {
    Properties tools = new Properties();
    Path workingDir = Paths.get(".").resolve("tools.properties");
    try (InputStream is = Files.newInputStream(workingDir)) {
      tools.load(is);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }

    javaHome = getFileProperty(tools, "JAVA_HOME");
    invoker = new DefaultInvoker()
            .setMavenHome(getFileProperty(tools, "MVN_HOME"));

    Param artifactRoot = (props, cur, old) -> props.put(MavenConstants.PARAM_ARTIFACT, cur.toString());
    Param oldArtifactRoot = (props, cur, old) -> {
      if (old != null) props.put(MavenConstants.PARAM_OLD_ARTIFACT, old.toString());
    };
    requires.put(MavenPluginType.HASH, Collections.singletonList(artifactRoot));
    requires.put(MavenPluginType.ORTA, Arrays.asList(artifactRoot, oldArtifactRoot));
    requires.put(MavenPluginType.SEPARATE_RTA, Arrays.asList(artifactRoot, oldArtifactRoot));
    requires.put(MavenPluginType.SINGLE_AEC, Arrays.asList(artifactRoot, oldArtifactRoot));
    requires.put(MavenPluginType.COUNT_EDGES, Collections.singletonList(artifactRoot));
    requires.put(MavenPluginType.NONE, Collections.emptyList());
  }

  private final Path excPath;
  @NonNull
  private final File pomFile;
  private final File modulePomFile;
  private final Path profilerHome;
  private final Path logPath;
  private final List<Param> currentParams;
  @NonNull
  private final String workingDirectory;
  private final Path surefireHome;

  public MavenHelper(@NonNull Path repoHome, String projName, Path excPath, MavenPluginType type) throws IOException {
    Objects.requireNonNull(invoker, "mavenHome is not set");
    Objects.requireNonNull(MavenHelper.javaHome, "javenHome is not set");
    this.logPath = Files.createTempFile("mvn", ".log");
    Files.deleteIfExists(logPath);
    this.pomFile = resolvePOM(projName, repoHome);
    this.excPath = type != MavenPluginType.ANALYSIS ? excPath : null;
    this.workingDirectory = repoHome.toString();

    if (type != MavenPluginType.HYRTS && projName.equals("commons-functor")) {
      Path candidate = repoHome.resolve("core").resolve("pom.xml");
      if (Files.notExists(candidate)) {
        modulePomFile = pomFile;
      } else {
        modulePomFile = candidate.toFile();
      }
    } else {
      modulePomFile = pomFile;
    }

    surefireHome = modulePomFile.toPath().resolveSibling("target").resolve("surefire-reports");

    String licenseHeader = modifyPOM(modulePomFile.toPath(), excPath, projName, type);
    initializeProfiler(licenseHeader, repoHome);
    profilerHome = (type != MavenPluginType.ANALYSIS ? repoHome : modulePomFile.toPath().getParent()).resolve(".profiler");
    PathUtils.recursiveDelete(profilerHome);

    currentParams = requires.get(type);
  }

  private static File getFileProperty(Properties props, String name) {
    File f = Paths.get(props.getProperty(name)).toFile();
    if (!f.exists()) {
      throw new RuntimeException("Could not found " + name + ": " + f);
    }

    return f;
  }

  private static void initializeProfiler(String licenseHeader, Path rootPath) throws IOException {
    Path out = rootPath.resolve(".mvn");
    Files.createDirectories(out);
    out = out.resolve("extensions.xml");
    if (Files.exists(out)) {
      return;
    }

    try (BufferedReader io = new BufferedReader(new InputStreamReader(MavenHelper.class.getResourceAsStream("/extensions.xml"))); PrintWriter os = new PrintWriter(Files.newOutputStream(out))) {
      if (!licenseHeader.isEmpty()) {
        PrintWriter writer = new PrintWriter(os);
        writer.print(io.readLine());
        writer.print(licenseHeader);
      }

      IOUtils.copy(io, os);
    }
  }

  private static File resolvePOM(String projName, Path directory) throws IOException {
    String pomName = projName.equals("closure-compiler") ? "pom-main-unshaded.xml" : "pom.xml";
    Path p = directory.resolve(pomName);
    Preconditions.checkState(Files.exists(p));
    return p.toFile();
  }

  private static String modifyPOM(Path pomPath, Path excPath, String projName, MavenPluginType type) throws IOException {
    if (excPath != null) {
      excPath = excPath.toAbsolutePath();
    }

    boolean isCommonsMath = projName.equals("commons-math");

    MavenXpp3Reader reader = new MavenXpp3Reader();
    String appendForMath;
    if (isCommonsMath) {
      StringJoiner joiner = new StringJoiner("\n");
      try (BufferedReader is = Files.newBufferedReader(pomPath)) {
        String line;
        while (!(line = is.readLine()).equals("<!--")) {
        }

        joiner.add(line);
        while (!(line = is.readLine()).equals("-->")) {
          joiner.add(line);
        }
        joiner.add(line);
        appendForMath = joiner.toString();
      }
    } else {
      appendForMath = "";
    }

    Model model;
    try (InputStream is = Files.newInputStream(pomPath)) {
      model = reader.read(is);
    } catch (XmlPullParserException e) {
      throw new IOException(e);
    }

    Build build = model.getBuild();
    if (build == null) {
      build = new Build();
      model.setBuild(build);
    }

    List<Plugin> plugins = build.getPlugins();
    if (plugins == null) {
      plugins = new LinkedList<>();
      build.setPlugins(plugins);
    }

    Plugin rtscg = new Plugin();
    rtscg.setArtifactId(MVN_PLUGIN_NAME);
    rtscg.setGroupId(MVN_PLUGIN_GROUP);
    rtscg.setVersion("1.0-SNAPSHOT");

    Plugin hyRTS = new Plugin();
    hyRTS.setVersion("1.0.1");
    hyRTS.setGroupId("org.hyrts");
    hyRTS.setArtifactId("hyrts-maven-plugin");

    Iterator<Plugin> iter = plugins.iterator();
    while (iter.hasNext()) {
      Plugin plugin = iter.next();
      if (plugin.equals(hyRTS) || plugin.equals(rtscg)) {
        iter.remove();
      }
    }

    Properties props = model.getProperties();
    Object exc = props.remove("surefire.excludesFile");
    if (exc != null && excPath != null && !exc.toString().equals(excPath.toString())) {
      throw new RuntimeException();
    }

    iter = plugins.iterator();
    if (type == MavenPluginType.HYRTS) {
      boolean hasJacoco = false;

      while (iter.hasNext()) {
        Plugin plugin = iter.next();
        if (plugin.getArtifactId().equals("jacoco-maven-plugin")) {
          iter.remove();
          hasJacoco = true;
        } else if (hasJacoco && plugin.getArtifactId().equals("maven-surefire-plugin")) {
          Xpp3Dom cfg = (Xpp3Dom) plugin.getConfiguration();
          for (int idx = 0; idx < cfg.getChildCount(); ++idx) {
            if (cfg.getChild(idx).getName().equals("argLine")) {
              cfg.removeChild(idx);
            }
          }
        }
      }

      plugins.add(hyRTS);
    } else if (type != MavenPluginType.NONE) {
      if (excPath != null) {
        props.setProperty("surefire.excludesFile", excPath.toString());
      }

      PluginExecution exe = new PluginExecution();
      for (String goal : type.goals) {
        exe.addGoal(goal);
      }

      rtscg.addExecution(exe);

      boolean configured = false;
      while (iter.hasNext()) {
        Plugin plugin = iter.next();
        if (plugin.getArtifactId().equals("maven-surefire-plugin")) {
          if (!Objects.equals(plugin.getVersion(), "3.0.0-M3")) {
            plugin.setVersion("3.0.0-M3");
          }

          Xpp3Dom cfg = (Xpp3Dom) plugin.getConfiguration();
          if (cfg == null) {
            cfg = new Xpp3Dom("configuration");
            plugin.setConfiguration(cfg);
          }
          Xpp3Dom excludes = cfg.getChild("excludesFile");
          if (excludes != null) throw new RuntimeException();
          configured = true;
        }
      }

      if (!configured) {
        Plugin surefire = new Plugin();
        surefire.setArtifactId("maven-surefire-plugin");
        surefire.setGroupId("org.apache.maven.plugins");
        surefire.setVersion("3.0.0-M3");
        Xpp3Dom cfg = new Xpp3Dom("configuration");
        surefire.setConfiguration(cfg);
        plugins.add(surefire);
      }

      plugins.add(rtscg);
    }

    MavenXpp3Writer writer = new MavenXpp3Writer();
    try (OutputStream is = Files.newOutputStream(pomPath)) {
      writer.write(is, model);
    }

    StringJoiner joiner = new StringJoiner("\n");
    try (BufferedReader is = Files.newBufferedReader(pomPath)) {
      String line;
      joiner.add(is.readLine());
      joiner.add(appendForMath);
      while ((line = is.readLine()) != null) {
        joiner.add(line);
      }
    }

    Files.write(pomPath, joiner.toString().getBytes(), StandardOpenOption.WRITE);
    return appendForMath;
  }

  private static int parseTime(String value) {
    String[] elems = value.split(" ");
    Preconditions.checkState(elems[1].equals("ms"));
    return Integer.parseInt(elems[0]);
  }

  private static boolean isArtifact(String[] names, String groupId, String artifactId, String goal) {
    return names[0].equals(groupId) && names[1].equals(artifactId) && names[3].equals(goal);
  }

  private static boolean isArtifact(String[] names, String groupId, String artifactId, String... goals) {
    if (names[0].equals(groupId) && names[1].equals(artifactId)) {
      for (String goal : goals) {
        if (goal.equals(names[3])) {
          return true;
        }
      }
    }

    return false;
  }

  private static void printLog(MavenException e) {
    try (BufferedReader os = Files.newBufferedReader(e.getLogPath())) {
      os.lines()
              .map(String::trim)
              .forEach(logger::error);
    } catch (IOException ignored) {
    }
  }

  private int[] readProfiler() throws IOException {
    if (Files.notExists(profilerHome)) {
      logger.warn("Profiler did not work.");
      return null;
    }

    Path p;
    try (Stream<Path> files = Files.list(profilerHome)) {
      Iterator<Path> iter = files.iterator();
      p = iter.next();
      if (iter.hasNext()) {
        throw new IllegalStateException("More than two profiling results found.");
      }
    }

    int executionTime = 0;
    int analysisTime = 0;
    int constructTime = 0;
    int ortaTime = 0;
    boolean analysis = false;
    boolean execution = false;
    boolean construct = false;
    boolean orta = false;
    try (JsonReader reader = new JsonReader(Files.newBufferedReader(p))) {
      reader.beginObject();
      while (!reader.nextName().equals("projects")) {
        reader.skipValue();
      }

      reader.beginArray();
      reader.beginObject();
      while (!reader.nextName().equals("mojos")) {
        reader.skipValue();
      }

      reader.beginArray();
      while (reader.peek() != JsonToken.END_ARRAY) {
        reader.beginObject();
        Preconditions.checkState(reader.nextName().equals("mojo"));
        String[] big = reader.nextString().split(" ");
        String[] names = big[0].split(":");
        Preconditions.checkState(reader.nextName().equals("time"));
        String time = reader.nextString();
        if (isArtifact(names, "org.apache.maven.plugins", "maven-surefire-plugin", "test")) {
          Preconditions.checkState(!execution);
          execution = true;
          executionTime += parseTime(time);
        } else if (isArtifact(names, MVN_PLUGIN_GROUP, MVN_PLUGIN_NAME, MavenConstants.OFFLINE_SELECT_SINGLE, MavenConstants.OFFLINE_SELECT_SEPARATE)) {
          Preconditions.checkState(!analysis);
          analysis = true;
          analysisTime += parseTime(time);
        } else if (isArtifact(names, MVN_PLUGIN_GROUP, MVN_PLUGIN_NAME, MavenConstants.SEP_RTA, MavenConstants.SINGLE_RTA)) {
          Preconditions.checkState(!construct);
          construct = true;
          constructTime += parseTime(time);
        } else if (isArtifact(names, MVN_PLUGIN_GROUP, MVN_PLUGIN_NAME, MavenConstants.ORTA)) {
          Preconditions.checkState(!orta);
          orta = true;
          ortaTime += parseTime(time);
        } else if (isArtifact(names, MVN_PLUGIN_GROUP, MVN_PLUGIN_NAME, MavenConstants.ANALYSIS)) {
          Preconditions.checkState(!analysis);
          analysis = true;
          analysisTime += parseTime(time);
        }
        reader.endObject();
      }
      reader.endArray();
      reader.endObject();
      if (reader.peek() == JsonToken.BEGIN_ARRAY) {
        throw new IllegalStateException("More than two projects found.");
      }
    }

    Files.deleteIfExists(p);
    return new int[]{analysisTime, executionTime, constructTime, ortaTime};
  }

  public void select(Path cur, Path old, boolean printAll) throws IOException {
    try {
      run(pomFile, cur, old, printAll, false, "test-compile");
    } catch (MavenException e) {
      printLog(e);
      throw new RuntimeException(e);
    }
  }

  public int[] profileTestAll(boolean printAll) throws IOException {
    return profileTest(null, null, printAll);
  }

  private void run(File pomFile, Path cur, Path old, boolean printAll, boolean profile, String... goals) throws IOException, MavenException {
    if (cur != null) {
      cur = cur.toAbsolutePath();
      Files.createDirectories(cur);
    }

    if (old != null) {
      old = old.toAbsolutePath();
      Files.createDirectories(old);
    }

    try (ConsoleErrorPrinter printer = new ConsoleErrorPrinter(logPath, printAll)) {
      PathUtils.recursiveDelete(profilerHome);
      InvocationRequest request = new DefaultInvocationRequest();
      request.setPomFile(pomFile);
      request.setGoals(Arrays.asList(goals));
      request.setJavaHome(javaHome);
      request.setBatchMode(true);
      request.addShellEnvironment("maven.multiModuleProjectDirectory", workingDirectory);
      Properties props = new Properties();
      if (cur != null) {
        for (Param p : currentParams) {
          p.set(props, cur, old);
        }

        request.setProperties(props);
      }
      if (profile) {
        request.setMavenOpts("-Dprofile -DprofileFormat=JSON");
      }

      if (excPath != null) {
        Files.deleteIfExists(excPath);
      }
      logger.info("run(profile: {}, {}, {}, {})", profile, printAll, goals, props.toString());
      invoker.setOutputHandler(printer::print);
      invoker.setErrorHandler(printer::print);
      InvocationResult result = invoker.execute(request);
      CommandLineException e = result.getExecutionException();
      if (e != null || result.getExitCode() != 0) {
        if (e != null) logger.error(e.toString());
        else logger.error(printer.errorMessages.toString());
        throw new MavenException(e, printer.errorMessages);
      }
    } catch (MavenInvocationException e) {
      throw new MavenException(e);
    }
  }

  public File getPomPath() {
    return pomFile;
  }

  public void clean() throws IOException, MavenException {
    run(pomFile, null, null, false, false, "clean");
  }

  @Override
  public void close() {
    if (logPath != null) {
      try {
        Files.deleteIfExists(logPath);
      } catch (IOException ignored) {
      }
    }
  }

  public void runTest(Path outRoot, boolean printAll) throws IOException, MavenException {
    run(pomFile, outRoot, null, printAll, false, "test");
  }

  public int[] profileSelect(Path cur, Path old, boolean printAll) throws IOException, MavenException {
    try {
      run(pomFile, cur, old, printAll, true, "test-compile");
    } catch (MavenException e) {
      printLog(e);
      throw new RuntimeException(e);
    }

    return readProfiler();
  }

  private boolean isBuildFailure() {
    return Files.notExists(surefireHome);
  }

  public int[] profileTest(Path cur, Path old, boolean printAll) throws IOException {
    try {
      run(pomFile, cur, old, printAll, true, "test");
    } catch (MavenException e) {
      if (isBuildFailure()) {
        printLog(e);
        throw new RuntimeException(e);
      }
    }

    return readProfiler();
  }

  public void runGoal(Path artifactRoot, Path oldArtifactRoot, boolean printAll, String... goal) throws IOException, MavenException {
    run(pomFile, artifactRoot, oldArtifactRoot, printAll, false, goal);
  }

  public void checkTestResult() throws IOException, MavenException {
    run(modulePomFile, null, null, false, false, MavenConstants.GOAL_CHECK_TEST_RESULT);
  }

  public void countEdges(Path curRoot, Path oldRoot, boolean printAll) throws IOException, MavenException {
    run(pomFile, curRoot, oldRoot, printAll, false, "test-compile");
    run(modulePomFile, curRoot, oldRoot, printAll, false, MavenConstants.GOAL_EDGE_COUNT); // Do not expect build failure.
  }

  public void collectHash(Path outRoot, boolean b) throws IOException, MavenException {
    run(pomFile, outRoot, null, b, false, "test-compile");
    run(modulePomFile, outRoot, null, b, false, MavenConstants.GOAL_HASH);
  }

  public Integer analysis(boolean printAll) throws IOException, MavenException {
    try {
      run(modulePomFile, null, null, printAll, true, MavenConstants.GOAL_ANALYSIS);
    } catch (MavenException e) {
      if (isBuildFailure()) {
        printLog(e);
        throw new RuntimeException(e);
      }
    }

    int[] times = readProfiler();
    return times == null ? null : times[0];
  }

  private interface Param {
    void set(Properties props, Path curId, Path oldId);
  }

  private static class ConsoleErrorPrinter implements AutoCloseable {

    private final BufferedWriter stream;
    private final boolean printAll;
    private StringJoiner errorMessages = new StringJoiner("\n");

    ConsoleErrorPrinter(Path outPath, boolean printAll) throws IOException {
      PathUtils.slientDeleteIfExsits(outPath);
      stream = Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW);
      this.printAll = printAll;
    }

    private void print(String s) {
      String out = s.trim();
      if (out.startsWith("[ERROR]")) {
        errorMessages.add(out);
      }

      if (printAll) {
        logger.info(out);
      }

      try {
        stream.write(s);
        stream.newLine();
      } catch (IOException ignored) {
      }
    }

    @Override
    public void close() {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignored) {
        }
      }
    }
  }

  public class MavenException extends Exception {
    MavenException(Throwable e, StringJoiner msg) {
      super(msg.toString(), e);
    }

    MavenException(MavenInvocationException e) {
      super(e);
    }

    public Path getLogPath() {
      return logPath;
    }
  }
}

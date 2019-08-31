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


import experiments.commons.PathUtils;
import experiments.utils.GitHelper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ChainTaskOption implements AutoCloseable {
  public final String url;
  public final int repoId;
  public final String head;
  public final Path outDirectory;
  public final int targetCount;
  public final Connection conn;
  public final String projName;

  public ChainTaskOption(String... args) throws ParseException, SQLException, IOException {
    Options options = new Options();

    options.addRequiredOption("url", "url", true, "Repository URL");
    options.addRequiredOption("head", "head", true, "Head Commit");
    options.addRequiredOption("count", "count", true, "The number of commits to be selected");
    CommandLineParser parser = new DefaultParser();
    CommandLine cmds = parser.parse(options, args);
    this.url = cmds.getOptionValue("url");
    this.head = cmds.getOptionValue("head");
    this.targetCount = Integer.parseInt(cmds.getOptionValue("count"));
    this.outDirectory = Files.createTempDirectory("chain");
    this.conn = DBConnector.connect();
    Files.createDirectories(outDirectory);

    try (Statement stmt = conn.createStatement()) {
      ResultSet result = stmt.executeQuery(String.format("select id from repositories where url='%s'", url));
      if (!result.next()) {
        stmt.execute(String.format("insert into repositories (url) values ('%s')", url), Statement.RETURN_GENERATED_KEYS);
        result = stmt.getGeneratedKeys();
        result.next();
      }

      this.repoId = result.getInt(1);
      if (result.next()) {
        throw new IllegalStateException();
      }
    }

    this.projName = url.substring(url.lastIndexOf('/') + 1);
  }

  public GitHelper createGit() throws IOException, GitHelper.GitHelperException {
    return new GitHelper(projName, outDirectory, url);
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
      if (Files.exists(outDirectory)) {
        PathUtils.recursiveDelete(outDirectory);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

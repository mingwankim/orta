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


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Properties;
import java.util.StringJoiner;

public abstract class DBConnector {
  private static final String DRIVER_NAME = "org.mariadb.jdbc.Driver";
  private static final Properties props = new Properties();
  private static final String connectionURL;

  static {
    try {
      Class.forName(DRIVER_NAME);
      Path workingDir = Paths.get(".").resolve("db.properties");
      try (InputStream is = Files.newInputStream(workingDir)) {
        props.load(is);
      }


      String url = props.remove("url").toString();
      connectionURL = "jdbc:" + url;
    } catch (ClassNotFoundException | IOException e) {
      throw new RuntimeException("Could not found " + DRIVER_NAME);
    }
  }

  private static boolean isTableMissing(DatabaseMetaData dbm, String tableName) throws SQLException {
    ResultSet table = dbm.getTables(null, null, tableName, null);
    return !table.next();
  }

  public static Connection connect() throws SQLException {
    Connection conn = DriverManager.getConnection(connectionURL, props);
    conn.setAutoCommit(false);
    try (Statement stmt = conn.createStatement()) {
      DatabaseMetaData dbm = conn.getMetaData();
      if (isTableMissing(dbm, "repositories")) {
        stmt.execute("create table repositories ("
                + "id int not null auto_increment primary key,"
                + "url text not null unique key,"
                + "unique key (url))");
      }

      if (isTableMissing(dbm, "commits")) {
        stmt.execute("create table commits (" +
                "id int not null auto_increment primary key," +
                "repo_id int not null references repositories(id)," +
                "name text not null," +
                "is_available boolean not null," +
                "unique key (repo_id, name));");
      }

      if (isTableMissing(dbm, "excluded_edges")) {
        stmt.execute("create table excluded_edges (" +
                "repo_id int not null references repositories(id)," +
                "head_commit int not null references commits(id)," +
                "tail_commit int not null references commits(id)," +
                "unique key (repo_id, head_commit, tail_commit));");
      }

      if (isTableMissing(dbm, "edges")) {
        stmt.execute("create table edges (" +
                "id int not null auto_increment primary key," +
                "repo_id int not null references repositories(id)," +
                "head_commit int not null references commits(id)," +
                "tail_commit int not null references commits(id)," +
                "sep_selected text default null," +
                "sep_affected text default null," +
                "all_tests text default null," +
                "single_selected text default null," +
                "single_affected text default null," +
                "hyRTS_selected text default null," +
                "has_problem boolean default false," +
                "sep_edge_count int default null," +
                "sep_dups_count int default null," +
                "single_edge_count int default null," +
                "single_dups_count int default null," +
                "duplicate_edge_count int default null," +
                "unique key (repo_id, head_commit, tail_commit));");
      }

      if (isTableMissing(dbm, "benchmark")) {
        stmt.execute("create table benchmark (" +
                "edge_id int not null references edges(id)," +
                "type text not null," +
                "value int not null," +
                "index (edge_id));");
      }

      conn.commit();
    } catch (SQLException e) {
      conn.rollback();
      throw e;
    }
    conn.setAutoCommit(true);
    return conn;
  }

  public static String listToString(Collection<String> list) {
    StringJoiner joiner = new StringJoiner(",");
    list.forEach(joiner::add);
    return "'" + joiner.toString() + "'";
  }
}

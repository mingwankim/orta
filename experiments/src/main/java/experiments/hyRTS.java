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



import experiments.utils.GitHelper;
import experiments.utils.MavenHelper;
import experiments.utils.MavenPluginType;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

public class hyRTS {

  private static final Logger logger = LoggerFactory.getLogger(hyRTS.class);

  public static void main(String... args)
          throws IOException, ParseException, SQLException, GitHelper.GitHelperException, MavenHelper.MavenException, XPathExpressionException {
    try (BenchTaskOption opts = new BenchTaskOption(args)) {
      if (opts.url.contains("HikariCP") || opts.hyRTS != null) {
        return;
      }

      Path surefireHome = opts.git.getRootPath().resolve("target").resolve("surefire-reports");
      try (MavenHelper mvn = opts.checkoutTail(MavenPluginType.HYRTS)) {
        run(mvn, surefireHome);
      }

      try (MavenHelper mvn = opts.checkoutHead(MavenPluginType.HYRTS)) {
        run(mvn, surefireHome);
      }

      Set<String> classes = listTestClasses(surefireHome);
      Statement stmt = opts.conn.createStatement();
      stmt.execute("update edges set hyRTS_selected=" + DBConnector.listToString(classes) + " where id=" + opts.edgeId);
    }
  }

  private static void run(MavenHelper mvn, Path surefireHome) throws IOException {
    try {
      mvn.runGoal(null, null, false, "hyrts:HyRTSf");
    } catch (MavenHelper.MavenException e) {
      if (Files.notExists(surefireHome)) {
        try (BufferedReader os = Files.newBufferedReader(e.getLogPath())) {
          os.lines()
                  .map(String::trim)
                  .filter(x -> x.startsWith("[ERROR]"))
                  .limit(10)
                  .forEach(logger::error);
        } catch (IOException ignored) {
        }

        throw new RuntimeException("Could not execute hyRTS properly");
      }
    }
  }

  private static Set<String> listTestClasses(Path surefireHome) throws IOException, XPathExpressionException {
    if (Files.notExists(surefireHome)) {
      return Collections.emptySet();
    }

    try (Stream<Path> files = Files.list(surefireHome)
            .filter(x -> x.getFileName().toString().endsWith(".xml"))) {
      Iterator<Path> iterator = files.iterator();
      Set<String> cases = new HashSet<>();
      while (iterator.hasNext()) {
        Path p = iterator.next();
        try (InputStream is = Files.newInputStream(p)) {
          InputSource iss = new InputSource(is);
          XPath xPath = XPathFactory.newInstance().newXPath();
          boolean ispassed = true;
          NodeList nodes = (NodeList) xPath
                  .evaluate(
                          "/testsuite/@failures | /testsuite/@errors | /testsuite/testcase",
                          iss,
                          XPathConstants.NODESET);
          for (int i = 0; i < nodes.getLength(); ++i) {
            Node n = nodes.item(i);
            switch (n.getLocalName()) {
              case "failures":
              case "errors":
                ispassed = ispassed && Integer.parseInt(n.getTextContent()) == 0;
                break;
              case "testcase":
                NamedNodeMap attrs = n.getAttributes();
                String name = attrs.getNamedItem("classname").getTextContent();
                cases.add(name);
                break;
            }
          }
        }
      }

      return cases;
    }
  }
}

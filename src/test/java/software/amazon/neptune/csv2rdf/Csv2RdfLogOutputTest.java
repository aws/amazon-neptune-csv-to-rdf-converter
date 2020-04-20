/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.neptune.csv2rdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;

@Tag("IntegrationTest")
@Tag("Csv2RdfLogOutputTest")
public class Csv2RdfLogOutputTest {

	private static final PrintStream STDOUT = System.out;
	private static final PrintStream STDERR = System.err;
	private static final ByteArrayOutputStream STDOUT_BAOS = new ByteArrayOutputStream();
	private static final ByteArrayOutputStream STDERR_BAOS = new ByteArrayOutputStream();

	private static final Path LOG_FILE = Paths.get("target", "csv2rdf.checkLogOutput.log");
	private static final String LOG_FILE_PROPERTY = "software.amazon.neptune.csv2rdf.log.file";
	private static final String LOG_IMMEDIATE_FLUSH_PROPERTY = "software.amazon.neptune.csv2rdf.log.immediateFlush";
	static {
		System.setProperty(LOG_FILE_PROPERTY, Paths.get("target", "csv2rdf.tests.log").toFile().getPath());
	}

	private static final File TARGET = new File("target");

	@BeforeAll
	public static void setup() {

		// load the real log configuration for this test instead of log4j2-test.xml
		System.setProperty("log4j.configurationFile", "classpath:log4j2.xml");
		// write log file to a dedicated file in target/
		System.setProperty(LOG_FILE_PROPERTY, LOG_FILE.toString());
		// flush immediately for writing the log file before checking its content
		System.setProperty(LOG_IMMEDIATE_FLUSH_PROPERTY, "true");

		System.setOut(new PrintStream(new MultiOutputStream(STDOUT, STDOUT_BAOS)));
		System.setErr(new PrintStream(new MultiOutputStream(STDERR, STDERR_BAOS)));

		LoggerFactory.getLogger(Csv2RdfLogOutputTest.class);
	}

	@AfterAll
	public static void tearDown() {

		System.setOut(STDOUT);
		System.setErr(STDERR);
	}

	@BeforeEach
	public void init() throws IOException {

		if (Files.exists(LOG_FILE)) {
			Files.write(LOG_FILE, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
		}
		STDOUT_BAOS.reset();
		STDERR_BAOS.reset();
	}

	@Test
	public void validateConfigParameter() throws IOException {

		File nonExistingProperties = new File(TARGET, "non-existing.properties");

		assertEquals(-1, new CommandLine(new Csv2Rdf()).execute("-c", nonExistingProperties.getPath(), "-i",
				TARGET.getPath(), "-o", TARGET.getPath()));

		final String[] lines = STDERR_BAOS.toString(StandardCharsets.UTF_8.name()).split("\\r?\\n");
		assertEquals(2, lines.length);
		assertEquals("CSV to RDF conversion failed.", lines[0]);
		assertTrue(lines[1].matches(
				"File for parameter <configuration file> does not exist: .*" + nonExistingProperties.getPath()));

		List<String> logLines = Files.readAllLines(LOG_FILE);
		assertTrue(logLines.size() > 2);
		assertTrue(logLines.get(0)
				.contains("ERROR software.amazon.neptune.csv2rdf.Csv2Rdf - CSV to RDF conversion failed."));
		assertTrue(logLines.get(1).contains("File for parameter <configuration file> does not exist:"));
	}

	/**
	 *
	 * Logging should be configured as:
	 * <ul>
	 * <li>INFO to stdout (but no DEBUG, no TRACE, no WARN, no ERROR)</li>
	 * <li>WARN to stderr (but no ERROR)</li>
	 * <li>ALL to amazon-neptune-csv2rdf.log</li>
	 * </ul>
	 *
	 * This test must be <b>run before any logger</b> has been initialized! Use the
	 * tag <em>Csv2RdfTest::checkLogOutput</em> to include or exclude this test.
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void checkLogOutput() throws IOException, InterruptedException {

		Logger log = LoggerFactory.getLogger(getClass());
		log.trace("trace");
		log.debug("debug");
		log.info("info");
		log.warn("warn");
		log.info("info2");
		log.error("error");
		log.warn("warn2");

		assertEquals(String.format("info%ninfo2%n"), STDOUT_BAOS.toString(StandardCharsets.UTF_8.name()));
		assertEquals(String.format("warn%nwarn2%n"), STDERR_BAOS.toString(StandardCharsets.UTF_8.name()));

		List<String> logLines = Files.readAllLines(LOG_FILE);
		assertEquals(5, logLines.size());
		assertTrue(logLines.get(0).matches(".*INFO .+? - info"));
		assertTrue(logLines.get(1).matches(".*WARN .+? - warn"));
		assertTrue(logLines.get(2).matches(".*INFO .+? - info2"));
		assertTrue(logLines.get(3).matches(".*ERROR .+? - error"));
		assertTrue(logLines.get(4).matches(".*WARN .+? - warn2"));
	}

	/**
	 *
	 * An output stream for multiplying the written bytes to multiple streams
	 *
	 */
	public static class MultiOutputStream extends OutputStream {

		private final OutputStream[] streams;

		public MultiOutputStream(OutputStream... streams) {
			this.streams = streams;
		}

		@Override
		public void write(int b) throws IOException {
			for (OutputStream s : streams) {
				s.write(b);
			}
		}

		@Override
		public void flush() throws IOException {
			for (OutputStream s : streams) {
				s.flush();
			}
		}

		@Override
		public void close() throws IOException {
			for (OutputStream s : streams) {
				s.close();
			}
		}
	}
}

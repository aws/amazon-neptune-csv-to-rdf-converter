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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import picocli.CommandLine;

@Tag("IntegrationTest")
public class Csv2RdfIntegrationTest {

	private static final String AIR_ROUTES_VERSION = "0.81";
	private static final Path AIR_ROUTES = Paths.get("src", "test", "air-routes");
	private static final Path INTEGRATION_TEST = Paths.get("src", "test", "integration-test");
	private static final Path CARDINALITY_TEST = Paths.get("src", "test", "cardinality-test");
	private static final Path EXAMPLE = Paths.get("src", "test", "example");
	private static final Path TARGET = Paths.get("target");
	private static final Path UNZIPPED_AIR_ROUTES = TARGET.resolve("air-routes-" + AIR_ROUTES_VERSION);

	@Test
	public void compareAirRoutesOutputFiles() throws IOException {

		unzip(AIR_ROUTES.resolve("air-routes-" + AIR_ROUTES_VERSION + ".zip"), UNZIPPED_AIR_ROUTES);

		int exitCode = new CommandLine(new Csv2Rdf()).execute("-c",
				AIR_ROUTES.resolve("air-routes.properties").toString(), "-i", UNZIPPED_AIR_ROUTES.toString(), "-o",
				TARGET.toString());

		Assertions.assertEquals(0, exitCode);

		this.assertThatAllLinesInFilesAreEqual(
				UNZIPPED_AIR_ROUTES.resolve("air-routes-" + AIR_ROUTES_VERSION + "-edges.nq.master"),
				TARGET.resolve("air-routes-" + AIR_ROUTES_VERSION + "-edges.nq"));
		this.assertThatAllLinesInFilesAreEqual(
				UNZIPPED_AIR_ROUTES.resolve("air-routes-" + AIR_ROUTES_VERSION + "-nodes.nq.master"),
				TARGET.resolve("air-routes-" + AIR_ROUTES_VERSION + "-nodes.nq"));
	}

	@Test
	public void smallIntegrationTest() throws IOException {

		int exitCode = new CommandLine(new Csv2Rdf()).execute("-c",
				INTEGRATION_TEST.resolve("integration-test.properties").toString(), "-i", INTEGRATION_TEST.toString(),
				"-o", TARGET.toString());

		assertEquals(0, exitCode);

		this.assertThatAllLinesInFilesAreEqual(INTEGRATION_TEST.resolve("integration-test-edges.nq.master"),
				TARGET.resolve("integration-test-edges.nq"));
		this.assertThatAllLinesInFilesAreEqual(INTEGRATION_TEST.resolve("integration-test-nodes.nq.master"),
				TARGET.resolve("integration-test-nodes.nq"));
	}

	@Test
	public void smallIntegrationTestUsingDefaultConfiguration() throws IOException {

		Path nestedSubdiretcory = TARGET.resolve("nested-integration").resolve("subdirectory");
		assertFalse(Files.exists(nestedSubdiretcory.getParent()));

		AssertionFailedError assertionFailedError = null;
		try {
			int exitCode = new CommandLine(new Csv2Rdf()).execute("-i", INTEGRATION_TEST.toString(), "-o",
					nestedSubdiretcory.toString());

			assertEquals(0, exitCode);

			this.assertThatAllLinesInFilesAreEqual(INTEGRATION_TEST.resolve("integration-test-edges.nq.master.default"),
					nestedSubdiretcory.resolve("integration-test-edges.nq"));
			this.assertThatAllLinesInFilesAreEqual(INTEGRATION_TEST.resolve("integration-test-nodes.nq.master.default"),
					nestedSubdiretcory.resolve("integration-test-nodes.nq"));
		} catch (AssertionFailedError e) {
			assertionFailedError = e;
		} finally {
			try {
				assertDoesNotThrow(() -> Files.delete(nestedSubdiretcory.resolve("integration-test-edges.nq")));
				assertDoesNotThrow(() -> Files.delete(nestedSubdiretcory.resolve("integration-test-nodes.nq")));
				assertDoesNotThrow(() -> Files.delete(nestedSubdiretcory));
				assertDoesNotThrow(() -> Files.delete(nestedSubdiretcory.getParent()));
			} catch (AssertionFailedError e2) {
				if (assertionFailedError != null) {
					assertionFailedError.addSuppressed(e2);
					throw assertionFailedError;
				}
				throw e2;
			}
			if (assertionFailedError != null) {
				throw assertionFailedError;
			}
		}
	}

	@Test
	public void smallExample() throws IOException {

		int exitCode = new CommandLine(new Csv2Rdf()).execute("-c", EXAMPLE.resolve("city.properties").toString(), "-i",
				EXAMPLE.toString(), "-o", TARGET.toString());

		assertEquals(0, exitCode);

		this.assertThatAllLinesInFilesAreEqual(EXAMPLE.resolve("city-edges.nq.master"),
				TARGET.resolve("city-edges.nq"));
		this.assertThatAllLinesInFilesAreEqual(EXAMPLE.resolve("city-nodes.nq.master"),
				TARGET.resolve("city-nodes.nq"));
	}

	/**
	 *
	 * Currently, each line of the CSV file is parsed individually, so property
	 * values defined on different lines for the same ID are not joined and
	 * cardinality constraints cannot be checked:
	 * <ul>
	 * <li>The statement {@code <vertex:1> <eproperty:since> "tomorrow" <dng:/>}
	 * should be rejected because egde properties have single cardinality.</li>
	 * <li>The result of the test should contain only one
	 * {@code <vertex:2> <edge:knows> <vertex:3> <vertex:1>} statement (however, RDF
	 * joins multiple equal statements into one).</li>
	 * <li>The statement
	 * {@code <vertex:3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <dt:/> <dng:/>}
	 * should not be generated because vertex 3 has a label.</li>
	 * </ul>
	 *
	 * @throws IOException
	 */
	@Test
	public void cardinalityTest() throws IOException {

		int exitCode = new CommandLine(new Csv2Rdf()).execute("-c", EXAMPLE.resolve("city.properties").toString(), "-i",
				CARDINALITY_TEST.toString(), "-o", TARGET.toString());

		assertEquals(0, exitCode);

		this.assertThatAllLinesInFilesAreEqual(CARDINALITY_TEST.resolve("cardinality-test-edges.nq.master"),
				TARGET.resolve("cardinality-test-edges.nq"));
		this.assertThatAllLinesInFilesAreEqual(CARDINALITY_TEST.resolve("cardinality-test-nodes.nq.master"),
				TARGET.resolve("cardinality-test-nodes.nq"));
	}

	/**
	 *
	 * Assert that two files are equal comparing line by line
	 *
	 * @param expected file with expected content
	 * @param actual   file with actual content
	 * @throws IOException
	 */
	private void assertThatAllLinesInFilesAreEqual(Path expected, Path actual) throws IOException {
		List<String> expectedLines = Files.readAllLines(expected);
		List<String> actualLines = Files.readAllLines(actual);

		Iterator<String> expectedIt = expectedLines.iterator();
		Iterator<String> actualIt = actualLines.iterator();

		while (expectedIt.hasNext() && actualIt.hasNext()) {
			assertEquals(expectedIt.next(), actualIt.next());
		}
		assertFalse(expectedIt.hasNext());
		assertFalse(actualIt.hasNext());
	}

	/**
	 *
	 * Unzip an archive
	 *
	 * @param zipFile         input file
	 * @param outputDirectory output directory, is created if it does not exist
	 * @throws IOException
	 */
	private void unzip(Path zipFile, Path outputDirectory) throws IOException {

		try {
			Files.createDirectory(outputDirectory);
		} catch (FileAlreadyExistsException e) {
			// continue
		}

		byte[] buffer = new byte[1024];

		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {

			ZipEntry zipEntry = zis.getNextEntry();

			while (zipEntry != null) {
				final Path zipEntryPath = outputDirectory.resolve(zipEntry.getName());
				if (!zipEntryPath.normalize().startsWith(outputDirectory.normalize())) {
					throw new IOException("Bad zip entry");
				}
				try (FileOutputStream fos = new FileOutputStream(
						zipEntryPath.toFile());) {
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
				}
				zis.closeEntry();
				zipEntry = zis.getNextEntry();
			}
		}
	}

}

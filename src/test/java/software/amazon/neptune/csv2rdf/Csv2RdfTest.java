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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import picocli.CommandLine;
import software.amazon.neptune.csv2rdf.Csv2Rdf.MavenVersionProvider;
import software.amazon.neptune.csv2rdf.Csv2RdfLogOutputTest.MultiOutputStream;

public class Csv2RdfTest {

	private static final Path INTEGRATION_TEST = Paths.get("src", "test", "integration-test");
	private static final Path TARGET = Paths.get("target");

	@Test
	public void printVersionInfo() {

		int exitCode = new CommandLine(new Csv2Rdf()).execute("--version");
		assertEquals(0, exitCode);
	}

	@Test
	public void getVersionInfo() {

		assertTrue(new MavenVersionProvider().getVersion()[0].matches("\\d+.\\d+.\\d+(-.+)?"));
	}

	@Test
	public void checkExistingFile() {

		File existingFile = Paths.get("src", "test", "inputDirectoryTest").resolve("ignore.txt").toFile();
		Csv2Rdf.validateFileParam(existingFile, "config");
	}

	@Test
	public void checkNonExistingFile() {

		File nonExistingFile = TARGET.resolve("non-existing-file.csv").toFile();
		Exception exception = assertThrows(Csv2RdfException.class,
				() -> Csv2Rdf.validateFileParam(nonExistingFile, "config"));
		assertTrue(exception.getMessage()
				.matches("File for parameter config does not exist: .*" + nonExistingFile.getPath()));
	}

	@Test
	public void checkFileButIsDirectory() {

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> Csv2Rdf.validateFileParam(TARGET.toFile(), "config"));
		assertTrue(exception.getMessage().matches("Parameter config does not point to a file: .*" + TARGET.toString()));
	}

	@Test
	public void checkExistingDirectory() {

		Csv2Rdf.validateDirectoryParam(TARGET.toFile(), "input", false);
	}

	@Test
	public void checkDirectoryButIsFile() {

		File existingFile = Paths.get("src", "test", "inputDirectoryTest").resolve("ignore.txt").toFile();
		Exception exception = assertThrows(Csv2RdfException.class,
				() -> Csv2Rdf.validateDirectoryParam(existingFile, "input", true));
		assertTrue(exception.getMessage()
				.matches("Parameter input does not point to a directory: .*" + existingFile.getPath()));
	}

	@Test
	public void checkNonExistingDirectory() {

		File nonExistingDir = TARGET.resolve("non-existing-dir").toFile();
		Exception exception = assertThrows(Csv2RdfException.class,
				() -> Csv2Rdf.validateDirectoryParam(nonExistingDir, "input", false));
		assertTrue(exception.getMessage()
				.matches("Directory for parameter input does not exist: .*" + nonExistingDir.getPath()));
	}

	@Test
	public void checkNonExistingDirectoryWithCreate() {

		File directoryToCreate = TARGET.resolve("directory-to-create").toFile();
		assertFalse(directoryToCreate.exists());

		AssertionFailedError assertionFailedError = null;
		try {
			Csv2Rdf.validateDirectoryParam(directoryToCreate, "output", true);
			assertTrue(directoryToCreate.isDirectory());
		} catch (AssertionFailedError e) {
			assertionFailedError = e;
		} finally {
			try {
				assertDoesNotThrow(() -> Files.delete(directoryToCreate.toPath()));
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
	public void checkNonExistingNestedDirectoryWithCreate() {

		File directoryToCreate = TARGET.resolve("nested").resolve("directory-to-create").toFile();
		assertFalse(directoryToCreate.getParentFile().exists());

		AssertionFailedError assertionFailedError = null;
		try {
			Csv2Rdf.validateDirectoryParam(directoryToCreate, "output", true);
			assertTrue(directoryToCreate.isDirectory());
		} catch (AssertionFailedError e) {
			assertionFailedError = e;
		} finally {
			try {
				assertDoesNotThrow(() -> Files.delete(directoryToCreate.toPath()));
				assertDoesNotThrow(() -> Files.delete(directoryToCreate.toPath().getParent()));
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
	public void checkNonExistingDirectoryWithCreateFails() throws Exception {

		File readOnlyDirectory = TARGET.resolve("read-ony-directory").toFile();
		AssertionFailedError assertionFailedError = null;
		try {
			assertTrue(readOnlyDirectory.mkdir(), "Prepatation of a read-only directory failed.");
			assertTrue(readOnlyDirectory.setReadOnly());
			File cannotBeCreated = new File(readOnlyDirectory, "cannot-be-created");
			Exception exception = assertThrows(Csv2RdfException.class,
					() -> Csv2Rdf.validateDirectoryParam(cannotBeCreated, "output", true));
			assertTrue(exception.getMessage()
					.matches("Directory for parameter output could not be created: .*" + cannotBeCreated.getPath()));
		} catch (AssertionFailedError e) {
			assertionFailedError = e;
		} finally {
			try {
				assertTrue(readOnlyDirectory.setWritable(true));
				assertDoesNotThrow(() -> Files.delete(readOnlyDirectory.toPath()));
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
	public void validateConfigParameter() throws UnsupportedEncodingException {

		File nonExistingProperties = TARGET.resolve("non-existing.properties").toFile();

		PrintStream stderr = System.err;
		ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();

		try {
			System.setErr(new PrintStream(new MultiOutputStream(stderr, stderrBytes)));
			assertEquals(-1, new CommandLine(new Csv2Rdf()).execute("-c", nonExistingProperties.getPath(), "-i",
					TARGET.toString(), "-o", TARGET.toString()));
		} finally {
			System.setErr(stderr);
		}

		String[] lines = stderrBytes.toString(StandardCharsets.UTF_8.name()).split("\\r?\\n");
		assertEquals(2, lines.length);
		assertEquals("CSV to RDF conversion failed.", lines[0]);
		assertTrue(lines[1].matches(
				"File for parameter <configuration file> does not exist: .*" + nonExistingProperties.getPath()));
	}

	@Test
	public void validateInputParameter() throws UnsupportedEncodingException {

		File nonExistingDirectory = TARGET.resolve("non-existing-directory").toFile();

		PrintStream stderr = System.err;
		ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();

		try {
			System.setErr(new PrintStream(new MultiOutputStream(stderr, stderrBytes)));

			assertEquals(-1, new CommandLine(new Csv2Rdf()).execute("-i", nonExistingDirectory.getPath(), "-o",
					TARGET.toString()));
		} finally {
			System.setErr(stderr);
		}

		String[] lines = stderrBytes.toString(StandardCharsets.UTF_8.name()).split("\\r?\\n");
		assertEquals(2, lines.length);
		assertEquals("CSV to RDF conversion failed.", lines[0]);
		assertTrue(lines[1].matches(
				"Directory for parameter <input directory> does not exist: .*" + nonExistingDirectory.getPath()));
	}

	@Test
	public void validateOutputParameter() throws UnsupportedEncodingException {

		File integrationTestProperties = INTEGRATION_TEST.resolve("integration-test.properties").toFile();

		PrintStream stderr = System.err;
		ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();

		try {
			System.setErr(new PrintStream(new MultiOutputStream(stderr, stderrBytes)));

			assertEquals(-1, new CommandLine(new Csv2Rdf()).execute("-i", TARGET.toString(), "-o",
					integrationTestProperties.getPath()));
		} finally {
			System.setErr(stderr);
		}

		String[] lines = stderrBytes.toString(StandardCharsets.UTF_8.name()).split("\\r?\\n");
		assertEquals(2, lines.length);
		assertEquals("CSV to RDF conversion failed.", lines[0]);
		assertTrue(lines[1].matches("Parameter <output directory> does not point to a directory: .*"
				+ integrationTestProperties.getPath()));
	}
}

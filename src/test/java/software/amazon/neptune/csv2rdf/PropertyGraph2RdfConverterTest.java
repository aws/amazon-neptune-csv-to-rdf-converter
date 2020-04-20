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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PropertyGraph2RdfConverterTest {

	private static final Path INPUT_DIRECTORY = Paths.get("src", "test", "inputDirectoryTest");
	private static final Path TARGET = Paths.get("target");

	private PropertyGraph2RdfConverter converter;

	@BeforeEach
	public void init() {

		converter = new PropertyGraph2RdfConverter(null);
	}

	@Test
	public void readInputDirectoryWithoutTrailingPathSeparator() {

		String inputDirectoryString = INPUT_DIRECTORY.toString();
		assertFalse(inputDirectoryString.endsWith(File.separator));

		List<File> files = converter.listPropertyGraphFiles(new File(inputDirectoryString));
		assertEquals(2, files.size());
		assertTrue(files.contains(INPUT_DIRECTORY.resolve("test1.csv").toFile()));
		assertTrue(files.contains(INPUT_DIRECTORY.resolve("test2.csv").toFile()));
	}

	@Test
	public void readInputDirectoryWithTrailingPathSeparator() {

		String inputDirectoryString = INPUT_DIRECTORY.toString();
		assertFalse(inputDirectoryString.endsWith(File.separator));
		inputDirectoryString += File.separator;

		List<File> files = converter.listPropertyGraphFiles(new File(inputDirectoryString));
		assertEquals(2, files.size());
		assertTrue(files.contains(INPUT_DIRECTORY.resolve("test1.csv").toFile()));
		assertTrue(files.contains(INPUT_DIRECTORY.resolve("test2.csv").toFile()));
	}

	@Test
	public void customInputFileExtension() {

		converter.setInputFileExtension("txt");

		List<File> files = converter.listPropertyGraphFiles(INPUT_DIRECTORY.toFile());
		assertEquals(1, files.size());
		assertTrue(files.contains(INPUT_DIRECTORY.resolve("ignore.txt").toFile()));
	}

	@Test
	public void inputDirectoryEmpty() {

		File emptyDir = TARGET.resolve("empty-dir").toFile();
		emptyDir.mkdir();

		Csv2RdfException exception = assertThrows(Csv2RdfException.class,
				() -> converter.listPropertyGraphFiles(emptyDir));
		assertTrue(exception.getMessage()
				.matches("No files with extension csv found at: .*" + Pattern.quote(emptyDir.getPath())));
	}

	@Test
	public void inputDirectoryDoesNotExist() {

		File nonExistingDir = TARGET.resolve("non-existing-dir").toFile();

		Csv2RdfException exception = assertThrows(Csv2RdfException.class,
				() -> converter.listPropertyGraphFiles(nonExistingDir));
		assertTrue(exception.getMessage()
				.matches("Could not read from input directory: .*" + Pattern.quote(nonExistingDir.getPath())));
	}

	@Test
	public void getOutputFileWithoutTrailingPathSeparator() {

		String outputDirectoryString = TARGET.toString();
		assertFalse(outputDirectoryString.endsWith(File.separator));

		File outputFile = converter.getRdfFile(new File(outputDirectoryString), new File("test.csv"));
		assertEquals(TARGET.resolve("test.nq").toFile(), outputFile);
	}

	@Test
	public void getOutputFileWithTrailingPathSeparator() {

		String outputDirectoryString = TARGET.toString();
		assertFalse(outputDirectoryString.endsWith(File.separator));
		outputDirectoryString += File.separator;

		File outputFile = converter.getRdfFile(new File(outputDirectoryString), new File("test.csv"));
		assertEquals(TARGET.resolve("test.nq").toFile(), outputFile);
	}

}

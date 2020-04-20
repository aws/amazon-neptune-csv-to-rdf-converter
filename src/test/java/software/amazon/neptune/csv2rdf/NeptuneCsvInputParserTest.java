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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvSetValuedUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphVertex;

public class NeptuneCsvInputParserTest {

	@Test
	public void emptyCsvIsRejected() {

		File empty = Paths.get("src", "test", "inputParserTest", "empty.csv").toFile();
		Csv2RdfException exception = assertThrows(Csv2RdfException.class, () -> new NeptuneCsvInputParser(empty));
		assertEquals("No header column found in input CSV file!", exception.getMessage());
	}

	@Test
	public void validUtf8IsSuccessful() {

		File invalidUtf8 = Paths.get("src", "test", "inputParserTest", "valid-utf8.csv").toFile();

		try (NeptuneCsvInputParser parser = new NeptuneCsvInputParser(invalidUtf8)) {
			NeptuneCsvUserDefinedProperty property = ((NeptunePropertyGraphVertex) parser.next())
					.getUserDefinedProperties().get(0);
			assertEquals("Bärbel", ((NeptuneCsvSetValuedUserDefinedProperty) property).getValues().iterator().next());
		}
	}

	@Test
	public void invalidUtf8CharacterIsReplaced() {

		File invalidUtf8 = Paths.get("src", "test", "inputParserTest", "iso-8859-15-with-E4-8bit-character.csv")
				.toFile();

		try (NeptuneCsvInputParser parser = new NeptuneCsvInputParser(invalidUtf8)) {
			NeptuneCsvUserDefinedProperty property = ((NeptunePropertyGraphVertex) parser.next())
					.getUserDefinedProperties().get(0);
			assertEquals("B�rbel", ((NeptuneCsvSetValuedUserDefinedProperty) property).getValues().iterator().next());
		}
	}

	@Test
	public void missingFileIsRejected() {

		File notExistingFile = Paths.get("src", "test", "inputParserTest", "not-existing.csv").toFile();
		Csv2RdfException exception = assertThrows(Csv2RdfException.class,
				() -> new NeptuneCsvInputParser(notExistingFile));
		assertTrue(exception.getMessage()
				.matches("Error creating input stream for CSV file .*" + notExistingFile.getPath()));
	}

}

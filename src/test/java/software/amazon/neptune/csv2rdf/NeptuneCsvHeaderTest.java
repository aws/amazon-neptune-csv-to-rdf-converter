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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.neptune.csv2rdf.NeptuneCsvHeader.NeptuneCsvEdgeHeader;
import software.amazon.neptune.csv2rdf.NeptuneCsvHeader.NeptuneCsvVertexHeader;
import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.DataType;

public class NeptuneCsvHeaderTest {

	private CSVFormat csvFormat;

	@BeforeEach
	public void init() {
		csvFormat = NeptuneCsvInputParser.createCSVFormat();
	}

	@Test
	public void invalidColumnIsRejected() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id:person")).iterator().next();
		Csv2RdfException exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		assertEquals("Invalid system column encountered: ~id:person", exception.getMessage());
	}

	@Test
	public void nullColumnIsRejected() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id,,~label")).iterator().next();
		Csv2RdfException exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		assertEquals("Empty column header encountered.", exception.getMessage());
	}

	@Test
	public void parseVertexHeaderIsSucsessful() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("code:, ~iD , :name;~id:ByTE")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		assertNull(header.getLabel());
		assertTrue(header instanceof NeptuneCsvVertexHeader);
		assertEquals(2, header.getUserDefinedTypes().size());

		NeptuneCsvUserDefinedColumn code = header.getUserDefinedTypes().get(0);
		assertEquals("code:", code.getName());
		assertEquals(DataType.STRING, code.getDataType());

		assertEquals(1, header.getId());

		NeptuneCsvUserDefinedColumn name = header.getUserDefinedTypes().get(1);
		assertEquals(":name;~id", name.getName());
		assertEquals(DataType.BYTE, name.getDataType());
	}

	@Test
	public void vertexHeaderWithEmptyColumnIsRejected() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,,name")).iterator().next();
		Exception exception = Assertions.assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		Assertions.assertEquals("Empty column header encountered.", exception.getMessage());

		CSVRecord record2 = csvFormat.parse(new StringReader("~id,~label,   ,name")).iterator().next();
		Exception exception2 = Assertions.assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record2));
		Assertions.assertEquals("Empty column header encountered.", exception2.getMessage());
	}

	@Test
	public void fromAndToMustBothBePresent() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,name")).iterator().next();
		Exception exception = Assertions.assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		Assertions.assertEquals("An edge requires a ~to field.", exception.getMessage());

		CSVRecord record2 = csvFormat.parse(new StringReader("~id,~label,~to,name")).iterator().next();
		Exception exception2 = Assertions.assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record2));
		Assertions.assertEquals("An edge requires a ~from field.", exception2.getMessage());
	}

	@Test
	public void parseEdgeHeaderIsSucsessful() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("code:int, ~Label , ~id,~to, name:String, ~from"))
				.iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		assertTrue(header instanceof NeptuneCsvEdgeHeader);
		assertEquals(2, header.getUserDefinedTypes().size());

		NeptuneCsvUserDefinedColumn code = header.getUserDefinedTypes().get(0);
		assertEquals("code", code.getName());
		assertEquals(DataType.INT, code.getDataType());

		assertEquals(2, header.getId());

		assertEquals(1, header.getLabel());

		assertEquals(3, ((NeptuneCsvEdgeHeader) header).getTo());

		NeptuneCsvUserDefinedColumn name = header.getUserDefinedTypes().get(1);
		assertEquals("name", name.getName());
		assertEquals(DataType.STRING, name.getDataType());

		assertEquals(5, ((NeptuneCsvEdgeHeader) header).getFrom());
	}

	@Test
	public void edgeHeaderWithEmptyColumnIsRejected() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,~to,,name")).iterator().next();
		Exception exception = Assertions.assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		Assertions.assertEquals("Empty column header encountered.", exception.getMessage());

		CSVRecord record2 = csvFormat.parse(new StringReader("~id,~label,~from,~to,   ,name")).iterator().next();
		Exception exception2 = Assertions.assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record2));
		Assertions.assertEquals("Empty column header encountered.", exception2.getMessage());
	}

	@Test
	public void multipleIdNotAllowed() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~id,name")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));

		assertEquals("Found duplicate field: ~id", exception.getMessage());
	}

	@Test
	public void multipleFromNotAllowed() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,name,~from,~to")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));

		assertEquals("Found duplicate field: ~from", exception.getMessage());
	}

	@Test
	public void multipleToNotAllowed() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,name,~to,~to")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));

		assertEquals("Found duplicate field: ~to", exception.getMessage());
	}

	@Test
	public void multipleVertexLabelNotAllowed() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,name,~label")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));

		assertEquals("Found duplicate field: ~label", exception.getMessage());
	}

	@Test
	public void multipleEdgeLabelNotAllowed() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,name,~label,~from,~to")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));

		assertEquals("Found duplicate field: ~label", exception.getMessage());
	}

	@Test
	public void duplicateVertexSimpleFieldsNotAllowed() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,name,code,name")).iterator().next();
		Csv2RdfException exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		assertEquals("Found duplicate field: name", exception.getMessage());
	}

	@Test
	public void duplicateVertexTypedFieldsNotAllowed() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,name:string,code,name")).iterator().next();
		Csv2RdfException exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		assertEquals("Found duplicate field: name", exception.getMessage());
	}

	@Test
	public void duplicateEdgeSimpleFieldsNotAllowed() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,name,code,name,~from,~to")).iterator().next();
		Csv2RdfException exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		assertEquals("Found duplicate field: name", exception.getMessage());
	}

	@Test
	public void duplicateEdgeTypedFieldsNotAllowed() throws IOException {

		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,name:string,code,name:byte,~from,~to"))
				.iterator().next();
		Csv2RdfException exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));
		assertEquals("Found duplicate field: name:byte", exception.getMessage());
	}

	@Test
	public void arrayTypesNotAllowedForEdges() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,~to,name:string[]")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));

		assertEquals("Array types are not allowed for edges: name", exception.getMessage());
	}

	@Test
	public void setValuedTypesNotAllowedForEdges() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,~to,name:string(set)")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvHeader.parse(record));

		assertEquals("Set-valued types are not allowed for edges: name", exception.getMessage());
	}

}

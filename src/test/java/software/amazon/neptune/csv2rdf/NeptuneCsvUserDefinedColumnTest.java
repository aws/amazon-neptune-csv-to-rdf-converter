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

import org.junit.jupiter.api.Test;

import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.Cardinality;
import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.DataType;

public class NeptuneCsvUserDefinedColumnTest {

	@Test
	public void userField() {
		NeptuneCsvUserDefinedColumn id = NeptuneCsvUserDefinedColumn.parse("id");
		assertEquals("id", id.getName());
		assertTrue(id instanceof NeptuneCsvUserDefinedColumn);
		assertEquals(DataType.STRING, id.getDataType());
	}

	@Test
	public void userFieldWithDatatype() {
		NeptuneCsvUserDefinedColumn id = NeptuneCsvUserDefinedColumn.parse("id:byte");
		assertEquals("id", id.getName());
		assertTrue(id instanceof NeptuneCsvUserDefinedColumn);
		assertEquals(DataType.BYTE, id.getDataType());
	}

	@Test
	public void allDataTypes() {

		assertEquals(DataType.BYTE, NeptuneCsvUserDefinedColumn.parse("id:byte").getDataType());
		assertEquals(DataType.BOOL, NeptuneCsvUserDefinedColumn.parse("id:boolean").getDataType());
		assertEquals(DataType.BOOL, NeptuneCsvUserDefinedColumn.parse("id:bool").getDataType());
		assertEquals(DataType.SHORT, NeptuneCsvUserDefinedColumn.parse("id:short").getDataType());
		assertEquals(DataType.INT, NeptuneCsvUserDefinedColumn.parse("id:int").getDataType());
		assertEquals(DataType.INT, NeptuneCsvUserDefinedColumn.parse("id:integer").getDataType());
		assertEquals(DataType.LONG, NeptuneCsvUserDefinedColumn.parse("id:long").getDataType());
		assertEquals(DataType.FLOAT, NeptuneCsvUserDefinedColumn.parse("id:float").getDataType());
		assertEquals(DataType.DOUBLE, NeptuneCsvUserDefinedColumn.parse("id:double").getDataType());
		assertEquals(DataType.STRING, NeptuneCsvUserDefinedColumn.parse("id:string").getDataType());
		assertEquals(DataType.DATETIME, NeptuneCsvUserDefinedColumn.parse("id:date").getDataType());
		assertEquals(DataType.DATETIME, NeptuneCsvUserDefinedColumn.parse("id:datetime").getDataType());
	}

	@Test
	public void invalidDatatypeIsRejected() {

		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvUserDefinedColumn.parse("id:bla"));
		assertEquals("Invalid data type encountered for header: id:bla", exception.getMessage());
	}

	@Test
	public void allCardinalities() {

		NeptuneCsvUserDefinedColumn type1 = NeptuneCsvUserDefinedColumn.parse("id:Byte");
		assertEquals(DataType.BYTE, type1.getDataType());
		assertEquals(Cardinality.DEFAULT, type1.getCardinality());
		assertFalse(type1.isArray());

		NeptuneCsvUserDefinedColumn type4 = NeptuneCsvUserDefinedColumn.parse("id:byte(set)");
		assertEquals(DataType.BYTE, type4.getDataType());
		assertEquals(Cardinality.SET, type4.getCardinality());
		assertFalse(type4.isArray());

		NeptuneCsvUserDefinedColumn type6 = NeptuneCsvUserDefinedColumn.parse("id:byte[]");
		assertEquals(DataType.BYTE, type6.getDataType());
		assertEquals(Cardinality.SET, type6.getCardinality());
		assertTrue(type6.isArray());

		NeptuneCsvUserDefinedColumn type5 = NeptuneCsvUserDefinedColumn.parse("id:byte(Set)[]");
		assertEquals(DataType.BYTE, type5.getDataType());
		assertEquals(Cardinality.SET, type5.getCardinality());
		assertTrue(type5.isArray());

		NeptuneCsvUserDefinedColumn type2 = NeptuneCsvUserDefinedColumn.parse("id:byte(single)");
		assertEquals(DataType.BYTE, type2.getDataType());
		assertEquals(Cardinality.SINGLE, type2.getCardinality());
		assertFalse(type2.isArray());

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> NeptuneCsvUserDefinedColumn.parse("id:byte(single)[]"));
		assertEquals("Type definition cannot be single cardinality but array: id", exception.getMessage());
	}

	@Test
	public void invalidFieldIsRejected() {

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> NeptuneCsvUserDefinedColumn.parse(" -*__, -*_. "));
		assertEquals("Invalid column encountered while parsing header: -*__, -*_.", exception.getMessage());
	}

	@Test
	public void invalidField2IsRejected() {

		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvUserDefinedColumn.parse("f:[]bla"));
		assertEquals("Invalid column encountered while parsing header: f:[]bla", exception.getMessage());
	}
}

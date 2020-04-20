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

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.DataType;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvSetValuedUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvSingleValuedUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvUserDefinedArrayProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphEdge;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphVertex;

public class NeptunePropertyGraphElementTest {

	private CSVFormat csvFormat;

	@BeforeEach
	public void init() {
		csvFormat = NeptuneCsvInputParser.createCSVFormat();
	}

	@Test
	public void vertex() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,relation")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader("1,name,next")).iterator().next();
		NeptunePropertyGraphVertex vertex = (NeptunePropertyGraphVertex) NeptuneCsvInputParser.create(header, data);

		assertEquals("1", vertex.getId());
		assertEquals(1, vertex.getLabels().size());
		assertEquals("name", vertex.getLabels().get(0));
		assertEquals(1, vertex.getUserDefinedProperties().size());
		NeptuneCsvUserDefinedProperty columnValue = vertex.getUserDefinedProperties().get(0);
		assertEquals("relation", columnValue.getName());
		assertEquals(1, columnValue.getValues().size());
		assertEquals("next", columnValue.getValues().iterator().next());
	}

	@Test
	public void vertexWithArrayProperty() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,relation:string[],costs:int(set)[]")).iterator()
				.next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader("1,name,next;prev,30;45;93")).iterator().next();
		NeptunePropertyGraphVertex vertex = (NeptunePropertyGraphVertex) NeptuneCsvInputParser.create(header, data);

		assertEquals("1", vertex.getId());
		assertEquals(1, vertex.getLabels().size());
		assertEquals("name", vertex.getLabels().get(0));
		assertEquals(2, vertex.getUserDefinedProperties().size());

		NeptuneCsvUserDefinedProperty relation = vertex.getUserDefinedProperties().get(0);
		assertEquals("relation", relation.getName());
		assertEquals(2, relation.getValues().size());
		Iterator<String> ri = relation.getValues().iterator();
		assertEquals("next", ri.next());
		assertEquals("prev", ri.next());

		NeptuneCsvUserDefinedProperty costs = vertex.getUserDefinedProperties().get(1);
		assertEquals("costs", costs.getName());
		assertEquals(3, costs.getValues().size());
		Iterator<String> ci = costs.getValues().iterator();
		assertEquals("30", ci.next());
		assertEquals("45", ci.next());
		assertEquals("93", ci.next());
	}

	@Test
	public void missingVertexIdIsRejected() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader(",name")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvInputParser.create(header, data));

		assertEquals("Vertex or edge ID must not be null or empty.", exception.getMessage());
	}

	@Test
	public void edge() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,~to,relation")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader("1,name,2,3,next")).iterator().next();
		NeptunePropertyGraphEdge edge = (NeptunePropertyGraphEdge) NeptuneCsvInputParser.create(header, data);

		assertEquals("1", edge.getId());
		assertEquals("2", edge.getFrom());
		assertEquals("3", edge.getTo());
		assertTrue(edge.hasLabel());
		assertEquals("name", edge.getLabel());
		assertEquals(1, edge.getUserDefinedProperties().size());
		NeptunePropertyGraphElement.NeptuneCsvSingleValuedUserDefinedProperty columnValue = edge
				.getUserDefinedProperties().get(0);
		assertEquals("relation", columnValue.getName());
		assertEquals("next", columnValue.getValue());
	}

	@Test
	public void missingEdgeIdIsRejected() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,~to")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader(",name,2,3")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvInputParser.create(header, data));

		assertEquals("Vertex or edge ID must not be null or empty.", exception.getMessage());
	}

	@Test
	public void missingFromIsRejected() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,~to")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader("1,name,,3")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvInputParser.create(header, data));

		assertEquals("Value for ~from is missing at edge 1.", exception.getMessage());
	}

	@Test
	public void missingToISRejected() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,~to")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader("1,name,2,")).iterator().next();
		Exception exception = assertThrows(Csv2RdfException.class, () -> NeptuneCsvInputParser.create(header, data));

		assertEquals("Value for ~to is missing at edge 1.", exception.getMessage());
	}

	@Test
	public void vertexLabelIsOptional() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader("1,")).iterator().next();
		NeptunePropertyGraphVertex vertex = (NeptunePropertyGraphVertex) NeptuneCsvInputParser.create(header, data);

		assertEquals("1", vertex.getId());
		assertTrue(vertex.getLabels().isEmpty());
		assertTrue(vertex.getUserDefinedProperties().isEmpty());
	}

	@Test
	public void edgeLabelIsOptional() throws IOException {
		CSVRecord record = csvFormat.parse(new StringReader("~id,~label,~from,~to")).iterator().next();
		NeptuneCsvHeader header = NeptuneCsvHeader.parse(record);

		CSVRecord data = csvFormat.parse(new StringReader("1,,2,3")).iterator().next();
		NeptunePropertyGraphEdge edge = (NeptunePropertyGraphEdge) NeptuneCsvInputParser.create(header, data);

		assertEquals("1", edge.getId());
		assertEquals("2", edge.getFrom());
		assertEquals("3", edge.getTo());
		assertFalse(edge.hasLabel());
		assertTrue(edge.getUserDefinedProperties().isEmpty());
	}

	@Test
	public void vertexRejectsNullLabels() {

		NeptunePropertyGraphVertex vertex = new NeptunePropertyGraphVertex("test");
		String label = null;
		Exception exception = assertThrows(Csv2RdfException.class, () -> vertex.add(label));
		assertEquals("Vertex labels must not be null or empty.", exception.getMessage());
	}

	@Test
	public void vertexRejectsEmptyLabels() {

		NeptunePropertyGraphVertex vertex = new NeptunePropertyGraphVertex("test");
		String label = "";
		Exception exception = assertThrows(Csv2RdfException.class, () -> vertex.add(label));
		assertEquals("Vertex labels must not be null or empty.", exception.getMessage());
	}

	@Test
	public void arrayIgnoresNullAndEmptyValues() {

		assertTrue(new NeptuneCsvUserDefinedArrayProperty("test", DataType.INT, "").getValues().isEmpty());
		NeptuneCsvUserDefinedArrayProperty multivaluedProperty = new NeptuneCsvUserDefinedArrayProperty("test",
				DataType.INT, ";a;b;;c;");
		assertEquals(3, multivaluedProperty.getValues().size());
		Iterator<String> it = multivaluedProperty.getValues().iterator();
		assertEquals("a", it.next());
		assertEquals("b", it.next());
		assertEquals("c", it.next());
	}

	@Test
	public void setValuedPropertyHasUniqueValues() {

		NeptuneCsvSetValuedUserDefinedProperty property = new NeptuneCsvSetValuedUserDefinedProperty("test",
				DataType.INT, "1");
		property.add("3");
		property.add("3");
		property.add("4");
		property.add("2");
		property.add("4");
		assertEquals(4, property.getValues().size());
		Iterator<String> it = property.getValues().iterator();
		assertEquals("1", it.next());
		assertEquals("3", it.next());
		assertEquals("4", it.next());
		assertEquals("2", it.next());
	}

	@Test
	public void arrayPropertySplitsMultipleValues() {

		NeptuneCsvUserDefinedArrayProperty property = new NeptuneCsvUserDefinedArrayProperty("test", DataType.INT,
				"1;2");
		property.add("3;4");
		assertEquals(4, property.getValues().size());
		Iterator<String> it = property.getValues().iterator();
		assertEquals("1", it.next());
		assertEquals("2", it.next());
		assertEquals("3", it.next());
		assertEquals("4", it.next());
	}

	@Test
	public void arrayPropertyAddsSingleValues() {

		NeptuneCsvUserDefinedArrayProperty property = new NeptuneCsvUserDefinedArrayProperty("test", DataType.INT, "1");
		property.add("2");
		property.add("1");
		assertEquals(2, property.getValues().size());
		Iterator<String> it = property.getValues().iterator();
		assertEquals("1", it.next());
		assertEquals("2", it.next());
	}

	@Test
	public void singleValuedPropertyHasOneValue() {

		NeptuneCsvSingleValuedUserDefinedProperty property = new NeptuneCsvSingleValuedUserDefinedProperty("test",
				DataType.INT, "1");
		assertEquals("1", property.getValue());
		assertEquals(1, property.getValues().size());
		Iterator<String> it = property.getValues().iterator();
		assertEquals("1", it.next());
	}
}

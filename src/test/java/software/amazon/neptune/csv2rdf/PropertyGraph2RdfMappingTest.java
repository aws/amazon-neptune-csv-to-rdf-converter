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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.DataType;

@SuppressWarnings("serial")
public class PropertyGraph2RdfMappingTest {

	private PropertyGraph2RdfMapping mapping;

	@BeforeEach
	public void init() {

		mapping = new PropertyGraph2RdfMapping();
		mapping.setTypeNamespace("tn:");
		mapping.setVertexNamespace("vn:");
		mapping.setEdgeNamespace("en:");
		mapping.setVertexPropertyNamespace("vpn:");
		mapping.setEdgePropertyNamespace("epn:");
		mapping.setDefaultNamedGraph("dng:a");
		mapping.setDefaultType("dt:a");
	}

	@Test
	public void invalidJavaUriSetDefaultNamedGraph() {

		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.setDefaultNamedGraph("dgn:"));
		assertEquals("Invalid resource URI <dgn:> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme-specific part at index 4: dgn:", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIriSetDefaultNamedGraph() {

		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.setDefaultNamedGraph("dgn"));
		assertEquals("Invalid resource URI <dgn> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: dgn", exception.getCause().getMessage());
	}

	@Test
	public void invalidJavaUriSetDefaultType() {

		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.setDefaultType("dt:"));
		assertEquals("Invalid resource URI <dt:> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme-specific part at index 3: dt:", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIriSetDefaultType() {

		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.setDefaultType("dt"));
		assertEquals("Invalid resource URI <dt> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: dt", exception.getCause().getMessage());
	}

	@Test
	public void invalidJavaUriTypeIri() {

		mapping.setTypeNamespace(":tn");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.typeIri("type"));
		assertEquals("Invalid resource URI <:tnType> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme name at index 0: :tnType", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIriTypeIri() {

		mapping.setTypeNamespace("tn");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.typeIri("type"));
		assertEquals("Invalid resource URI <tnType> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: tnType", exception.getCause().getMessage());
	}

	@Test
	public void invalidJavaUriVertexIri() {

		mapping.setVertexNamespace(":vn");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.vertexIri("vertex"));
		assertEquals("Invalid resource URI <:vnvertex> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme name at index 0: :vnvertex", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIriVertexIri() {

		mapping.setVertexNamespace("vn");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.vertexIri("vertex"));
		assertEquals("Invalid resource URI <vnvertex> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: vnvertex", exception.getCause().getMessage());
	}

	@Test
	public void invalidJavaUriEdgeIri() {

		mapping.setEdgeNamespace(":en");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.edgeIri("edge"));
		assertEquals("Invalid resource URI <:enedge> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme name at index 0: :enedge", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIriEdgeIri() {

		mapping.setEdgeNamespace("en");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.edgeIri("edge"));
		assertEquals("Invalid resource URI <enedge> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: enedge", exception.getCause().getMessage());
	}

	@Test
	public void invalidJavaUriVertexPopertyIri() {

		mapping.setVertexPropertyNamespace(":vpn");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.vertexPropertyIri("vprop"));
		assertEquals("Invalid resource URI <:vpnvprop> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme name at index 0: :vpnvprop", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIriVertexPropertyIri() {

		mapping.setVertexPropertyNamespace("vpn");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.vertexPropertyIri("vprop"));
		assertEquals("Invalid resource URI <vpnvprop> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: vpnvprop", exception.getCause().getMessage());
	}

	@Test
	public void invalidJavaUriEdgePopertyIri() {

		mapping.setEdgePropertyNamespace(":epn");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.edgePropertyIri("eprop"));
		assertEquals("Invalid resource URI <:epneprop> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme name at index 0: :epneprop", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIriEdgePropertyIri() {

		mapping.setEdgePropertyNamespace("epn");
		Exception exception = assertThrows(Csv2RdfException.class, () -> mapping.edgePropertyIri("eprop"));
		assertEquals("Invalid resource URI <epneprop> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: epneprop", exception.getCause().getMessage());
	}

	@Test
	public void invalidJavaUriMapPropertyValue2RdfResource() {

		String property = "word";
		mapping.getPgProperty2RdfResourcePattern().put(property, ":bad{{VALUE}}");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> mapping.getVertex2RdfMapping().mapPropertyValue2RdfResource(property, "deleyite"));
		assertEquals("Invalid resource URI <:baddeleyite> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme name at index 0: :baddeleyite", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIriMapPropertyValue2RdfResource() {

		String property = "word";
		mapping.getPgProperty2RdfResourcePattern().put(property, "bad{{VALUE}}");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> mapping.getVertex2RdfMapping().mapPropertyValue2RdfResource(property, "deleyite"));
		assertEquals("Invalid resource URI <baddeleyite> generated when mapping to RDF.", exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: baddeleyite", exception.getCause().getMessage());
	}

	@Test
	public void emptyType() {

		mapping.setTypeNamespace("tn://types/");
		assertEquals("tn://types/", mapping.typeIri("").stringValue());
	}

	@Test
	public void tinyType() {

		mapping.setTypeNamespace("tn://types/");
		assertEquals("tn://types/T", mapping.typeIri("t").stringValue());
	}

	@Test
	public void irisAreEncoded() {

		assertEquals("tn:%7BHeiz%C3%B6lr%C3%BCcksto%C3%9Fabd%C3%A4mpfung%7D",
				mapping.typeIri("{Heizölrückstoßabdämpfung}").stringValue());
		assertEquals("vn:%7BHeiz%C3%B6lr%C3%BCcksto%C3%9Fabd%C3%A4mpfung%7D",
				mapping.vertexIri("{Heizölrückstoßabdämpfung}").stringValue());
		assertEquals("en:%7BHeiz%C3%B6lr%C3%BCcksto%C3%9Fabd%C3%A4mpfung%7D",
				mapping.edgeIri("{Heizölrückstoßabdämpfung}").stringValue());
		assertEquals("vpn:%7BHeiz%C3%B6lr%C3%BCcksto%C3%9Fabd%C3%A4mpfung%7D",
				mapping.vertexPropertyIri("{Heizölrückstoßabdämpfung}").stringValue());
		assertEquals("epn:%7BHeiz%C3%B6lr%C3%BCcksto%C3%9Fabd%C3%A4mpfung%7D",
				mapping.edgePropertyIri("{Heizölrückstoßabdämpfung}").stringValue());
		assertEquals("dng:a", mapping.getDefaultNamedGraph().stringValue());
		assertEquals("dt:a", mapping.getDefaultType().stringValue());
	}

	@Test
	public void literalsAreNotEncoded() {

		assertEquals("\"{Heizölrückstoßabdämpfung}\"^^<http://www.w3.org/2001/XMLSchema#boolean>",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.BOOL).toString());
		assertEquals("\"{Heizölrückstoßabdämpfung}\"^^<http://www.w3.org/2001/XMLSchema#byte>",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.BYTE).toString());
		assertEquals("\"{Heizölrückstoßabdämpfung}\"^^<http://www.w3.org/2001/XMLSchema#date>",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.DATETIME).toString());
		assertEquals("\"{Heizölrückstoßabdämpfung}\"^^<http://www.w3.org/2001/XMLSchema#double>",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.DOUBLE).toString());
		assertEquals("\"{Heizölrückstoßabdämpfung}\"^^<http://www.w3.org/2001/XMLSchema#float>",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.FLOAT).toString());
		assertEquals("\"{Heizölrückstoßabdämpfung}\"^^<http://www.w3.org/2001/XMLSchema#integer>",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.INT).toString());
		assertEquals("\"{Heizölrückstoßabdämpfung}\"^^<http://www.w3.org/2001/XMLSchema#long>",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.LONG).toString());
		assertEquals("\"{Heizölrückstoßabdämpfung}\"^^<http://www.w3.org/2001/XMLSchema#short>",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.SHORT).toString());
		assertEquals("\"{Heizölrückstoßabdämpfung}\"",
				mapping.value("{Heizölrückstoßabdämpfung}", DataType.STRING).toString());
	}

	@Test
	public void propertyGraphVertexType2InstanceLabelMapping() {

		String vertexType = "country";
		String instanceLabelProperty = "code";
		Map<String, String> pgVertexType2InstanceLabelProperty = new HashMap<String, String>() {
			{
				put(vertexType, instanceLabelProperty);
			}
		};

		assertNull(mapping.getVertex2RdfMapping().getPropertyForRdfsLabel(vertexType));

		mapping.setPgVertexType2PropertyForRdfsLabel(pgVertexType2InstanceLabelProperty);
		assertEquals(instanceLabelProperty, mapping.getVertex2RdfMapping().getPropertyForRdfsLabel(vertexType));

		assertNull(mapping.getVertex2RdfMapping().getPropertyForRdfsLabel("city"));
	}

	@Test
	public void convertPropertyValue2RdfResource() {

		String property = "word";
		String value = "{Heizölrückstoßabdämpfung}";
		String pattern = "http://example.org/resource/word/{{VALUE}}";
		String resource = "http://example.org/resource/word/%7BHeiz%C3%B6lr%C3%BCcksto%C3%9Fabd%C3%A4mpfung%7D";
		Map<String, String> pgProperty2RdfResourcePattern = new HashMap<String, String>() {
			{
				put(property, pattern);
			}
		};

		assertFalse(mapping.getVertex2RdfMapping().containsRdfResourcePatternForProperty(property));
		assertNull(mapping.getVertex2RdfMapping().mapPropertyValue2RdfResource(property, value));

		mapping.setPgProperty2RdfResourcePattern(pgProperty2RdfResourcePattern);
		assertTrue(mapping.getVertex2RdfMapping().containsRdfResourcePatternForProperty(property));
		assertEquals(resource,
				mapping.getVertex2RdfMapping().mapPropertyValue2RdfResource(property, value).stringValue());
	}

	@Test
	public void invalidPatternIsRejected() {

		Map<String, String> pgProperty2RdfResourcePattern = new HashMap<String, String>() {
			{
				put("country", "something");
			}
		};

		Csv2RdfException exception = assertThrows(Csv2RdfException.class,
				() -> mapping.setPgProperty2RdfResourcePattern(pgProperty2RdfResourcePattern));
		assertEquals("The pattern <something> for the new URI must contain the replacement variable {{VALUE}}.",
				exception.getMessage());
	}

}

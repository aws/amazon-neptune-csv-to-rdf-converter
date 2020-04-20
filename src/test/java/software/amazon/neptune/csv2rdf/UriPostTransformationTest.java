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

import java.net.URISyntaxException;

import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UriPostTransformationTest {

	private static final String TYPE = "http://example.org/class/Country";
	private static final String PROPERTY = "http://example.org/datatypeProperty/code";

	private UriPostTransformation transformation;

	@BeforeEach
	public void init() {
		transformation = new UriPostTransformation("http://example.org/resource/([0-9]+)", TYPE, PROPERTY,
				"http://example.org/resource/{{VALUE}}");
	}

	@Test
	public void noTransformationBeforeRegistering() {

		assertNull(transformation.apply("http://example.org/resource/123"));
	}

	@Test
	public void transformUri() {

		String resource = "http://example.org/resource/123";
		transformation.registerResource(resource, RDF.TYPE.toString(), TYPE);
		assertNull(transformation.apply(resource));
		transformation.registerReplacementValue(resource, PROPERTY, "FR");
		assertEquals("http://example.org/resource/FR", transformation.apply(resource).stringValue());

	}

	@Test
	public void keyMustBeRegisteredBeforeValue() {

		String resource = "http://example.org/resource/456";
		transformation.registerReplacementValue(resource, PROPERTY, "CN");
		transformation.registerResource(resource, RDF.TYPE.toString(), TYPE);
		assertEquals("http://example.org/resource/CN", transformation.apply(resource).stringValue());
	}

	@Test
	public void noTransformationWithoutValue() {

		String resource = "http://example.org/resource/123";
		transformation.registerResource(resource, RDF.TYPE.toString(), TYPE);
		assertNull(transformation.apply(resource));
	}

	@Test
	public void noTransformationIfTypeDoesNotMatch() {

		String resource = "http://example.org/resource/456";
		transformation.registerResource(resource, RDF.TYPE.toString(), "http://example.org/class/City");
		transformation.registerReplacementValue(resource, PROPERTY, "BU");
		assertNull(transformation.apply(resource));
	}

	@Test
	public void noTransformationIfPropertyDoesNotMatch() {

		String resource = "http://example.org/resource/456";
		transformation.registerResource(resource, RDF.TYPE.toString(), TYPE);
		transformation.registerReplacementValue(resource, "http://example.org/datatypeProperty/name", "Peru");
		assertNull(transformation.apply(resource));
	}

	@Test
	public void specialCharactersAreEncoded() {

		String resource = "http://example.org/resource/456";
		transformation.registerResource(resource, RDF.TYPE.toString(), TYPE);
		transformation.registerReplacementValue(resource, PROPERTY, "[] {} ß ä ");
		assertEquals("http://example.org/resource/%5B%5D+%7B%7D+%C3%9F+%C3%A4+",
				transformation.apply(resource).stringValue());
	}

	@Test
	public void resourcePrefixIsNotEncoded() {

		transformation = new UriPostTransformation("http://example.org/resource/([0-9]+)", TYPE, PROPERTY,
				"{invalid} /resource/{{VALUE}}");

		String resource = "http://example.org/resource/456";
		transformation.registerResource(resource, RDF.TYPE.toString(), TYPE);
		transformation.registerReplacementValue(resource, PROPERTY, "[] {} ß ä ");

		Exception exception = assertThrows(Csv2RdfException.class, () -> transformation.apply(resource));
		assertEquals(
				"Invalid resource URI <{invalid} /resource/%5B%5D+%7B%7D+%C3%9F+%C3%A4+> generated when applying UriPostTransformation(srcPattern=http://example.org/resource/([0-9]+), typeUri=http://example.org/class/Country, propertyUri=http://example.org/datatypeProperty/code, dstPattern={invalid} /resource/{{VALUE}}).",
				exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Illegal character in path at index 0: {invalid} /resource/%5B%5D+%7B%7D+%C3%9F+%C3%A4+",
				exception.getCause().getMessage());
	}

	@Test
	public void rejectDuplicateKeyWithDistinctValues() {

		String resource = "http://example.org/resource/456";
		transformation.registerResource(resource, RDF.TYPE.toString(), TYPE);
		transformation.registerReplacementValue(resource, PROPERTY, "Peru");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> transformation.registerReplacementValue(resource, PROPERTY, "Chile"));

		assertEquals("Found duplicate, inconsistent value for <http://example.org/resource/456>: Chile vs. Peru",
				exception.getMessage());
	}

	@Test
	public void invalidJavaUri() {

		UriPostTransformation transformation2 = new UriPostTransformation("j:(\\d+)", "j:type", "j:prop", "{{VALUE}}:");

		transformation2.registerResource("j:12", RDF.TYPE.stringValue(), "j:type");
		transformation2.registerReplacementValue("j:12", "j:prop", "type");

		Exception exception = assertThrows(Csv2RdfException.class, () -> transformation2.apply("j:12"));
		assertEquals(
				"Invalid resource URI <type:> generated when applying UriPostTransformation(srcPattern=j:(\\d+), typeUri=j:type, propertyUri=j:prop, dstPattern={{VALUE}}:).",
				exception.getMessage());
		assertTrue(exception.getCause() instanceof URISyntaxException);
		assertEquals("Expected scheme-specific part at index 5: type:", exception.getCause().getMessage());
	}

	@Test
	public void invalidRdf4jIri() {

		UriPostTransformation transformation2 = new UriPostTransformation("j:(\\d+)", "j:type", "j:prop",
				"{{VALUE}}/resource");

		transformation2.registerResource("j:12", RDF.TYPE.stringValue(), "j:type");
		transformation2.registerReplacementValue("j:12", "j:prop", "type");

		Exception exception = assertThrows(Csv2RdfException.class, () -> transformation2.apply("j:12"));
		assertEquals(
				"Invalid resource URI <type/resource> generated when applying UriPostTransformation(srcPattern=j:(\\d+), typeUri=j:type, propertyUri=j:prop, dstPattern={{VALUE}}/resource).",
				exception.getMessage());
		assertTrue(exception.getCause() instanceof IllegalArgumentException);
		assertEquals("Not a valid (absolute) IRI: type/resource", exception.getCause().getMessage());
	}
}

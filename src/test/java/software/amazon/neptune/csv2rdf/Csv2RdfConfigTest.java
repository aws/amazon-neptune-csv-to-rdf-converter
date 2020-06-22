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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class Csv2RdfConfigTest {

	@Test
	public void defaultValues() {
		PropertyGraph2RdfConverter converter = new PropertyGraph2RdfConverter(null);

		assertEquals("csv", converter.getInputFileExtension());
		assertTrue(converter.getMapper().isAlwaysAddPropertyStatements());
		assertNotNull(converter.getMapper().getMapping());
		assertNotNull(converter.getTransformer().getUriPostTransformations());
	}

	@Test
	public void allConfigurationFields() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "complete.properties");

		PropertyGraph2RdfConverter converter = new PropertyGraph2RdfConverter(config.toFile());

		assertEquals("ife", converter.getInputFileExtension());
		assertEquals("tn", converter.getMapper().getMapping().getTypeNamespace());
		assertEquals("vn", converter.getMapper().getMapping().getVertexNamespace());
		assertEquals("en", converter.getMapper().getMapping().getEdgeNamespace());
		assertEquals("vpn", converter.getMapper().getMapping().getVertexPropertyNamespace());
		assertEquals("epn", converter.getMapper().getMapping().getEdgePropertyNamespace());
		assertEquals("dng:a", converter.getMapper().getMapping().getDefaultNamedGraph().toString());
		assertEquals("dt:a", converter.getMapper().getMapping().getDefaultType().toString());
		assertEquals("dp:a", converter.getMapper().getMapping().getDefaultPredicate().toString());
		assertEquals(false, converter.getMapper().isAlwaysAddPropertyStatements());

		assertEquals(2, converter.getMapper().getMapping().getPgVertexType2PropertyForRdfsLabel().size());
		assertEquals("A", converter.getMapper().getMapping().getPgVertexType2PropertyForRdfsLabel().get("a"));
		assertEquals("B", converter.getMapper().getMapping().getPgVertexType2PropertyForRdfsLabel().get("b"));

		assertEquals(1, converter.getMapper().getMapping().getPgProperty2RdfResourcePattern().size());
		assertEquals("A{{VALUE}}", converter.getMapper().getMapping().getPgProperty2RdfResourcePattern().get("a"));

		assertEquals(4, converter.getTransformer().getUriPostTransformations().size());
		Iterator<UriPostTransformation> iterator = converter.getTransformer().getUriPostTransformations().iterator();
		assertEquals(new UriPostTransformation("sp0", "tu0", "vp0", "dp0{{VALUE}}"), iterator.next());
		assertEquals(new UriPostTransformation("sp1", "tu1", "vp1", "dp1{{VALUE}}"), iterator.next());
		assertEquals(new UriPostTransformation("sp2", "tu2", "vp2", "dp2{{VALUE}}"), iterator.next());
		assertEquals(new UriPostTransformation("sp6", "tu6", "vp6", "dp6{{VALUE}}"), iterator.next());
	}

	@Test
	public void duplicateIntOverridesFirst() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "duplicate-int.properties");

		PropertyGraph2RdfConverter converter = new PropertyGraph2RdfConverter(config.toFile());

		assertEquals(2, converter.getTransformer().getUriPostTransformations().size());
		Iterator<UriPostTransformation> iterator = converter.getTransformer().getUriPostTransformations().iterator();
		assertEquals(new UriPostTransformation("sp2", "tu2", "vp2", "dp2{{VALUE}}"), iterator.next());
		assertEquals(new UriPostTransformation("sp4", "tu3", "vp3", "dp3{{VALUE}}"), iterator.next());
	}

	@Test
	public void missingRequiredPropertyFails() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "missing-property.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertEquals(
				"Loading configuration failed because of invalid input at srcPattern: Missing required creator property 'srcPattern' (index 0)",
				exception.getMessage());
	}

	@Test
	public void unknownPropertyFails() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "unknown-property.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertEquals("Loading configuration failed because of unknown property: _anything_", exception.getMessage());
	}

	@Test
	public void outputFileExtensionDisallowed() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "output-file-extension.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertEquals("Loading configuration failed because of unknown property: outputFileExtension",
				exception.getMessage());
	}

	@Test
	public void unknownNestedPropertyFails() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "unknown-nested-property.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertEquals("Loading configuration failed because of unknown property: prop", exception.getMessage());
	}

	@Test
	public void invalidTransformationDestinationPatternFails() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "invalid-pattern.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertEquals(
				"Loading configuration failed because of invalid input at uriPostTransformations: The pattern <dp6> for the new URI must contain the replacement variable {{VALUE}}.",
				exception.getMessage());
	}

	@Test
	public void invalidTransformationSourceRegexFails() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "invalid-regex.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertTrue(
				exception.getMessage().startsWith("Loading configuration failed because of invalid input at uriPostTransformations:")
				&& exception.getMessage().contains("{sp6}/resource/(\\d+)"),
				"{sp6}/resource/(\\d+) is an invalid regex and should have caused an exception when parsed");
	}

	@Test
	public void invalidDefaultNamedGraphIsRejected() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "invalid-defaultNamedGraph.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertEquals(
				"Loading configuration failed because of invalid input at defaultNamedGraph: Not a valid (absolute) IRI: dng",
				exception.getMessage());
	}

	@Test
	public void invalidDefaulTypeIsRejected() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "invalid-defaultType.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertEquals(
				"Loading configuration failed because of invalid input at defaultType: Not a valid (absolute) IRI: dt",
				exception.getMessage());
	}

	@Test
	public void invalidDefaultPredicateIsRejected() throws Exception {

		Path config = Paths.get("src", "test", "configurationTests", "invalid-defaultPredicate.properties");

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(config.toFile()));

		assertEquals(
				"Loading configuration failed because of invalid input at defaultPredicate: Not a valid (absolute) IRI: dp",
				exception.getMessage());
	}

	@Test
	public void missingConfigurationFileFails() throws Exception {

		File nonExistingProperties = Paths.get("target", "non-existing.properties").toFile();

		Exception exception = assertThrows(Csv2RdfException.class,
				() -> new PropertyGraph2RdfConverter(nonExistingProperties));

		assertTrue(exception.getMessage()
				.matches("Configuration file not found: .*" + Pattern.quote(nonExistingProperties.getPath())));
	}
}

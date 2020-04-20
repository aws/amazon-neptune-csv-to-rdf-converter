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

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UriPostTransformerTest {

	private static SimpleValueFactory VF = SimpleValueFactory.getInstance();

	private static Statement[] TEST = { relation("j:12", "j:b", "j:c", "j:d"), relation("j:a", "j:12", "j:c", "j:d"),
			relation("j:a", "j:b", "j:12", "j:d"), relation("j:a", "j:b", "j:c", "j:12"),
			relation("j:12", "j:b", "j:12", "j:d"), relation("j:12", "j:12", "j:12", "j:12") };

	private static Statement[] EXPECTED = { relation("j:xy", "j:b", "j:c", "j:d"),
			relation("j:a", "j:xy", "j:c", "j:d"), relation("j:a", "j:b", "j:xy", "j:d"),
			relation("j:a", "j:b", "j:c", "j:xy"), relation("j:xy", "j:b", "j:xy", "j:d"),
			relation("j:xy", "j:xy", "j:xy", "j:xy") };

	private static Statement TYPE_STATEMENT = relation("j:12", RDF.TYPE.stringValue(), "j:type", "j:d");
	private static Statement PROPERTY_STATEMENT = literal("j:12", "j:prop", "xy", "j:d");

	private UriPostTransformation transformation;
	private UriPostTransformer transformer;

	/**
	 *
	 * Create an RDF statement with resource object.
	 *
	 * @param subject
	 * @param predicate
	 * @param object
	 * @param context
	 * @return new statement
	 */
	private static Statement relation(String subject, String predicate, String object, String context) {

		return VF.createStatement(VF.createIRI(subject), VF.createIRI(predicate), VF.createIRI(object),
				VF.createIRI(context));
	}

	/**
	 *
	 * Create an RDF statement with literal object.
	 *
	 * @param subject
	 * @param predicate
	 * @param object
	 * @param context
	 * @return new statement
	 */
	private static Statement literal(String subject, String predicate, String object, String context) {

		return VF.createStatement(VF.createIRI(subject), VF.createIRI(predicate), VF.createLiteral(object),
				VF.createIRI(context));
	}

	@BeforeEach
	public void init() {
		transformation = new UriPostTransformation("j:(\\d+)", "j:type", "j:prop", "j:{{VALUE}}");
		transformer = new UriPostTransformer();
		transformer.setUriPostTransformations(new ArrayList<>(Arrays.asList(transformation)));
	}

	@Test
	public void noTransformationBeforeInitialization() {

		transformer = new UriPostTransformer();
		for (int i = 0; i < TEST.length; ++i) {
			assertEquals(TEST[i], transformer.transform(TEST[i]));
		}
	}

	@Test
	public void transformStatementUsingPreparedTransformation() {

		transformation.registerResource("j:12", RDF.TYPE.stringValue(), "j:type");
		transformation.registerReplacementValue("j:12", "j:prop", "xy");

		for (int i = 0; i < TEST.length; ++i) {
			assertEquals(EXPECTED[i], transformer.transform(TEST[i]));
		}
	}

	@Test
	public void prepareTransformerAndTransformStatement() {

		transformer.register(literal("j:12", RDF.TYPE.stringValue(), "j:type", "j:d"));
		transformer.register(PROPERTY_STATEMENT);

		for (int i = 0; i < TEST.length; ++i) {
			assertEquals(TEST[i], transformer.transform(TEST[i]));
		}
	}

	@Test
	public void literalDoesNotPrepareTransformer() {

		transformer.register(TYPE_STATEMENT);
		transformer.register(PROPERTY_STATEMENT);

		for (int i = 0; i < TEST.length; ++i) {
			assertEquals(EXPECTED[i], transformer.transform(TEST[i]));
		}
		assertEquals(relation("j:xy", RDF.TYPE.stringValue(), "j:type", "j:d"), transformer.transform(TYPE_STATEMENT));
		assertEquals(literal("j:xy", "j:prop", "xy", "j:d"), transformer.transform(PROPERTY_STATEMENT));

	}

	@Test
	public void registerInAnyOrderAndTransformStatement() {

		transformer.register(PROPERTY_STATEMENT);
		transformer.register(TYPE_STATEMENT);

		for (int i = 0; i < TEST.length; ++i) {
			assertEquals(EXPECTED[i], transformer.transform(TEST[i]));
		}
	}

	@Test
	public void firstComeFirstServed() {

		UriPostTransformation transformation2 = new UriPostTransformation("j:(\\d+)", "j:type", "j:prop",
				"j:z/{{VALUE}}");

		transformer.setUriPostTransformations(new ArrayList<>(Arrays.asList(transformation2, transformation)));
		transformer.register(TYPE_STATEMENT);
		transformer.register(PROPERTY_STATEMENT);

		assertEquals(relation("j:z/xy", "j:b", "j:c", "j:d"), transformer.transform(TEST[0]));
		assertEquals(relation("j:z/xy", "j:z/xy", "j:z/xy", "j:z/xy"), transformer.transform(TEST[5]));
	}

	@Test
	public void differentTransformationsOnSameStatement() {

		UriPostTransformation transformation2 = new UriPostTransformation("j:r/(\\d+)", "j:type2", "j:prop",
				"j:z/{{VALUE}}");

		transformer.setUriPostTransformations(new ArrayList<>(Arrays.asList(transformation2, transformation)));
		transformer.register(TYPE_STATEMENT);
		transformer.register(PROPERTY_STATEMENT);
		transformer.register(relation("j:r/10", RDF.TYPE.stringValue(), "j:type2", "j:g"));
		transformer.register(literal("j:r/10", "j:prop", "ab", "j:d"));

		assertEquals(EXPECTED[0], transformer.transform(TEST[0]));
		assertEquals(relation("j:z/ab", "j:r/b", "j:r/c", "j:z/ab"),
				transformer.transform(relation("j:r/10", "j:r/b", "j:r/c", "j:r/10")));
		assertEquals(relation("j:xy", "j:z/ab", "j:r/c", "j:r/34"),
				transformer.transform(relation("j:12", "j:r/10", "j:r/c", "j:r/34")));
	}

	@Test
	public void literalsAreNotTransformed() {

		transformer.register(TYPE_STATEMENT);
		transformer.register(PROPERTY_STATEMENT);

		assertEquals(relation("j:a", "j:b", "j:xy", "j:d"),
				transformer.transform(relation("j:a", "j:b", "j:12", "j:d")));
		assertEquals(literal("j:a", "j:b", "j:12", "j:d"), transformer.transform(literal("j:a", "j:b", "j:12", "j:d")));
	}

	@Test
	public void specialCharactersAreEncoded() {

		transformer.register(TYPE_STATEMENT);
		transformer.register(literal("j:12", "j:prop", " { very späcial } ", "j:d"));

		assertEquals(relation("j:a", "j:b", "j:+%7B+very+sp%C3%A4cial+%7D+", "j:d"),
				transformer.transform(relation("j:a", "j:b", "j:12", "j:d")));
	}

	@Test
	public void datatypeIsRemovedFromReplacementValue() {

		transformer.register(TYPE_STATEMENT);
		Literal integer = VF.createLiteral(1001);
		transformer.register(VF.createStatement(PROPERTY_STATEMENT.getSubject(), PROPERTY_STATEMENT.getPredicate(),
				integer, PROPERTY_STATEMENT.getContext()));

		assertEquals("\"1001\"^^<http://www.w3.org/2001/XMLSchema#int>", integer.toString());
		assertEquals(relation("j:a", "j:b", "j:1001", "j:d"),
				transformer.transform(relation("j:a", "j:b", "j:12", "j:d")));
	}
}

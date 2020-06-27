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

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphEdge;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphVertex;

@SuppressWarnings("serial")
public class PropertGraph2RdfMapperTest {

	private static SimpleValueFactory VF = SimpleValueFactory.getInstance();

	private CSVFormat csvFormat;
	private PropertyGraph2RdfMapping mapping;
	private PropertyGraph2RdfMapper mapper;

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

	/**
	 *
	 * Create an RDF statement with literal object.
	 *
	 * @param subject
	 * @param predicate
	 * @param object
	 * @param dataType
	 * @param context
	 * @return new statement
	 */
	private static Statement literal(String subject, String predicate, String object, IRI dataType, String context) {

		return VF.createStatement(VF.createIRI(subject), VF.createIRI(predicate), VF.createLiteral(object, dataType),
				VF.createIRI(context));
	}

	@BeforeEach
	public void init() {
		csvFormat = NeptuneCsvInputParser.createCSVFormat();
		mapping = new PropertyGraph2RdfMapping();
		mapping.setTypeNamespace("tn:");
		mapping.setVertexNamespace("vn:");
		mapping.setEdgeNamespace("en:");
		mapping.setVertexPropertyNamespace("vpn:");
		mapping.setEdgePropertyNamespace("epn:");
		mapping.setDefaultNamedGraph("dng:a");
		mapping.setDefaultType("dt:a");
		mapping.setDefaultPredicate("dp:a");
		mapping.setEdgeContextNamespace("vn:");

		mapper = new PropertyGraph2RdfMapper();
		mapper.setMapping(mapping);
	}

	/**
	 *
	 * Return a list of statements for the input string.
	 *
	 * @param csv CSV formatted input
	 * @return list of RDF statements
	 * @throws IOException
	 * @throws ClassCastException if the CSV did not describe a vertex
	 */
	private List<Statement> getStatementsForVertex(String csv) throws IOException {

		NeptunePropertyGraphElement pgElement = parse(csv);
		return mapper.mapToStatements((NeptunePropertyGraphVertex) pgElement);
	}

	/**
	 *
	 * Return a list of statements for the input string.
	 *
	 * @param csv CSV formatted input
	 * @return list of RDF statements
	 * @throws IOException
	 * @throws ClassCastException if the CSV did not describe an edge
	 */
	private List<Statement> getStatementsForEdge(String csv) throws IOException {

		NeptunePropertyGraphElement pgElement = parse(csv);
		return mapper.mapToStatements((NeptunePropertyGraphEdge) pgElement);
	}

	/**
	 *
	 * Parse a CSV snippet
	 *
	 * @param csv
	 * @return vertex or edge depending on the CSV header
	 * @throws IOException
	 */
	private NeptunePropertyGraphElement parse(String csv) throws IOException {

		Iterator<CSVRecord> csvRecords = csvFormat.parse(new StringReader(csv)).iterator();

		NeptuneCsvHeader header = NeptuneCsvHeader.parse(csvRecords.next());
		CSVRecord record = csvRecords.next();
		return NeptuneCsvInputParser.create(header, record);
	}

	@Test
	public void vertexWithoutLabel() throws IOException {

		List<Statement> statements = getStatementsForVertex("~id,name\n1,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "dt:a", "dng:a"), statements.get(0));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(1));
	}

	@Test
	public void vertexWithLabel() throws IOException {

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(1));
	}

	@Test
	public void vertexWithWhitespaceLabel() throws IOException {

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,   ,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "dt:a", "dng:a"), statements.get(0));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(1));
	}

	@Test
	public void vertexWithMultipleLabels() throws IOException {

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister;mister2;mister3,x");
		assertEquals(4, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister2", "dng:a"), statements.get(1));
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister3", "dng:a"), statements.get(2));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(3));
	}

	@Test
	public void vertexWithNullLabel() throws IOException {

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "dt:a", "dng:a"), statements.get(0));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(1));
	}

	@Test
	public void nullValueDoesNotCreateVertexProperty() throws IOException {

		List<Statement> statements = getStatementsForVertex("~id,name\n1,");
		assertEquals(1, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "dt:a", "dng:a"), statements.get(0));
	}

	@Test
	public void emptyValueDoesNotCreateVertexProperty() throws IOException {

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister, \t");
		assertEquals(1, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
	}

	@Test
	public void nullValueDoesNotCreateRdfsLabel() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
			}
		});

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister,");
		assertEquals(1, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
	}

	@Test
	public void emptyValueDoesNotCreateRdfsLabel() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
			}
		});

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister,   ");
		assertEquals(1, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
	}

	@Test
	public void createOnlyRdfsLabel() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
			}
		});
		mapper.setAlwaysAddPropertyStatements(false);

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x", "dng:a"), statements.get(1));
	}

	@Test
	public void createOnlyRdfsLabelHavingTwoLabels() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
			}
		});
		mapper.setAlwaysAddPropertyStatements(false);

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister;mister2,x");
		assertEquals(3, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister2", "dng:a"), statements.get(1));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x", "dng:a"), statements.get(2));
	}

	@Test
	public void createOnlyRdfsLabelHavingTwoLabelsAndTwoLabelProperties() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
				put("mister2", "name");
			}
		});
		mapper.setAlwaysAddPropertyStatements(false);

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister;mister2,x");
		assertEquals(3, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister2", "dng:a"), statements.get(1));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x", "dng:a"), statements.get(2));
	}

	@Test
	public void createOnlyRdfsLabelHavingTwoLabelsAndTwoDifferentLabelProperties() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
				put("mister2", "name2");
			}
		});
		mapper.setAlwaysAddPropertyStatements(false);

		List<Statement> statements = getStatementsForVertex("~id,~label,name,name2\n1,mister;mister2,x,x2");
		assertEquals(4, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister2", "dng:a"), statements.get(1));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x", "dng:a"), statements.get(2));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x2", "dng:a"), statements.get(3));
	}

	@Test
	public void createRdfsLabelAndProperty() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
			}
		});

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister,x");
		assertEquals(3, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x", "dng:a"), statements.get(1));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(2));
	}

	@Test
	public void createRdfsLabelAndPropertyHavingTwoLabels() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
			}
		});

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister;mister2,x");
		assertEquals(4, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister2", "dng:a"), statements.get(1));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x", "dng:a"), statements.get(2));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(3));
	}

	@Test
	public void createRdfsLabelAndPropertyHavingTwoLabelsAndTwoLabelProperties() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
				put("mister2", "name");
			}
		});

		List<Statement> statements = getStatementsForVertex("~id,~label,name\n1,mister;mister2,x");
		assertEquals(4, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister2", "dng:a"), statements.get(1));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x", "dng:a"), statements.get(2));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(3));
	}

	@Test
	public void createRdfsLabelAndPropertyHavingTwoLabelsAndTwoDifferentLabelProperties() throws IOException {

		mapping.setPgVertexType2PropertyForRdfsLabel(new HashMap<String, String>() {
			{
				put("mister", "name");
				put("mister2", "name2");
			}
		});

		List<Statement> statements = getStatementsForVertex("~id,~label,name,name2\n1,mister;mister2,x,x2");
		assertEquals(6, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister2", "dng:a"), statements.get(1));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x", "dng:a"), statements.get(2));
		assertEquals(literal("vn:1", "vpn:name", "x", "dng:a"), statements.get(3));
		assertEquals(literal("vn:1", RDFS.LABEL.stringValue(), "x2", "dng:a"), statements.get(4));
		assertEquals(literal("vn:1", "vpn:name2", "x2", "dng:a"), statements.get(5));
	}

	@Test
	public void propertyIsEncoded() throws IOException {

		List<Statement> statements = getStatementsForVertex("~id,~label,{Heizölrückstoßabdämpfung}\n1,mister,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:Mister", "dng:a"), statements.get(0));
		assertEquals(literal("vn:1", "vpn:%7BHeiz%C3%B6lr%C3%BCcksto%C3%9Fabd%C3%A4mpfung%7D", "x", "dng:a"),
				statements.get(1));
	}

	@Test
	public void edgeWithLabel() throws IOException {

		List<Statement> statements = getStatementsForEdge("~id,~label,~from,~to,name\n1,related,2,3,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:2", "en:related", "vn:3", "vn:1"), statements.get(0));
		assertEquals(literal("vn:1", "epn:name", "x", "dng:a"), statements.get(1));
	}

	@Test
	public void edgeWithNullLabel() throws IOException {

		List<Statement> statements = getStatementsForEdge("~id,~label,~from,~to,name\n1,,2,3,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:2", "dp:a", "vn:3", "vn:1"), statements.get(0));
		assertEquals(literal("vn:1", "epn:name", "x", "dng:a"), statements.get(1));
	}

	@Test
	public void edgeWithWhitespaceLabel() throws IOException {

		List<Statement> statements = getStatementsForEdge("~id,~label,~from,~to,name\n1,  ,2,3,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:2", "dp:a", "vn:3", "vn:1"), statements.get(0));
		assertEquals(literal("vn:1", "epn:name", "x", "dng:a"), statements.get(1));
	}

	@Test
	public void edgeDoesNotSplitLabel() throws IOException {

		List<Statement> statements = getStatementsForEdge(
				"~id,~label,~from,~to,name\n1,related;related2;related3,2,3,x");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:2", "en:related%3Brelated2%3Brelated3", "vn:3", "vn:1"), statements.get(0));
		assertEquals(literal("vn:1", "epn:name", "x", "dng:a"), statements.get(1));
	}

	@Test
	public void nullValueDoesNotCreateEdgeProperty() throws IOException {

		List<Statement> statements = getStatementsForEdge("~id,~label,~from,~to,name\n1,related,2,3,");
		assertEquals(1, statements.size());
		assertEquals(relation("vn:2", "en:related", "vn:3", "vn:1"), statements.get(0));
	}

	@Test
	public void emptyValueDoesNotCreateEdgeProperty() throws IOException {

		List<Statement> statements = getStatementsForEdge("~id,~label,name,~from,~to\n1,related,  ,2,3");
		assertEquals(1, statements.size());
		assertEquals(relation("vn:2", "en:related", "vn:3", "vn:1"), statements.get(0));
	}

	@Test
	public void tolerateTooFewRecordColumns() throws IOException {

		List<Statement> statements = getStatementsForEdge("~id,~label,~from,~to,name\n1,a,2,3");
		assertEquals(1, statements.size());
		assertEquals(relation("vn:2", "en:a", "vn:3", "vn:1"), statements.get(0));
	}

	@Test
	public void tolerateTooManyRecordColumns() throws IOException {

		List<Statement> statements = getStatementsForEdge("~id,~label,~from,~to,name\n1,a,2,3,Alice,Bob");
		assertEquals(2, statements.size());
		assertEquals(relation("vn:2", "en:a", "vn:3", "vn:1"), statements.get(0));
		assertEquals(literal("vn:1", "epn:name", "Alice", "dng:a"), statements.get(1));
	}

	@Test
	public void allDataTypes() throws IOException {

		List<Statement> statements = getStatementsForVertex(
				"~id,~label,byte:byte,bool:bool,boolean:boolean,short:short,int:int,integer:integer,long:long,float:float,double:double,string:string,datetime:datetime,date:date"
						+ "\n1,dataTypes,A0,true,false,32767,2147483647,-2147483648,9223372036854775807,3.14,3.1415926,Hello World!,2020-03-05T13:30:00Z,2020-02-29");
		assertEquals(13, statements.size());
		assertEquals(relation("vn:1", RDF.TYPE.stringValue(), "tn:DataTypes", "dng:a"), statements.get(0));
		assertEquals(literal("vn:1", "vpn:byte", "A0", XMLSchema.BYTE, "dng:a"), statements.get(1));
		assertEquals(literal("vn:1", "vpn:bool", "true", XMLSchema.BOOLEAN, "dng:a"), statements.get(2));
		assertEquals(literal("vn:1", "vpn:boolean", "false", XMLSchema.BOOLEAN, "dng:a"), statements.get(3));
		assertEquals(literal("vn:1", "vpn:short", "32767", XMLSchema.SHORT, "dng:a"), statements.get(4));
		assertEquals(literal("vn:1", "vpn:int", "2147483647", XMLSchema.INTEGER, "dng:a"), statements.get(5));
		assertEquals(literal("vn:1", "vpn:integer", "-2147483648", XMLSchema.INTEGER, "dng:a"), statements.get(6));
		assertEquals(literal("vn:1", "vpn:long", "9223372036854775807", XMLSchema.LONG, "dng:a"), statements.get(7));
		assertEquals(literal("vn:1", "vpn:float", "3.14", XMLSchema.FLOAT, "dng:a"), statements.get(8));
		assertEquals(literal("vn:1", "vpn:double", "3.1415926", XMLSchema.DOUBLE, "dng:a"), statements.get(9));
		assertEquals(literal("vn:1", "vpn:string", "Hello World!", XMLSchema.STRING, "dng:a"), statements.get(10));
		assertEquals(literal("vn:1", "vpn:datetime", "2020-03-05T13:30:00Z", XMLSchema.DATE, "dng:a"),
				statements.get(11));
		assertEquals(literal("vn:1", "vpn:date", "2020-02-29", XMLSchema.DATE, "dng:a"), statements.get(12));
	}

}

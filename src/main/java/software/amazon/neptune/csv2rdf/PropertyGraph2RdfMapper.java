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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvSingleValuedUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphEdge;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphVertex;
import software.amazon.neptune.csv2rdf.PropertyGraph2RdfMapping.PropertyGraphEdge2RdfMapping;
import software.amazon.neptune.csv2rdf.PropertyGraph2RdfMapping.PropertyGraphVertex2RdfMapping;

/**
 *
 * This class performs the basic mapping specified in
 * {@link PropertyGraph2RdfMapping} between property graph vertices and edges
 * into RDF. RDF quads are used to represent edges with properties.
 *
 * The mapping can be defined in the configuration file.
 *
 * <b>Simple Example</b><br>
 *
 * Simplified configuration values:
 *
 * <pre>
 * mapper.mapping.typeNamespace=type:
 * mapper.mapping.vertexNamespace=vertex:
 * mapper.mapping.edgeNamespace=edge:
 * mapper.mapping.edgeContextNamespace=econtext:
 * mapper.mapping.vertexPropertyNamespace=vproperty:
 * mapper.mapping.edgePropertyNamespace=eproperty:
 * mapper.mapping.defaultNamedGraph=dng:/
 * mapper.mapping.defaultType=dt:/
 * mapper.mapping.defaultProperty=dp:/
 * </pre>
 *
 * Vertices:
 *
 * <pre>
 * ~id,~label,name,code,country
 * 1,city,Seattle,S,USA
 * 2,city,Vancouver,V,CA
 * </pre>
 *
 * Edges:
 *
 * <pre>
 * ~id,~label,~from,~to,distance,type
 * a,route,1,2,166,highway
 * </pre>
 *
 * RDF statements:
 *
 * <pre>
 * {@code
 * <vertex:1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <type:City> <dng:/> .
 * <vertex:1> <vproperty:name> "Seattle" <dng:/> .
 * <vertex:1> <vproperty:code> "S" <dng:/> .
 * <vertex:1> <vproperty:country> "USA" <dng:/> .
 * <vertex:2> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <type:City> <dng:/> .
 * <vertex:2> <vproperty:name> "Vancouver" <dng:/> .
 * <vertex:2> <vproperty:code> "V" <dng:/> .
 * <vertex:2> <vproperty:country> "CA" <dng:/> .
 *
 * <vertex:1> <edge:route> <vertex:2> <econtext:a> .
 * <econtext:a> <eproperty:distance> "166" <dng:/> .
 * <econtext:a> <eproperty:type> "highway" <dng:/> .
 * }
 * </pre>
 */
@Slf4j
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE)
public class PropertyGraph2RdfMapper {

	/**
	 * When {@code true}, a property that is selected to create RDFS labels will
	 * create normal property statements, too. Otherwise only the RDFS label
	 * statements are created. <br>
	 * The properties to create RDFS labels are configured in
	 * {@link PropertyGraph2RdfMapping#pgVertexType2PropertyForRdfsLabel}.
	 */
	@Getter
	@Setter
	private boolean alwaysAddPropertyStatements = true;

	/**
	 * The {@link PropertyGraph2RdfMapping} defines how property graph vertices and
	 * edges are mapped to RDF.
	 */
	@Getter
	@Setter
	private PropertyGraph2RdfMapping mapping = new PropertyGraph2RdfMapping();

	/**
	 * Map a property graph file to RDF
	 *
	 * @param propertyGraphInFile a property graph file
	 * @param rdfOutFile          RDF output file
	 * @throws Csv2RdfException if an error occurs during the process
	 */
	public void map(final File propertyGraphInFile, File rdfOutFile) {

		log.info("-> Converting input file {}...", propertyGraphInFile.getName());

		RDFWriter rdfWriter = null;
		try (NeptuneCsvInputParser inputParser = new NeptuneCsvInputParser(propertyGraphInFile);
				FileOutputStream fos = new FileOutputStream(rdfOutFile)) {

			rdfWriter = Rio.createWriter(PropertyGraph2RdfConverter.RDF_FORMAT, fos);
			rdfWriter.startRDF();
			rdfWriter.handleNamespace("vertex", mapping.getVertexNamespace());
			rdfWriter.handleNamespace("edge", mapping.getEdgeNamespace());
			rdfWriter.handleNamespace("vertexprop", mapping.getVertexPropertyNamespace());
			rdfWriter.handleNamespace("edgeprop", mapping.getEdgePropertyNamespace());

			while (inputParser.hasNext()) {
				List<Statement> statements = mapToStatements(inputParser.next());
				for (Statement statement : statements) {
					rdfWriter.handleStatement(statement);
				}
			}

			rdfWriter.endRDF();
		} catch (UnsupportedRDFormatException | RDFHandlerException | IOException e) {

			throw new Csv2RdfException("Conversion of file " + propertyGraphInFile.getAbsolutePath() + " failed.", e);
		}
	}

	/**
	 *
	 * Map a {@link NeptunePropertyGraphElement} to RDF statements according the
	 * configured {@link PropertyGraph2RdfMapper#mapping}.
	 *
	 * @param pgElement
	 * @return list of RDF statements
	 */
	private List<Statement> mapToStatements(NeptunePropertyGraphElement pgElement) {

		if (pgElement instanceof NeptunePropertyGraphEdge) {
			return mapToStatements((NeptunePropertyGraphEdge) pgElement);
		}
		if (pgElement instanceof NeptunePropertyGraphVertex) {
			return mapToStatements((NeptunePropertyGraphVertex) pgElement);
		}
		throw new IllegalArgumentException("Property graph element type not recognized: " + pgElement.getClass());
	}

	/**
	 *
	 * Map a {@link NeptunePropertyGraphEdge} to RDF statements according the
	 * configured {@link PropertyGraph2RdfMapper#mapping}.
	 *
	 * @param edge property graph edge
	 * @return list of RDF statements
	 */
	public List<Statement> mapToStatements(NeptunePropertyGraphEdge edge) {

		List<Statement> statements = new ArrayList<>();

		PropertyGraphEdge2RdfMapping edgeMapper = mapping.getEdge2RdfMapping();

		// the edge itself
		if (edge.hasLabel()) {
			// edge ID goes into graph position
			statements.add(
					edgeMapper.createRelationStatement(edge.getFrom(), edge.getLabel(), edge.getTo(), edge.getId()));
		} else {
			statements.add(edgeMapper.createRelationStatement(edge.getFrom(), edge.getTo(), edge.getId()));
		}

		// append edge properties
		for (NeptuneCsvSingleValuedUserDefinedProperty userDefinedProperty : edge.getUserDefinedProperties()) {

			statements.add(edgeMapper.createLiteralStatement(edge.getId(), userDefinedProperty.getName(),
					userDefinedProperty.getValue(), userDefinedProperty.getDataType()));
		}

		return statements;
	}

	/**
	 *
	 * Map a {@link NeptunePropertyGraphVertex} to RDF statements according the
	 * configured {@link PropertyGraph2RdfMapper#mapping}.
	 *
	 * @param vertex property graph vertex
	 * @return list of RDF statements
	 */
	public List<Statement> mapToStatements(NeptunePropertyGraphVertex vertex) {

		final List<Statement> statements = new ArrayList<>();

		final PropertyGraphVertex2RdfMapping vertexMapper = mapping.getVertex2RdfMapping();

		Set<String> propertiesForRdfsLabel = new HashSet<>();
		// the vertex itself; for now, we always type (falling back on a default if no
		// type is given)
		if (vertex.getLabels().isEmpty()) {
			statements.add(vertexMapper.createTypeStatement(vertex.getId()));
		} else {
			for (String label : vertex.getLabels()) {
				statements.add(vertexMapper.createTypeStatement(vertex.getId(), label));
				String propertyForRdfsLabel = vertexMapper.getPropertyForRdfsLabel(label);
				if (propertyForRdfsLabel != null) {
					propertiesForRdfsLabel.add(propertyForRdfsLabel);
				}
			}
		}

		for (NeptuneCsvUserDefinedProperty userDefinedProperty : vertex.getUserDefinedProperties()) {

			String propertyName = userDefinedProperty.getName();

			if (vertexMapper.containsRdfResourcePatternForProperty(propertyName)) {

				// in this case, we do not write a literal statement but a relation
				for (String value : userDefinedProperty.getValues()) {
					statements.add(vertexMapper.createRelationStatement(vertex.getId(), propertyName, value));
				}

			} else {

				boolean addRdfsLabel = propertiesForRdfsLabel.contains(propertyName);

				// this property has been marked as the property used as the rdfs:label
				if (addRdfsLabel) {
					for (String value : userDefinedProperty.getValues()) {
						statements.add(vertexMapper.createRdfsLabelStatement(vertex.getId(), value));
					}
				}

				// if either this was not written as rdfs:label or the configuration tells us to
				// write label properties
				// redundantly, we also emit the datatype property statement
				if (!addRdfsLabel || alwaysAddPropertyStatements) {
					for (String value : userDefinedProperty.getValues()) {
						statements.add(vertexMapper.createLiteralStatement(vertex.getId(), propertyName, value,
								userDefinedProperty.getDataType()));
					}
				}
			}
		}

		return statements;
	}
}

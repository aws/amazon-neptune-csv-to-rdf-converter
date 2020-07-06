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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.DataType;

/**
 *
 * {@link PropertyGraph2RdfMapping} contains the configuration for mapping
 * property graph vertices and edges to RDF statements. The configuration
 * consists of several namespaces, a default type, a default named graph,
 * {@link PropertyGraph2RdfMapping#pgVertexType2PropertyForRdfsLabel} for
 * mapping certain properties to RDFS labels, and
 * {@link PropertyGraph2RdfMapping#pgProperty2RdfResourcePattern} for creating
 * RDF resources from property values. <br>
 * It provides access to {@link PropertyGraphVertex2RdfMapping} for mapping
 * vertices and to {@link PropertyGraphEdge2RdfMapping} for mapping edges to RDF
 * statements.
 *
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE)
public class PropertyGraph2RdfMapping {

	public static final String DEFAULT_TYPE_NAMESPACE = "http://aws.amazon.com/neptune/csv2rdf/class/";
	public static final String DEFAULT_VERTEX_NAMESPACE = "http://aws.amazon.com/neptune/csv2rdf/resource/";
	public static final String DEFAULT_EDGE_NAMESPACE = "http://aws.amazon.com/neptune/csv2rdf/objectProperty/";
	public static final String DEFAULT_VERTEX_PROPERTY_NAMESPACE = "http://aws.amazon.com/neptune/csv2rdf/datatypeProperty/";
	public static final String DEFAULT_EDGE_PROPERTY_NAMESPACE = "http://aws.amazon.com/neptune/csv2rdf/datatypeProperty/";
	public static final String DEFAULT_TYPE = "http://www.w3.org/2002/07/owl#Thing";
	public static final String DEFAULT_PREDICATE = DEFAULT_EDGE_NAMESPACE + "edge";
	public static final String DEFAULT_NAMED_GRAPH = "http://aws.amazon.com/neptune/vocab/v01/DefaultNamedGraph";

	private final ValueFactory vf = SimpleValueFactory.getInstance();

	/**
	 *
	 * Namespace in which types are stored
	 */
	@Getter(AccessLevel.PACKAGE)
	@Setter
	private String typeNamespace = DEFAULT_TYPE_NAMESPACE;

	/**
	 *
	 * Namespace in which nodes are stored
	 */
	@Getter
	@Setter
	private String vertexNamespace = DEFAULT_VERTEX_NAMESPACE;

	/**
	 *
	 * Namespace in which edges are stored
	 */
	@Getter
	@Setter
	private String edgeNamespace = DEFAULT_EDGE_NAMESPACE;

	/**
	 *
	 * Namespace in which vertex properties are stored
	 */
	@Getter
	@Setter
	private String vertexPropertyNamespace = DEFAULT_VERTEX_PROPERTY_NAMESPACE;

	/**
	 *
	 * Namespace in which edge contexts are stored.
	 */
	@Setter
	private String edgeContextNamespace;

	/**
	 *
	 * Namespace in which edge properties are stored
	 */
	@Getter
	@Setter
	private String edgePropertyNamespace = DEFAULT_EDGE_PROPERTY_NAMESPACE;

	@Getter
	private IRI defaultType = this.toValidatedIri(DEFAULT_TYPE);

	@Getter
	private IRI defaultPredicate = this.toValidatedIri(DEFAULT_PREDICATE);

	@Getter
	private IRI defaultNamedGraph = this.toValidatedIri(DEFAULT_NAMED_GRAPH);

	/**
	 *
	 * <h1>Mapping from property graph vertex types to instance label
	 * properties</h1>
	 *
	 * Properties that need to be used for RDFS labels are represented as map from
	 * vertex type to property name.
	 *
	 * <br>
	 * A property that is selected for an RDFS label will be added as normal
	 * property statement or not depending on the the configuration of
	 * {@link PropertyGraph2RdfMapper#alwaysAddPropertyStatements}.
	 *
	 * <h4>Example:</h4>
	 *
	 * {@code pgVertexType2PropertyForRdfsLabel.country=code} <br>
	 * defines the property value of <em>code</em> as label for vertices of type
	 * <em>country</em>.
	 */
	@Getter
	@Setter
	private Map<String, String> pgVertexType2PropertyForRdfsLabel = new HashMap<>();

	/**
	 *
	 * <h1>Mapping from property graph properties to RDF resources</h1>
	 *
	 * Properties that need to be mapped to resources are represented as a map from
	 * a property name to an IRI pattern. The pattern must contain the
	 * <em>{{VALUE}}</em> substring. This will be substituted with the property
	 * value.
	 *
	 * <h4>Example:</h4>
	 *
	 * pgProperty2RdfResourcePattern.country=http://example.org/resource/country/{{VALUE}}
	 * <br>
	 * converts the property value 'FR' of <em>country</em> into
	 * <em>http://example.org/resource/country/FR</em>.
	 */
	@Getter
	private Map<String, String> pgProperty2RdfResourcePattern = new HashMap<>();

	/**
	 *
	 * A {@link PropertyGraphVertex2RdfMapping} exposing methods for creating RDF
	 * statements for vertices according to this {@link PropertyGraph2RdfMapping}.
	 */
	@Getter
	private PropertyGraphVertex2RdfMapping vertex2RdfMapping = new PropertyGraphVertex2RdfMapping(this);

	/**
	 *
	 * A {@link PropertyGraphEdge2RdfMapping} exposing methods for creating RDF
	 * statements for edges according to this {@link PropertyGraph2RdfMapping}.
	 *
	 * {@link PropertyGraph2RdfMapping}.
	 */
	@Getter
	private PropertyGraphEdge2RdfMapping edge2RdfMapping = new PropertyGraphEdge2RdfMapping(this);

	/**
	 *
	 * Set the map from property graph properties to RDF resource patterns. RDF
	 * resource patterns must contain the string {{VALUE}}.
	 *
	 * @param pgProperty2RdfResourcePattern a map from properties to RDF resource
	 *                                      patterns
	 * @throws Csv2RdfException if a pattern in the map does not contain {{VALUE}}
	 */
	public void setPgProperty2RdfResourcePattern(Map<String, String> pgProperty2RdfResourcePattern) {

		for (String pattern : pgProperty2RdfResourcePattern.values()) {
			if (!pattern.contains(PropertyGraph2RdfConverter.REPLACEMENT_VARIABLE)) {
				throw new Csv2RdfException(
						"The pattern <" + pattern + "> for the new URI must contain the replacement variable "
								+ PropertyGraph2RdfConverter.REPLACEMENT_VARIABLE + ".");
			}
		}
		this.pgProperty2RdfResourcePattern = pgProperty2RdfResourcePattern;
	}

	public void setDefaultNamedGraph(String defaultNamedGraph) {

		this.defaultNamedGraph = toValidatedIri(defaultNamedGraph);
	}

	public void setDefaultType(String defaultType) {

		this.defaultType = toValidatedIri(defaultType);
	}

	public void setDefaultPredicate(String defaultProperty) {

		this.defaultPredicate = toValidatedIri(defaultProperty);
	}

	public String getEdgeContextNamespace() {
		return (this.edgeContextNamespace == null ? getVertexNamespace() : this.edgeContextNamespace);
	}

	/**
	 *
	 * Create an IRI that represents a vertex type as class (aka type) in RDF
	 *
	 * @param type local name, will be URI encoded
	 * @return {@link PropertyGraph2RdfMapping#typeNamespace} + encoded type
	 * @throws Csv2RdfException if the IRI cannot be created
	 */
	// visible for testing
	IRI typeIri(@NonNull String type) {

		String labelUpperCase;
		if (type.isEmpty()) {
			labelUpperCase = type;
		} else {
			labelUpperCase = Character.toUpperCase(type.charAt(0)) + type.substring(1);
		}
		String iri = typeNamespace + encode(labelUpperCase);
		return toValidatedIri(iri);
	}

	/**
	 *
	 * Create an IRI that represents a vertex in RDF
	 *
	 * @param vertex local name, will be URI encoded
	 * @return {@link PropertyGraph2RdfMapping#vertexNamespace} + encoded vertex
	 * @throws Csv2RdfException if the IRI cannot be created
	 */
	// visible for testing
	IRI vertexIri(@NonNull String vertex) {

		String iri = vertexNamespace + encode(vertex);
		return toValidatedIri(iri);
	}

	/**
	 *
	 * Create an IRI that represents an edge in RDF
	 *
	 * @param edge local name, will be URI encoded
	 * @return {@link PropertyGraph2RdfMapping#edgeNamespace} + encoded edge
	 * @throws Csv2RdfException if the IRI cannot be created
	 */
	// visible for testing
	IRI edgeIri(@NonNull String edge) {

		String iri = edgeNamespace + encode(edge);
		return toValidatedIri(iri);
	}

	/**
	 *
	 * Create an IRI that represents an edge context in RDF.
	 *
	 * @param context local context name, will be URI encoded.
	 * @return {@link PropertyGraph2RdfMapping#getEdgeContextNamespace()} + encoded edge context.
	 * @throws Csv2RdfException if the IRI cannot be created.
	 */
	IRI edgeContextIri(@NonNull String context) {
		String iri = getEdgeContextNamespace() + encode(context);
		return toValidatedIri(iri);
	}

	/**
	 * Create an IRI that represents a vertex property in RDF
	 *
	 * @param vertexProperty local name, will be URI encoded
	 * @return {@link PropertyGraph2RdfMapping#vertexPropertyNamespace} + encoded
	 *         vertex property
	 * @throws Csv2RdfException if the IRI cannot be created
	 */
	// visible for testing
	IRI vertexPropertyIri(@NonNull String vertexProperty) {

		String iri = vertexPropertyNamespace + encode(vertexProperty);
		return toValidatedIri(iri);
	}

	/**
	 *
	 * Create an IRI that represents an edge property in RDF
	 *
	 * @param edgeProperty local name, will be URI encoded
	 * @return {@link PropertyGraph2RdfMapping#edgePropertyNamespace} + encoded edge
	 *         property
	 * @throws Csv2RdfException if the IRI cannot be created
	 */
	// visible for testing
	IRI edgePropertyIri(@NonNull String edgeProperty) {

		String iri = edgePropertyNamespace + encode(edgeProperty);
		return toValidatedIri(iri);
	}

	/**
	 *
	 * URI encode a value using the UTF-8 encoding scheme
	 *
	 * @param value
	 * @return URI encoded value
	 * @throws Csv2RdfException if the value could not be encoded
	 */
	private String encode(String value) {

		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			throw new Csv2RdfException("Could not encode '" + value + "' when mapping to RDF.", e);
		}
	}

	/**
	 *
	 * Convert a string into an IRI
	 *
	 * @param iri
	 * @return new {@link IRI}
	 * @throws Csv2RdfException if the IRI cannot be created
	 */
	private IRI toValidatedIri(String iri) {

		try {
			return vf.createIRI(new URI(iri).toString());
		} catch (URISyntaxException | IllegalArgumentException e) {
			throw new Csv2RdfException("Invalid resource URI <" + iri + "> generated when mapping to RDF.", e);
		}
	}

	/**
	 *
	 * Return a literal value including an XML schema data type for all type in
	 * {@link DataType} except {@link DataType#STRING}:
	 * <ul>
	 * <li>Values of {@link DataType#STRING} are not appended with an XML schema
	 * data type.</li>
	 * </ul>
	 *
	 * @param value
	 * @param datatype
	 * @return literal with XML schema data type except for strings
	 * @throws IllegalArgumentException if the data type is not recognized
	 */
	// visible for testing
	Literal value(@NonNull String value, @NonNull DataType datatype) {

		switch (datatype) {
		case BYTE:
			return vf.createLiteral(value, XMLSchema.BYTE);
		case BOOL:
			return vf.createLiteral(value, XMLSchema.BOOLEAN);
		case SHORT:
			return vf.createLiteral(value, XMLSchema.SHORT);
		case INT:
			return vf.createLiteral(value, XMLSchema.INTEGER);
		case LONG:
			return vf.createLiteral(value, XMLSchema.LONG);
		case FLOAT:
			return vf.createLiteral(value, XMLSchema.FLOAT);
		case DOUBLE:
			return vf.createLiteral(value, XMLSchema.DOUBLE);
		case STRING:
			return vf.createLiteral(value);
		case DATETIME:
			return vf.createLiteral(value, XMLSchema.DATE);
		default:
			throw new IllegalArgumentException("Data type not recognized: " + datatype + " for value " + value);
		}

	}

	/**
	 *
	 * Create a RDF statement.
	 *
	 * @param subject
	 * @param predicate
	 * @param object
	 * @param graph
	 * @return a new RDF statement
	 */
	private Statement statement(@NonNull IRI subject, @NonNull IRI predicate, @NonNull Value object,
			@NonNull IRI graph) {

		return vf.createStatement(subject, predicate, object, graph);
	}

	/**
	 *
	 * {@link PropertyGraphVertex2RdfMapping} contains methods that are necessary to
	 * create RDF statements from vertices.
	 *
	 */
	public static class PropertyGraphVertex2RdfMapping {

		/**
		 *
		 * This mapping is used for creating RDF statements.
		 */
		private final PropertyGraph2RdfMapping mapping;

		private PropertyGraphVertex2RdfMapping(PropertyGraph2RdfMapping mapping) {
			this.mapping = mapping;
		}

		/**
		 *
		 * Check if values of the given property can be mapped to an RDF resource.
		 *
		 * @param property vertex property
		 * @return {@code true} if there is pattern to build a resource, else
		 *         {@code false}
		 */
		public boolean containsRdfResourcePatternForProperty(String property) {

			return mapping.pgProperty2RdfResourcePattern.containsKey(property);
		}

		/**
		 *
		 * Create a resource for the value of the given property using the configured
		 * resource pattern for the property. The configuration needs to be done in
		 * {@link PropertyGraph2RdfMapping#pgProperty2RdfResourcePattern}.
		 *
		 * @param property vertex property
		 * @param value    value of the property
		 * @return a resource IRI
		 */

		public IRI mapPropertyValue2RdfResource(String property, String value) {

			String resourcePattern = mapping.pgProperty2RdfResourcePattern.get(property);
			if (resourcePattern == null) {
				return null;
			}
			String resource = resourcePattern.replace(PropertyGraph2RdfConverter.REPLACEMENT_VARIABLE,
					mapping.encode(value));
			return mapping.toValidatedIri(resource);
		}

		/**
		 *
		 * Get the property whose values should be used as RDFS labels for the given
		 * vertex type. The mapping from vertex type to property needs to be configured
		 * in {@link PropertyGraph2RdfMapping#pgVertexType2PropertyForRdfsLabel}.
		 *
		 * @param vertexType type of the vertex (property ~label)
		 * @return property for creating RDFS labels
		 */
		public String getPropertyForRdfsLabel(String vertexType) {

			return mapping.pgVertexType2PropertyForRdfsLabel.get(vertexType);
		}

		/**
		 *
		 * @param subject local name of the subject, will be prefixed with
		 *                {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param type    local name of the type, will be prefixed with
		 *                {@link PropertyGraph2RdfMapping#typeIri}
		 * @return a type statement in
		 *         {@link PropertyGraph2RdfMapping#defaultNamedGraph}
		 */
		public Statement createTypeStatement(@NonNull String subject, @NonNull String type) {

			return mapping.statement(mapping.vertexIri(subject), RDF.TYPE, mapping.typeIri(type),
					mapping.getDefaultNamedGraph());
		}

		/**
		 *
		 * @param subject local name, will be prefixed with
		 *                {@link PropertyGraph2RdfMapping#vertexIri}
		 * @return a type statement in
		 *         {@link PropertyGraph2RdfMapping#defaultNamedGraph} using
		 *         {@link PropertyGraph2RdfMapping#defaultType} as type
		 */
		public Statement createTypeStatement(@NonNull String subject) {

			return mapping.statement(mapping.vertexIri(subject), RDF.TYPE, mapping.getDefaultType(),
					mapping.getDefaultNamedGraph());
		}

		/**
		 *
		 * @param subject local name, will be prefixed with
		 *                {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param label   RDFS label value
		 * @return a statement in {@link PropertyGraph2RdfMapping#defaultNamedGraph}
		 */
		public Statement createRdfsLabelStatement(@NonNull String subject, @NonNull String label) {

			return mapping.statement(mapping.vertexIri(subject), RDFS.LABEL, mapping.value(label, DataType.STRING),
					mapping.getDefaultNamedGraph());
		}

		/**
		 *
		 * @param subject   local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param predicate local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#vertexPropertyIri}
		 * @param literal   literal value
		 * @param dataType  data type of the value
		 * @return a statement in {@link PropertyGraph2RdfMapping#defaultNamedGraph}
		 */
		public Statement createLiteralStatement(@NonNull String subject, @NonNull String predicate,
				@NonNull String literal, @NonNull DataType dataType) {

			return mapping.statement(mapping.vertexIri(subject), mapping.vertexPropertyIri(predicate),
					mapping.value(literal, dataType), mapping.getDefaultNamedGraph());
		}

		/**
		 *
		 * @param subject   local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param predicate local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#edgeIri}
		 * @param value     value will be mapped to an RDF resource by
		 *                  {@link #mapPropertyValue2RdfResource}
		 * @return a statement in {@link PropertyGraph2RdfMapping#defaultNamedGraph}
		 */
		public Statement createRelationStatement(@NonNull String subject, @NonNull String predicate,
				@NonNull String value) {

			return mapping.statement(mapping.vertexIri(subject), mapping.edgeIri(predicate),
					mapPropertyValue2RdfResource(predicate, value), mapping.getDefaultNamedGraph());
		}

	}

	/**
	 *
	 * {@link PropertyGraphEdge2RdfMapping} contains methods that are necessary to
	 * create RDF statements from edges.
	 */
	public static class PropertyGraphEdge2RdfMapping {

		/**
		 *
		 * This mapping is used for creating RDF statements.
		 */
		private final PropertyGraph2RdfMapping mapping;

		private PropertyGraphEdge2RdfMapping(PropertyGraph2RdfMapping mapping) {

			this.mapping = mapping;
		}

		/**
		 *
		 * @param subject   local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param predicate local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#edgeIri}
		 * @param object    local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param context   local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#edgeContextIri}
		 * @return a statement in {@link PropertyGraph2RdfMapping#edgeContextIri}(context)
		 */
		public Statement createRelationStatement(@NonNull String subject, @NonNull String predicate,
				@NonNull String object, @NonNull String context) {

			return mapping.statement(mapping.vertexIri(subject), mapping.edgeIri(predicate), mapping.vertexIri(object),
					mapping.edgeContextIri(context));
		}

		/**
		 *
		 * @param subject local name, will be prefixed with
		 *                {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param object  local name, will be prefixed with
		 *                {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param context local name, will be prefixed with
		 *                {@link PropertyGraph2RdfMapping#edgeContextIri}
		 * @return a statement in {@link PropertyGraph2RdfMapping#edgeContextIri}(context)
		 *         using {@link PropertyGraph2RdfMapping#defaultPredicate} as predicate
		 */
		public Statement createRelationStatement(@NonNull String subject, @NonNull String object,
				@NonNull String context) {

			return mapping.statement(mapping.vertexIri(subject), mapping.getDefaultPredicate(),
					mapping.vertexIri(object), mapping.edgeContextIri(context));
		}

		/**
		 *
		 * @param subject   local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#vertexIri}
		 * @param predicate local name, will be prefixed with
		 *                  {@link PropertyGraph2RdfMapping#edgePropertyIri}
		 * @param literal   literal value
		 * @param dataType  data type of the value
		 * @return a statement in {@link PropertyGraph2RdfMapping#defaultNamedGraph}
		 */
		public Statement createLiteralStatement(@NonNull String subject, @NonNull String predicate,
				@NonNull String literal, @NonNull DataType dataType) {

			return mapping.statement(mapping.vertexIri(subject), mapping.edgePropertyIri(predicate),
					mapping.value(literal, dataType), mapping.getDefaultNamedGraph());
		}

	}

}

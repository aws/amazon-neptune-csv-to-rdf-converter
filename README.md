# Amazon Neptune CSV to RDF Converter

A tool for [Amazon Neptune](https://aws.amazon.com/neptune/) that converts property graphs stored as comma separated values into RDF graphs.


## Usage

Amazon Neptune CSV to RDF Converter is a Java library for converting a property graph stored in
CSV files to RDF. It expects an input directory containing the CSV files, an output directory, and an
optional configuration file. The output directory will be created if it does not exist.
See [Gremlin Load Data Format](https://docs.aws.amazon.com/neptune/latest/userguide/bulk-load-tutorial-format-gremlin.html)
about the input and [RDF 1.1 N-Quads](https://www.w3.org/TR/n-quads/) about the output format.

The input files need to be UTF-8 encoded. The same encoding is used for the output files.

The library is available as executable Jar file and can be run from the command line by `java -jar amazon-neptune-csv2rdf.jar -i <input directory> -o <output directory>`. Use `java -jar amazon-neptune-csv2rdf.jar -h` to see all options.

The conversion is based on two steps. First, a **general mapping** from property graph vertices and edges
to RDF statements is applied to the input files. The optional second step **transforms RDF resource IRIs**
according to user defined rules for replacing artificial ids with more natural ones. However, this
transformation needs to load all triples into main memory, so the JVM memory must be set accordingly with
`-Xmx`, e.g., `java -Xmx2g`.

Let's start with a small example to see how both steps work.

**General mapping**

Let vertices and edges be given as

	~id,~label,name,code,country
	1,city,Seattle,S,USA
	2,city,Vancouver,V,CA

and

	~id,~label,~from,~to,distance,type
	a,route,1,2,166,highway

Using some simplified namespaces (see Configuration below for the details), the mapping results in:

	<vertex:1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <type:City> <dng:/> .
	<vertex:1> <vproperty:name> "Seattle" <dng:/> .
	<vertex:1> <vproperty:code> "S" <dng:/> .
	<vertex:1> <vproperty:country> "USA" <dng:/> .
	<vertex:2> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <type:City> <dng:/> .
	<vertex:2> <vproperty:name> "Vancouver" <dng:/> .
	<vertex:2> <vproperty:code> "V" <dng:/> .
	<vertex:2> <vproperty:country> "CA" <dng:/> .
	
	<vertex:1> <edge:route> <vertex:2> <vertex:a> .
	<vertex:a> <eproperty:distance> "166" <dng:/> .
	<vertex:a> <eproperty:type> "highway" <dng:/> .

The result shows that **edge identifiers are stored as context** of the corresponding RDF statement, and
the edge properties are statements about that context. The edge identifiers can be queried in SPARQL using
the [GRAPH keyword](https://www.w3.org/TR/sparql11-query/#accessByLabel).

Vertex labels are mapped to **RDF types**. The first letter of the label will be capitalized for this step:
The label `city` becomes the RDF type `<type:City>`.

Additionally, the mapping can add **RDFS labels** to the vertices. For example, the configuration

	mapper.mapping.pgVertexType2PropertyForRdfsLabel.city=name
    
 creates two additional RDF statements:

	<vertex:1> <http://www.w3.org/2000/01/rdf-schema#label> "Seattle" <dng:/> .
	<vertex:2> <http://www.w3.org/2000/01/rdf-schema#label> "Vancouver" <dng:/> .

The mapping can also map **property values to resources**. In the example, the value for *country* becomes
an URI with

	mapper.mapping.pgProperty2RdfResourcePattern.country=country:{{VALUE}}

and the two statements with the literal value "USA" and "CA" are replaced by:

	<vertex:1> <edge:country> <country:USA> <dng:/> .
	<vertex:2> <edge:country> <country:CA> <dng:/> .

**URI transformations**

A URI transformation rule replaces parts of a resource URI with the value of a property. In the previous
example, the code could be used to create the resource URIs. This can be achieved using:

	transformer.uriPostTransformations.1.srcPattern=vertex:([0-9]+)
	transformer.uriPostTransformations.1.typeUri=type:City
	transformer.uriPostTransformations.1.propertyUri=vproperty:code
	transformer.uriPostTransformations.1.dstPattern=city:{{VALUE}}

The resulting statements are now:

	<city:S> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <type:City> <dng:/> .
	<city:S> <http://www.w3.org/2000/01/rdf-schema#label> "Seattle" <dng:/> .
	<city:S> <vproperty:name> "Seattle" <dng:/> .
	<city:S> <vproperty:code> "S" <dng:/> .
	<city:S> <edge:country> <country:USA> <dng:/> .
	<city:V> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <type:City> <dng:/> .
	<city:V> <http://www.w3.org/2000/01/rdf-schema#label> "Vancouver" <dng:/> .
	<city:V> <vproperty:name> "Vancouver" <dng:/> .
	<city:V> <vproperty:code> "V" <dng:/> .
	<city:V> <edge:country> <country:CA> <dng:/> .
	<city:S> <edge:route> <city:V> <vertex:a> .
	<vertex:a> <eproperty:distance> "166" <dng:/> .
	<vertex:a> <eproperty:type> "highway" <dng:/> .


## Configuration

The configuration of the converter is a property file. It contains a default type, a default named graph,
and namespaces for building vertex URIs, edge URIs, type URIs, vertex property URIs, and edge property URIs.
The rules for adding RDFS labels, creating resources from property values, and the URI transformations are
optional. It's also possible to set the file extension of the input files.

If no configuration file is given, the following default values are used:

	inputFileExtension=csv
	
	mapper.alwaysAddPropertyStatements=true
	
	mapper.mapping.typeNamespace=http://aws.amazon.com/neptune/csv2rdf/class/
	mapper.mapping.vertexNamespace=http://aws.amazon.com/neptune/csv2rdf/resource/
	mapper.mapping.edgeNamespace=http://aws.amazon.com/neptune/csv2rdf/objectProperty/
	mapper.mapping.vertexPropertyNamespace=http://aws.amazon.com/neptune/csv2rdf/datatypeProperty/
	mapper.mapping.edgePropertyNamespace=http://aws.amazon.com/neptune/csv2rdf/datatypeProperty/
	mapper.mapping.defaultNamedGraph=http://aws.amazon.com/neptune/vocab/v01/DefaultNamedGraph
	mapper.mapping.defaultType=http://www.w3.org/2002/07/owl#Thing

The setting `mapper.alwaysAddPropertyStatements` has only effect if a rule for adding RDFS labels is used. In
that case it decides whether or not to add the property that is being used for the RDFS label additionally
as RDF literal statement with that property. For the small example above, if the setting was chosen to be
`false`, two statements would not be generated:

	<city:S> <vproperty:name> "Seattle" <dng:/> .
	<city:V> <vproperty:name> "Vancouver" <dng:/> .
    
**Vertex type to RDFS label mapping**

Vertex types are defined by vertex labels. The option `mapper.mapping.pgVertexType2PropertyForRdfsLabel.<vertex type>.<vertex property>` is used to specify a mapping from a vertex type to to a vertex property, whose value is then used
to create RDFS labels for any vertex belonging to this vertex type. Multiple such mappings are allowed.

**Property to RDF resource mapping**

The option `pgProperty2RdfResourcePattern.<vertex property>=<namespace>{{VALUE}}` is used to create RDF resources
instead of literal values for vertices where the specified property is found. The variable `{{value}}` will
be replaced with the value of the property and prefixed with the given namespace. Multiple such mappings
are allowed.

**URI Post Transformations**

URI Post Transformations are used to transform RDF resource IRIs into more readable ones.

An URI Post Transformation consists of four elements:

	uriPostTransformation.<ID>.srcPattern=<URI regex patten>
	uriPostTransformation.<ID>.typeUri=<URI>
	uriPostTransformation.<ID>.propertyUri=<URI>
	uriPostTransformation.<ID>.dstPattern=<URI pattern>

A positive integer `<ID>` is required to group the elements. The groupingnumbers of several transformation
configurations do not need to be consecutive. The transformation rules will be executed in ascending orde
according to the grouping numbers. All four configuration items are required:

* `srcPattern` is a URI with a single regular expression group, e.g.
 `<http://aws.amazon.com/neptune/csv2rdf/resource/([0-9]+)>`, defining
 the URI patterns of RDF resources to which the post transformation applies.
 * `typeUri` filters out all matched source URIs that do not belong to
 the specified RDF type.
* `propertyUri` is the RDF predicate pointing to the replacement
 value.
* `dstPattern` is the new URI, which must contain a
 `{{VALUE}}` substring which is then substituted with the value of
 `valueProp`.

*Example:*

	uriPostTransformation.1.srcPattern=http://example.org/resource/([0-9]+)
	uriPostTransformation.1.typeUri=http://example.org/class/Country
	uriPostTransformation.1.propertyUri=http://example.org/datatypeProperty/code
	uriPostTransformation.1.dstPattern=http://example.org/resource/{{VALUE}}

This configuration transforms the URI `http://example.org/resource/123` into  `http://example.org/resource/FR`,
given that there are the statements:

	http://example.org/resource/123 a http://example.org/class/Country.
	http://example.org/resource/123 http://example.org/datatypeProperty/code "FR".

Note that we assume that the property `valueProp` is unique for each resource, otherwise a runtime exception
will be thrown. Also note that the post transformation is applied using a two-pass algorithm over the
generated data, and the translation mapping is kept fully in memory. This means the property is suitable
only in cases where the number of mappings is small or if the amount of main memory is large.


**Complete Configuration**

The complete configuration for the small example above is:

	mapper.alwaysAddPropertyStatements=false
	
	mapper.mapping.typeNamespace=type:
	mapper.mapping.vertexNamespace=vertex:
	mapper.mapping.edgeNamespace=edge:
	mapper.mapping.vertexPropertyNamespace=vproperty:
	mapper.mapping.edgePropertyNamespace=eproperty:
	mapper.mapping.defaultNamedGraph=dng:/
	mapper.mapping.defaultType=dt:/
	mapper.mapping.defaultPredicate=dp:/	
	mapper.mapping.pgVertexType2PropertyForRdfsLabel.city=name
	
	mapper.mapping.pgProperty2RdfResourcePattern.country=country:{{VALUE}}
	
	transformer.uriPostTransformations.1.srcPattern=vertex:([0-9]+)
	transformer.uriPostTransformations.1.typeUri=type:City
	transformer.uriPostTransformations.1.propertyUri=vproperty:code
	transformer.uriPostTransformations.1.dstPattern=city:{{VALUE}}


## Examples

The small example above is contained in `src/test/example` and can be tested with:

	java -jar amazon-neptune-csv2rdf.jar -i src/test/example/ -o . -c src/test/example/city.properties

Additionally, the directory `src/test/air-routes` contains a Zip archive of the
[Air Routes data set](https://github.com/krlawrence/graph/tree/master/sample-data) and a sample configuration.
After unzipping the archive into `air-routes`, it can be converted with:

	java -jar amazon-neptune-csv2rdf.jar -i air-routes/ -o . -c src/test/air-routes/air-routes.properties


## Known Limitations

The general mapping from property graph vertices and edges is done individually for each CSV line in order to avoid
loading the whole CSV file into memory. However, that means that properties being defined on different lines are not joined
and cardinality constraints cannot be checked. For example, the RDF mapping (using the simplified namespaces from the small
example above) of the following property graph
 * should reject the statement `<vertex:1> <eproperty:since> "tomorrow" <dng:/>` because edge properties have *single*
   cardinality,
 * should contain only one `<vertex:2> <edge:knows> <vertex:3> <vertex:1>` statement (however, RDF joins multiple equal
   statements into one), and
 * should not generate the statement `<vertex:3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <dt:/> <dng:/>` because
   vertex 3 has a label.

**Property Graph:**

	~id,~label,name
	2,person,Alice
	3,person,Bob
	3,,Robert

	~id,~label,~from,~to,since,personally
	1,knows,2,3,yesterday,
	1,knows,2,3,tomorrow,
	1,knows,2,3,,true

**RDF mapping:**

	<vertex:2> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <type:Person> <dng:/> .
	<vertex:2> <vproperty:name> "Alice" <dng:/> .
	<vertex:3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <type:Person> <dng:/> .
	<vertex:3> <vproperty:name> "Bob" <dng:/> .
	<vertex:3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <dt:/> <dng:/> .
	<vertex:3> <vproperty:name> "Robert" <dng:/> .

	<vertex:2> <edge:knows> <vertex:3> <vertex:1> .
	<vertex:1> <eproperty:since> "yesterday" <dng:/> .
	<vertex:2> <edge:knows> <vertex:3> <vertex:1> .
	<vertex:1> <eproperty:since> "tomorrow" <dng:/> .
	<vertex:2> <edge:knows> <vertex:3> <vertex:1> .
	<vertex:1> <eproperty:personally> "true" <dng:/> .


## Building from source

Amazon Neptune CSV to RDF Converter is a Java Maven project and requires JDK 8 and Maven 3 to build from source. Change
into the source folder containing the file `pom.xml` and run `mvn clean install`. The directory `target/` contains the
executable Jar library `amazon-neptune-csv2rdf.jar` after a successful build.


## License

Amazon Neptune CSV to RDF Converter is available under [Apache License, Version 2.0](https://aws.amazon.com/apache2.0).


----

Copyright Amazon.com Inc. or its affiliates.

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.rdf4j.rio.RDFFormat;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsFactory;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 *
 * {@link PropertyGraph2RdfConverter} converts property graph vertices and edges
 * stored as comma separated values into RDF N-Quads files. The conversion uses
 * two steps: <br>
 * First, an {@link PropertyGraph2RdfMapper} applies the configured
 * {@link PropertyGraph2RdfMapping} to the property graph data for generating
 * RDF resources, predicates, literals, triples, and, in case of edge
 * properties, quads. <br>
 * Then, an {@link UriPostTransformer} performs the configured
 * {@link UriPostTransformation}s on the RDF data. These transformations can be
 * used to rewrite resource IRIs into more readable ones by replacing parts of
 * them with property values.
 *
 */
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE)
public class PropertyGraph2RdfConverter {

	public static final RDFFormat RDF_FORMAT = RDFFormat.NQUADS;

	public static final String DEFAULT_PROPERTY_GRAPH_FILE_EXTENSION = "csv";
	public static final String DEFAULT_RDF_FILE_EXTENSION = RDF_FORMAT.getDefaultFileExtension();

	public static final String REPLACEMENT_VARIABLE = "{{VALUE}}";

	/**
	 *
	 * Extension of the property graph input files. Only files matching the
	 * extension are converted.
	 */
	@Getter
	@Setter
	private String inputFileExtension = DEFAULT_PROPERTY_GRAPH_FILE_EXTENSION;

	/**
	 *
	 * Output file suffix, determining the RDF format in which the result is
	 * written. Currently, only N-Quads is supported so this value cannot be
	 * changed.
	 */
	@Getter
	private String outputFileExtension = DEFAULT_RDF_FILE_EXTENSION;

	/**
	 *
	 * The {@link PropertyGraph2RdfMapper} performs the basic mapping defined in
	 * {@link PropertyGraph2RdfMapping} from property graph vertices and edges into
	 * RDF.
	 */
	@Getter
	@Setter
	private PropertyGraph2RdfMapper mapper = new PropertyGraph2RdfMapper();

	/**
	 *
	 * The {@link UriPostTransformer} runs additional transformations defined in
	 * {@link UriPostTransformation}s on RDF resource IRIs.
	 */
	@Getter
	@Setter
	private UriPostTransformer transformer = new UriPostTransformer();

	/**
	 *
	 * @param config property file, can be {@code null}
	 */
	public PropertyGraph2RdfConverter(File config) {
		if (config != null) {
			this.load(config);
		}
	}

	/**
	 *
	 * Convert property graph files into RDF files.
	 *
	 * @param inputDirectory  directory containing the property graph files, must
	 *                        exist, available files must be UTF-8 encoded
	 * @param outputDirectory output directory for the RDF files, must exist, output
	 *                        will be UTF-8 encoded
	 */
	public void convert(File inputDirectory, File outputDirectory) {

		List<File> propertyGraphFiles = this.listPropertyGraphFiles(inputDirectory);
		List<File> rdfFiles = new ArrayList<>();

		for (File propertyGraphFile : propertyGraphFiles) {
			File rdfFile = getRdfFile(outputDirectory, propertyGraphFile);
			mapper.map(propertyGraphFile, rdfFile);
			rdfFiles.add(rdfFile);
		}

		transformer.applyTo(rdfFiles, mapper.getMapping().getVertexNamespace());
	}

	/**
	 *
	 * List files in a directory matching
	 * {@link PropertyGraph2RdfConverter#inputFileExtension}.
	 *
	 * @param directory
	 * @return list of matching files
	 */
	// visible for testing
	List<File> listPropertyGraphFiles(File directory) {

		final File[] files = directory.listFiles((file) -> {
			return file.isFile() && file.getName().endsWith("." + inputFileExtension);
		});

		if (files == null) {
			throw new Csv2RdfException("Could not read from input directory: " + directory.getAbsolutePath());
		}
		if (files.length == 0) {
			throw new Csv2RdfException(
					"No files with extension " + inputFileExtension + " found at: " + directory.getAbsolutePath());
		}

		return new ArrayList<File>(Arrays.asList(files));
	}

	/**
	 *
	 * @param rdfDirectory      the output directory
	 * @param propertyGraphFile
	 * @return a file in the output directory with the name of the property graph
	 *         file but RDF extension of
	 *         {@link PropertyGraph2RdfConverter#outputFileExtension}
	 */
	// visible for testing
	File getRdfFile(File rdfDirectory, File propertyGraphFile) {

		String rdfFileName = propertyGraphFile.getName().replaceAll(Pattern.quote(inputFileExtension) + "$",
				outputFileExtension);
		return new File(rdfDirectory, rdfFileName);
	}

	/**
	 * Load the configuration values and initialize all fields of the
	 * {@link PropertyGraph2RdfConverter} instance and its dependent objects.
	 *
	 * @param config property file
	 */
	private void load(@NonNull File config) {
		try {
			ObjectMapper mapper = new ObjectMapper(new JavaPropsFactory());
			mapper.readerForUpdating(this).readValue(config);
		} catch (UnrecognizedPropertyException e) {
			throw new Csv2RdfException(
					"Loading configuration failed because of unknown property: " + e.getPropertyName(), e);
		} catch (JsonMappingException e) {
			throw new Csv2RdfException(getErrorMessage(e), e);
		} catch (IOException e) {
			throw new Csv2RdfException("Configuration file not found: " + config.getAbsolutePath(), e);
		}
	}

	/**
	 * Try to find the field and the specific cause where the failure occurred. As
	 * {@link PropertyGraph2RdfMapping#setPgProperty2RdfResourcePattern} and
	 * {@link UriPostTransformation#UriPostTransformation} perform consistency
	 * checks, {@link Csv2RdfException} can be the cause of
	 * {@link JsonMappingException}, too.
	 *
	 * @param e
	 * @return error message
	 */
	private String getErrorMessage(JsonMappingException e) {

		List<Reference> path = e.getPath();
		String message;
		if (e.getCause() instanceof Csv2RdfException) {
			message = e.getCause().getMessage();
		} else {
			message = e.getOriginalMessage();
		}
		for (int i = path.size() - 1; i >= 0; --i) {
			String field = path.get(i).getFieldName();
			if (field != null) {
				return "Loading configuration failed because of invalid input at " + field + ": " + message;
			}
		}

		return "Loading configuration failed because of invalid input: " + message;
	}

}

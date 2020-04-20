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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.UnsupportedRDFormatException;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * This class applies transformations specified in zero to many
 * {@link UriPostTransformation} to the RDF resource IRIs resulting from the
 * basic mapping performed by {@link PropertyGraph2RdfMapper}.
 *
 * The transformations can be defined in the configuration file.
 *
 */
@Slf4j
@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE)
public class UriPostTransformer {

	private final SimpleValueFactory vf = SimpleValueFactory.getInstance();

	/**
	 * List of rewriting rules for RDF resource IRIs.
	 */
	@Getter
	@Setter
	private Collection<UriPostTransformation> uriPostTransformations = new ArrayList<>();

	/**
	 *
	 * @param files   The list of RDF files that need to be transformed.
	 * @param baseUri used for resolving relative URIs
	 * @throws Csv2RdfException if the transformation fails
	 */
	public void applyTo(List<File> files, String baseUri) {
		if (uriPostTransformations.isEmpty()) {
			return;
		}

		log.info("-> Applying URI post transformations...");

		for (File file : files) {

			extractUriTransformationResourcesAndReplacementValues(file, baseUri);
		}

		for (File file : files) {

			transformResources(file, baseUri);
		}
	}

	/**
	 * Read each RDF statement and store its resources and/or literal values if they
	 * are matching a transformation rule.
	 *
	 * @param file    RDF file, will not be changed
	 * @param baseUri used for resolving relative URIs
	 */
	private void extractUriTransformationResourcesAndReplacementValues(File file, String baseUri) {

		log.info("--> Extracting URI transformation resources and replacement values from " + file.getName() + "...");

		try (FileInputStream fis = new FileInputStream(file)) {
			RDFParser rdfParser = Rio.createParser(PropertyGraph2RdfConverter.RDF_FORMAT);
			rdfParser.setRDFHandler(new AbstractRDFHandler() {
				@Override
				public void handleStatement(Statement statement) {

					register(statement);
				}
			});

			rdfParser.parse(fis, baseUri);
		} catch (UnsupportedRDFormatException | RDFHandlerException | RDFParseException | IOException e) {
			throw new Csv2RdfException("Extracting URI transformation resources and replacement values from "
					+ file.getAbsolutePath() + " failed.", e);
		}
	}

	/**
	 *
	 * Register all resources and the literal of a statement at all transformation
	 * rules.
	 *
	 * @param statement
	 */
	// visible for testing
	void register(Statement statement) {
		for (UriPostTransformation uriPostTransformation : uriPostTransformations) {

			// register the URIs (may be a no-op)
			if (statement.getSubject() instanceof IRI && statement.getPredicate() instanceof IRI
					&& statement.getObject() instanceof IRI) {
				uriPostTransformation.registerResource(statement.getSubject().stringValue(),
						statement.getPredicate().stringValue(), statement.getObject().stringValue());
			}

			// register the value (may be a no-op)
			if (statement.getSubject() instanceof IRI && statement.getPredicate() instanceof IRI
					&& statement.getObject() instanceof Literal) {
				uriPostTransformation.registerReplacementValue(statement.getSubject().stringValue(),
						statement.getPredicate().stringValue(), statement.getObject().stringValue());
			}
		}
	}

	/**
	 * Read all RDF statements and rewrite their IRI if applicable. <br>
	 * Has no effect for resources whose IRI did not match any transformation rule
	 * in
	 * {@link UriPostTransformer#extractUriTransformationResourcesAndReplacementValues}
	 * or if no replacement value was found.
	 *
	 * @param file    RDF file, will be changed during the process
	 * @param baseUri used for resolving relative URIs
	 */
	private void transformResources(File file, String baseUri) {

		log.info("--> Transforming resources in " + file.getName() + "...");

		File transformedFile = new File(file.getParentFile(), "transformed." + file.getName());

		try (FileOutputStream fos = new FileOutputStream(transformedFile);
				FileInputStream fis = new FileInputStream(file)) {
			final RDFWriter rdfWriter = Rio.createWriter(PropertyGraph2RdfConverter.RDF_FORMAT, fos);
			rdfWriter.startRDF();
			RDFParser rdfParser = Rio.createParser(PropertyGraph2RdfConverter.RDF_FORMAT);
			rdfParser.setRDFHandler(new AbstractRDFHandler() {
				@Override
				public void handleStatement(Statement statement) {

					Statement statement2 = transform(statement);
					rdfWriter.handleStatement(statement2);
				}
			});
			rdfParser.parse(fis, baseUri);
			rdfWriter.endRDF();
		} catch (UnsupportedRDFormatException | RDFHandlerException | RDFParseException | IOException e) {
			throw new Csv2RdfException("Applying URI transformation to file " + file.getAbsolutePath() + " failed.", e);
		}

		if (!transformedFile.renameTo(file)) {
			throw new Csv2RdfException("Transformed file " + transformedFile.getName() + " could not be renamed to: "
					+ file.getAbsolutePath());
		}
	}

	/**
	 *
	 * Apply the transformation rules to the resources of a statement. The first
	 * matching rule makes the change.
	 *
	 * @param statement
	 * @return a new statement with transformed resources, resources that do not
	 *         match a rule are not modified; if no resource matched a rule the
	 *         incoming statement is returned
	 * @throws Csv2RdfException if the IRI cannot be created
	 */
	// visible for testing
	Statement transform(Statement statement) {
		Resource newSubject = null;
		IRI newPredicate = null;
		Value newObject = null;
		Resource newContext = null;

		for (UriPostTransformation uriPostTransformation : uriPostTransformations) {

			// register the URIs (may be a no-op)
			if (newSubject == null && statement.getSubject() instanceof IRI) {
				newSubject = uriPostTransformation.apply(statement.getSubject().stringValue());
			}

			if (newPredicate == null && statement.getPredicate() instanceof IRI) {
				newPredicate = uriPostTransformation.apply(statement.getPredicate().stringValue());
			}

			if (newObject == null && statement.getObject() instanceof IRI) {
				newObject = uriPostTransformation.apply(statement.getObject().stringValue());
			}

			if (newContext == null && statement.getContext() instanceof IRI) {
				newContext = uriPostTransformation.apply(statement.getContext().stringValue());
			}
		}

		if (newSubject == null && newPredicate == null && newObject == null && newContext == null) {
			return statement;
		}

		Resource subject = newSubject == null ? statement.getSubject() : newSubject;
		IRI predicate = newPredicate == null ? statement.getPredicate() : newPredicate;
		Value object = newObject == null ? statement.getObject() : newObject;
		Resource context = newContext == null ? statement.getContext() : newContext;

		return vf.createStatement(subject, predicate, object, context);
	}
}
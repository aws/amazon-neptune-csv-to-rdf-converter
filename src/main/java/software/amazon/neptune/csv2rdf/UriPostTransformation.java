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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <h1>URI Post Transformation</h1>
 *
 * URI Post Transformations are used to transform RDF resource IRIs into more
 * readable ones.
 *
 * An URI Post Transformation consists of four elements:
 *
 * <pre>
 * {@code
 * uriPostTransformation.<ID>.srcPattern=<URI regex patten>
 * uriPostTransformation.<ID>.typeUri=<URI>
 * uriPostTransformation.<ID>.propertyUri=<URI>
 * uriPostTransformation.<ID>.dstPattern=<URI pattern>
 * }
 * </pre>
 *
 * A positive integer {@code <ID>} is required to group the elements. The
 * grouping numbers of several transformation configurations do not need to be
 * consecutive. The transformation rules will be executed in ascending order
 * according to the grouping numbers. All four configuration items are required:
 *
 * <ol>
 * <li>{@code srcPattern} is a URI with a single regular expression group, e.g.
 * {@code <http://aws.amazon.com/neptune/csv2rdf/resource/([0-9]+)>}, defining
 * the URI patterns of RDF resources to which the post transformation applies.
 * <li>{@code typeUri} filters out all matched source URIs that do not belong to
 * the specified RDF type.
 * <li>{@code propertyUri} is the RDF predicate pointing to the replacement
 * value.
 * <li>{@code dstPattern} is the new URI, which must contain a
 * <em>{{VALUE}}</em> substring which is then substituted with the value of
 * {@code propertyUri}.
 * </ol>
 *
 * <b>Example:</b>
 *
 * <pre>
 * uriPostTransformation.1.srcPattern=http://example.org/resource/([0-9]+)
 * uriPostTransformation.1.typeUri=http://example.org/class/Country
 * uriPostTransformation.1.propertyUri=http://example.org/datatypeProperty/code
 * uriPostTransformation.1.dstPattern=http://example.org/resource/{{VALUE}}
 * </pre>
 *
 * This configuration transforms the URI {@code http://example.org/resource/123}
 * into {@code http://example.org/resource/FR}, given that there are the
 * statements: <br>
 * {@code http://example.org/resource/123} a
 * {@code http://example.org/class/Country}. <br>
 * {@code http://example.org/resource/123}
 * {@code http://example.org/datatypeProperty/code} "FR".
 *
 * <p>
 * Note that we assume that the property {@code propertyUri} is unique for each
 * resource, otherwise a runtime exception will be thrown. Also note that the
 * post transformation is applied using a two-pass algorithm over the generated
 * data, and the translation mapping is kept fully in memory. This means the
 * property is suitable only in cases where the number of mappings is small or
 * if the amount of main memory is large.
 * </p>
 */
@Slf4j
@ToString(includeFieldNames = true)
public class UriPostTransformation {

	@Getter
	private final Pattern srcPattern;
	@Getter
	private final String typeUri;
	@Getter
	private final String propertyUri;
	@Getter
	private final String dstPattern;

	@ToString.Exclude
	private final Set<String> resources = new HashSet<>();
	@ToString.Exclude
	private final Map<String, String> resource2Value = new HashMap<>();

	@ToString.Exclude
	private final SimpleValueFactory vf = SimpleValueFactory.getInstance();

	/**
	 * Create a URI post transformation rule.
	 *
	 * @param srcPattern  URI with a single regular expression group, e.g.
	 *                    {@code <http://aws.amazon.com/neptune/csv2rdf/resource/([0-9]+)>},
	 *                    defining the URI patterns of RDF resources to which the
	 *                    post transformation applies.
	 * @param typeUri     RDF type URI to filter out all matched source URIs that do
	 *                    not belong to the specified RDF type.
	 * @param propertyUri An RDF predicate pointing to the replacement value.
	 * @param dstPattern  is the new URI, which must contain a <em>{{VALUE}}</em>
	 *                    substring which is then substituted with the value of
	 *                    {@code propertyUri}.
	 * @throws Csv2RdfException if the regex of scrPattern is invalid or dstPattern
	 *                          does not contain {{VALUE}}
	 */
	@JsonCreator
	public UriPostTransformation(@JsonProperty(value = "srcPattern", required = true) @NonNull String srcPattern,
			@JsonProperty(value = "typeUri", required = true) @NonNull String typeUri,
			@JsonProperty(value = "propertyUri", required = true) @NonNull String propertyUri,
			@JsonProperty(value = "dstPattern", required = true) @NonNull String dstPattern) {

		try {
			this.srcPattern = Pattern.compile(srcPattern);
		} catch (PatternSyntaxException e) {
			throw new Csv2RdfException("Regex is bad. " + e.getMessage() + ".", e);
		}
		this.typeUri = typeUri;
		this.propertyUri = propertyUri;
		if (!dstPattern.contains(PropertyGraph2RdfConverter.REPLACEMENT_VARIABLE)) {
			throw new Csv2RdfException(
					"The pattern <" + dstPattern + "> for the new URI must contain the replacement variable "
							+ PropertyGraph2RdfConverter.REPLACEMENT_VARIABLE + ".");
		}

		this.dstPattern = dstPattern;
	}

	/**
	 * Phase 1: register all URIs; this is a no-op if the paramters do not match as
	 * described.
	 *
	 * @param subject   resource to be possibly transformed, must match
	 *                  {@link UriPostTransformation#srcPattern}
	 * @param predicate must match {@link RDF#TYPE}
	 * @param object    must match the {@link UriPostTransformation#typeUri}
	 */
	public void registerResource(String subject, String predicate, String object) {

		if (predicate.equals(RDF.TYPE.toString()) && object.equals(typeUri)) {
			Matcher matcher = srcPattern.matcher(subject);
			if (matcher.matches()) {
				resources.add(subject); // may override (which does not harm)
			}
		}
	}

	/**
	 * Phase 2: register the replacement values; this is a no-op if the predicate
	 * does not match as described.
	 *
	 * @param subject   resource to be possibly transformed
	 * @param predicate must match {@link UriPostTransformation#propertyUri}
	 * @param object    possible replacement value
	 * @throws Csv2RdfException if there was already a replacement value registered
	 *                          for the subject
	 */
	public void registerReplacementValue(String subject, String predicate, String object) {

		if (propertyUri.equals(predicate)) {

			if (resource2Value.containsKey(subject) && !resource2Value.get(subject).equals(object)) {
				throw new Csv2RdfException("Found duplicate, inconsistent value for <" + subject + ">: " + object
						+ " vs. " + resource2Value.get(subject));
			}
			resource2Value.put(subject, object);
		}
	}

	/**
	 * Phase 3: apply transformation, return {@code null} if no transformation has
	 * been applied. <br>
	 * The replacement value will be URI encoded.
	 *
	 * @param uri where the part according to the matching group of
	 *            {@link UriPostTransformation#srcPattern} is going to be replaced
	 * @return the new URI string or {@code null} if the resource did not match the
	 *         type at {@link UriPostTransformation#typeUri} or no replacement value
	 *         was found
	 */
	public IRI apply(String uri) {
		if (!resources.contains(uri)) {
			return null; // type did not match, no op
		}

		if (!resource2Value.containsKey(uri)) {
			log.info("---> No replacement value found for <{}>. Resource was not transformed.", uri);
			return null;
		}

		String value = resource2Value.get(uri);

		String resource = dstPattern.replace(PropertyGraph2RdfConverter.REPLACEMENT_VARIABLE, encode(value));
		return toValidatedIri(resource);
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
			throw new Csv2RdfException("Could not encode '" + value + "' when applying " + toString() + ".", e);
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
			throw new Csv2RdfException("Invalid resource URI <" + iri + "> generated when applying " + toString() + ".",
					e);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(srcPattern, typeUri, propertyUri, dstPattern);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UriPostTransformation other = (UriPostTransformation) obj;
		if (srcPattern == null) {
			if (other.srcPattern != null)
				return false;
		} else if (other.srcPattern == null) {
			return false;
		} else if (!srcPattern.pattern().equals(other.srcPattern.pattern())) {
			return false;
		}
		return Objects.equals(propertyUri, other.propertyUri) && Objects.equals(typeUri, other.typeUri)
				&& Objects.equals(dstPattern, other.dstPattern);
	}
}
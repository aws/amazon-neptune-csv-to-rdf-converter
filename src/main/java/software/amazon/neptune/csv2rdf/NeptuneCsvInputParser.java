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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.neptune.csv2rdf.NeptuneCsvHeader.NeptuneCsvEdgeHeader;
import software.amazon.neptune.csv2rdf.NeptuneCsvHeader.NeptuneCsvVertexHeader;
import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.Cardinality;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvSetValuedUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvSingleValuedUserDefinedProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptuneCsvUserDefinedArrayProperty;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphEdge;
import software.amazon.neptune.csv2rdf.NeptunePropertyGraphElement.NeptunePropertyGraphVertex;

/**
 *
 * Parser for the Neptune CSV property graph format.
 * {@link NeptuneCsvInputParser#next()} iterates over all vertices or edges in
 * the source file.
 *
 */
@Slf4j
public class NeptuneCsvInputParser implements AutoCloseable, Iterator<NeptunePropertyGraphElement> {

	/**
	 *
	 * CSV parser for the Neptune property graph file
	 */
	private final CSVParser csvParser;

	/**
	 *
	 * header of the CSV file (first row)
	 */
	private final NeptuneCsvHeader header;

	/**
	 *
	 * the record iterator, which will return the data (non-header) records of the
	 * file
	 */
	private final Iterator<CSVRecord> iterator;

	/**
	 *
	 * Sets up a {@link CSVRecord} iterator over the input file and parses the first
	 * row as header.
	 *
	 * @param file CSV input file
	 */
	public NeptuneCsvInputParser(final File file) {

		try {
			this.csvParser = setupParser(createInputStreamReader(file));
			this.iterator = this.csvParser.iterator();
			this.header = setupHeader();
		} catch (Exception e) {
			this.close();
			throw e;
		}
	}

	/**
	 *
	 * Create the format for parsing the CSV file according to RFC4180 with:
	 * <ul>
	 * <li>ignore empty lines</li>
	 * <li>ignore surrounding spaces</li>
	 * <li>empty string means {@code null}</li>
	 * <li>minimal quotes</li>
	 * </ul>
	 *
	 * @return CSV format
	 */
	// visible for testing
	static CSVFormat createCSVFormat() {
		return CSVFormat.RFC4180.withIgnoreEmptyLines(true).withIgnoreSurroundingSpaces(true).withNullString("")
				.withQuoteMode(QuoteMode.MINIMAL);
	}

	@Override
	public void close() {

		if (this.csvParser != null) {
			try {
				this.csvParser.close();

			} catch (IOException e) {
				throw new Csv2RdfException("Parser could not be closed.", e);
			}
		}
	}

	/**
	 *
	 * Sets up and returns the record parser, positioned at the beginning of the
	 * file.
	 *
	 * @param reader a reader for the input CSV file
	 * @return CSV parser
	 */
	private CSVParser setupParser(@NonNull final Reader reader) {

		try {
			CSVFormat csvFormat = createCSVFormat();
			return csvFormat.parse(reader);
		} catch (final IOException e) {
			try {
				reader.close();
			} catch (IOException e1) {
				e.addSuppressed(new Csv2RdfException(
						"Error setting up CSV parser, reader is supposed to close but could not be closed.", e1));
			}
			throw new Csv2RdfException("Error setting up CSV parser.", e);
		}
	}

	/**
	 *
	 * Initializes the header column, using the iterator's current position. This
	 * must be called exactly once at the beginning. The iterator is advanced by one
	 * line.
	 *
	 * @return the parsed header
	 */
	private NeptuneCsvHeader setupHeader() {

		if (!iterator.hasNext()) {
			throw new Csv2RdfException("No header column found in input CSV file!");
		}

		final CSVRecord record = iterator.next();

		return NeptuneCsvHeader.parse(record);
	}

	/**
	 *
	 * Create an input stream reader over the given file
	 *
	 * @param file the Neptune CSV property graph input file
	 * @return input stream reader
	 */
	private Reader createInputStreamReader(@NonNull final File file) {

		BufferedInputStream bufferedStream = null;
		try {
			InputStream inputStream = new FileInputStream(file);
			bufferedStream = new BufferedInputStream(inputStream);

			return new InputStreamReader(bufferedStream, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			if (bufferedStream != null) {
				try {
					bufferedStream.close();
				} catch (IOException e1) {
					e.addSuppressed(new Csv2RdfException(
							"Encoding not supported for decoding, stream is supposed to close but could not be closed.",
							e1));
				}
			}
			throw new Csv2RdfException("Encoding not supported for decoding " + file.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new Csv2RdfException("Error creating input stream for CSV file " + file.getAbsolutePath(), e);
		}
	}

	@Override
	public boolean hasNext() {

		return iterator.hasNext();
	}

	@Override
	public NeptunePropertyGraphElement next() {

		CSVRecord record = this.iterator.next();
		return create(header, record);
	}

	/**
	 *
	 * Create a vertex or an edge
	 *
	 * @param header
	 * @param record
	 * @return the created vertex or edge
	 * @throws Csv2RdfException if the vertex or edge is not valid
	 */
	// visible for testing
	static NeptunePropertyGraphElement create(NeptuneCsvHeader header, CSVRecord record) {

		if (header instanceof NeptuneCsvEdgeHeader) {
			return create((NeptuneCsvEdgeHeader) header, record);
		}
		if (header instanceof NeptuneCsvVertexHeader) {
			return create((NeptuneCsvVertexHeader) header, record);
		}
		throw new IllegalArgumentException("Header type not recognized: " + header.getClass());
	}

	/**
	 *
	 * Get a value from the CSV record
	 *
	 * @param record
	 * @param index
	 * @return value at index or {@code null} if the index is out of bounds of the
	 *         record
	 */
	private static String getValueIfExists(CSVRecord record, int index) {

		if (index >= record.size()) {
			log.debug("CSV record does not contain field {}.", index);
			return null;
		}
		return record.get(index);
	}

	/**
	 *
	 * Create an edge
	 *
	 * @param header
	 * @param record
	 * @return new edge
	 * @throws Csv2RdfException if the edge is not valid
	 */
	private static NeptunePropertyGraphEdge create(NeptuneCsvEdgeHeader header, CSVRecord record) {

		String id = header.getId() == null ? null : getValueIfExists(record, header.getId());
		String from = getValueIfExists(record, header.getFrom());
		String to = getValueIfExists(record, header.getTo());
		String label = getValueIfExists(record, header.getLabel());

		NeptunePropertyGraphEdge edge = new NeptunePropertyGraphEdge(id, from, to, label);

		for (NeptuneCsvUserDefinedColumn userDefinedType : header.getUserDefinedTypes()) {
			if (userDefinedType.getCardinality() == Cardinality.SET) {
				throw new Csv2RdfException("Set-valued types are not allowed for edges: " + userDefinedType.getName());
			}

			String fieldValue = getValueIfExists(record, userDefinedType.getIndex());
			if (fieldValue == null || fieldValue.isEmpty()) {
				continue;
			}
			edge.add(new NeptuneCsvSingleValuedUserDefinedProperty(userDefinedType.getName(),
					userDefinedType.getDataType(), fieldValue));
		}

		return edge;
	}

	/**
	 *
	 * Create a vertex
	 *
	 * @param header
	 * @param record
	 * @return new edge
	 * @throws Csv2RdfException if the vertex is not valid
	 */
	private static NeptunePropertyGraphVertex create(NeptuneCsvVertexHeader header, CSVRecord record) {

		String id = header.getId() == null ? null : getValueIfExists(record, header.getId());
		NeptunePropertyGraphVertex vertex = new NeptunePropertyGraphVertex(id);

		for (NeptuneCsvUserDefinedColumn userDefinedType : header.getUserDefinedTypes()) {
			String fieldValue = getValueIfExists(record, userDefinedType.getIndex());
			if (fieldValue == null || fieldValue.isEmpty()) {
				continue;
			}
			switch (userDefinedType.getCardinality()) {
			case SINGLE:
				vertex.add(new NeptuneCsvSingleValuedUserDefinedProperty(userDefinedType.getName(),
						userDefinedType.getDataType(), fieldValue));
				break;
			case SET:
			case DEFAULT:
				if (userDefinedType.isArray()) {
					vertex.add(new NeptuneCsvUserDefinedArrayProperty(userDefinedType.getName(),
							userDefinedType.getDataType(), fieldValue));
				} else {
					vertex.add(new NeptuneCsvSetValuedUserDefinedProperty(userDefinedType.getName(),
							userDefinedType.getDataType(), fieldValue));
				}
				break;
			default:
				break;

			}
		}

		String labels = header.getLabel() == null ? null : getValueIfExists(record, header.getLabel());
		if (labels == null) {
			return vertex;
		}

		for (String labelValue : labels.split(NeptuneCsvUserDefinedColumn.ARRAY_VALUE_SEPARATOR)) {
			if (labelValue != null && !labelValue.isEmpty()) {
				vertex.add(labelValue);
			}
		}

		return vertex;
	}
}

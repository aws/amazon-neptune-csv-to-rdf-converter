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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVRecord;

import lombok.Getter;
import lombok.NonNull;
import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.Cardinality;

/**
 *
 * An {@link NeptuneCsvHeader} is either a {@link NeptuneCsvVertexHeader} for
 * describing the the fields of property graph vertex or a
 * {@link NeptuneCsvEdgeHeader} for containing the fields types of a property
 * graph edge.
 *
 */
public abstract class NeptuneCsvHeader {

	public static final String SYSTEM_COLUMN_PREFIX = "~";
	public static final String ID = SYSTEM_COLUMN_PREFIX + "id";
	public static final String LABEL = SYSTEM_COLUMN_PREFIX + "label";
	public static final String FROM = SYSTEM_COLUMN_PREFIX + "from";
	public static final String TO = SYSTEM_COLUMN_PREFIX + "to";

	private static final Set<String> SYSTEM_COLUMNS = new HashSet<>();
	static {
		SYSTEM_COLUMNS.add(ID);
		SYSTEM_COLUMNS.add(LABEL);
		SYSTEM_COLUMNS.add(FROM);
		SYSTEM_COLUMNS.add(TO);
	}

	/**
	 *
	 * ID column (optional)
	 */
	@Getter
	private Integer id;

	/**
	 *
	 * Label field
	 */
	@Getter
	private Integer label;

	/**
	 *
	 * All user-defined fields
	 */
	@Getter
	private List<NeptuneCsvUserDefinedColumn> userDefinedTypes = new ArrayList<>();

	/**
	 *
	 * Constructor is private and can only be called from
	 * {@link NeptuneCsvVertexHeader} and {@link NeptuneCsvEdgeHeader}.
	 *
	 * @param id
	 * @param label
	 * @param userDefinedTypes
	 */
	private NeptuneCsvHeader(Integer id, Integer label, @NonNull List<NeptuneCsvUserDefinedColumn> userDefinedTypes) {

		this.id = id;
		this.label = label;
		this.userDefinedTypes = userDefinedTypes;
	}

	/**
	 *
	 * Parse a vertex or edge header from a CSV record.
	 *
	 * @param record CSV record
	 * @return {@link NeptuneCsvEdgeHeader} when ~from and ~to are present, else
	 *         {@link NeptuneCsvVertexHeader}
	 * @throws Csv2RdfException if the vertex or edge validation fails
	 */
	public static NeptuneCsvHeader parse(@NonNull CSVRecord record) {

		Set<String> names = new HashSet<>();
		Map<String, Integer> system = new HashMap<>();
		List<NeptuneCsvUserDefinedColumn> user = new ArrayList<>();

		for (int i = 0; i < record.size(); ++i) {
			String name = record.get(i);

			if (name == null) {
				throw new Csv2RdfException("Empty column header encountered.");
			}

			String normalized = name.trim().toLowerCase();

			if (SYSTEM_COLUMNS.contains(normalized)) {
				system.put(normalized, i);
			} else if (normalized.startsWith(SYSTEM_COLUMN_PREFIX)) {
				throw new Csv2RdfException("Invalid system column encountered: " + normalized);
			} else {
				NeptuneCsvUserDefinedColumn column = NeptuneCsvUserDefinedColumn.parse(name);
				column.setIndex(i);
				user.add(column);
				normalized = column.getName();
			}

			if (!names.add(normalized)) {
				throw new Csv2RdfException("Found duplicate field: " + name);
			}
		}

		NeptuneCsvHeader header;
		if (system.get(FROM) != null || system.get(TO) != null) {
			header = new NeptuneCsvEdgeHeader(system.get(ID), system.get(FROM), system.get(TO), system.get(LABEL),
					user);
		} else {
			header = new NeptuneCsvVertexHeader(system.get(ID), system.get(LABEL), user);
		}
		return header;
	}

	/**
	 *
	 * {@link NeptuneCsvVertexHeader} provides access to types of the id field, the
	 * label fields, and the user-defined fields.
	 *
	 */
	public static class NeptuneCsvVertexHeader extends NeptuneCsvHeader {

		/**
		 *
		 * @param id               optional
		 * @param label            optional
		 * @param userDefinedTypes may be empty
		 */
		public NeptuneCsvVertexHeader(Integer id, Integer label,
				@NonNull List<NeptuneCsvUserDefinedColumn> userDefinedTypes) {
			super(id, label, userDefinedTypes);

		}
	}

	/**
	 *
	 * {@link NeptuneCsvEdgeHeader} provides access to the type of the id field, the
	 * from field, the to field, the label fields, and the user-defined fields.
	 *
	 */
	public static class NeptuneCsvEdgeHeader extends NeptuneCsvHeader {

		@Getter
		private final Integer from;
		@Getter
		private final Integer to;

		/**
		 *
		 * @param id               optional
		 * @param from             required
		 * @param to               required
		 * @param label            required
		 * @param userDefinedTypes may be empty
		 * @throws Csv2RdfException if from or to is missing or there is no label or an
		 *                          user-defined type is an array type
		 */
		public NeptuneCsvEdgeHeader(Integer id, Integer from, Integer to, Integer label,
				@NonNull List<NeptuneCsvUserDefinedColumn> userDefinedTypes) {
			super(id, label, userDefinedTypes);
			this.from = from;
			this.to = to;
			if (this.from == null) {
				throw new Csv2RdfException("An edge requires a " + FROM + " field.");
			}
			if (this.to == null) {
				throw new Csv2RdfException("An edge requires a " + TO + " field.");
			}
			if (this.getLabel() == null) {
				throw new Csv2RdfException("An edge requires a " + LABEL + " field.");
			}
			for (NeptuneCsvUserDefinedColumn userDefinedType : this.getUserDefinedTypes()) {
				if (userDefinedType.isArray()) {
					throw new Csv2RdfException("Array types are not allowed for edges: " + userDefinedType.getName());
				}
				if (userDefinedType.getCardinality() == Cardinality.SET) {
					throw new Csv2RdfException(
							"Set-valued types are not allowed for edges: " + userDefinedType.getName());
				}
			}
		}
	}
}

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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import software.amazon.neptune.csv2rdf.NeptuneCsvUserDefinedColumn.DataType;

/**
 *
 * {@link NeptunePropertyGraphElement} is either a
 * {@link NeptunePropertyGraphVertex} representing a vertex or a
 * {@link NeptunePropertyGraphEdge} representing an edge.
 *
 */
public abstract class NeptunePropertyGraphElement {

	/**
	 *
	 * The Id of this vertex or edge.
	 */
	@Getter
	private final String id;

	/**
	 *
	 * Create a vertex or an edge
	 *
	 * @param id required
	 */
	private NeptunePropertyGraphElement(String id) {

		if (id == null || id.isEmpty()) {
			throw new Csv2RdfException("Vertex or edge ID must not be null or empty.");
		} else {
			this.id = id;
		}

	}

	/**
	 *
	 * {@link NeptunePropertyGraphVertex} represents a property graph vertex. It
	 * provides access to the Id, the labels, and the user-defined fields of a
	 * vertex.
	 *
	 */
	public static class NeptunePropertyGraphVertex extends NeptunePropertyGraphElement {

		/**
		 *
		 * The user-defined properties of this vertex. Their values are never
		 * {@code null} or empty.
		 */
		@Getter
		private final List<NeptuneCsvUserDefinedProperty> userDefinedProperties = new ArrayList<>();

		public NeptunePropertyGraphVertex(String id) {

			super(id);
		}

		/**
		 *
		 * The labels of this vertex. The list may be empty. A label is never
		 * {@code null} or empty.
		 */
		@Getter
		private final List<String> labels = new ArrayList<>();

		/**
		 *
		 * Add a single or set values property or an array property
		 *
		 * @param property vertex property
		 */
		public void add(NeptuneCsvUserDefinedProperty property) {

			this.userDefinedProperties.add(property);
		}

		/**
		 *
		 * Add a label
		 *
		 * @param label must not be {@code null} or empty
		 */
		public void add(String label) {

			if (label == null || label.isEmpty()) {
				throw new Csv2RdfException("Vertex labels must not be null or empty.");
			}
			this.labels.add(label);
		}
	}

	/**
	 *
	 * {@link NeptunePropertyGraphEdge} represents a property graph edge. It
	 * provides access to the Id, the labels, the source and target, and the
	 * user-defined fields of an edge.
	 *
	 */
	public static class NeptunePropertyGraphEdge extends NeptunePropertyGraphElement {

		/**
		 *
		 * Source vertex
		 */
		@Getter
		private final String from;

		/**
		 *
		 * Target vertex
		 */
		@Getter
		private final String to;

		/**
		 *
		 * Label of the edge (optional)
		 */
		@Getter
		private final String label;

		/**
		 *
		 * The user-defined properties of this edge. Their values are never {@code null}
		 * or empty.
		 */
		@Getter
		private final List<NeptuneCsvSingleValuedUserDefinedProperty> userDefinedProperties = new ArrayList<>();

		/**
		 *
		 * Add a single valued property
		 *
		 * @param property edge property
		 */
		public void add(NeptuneCsvSingleValuedUserDefinedProperty property) {

			this.userDefinedProperties.add(property);
		}

		/**
		 *
		 * Check if a label exists for this edge
		 *
		 * @return {@code true} if there is a label
		 */
		public boolean hasLabel() {

			return label != null && !label.isEmpty();
		}

		/**
		 *
		 * Creates an edge.
		 *
		 * @param id    optional
		 * @param from  required
		 * @param to    required
		 * @param label optional
		 * @throws Csv2RdfException if from or to is missing
		 */
		public NeptunePropertyGraphEdge(String id, String from, String to, String label) {

			super(id);
			if (from == null || from.isEmpty()) {
				throw new Csv2RdfException(
						"Value for " + NeptuneCsvHeader.FROM + " is missing at edge " + this.getId() + ".");

			}
			if (to == null || to.isEmpty()) {
				throw new Csv2RdfException(
						"Value for " + NeptuneCsvHeader.TO + " is missing at edge " + this.getId() + ".");

			}

			this.from = from;
			this.to = to;
			this.label = label;
		}
	}

	/**
	 *
	 * A property of a vertex or an edge. It can be multi or single valued.
	 *
	 */
	@Getter
	@AllArgsConstructor
	public static abstract class NeptuneCsvUserDefinedProperty {

		/**
		 *
		 * Name of this property
		 */
		@NonNull
		private final String name;

		/**
		 *
		 * Data type of this property
		 */
		@NonNull
		private final DataType dataType;

		/**
		 *
		 * All values of this property. Single valued properties return only one value.
		 *
		 * @return an unmodifiable collection containing all values
		 */
		public abstract Collection<String> getValues();
	}

	/**
	 *
	 * {@link NeptuneCsvSetValuedUserDefinedProperty} combines a data value from a
	 * CSV record with the corresponding header column type
	 * {@link NeptuneCsvUserDefinedColumn}. It is essentially a property of a vertex
	 * and can have multiple values. The value must not be {@code null}. Empty or
	 * {@code null} values of arrays are skipped.
	 *
	 */
	public static class NeptuneCsvSetValuedUserDefinedProperty extends NeptuneCsvUserDefinedProperty {

		// use linked hash set to make iteration order predictable for tests
		private final Set<String> values = new LinkedHashSet<>();

		public NeptuneCsvSetValuedUserDefinedProperty(@NonNull String name, @NonNull DataType dataType,
				@NonNull String value) {

			super(name, dataType);
			this.add(value);
		}

		/**
		 *
		 * Add value
		 *
		 * @param value must not be {@code null}
		 */
		public void add(@NonNull String value) {

			values.add(value);
		}

		@Override
		public Collection<String> getValues() {

			return Collections.unmodifiableCollection(values);
		}
	}

	/**
	 *
	 * {@link NeptuneCsvUserDefinedArrayProperty} combines a data value from a CSV
	 * record with the corresponding header column type
	 * {@link NeptuneCsvUserDefinedColumn}. It is essentially a property of a vertex
	 * and can have multiple values. The value must not be {@code null}. Empty or
	 * {@code null} values of arrays are skipped.
	 *
	 */
	public static class NeptuneCsvUserDefinedArrayProperty extends NeptuneCsvSetValuedUserDefinedProperty {

		public NeptuneCsvUserDefinedArrayProperty(@NonNull String name, @NonNull DataType dataType,
				@NonNull String value) {

			super(name, dataType, value);

		}

		/**
		 *
		 * Split the value at semicolons and add all except {@code null} and empty
		 * values
		 *
		 * @param value must not be {@code null}
		 */
		@Override
		public void add(@NonNull String value) {

			for (String v : value.split("(?<!\\\\)" + NeptuneCsvUserDefinedColumn.ARRAY_VALUE_SEPARATOR)) {
				if (v == null || v.isEmpty()) {
					continue;
				}
				super.add(v.replace("\\;",";"));
			}
		}
	}

	/**
	 *
	 * {@link NeptuneCsvSingleValuedUserDefinedProperty} combines a data value from
	 * a CSV record with the corresponding header column type
	 * {@link NeptuneCsvUserDefinedColumn}. It is essentially a property of an edge
	 * and has always a single value only. The value must not be {@code null}.
	 *
	 */
	public static class NeptuneCsvSingleValuedUserDefinedProperty extends NeptuneCsvUserDefinedProperty {

		@Getter
		private final String value;

		public NeptuneCsvSingleValuedUserDefinedProperty(@NonNull String name, @NonNull DataType dataType,
				@NonNull String value) {

			super(name, dataType);
			this.value = value;
		}

		@Override
		public Collection<String> getValues() {

			return Collections.singletonList(value);
		}
	}
}

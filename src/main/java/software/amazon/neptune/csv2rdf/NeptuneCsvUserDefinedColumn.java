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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 *
 * {@link NeptuneCsvUserDefinedColumn} describes a user-defined column of the
 * property graph CSV file.
 *
 */
public class NeptuneCsvUserDefinedColumn {

	/**
	 *
	 * Array data types accept multiple values separated by a semicolon
	 */
	public static final String ARRAY_VALUE_SEPARATOR = ";";

	/**
	 *
	 * Field name
	 */
	@Getter
	private String name;

	/**
	 *
	 * Field index
	 */
	@Getter
	@Setter
	private int index;

	/**
	 *
	 * Enumeration of data types. Each is available by its name and an optional
	 * alias.
	 *
	 * @see #BYTE
	 * @see #BOOL
	 * @see #SHORT
	 * @see #INT
	 * @see #LONG
	 * @see #FLOAT
	 * @see #DOUBLE
	 * @see #STRING
	 * @see #DATETIME
	 *
	 */
	public enum DataType {
		/**
		 * byte
		 */
		BYTE,

		/**
		 * bool, boolean
		 */
		BOOL,
		/**
		 * short
		 */
		SHORT,
		/**
		 * int, integer
		 */
		INT,
		/**
		 * long
		 */
		LONG,
		/**
		 * float
		 */
		FLOAT,
		/**
		 * double
		 */
		DOUBLE,
		/**
		 * string
		 */
		STRING,
		/**
		 * datetime, date
		 */
		DATETIME;

		private static final Map<String, DataType> DATA_TYPES_MAP = new HashMap<>();
		static {
			for (DataType dataType : DataType.values()) {
				DATA_TYPES_MAP.put(dataType.name().toLowerCase(), dataType);
			}
			DATA_TYPES_MAP.put("boolean", BOOL);
			DATA_TYPES_MAP.put("integer", INT);
			DATA_TYPES_MAP.put("date", DATETIME);
		}

		/**
		 *
		 * Return the data type for the given name, or {@code null} if none exists. Some
		 * data types are available under several aliases:
		 * <ul>
		 * <li>{@link DataType#BOOL}: bool, boolean</li>
		 * <li>{@link DataType#INT}: int, interger</li>
		 * <li>{@link DataType#DATETIME}: datetime, date</li>
		 * </ul>
		 *
		 * @param name will be lowercased
		 * @return data type or {@code null}
		 */
		public static DataType fromName(String name) {
			return DATA_TYPES_MAP.get(name.toLowerCase());
		}
	}

	/**
	 *
	 * Cardinality of a user-defined type
	 *
	 */
	public enum Cardinality {

		/**
		 * Only one value is allowed
		 */
		SINGLE,
		/**
		 * Multiple distinct values are allowed
		 */
		SET,
		/**
		 * Default is {@link #SET} for vertices and {@link #SINGLE} for edges.
		 */
		DEFAULT;

		public static Cardinality fromName(String name) {
			for (Cardinality cardinality : values()) {
				if (cardinality.name().equalsIgnoreCase(name)) {
					return cardinality;
				}
			}
			return null;
		}
	}

	/**
	 *
	 * An array is declared with trailing brackets
	 */
	public static final String ARRAY_DECLARATION = "[]";

	/**
	 * A column name consists of two parts: name and optional data type. Name and
	 * data type are separated by a colon. Name and data type consist of
	 * non-whitespace characters. If a colon appears within the column name,
	 * it must be escaped by preceding it with a backslash: {@code \:}.
	 */
	private static final Pattern USER_HEADER_PATTERN = Pattern.compile("^(\\S+?)((?<!\\\\):\\S+)?$");
	private static final int GROUPS_IN_HEADER_PATTERN = 2;

	/**
	 * A user type pattern consists the type name, an optional cardinality and
	 * optional brackets.
	 */
	private static final Pattern USER_TYPE_PATTERN = Pattern.compile(":([^" + Pattern.quote(ARRAY_DECLARATION)
			+ "\\(\\)]+)(\\((" + Pattern.quote(Cardinality.SINGLE.name().toLowerCase()) + "|"
			+ Pattern.quote(Cardinality.SET.name().toLowerCase()) + ")\\))?(" + Pattern.quote(ARRAY_DECLARATION)
			+ ")?");
	private static final int GROUPS_IN_TYPE_PATTERN = 4;

	/**
	 *
	 * Data type of this column
	 */
	@Getter
	private DataType dataType;

	/**
	 *
	 * Cardinality of this column:
	 * <dl>
	 * <dt>{@link Cardinality#SINGLE}</dt>
	 * <dd>Only one value is allowed</dd>
	 * <dt>{@link Cardinality#SET}</dt>
	 * <dd>Multiple values are allowed</dd>
	 * </dl>
	 */
	@Getter
	private final Cardinality cardinality;

	/**
	 *
	 * Arrays allow multiple values in a field. The values will be separated.
	 */
	@Getter
	private final boolean isArray;

	/**
	 *
	 * Create a user defined non-array type. Cardinality is {@code null} and needs
	 * to set to {@link Cardinality#SET} for vertices and {@link Cardinality#SINGLE}
	 * for edges.
	 *
	 * @param name     field name
	 * @param dataType data type
	 */
	public NeptuneCsvUserDefinedColumn(@NonNull String name, @NonNull DataType dataType) {

		this.name = name;
		this.dataType = dataType;
		this.cardinality = Cardinality.DEFAULT;
		this.isArray = false;
	}

	/**
	 *
	 * Create a user defined non-array type.
	 *
	 * @param name        field name
	 * @param dataType    data type
	 * @param cardinality {@link Cardinality#SET} or {@link Cardinality#SINGLE}
	 */
	public NeptuneCsvUserDefinedColumn(@NonNull String name, @NonNull DataType dataType,
			@NonNull Cardinality cardinality) {

		this.name = name;
		this.dataType = dataType;
		this.cardinality = cardinality;
		this.isArray = false;
	}

	/**
	 *
	 * A user-defined column type
	 *
	 * @param name     field name
	 * @param dataType data type
	 * @param isArray  accepts multiple values if true
	 */
	public NeptuneCsvUserDefinedColumn(@NonNull String name, @NonNull DataType dataType, boolean isArray) {

		this.name = name;
		this.dataType = dataType;
		this.isArray = isArray;
		this.cardinality = this.isArray ? Cardinality.SET : Cardinality.DEFAULT;
	}

	/**
	 *
	 * Parse a user-defined field.
	 *
	 * @param fieldNameAndDatatype field declaration
	 * @return user-defined field with given data type or type string if no data
	 *         type given
	 * @throws Csv2RdfException if validation of the column definition fails
	 */
	public static NeptuneCsvUserDefinedColumn parse(@NonNull String fieldNameAndDatatype) {

		String trimmed = fieldNameAndDatatype.trim();

		// split column name and type definition

		Matcher matcher = USER_HEADER_PATTERN.matcher(trimmed);
		if (!matcher.matches() || matcher.groupCount() < GROUPS_IN_HEADER_PATTERN) {
			throw new Csv2RdfException("Invalid column encountered while parsing header: " + trimmed);
		}

		String columnName = matcher.group(1);
		if (columnName.isEmpty()) {
			throw new Csv2RdfException("Column name is not present for header field: " + trimmed);
		}
		columnName = columnName.replace("\\:",":");

		String typeString = matcher.group(2);
		if (typeString == null || typeString.isEmpty()) {
			return new NeptuneCsvUserDefinedColumn(columnName, DataType.STRING);
		}

		// split type name, cardinality, and array declaration

		Matcher typeMatcher = USER_TYPE_PATTERN.matcher(typeString.toLowerCase());
		if (!typeMatcher.matches() || typeMatcher.groupCount() < GROUPS_IN_TYPE_PATTERN) {
			throw new Csv2RdfException("Invalid column encountered while parsing header: " + trimmed);
		}

		// Parse type from first group
		DataType dataType = DataType.fromName(typeMatcher.group(1));
		if (dataType == null) {
			throw new Csv2RdfException("Invalid data type encountered for header: " + trimmed);
		}

		Cardinality cardinality = Cardinality.fromName(typeMatcher.group(3));

		boolean isArray = ARRAY_DECLARATION.equals(typeMatcher.group(4));

		if (cardinality == null) {
			return new NeptuneCsvUserDefinedColumn(columnName, dataType, isArray);
		}

		if (isArray && cardinality == Cardinality.SINGLE) {
			throw new Csv2RdfException("Type definition cannot be single cardinality but array: " + columnName);
		}

		if (isArray) {
			return new NeptuneCsvUserDefinedColumn(columnName, dataType, isArray);
		}

		return new NeptuneCsvUserDefinedColumn(columnName, dataType, cardinality);
	}
}

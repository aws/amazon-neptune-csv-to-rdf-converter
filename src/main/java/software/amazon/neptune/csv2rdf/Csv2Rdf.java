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
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.Callable;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;

/**
 *
 * Main class for running the CSV to RDF conversion. If an expected problem
 * occurs during the process, a message helping the user to fix it is printed to
 * the console and logged. Other exceptions are logged and a general message is
 * printed to the console. <br>
 * The CSV to RDF conversion is started by calling {@link Csv2Rdf#main}.
 *
 */
@Slf4j
@Command(name = "java -jar amazon-neptune-csv2rdf.jar", header = { "*** Amazon Neptune CSV to RDF Converter ***",
		"A tool for Amazon Neptune that converts property graphs stored as comma separated values into RDF graphs." }, footer = {
				"Fork me on GitHub: https://github.com/aws/amazon-neptune-csv-to-rdf-converter",
				"Licensed under Apache License, Version 2.0: https://aws.amazon.com/apache2.0",
				"Copyright Amazon.com Inc. or its affiliates. All Rights Reserved." }, usageHelpAutoWidth = true, mixinStandardHelpOptions = true, versionProvider = Csv2Rdf.MavenVersionProvider.class)
public class Csv2Rdf implements Callable<Integer> {

	// Name of the log file must match the configuration in log4j2.xml.
	private static final String LOG_FILE = "amazon-neptune-csv2rdf.log";
	private static final String VERSION_RESOURCE = "/amazon-neptune-csv2rdf-version.txt";

	private static final String PARAM_LABEL_CONFIGURATION_FILE = "<configuration file>";
	private static final String PARAM_LABEL_INPUT_DIRECTORY = "<input directory>";
	private static final String PARAM_LABEL_OUTPUT_DIRECTORY = "<output directory>";

	@Option(names = { "-c",
			"--config" }, required = false, arity = "1", paramLabel = PARAM_LABEL_CONFIGURATION_FILE, description = "Property file containing the configuration.")
	private File configFile;

	@Option(names = { "-i",
			"--input" }, required = true, arity = "1", paramLabel = PARAM_LABEL_INPUT_DIRECTORY, description = "Directory containing the CSV files (UTF-8 encoded).")
	private File inputDirectory;

	@Option(names = { "-o",
			"--output" }, required = true, arity = "1", paramLabel = PARAM_LABEL_OUTPUT_DIRECTORY, description = "Directory for writing the RDF files (UTF-8 encoded); will be created if it does not exist.")
	private File outputDirectory;

	/**
	 *
	 * Exits with code 0 for normal termination. Exit codes less than zero signal
	 * that the conversion failed. See failure codes at {@link Csv2Rdf#call}. Exit
	 * codes greater than 0 mean that executing a command line argument failed.
	 *
	 * @param args command line arguments
	 */
	public static void main(final String[] args) {

		int exitCode = new CommandLine(new Csv2Rdf()).execute(args);
		System.exit(exitCode);
	}

	/**
	 *
	 * Load a text file resource as string.
	 *
	 * @param resource
	 * @return text content of the resource
	 */
	private static String getResourceAsString(String resource) {
		try (Scanner scanner = new Scanner(Csv2Rdf.class.getResourceAsStream(resource),
				StandardCharsets.UTF_8.name())) {
			return scanner.useDelimiter("\\A").next();
		}
	}

	/**
	 *
	 * Main method for running Csv2Rdf automatically by picocli.
	 *
	 * @return exit code: 0 for normal termination, -1 if an
	 *         {@link Csv2RdfException} occurred, -2 for any other {@link Exception}
	 */
	@Override
	public Integer call() {

		try {
			System.out.println(Csv2Rdf.class.getAnnotationsByType(Command.class)[0].header()[0]);
			validateParameters();
			echoParameters();

			System.out.println("Initializing the converter...");
			PropertyGraph2RdfConverter converter = new PropertyGraph2RdfConverter(configFile);

			System.out.println("Running CSV to RDF conversion...");
			converter.convert(inputDirectory, outputDirectory);

			System.out.println("Your RDF files have been written to: " + outputDirectory.getPath());

			System.out.println("All done.");
			return 0;
		} catch (Csv2RdfException e) {
			log.error("CSV to RDF conversion failed.", e);
			System.err.println("CSV to RDF conversion failed.");
			System.err.println(e.getMessage());
			return -1;
		} catch (Exception e) {
			log.error("CSV to RDF conversion failed.", e);
			System.err.println("CSV to RDF conversion failed.");
			System.err.println("Please see log file for details: " + LOG_FILE);
			return -2;
		}
	}

	/**
	 *
	 * Validate values of command line parameters -c, -i, and -o
	 *
	 * @throws Csv2RdfException for the first encountered invalid value
	 */
	private void validateParameters() {

		if (configFile != null) {
			validateFileParam(configFile, PARAM_LABEL_CONFIGURATION_FILE);
		}
		validateDirectoryParam(inputDirectory, PARAM_LABEL_INPUT_DIRECTORY, false);
		validateDirectoryParam(outputDirectory, PARAM_LABEL_OUTPUT_DIRECTORY, true);
	}

	/**
	 *
	 * Echo the parameter values of -c, -i, and -o to stdout
	 */
	private void echoParameters() {

		System.out.println("Parameter values:");
		if (configFile != null) {
			System.out.println("* " + PARAM_LABEL_CONFIGURATION_FILE + " : " + configFile.getPath());
		}
		System.out.println("* " + PARAM_LABEL_INPUT_DIRECTORY + " : " + inputDirectory.getPath());
		System.out.println("* " + PARAM_LABEL_OUTPUT_DIRECTORY + " : " + outputDirectory.getPath());
	}

	/**
	 *
	 * Check if a file exists.
	 *
	 * @param file
	 * @param param parameter name appears in the exception message
	 * @throws Csv2RdfException if it is not a file or does not exist.
	 */
	// visible for testing
	static void validateFileParam(@NonNull File file, @NonNull String param) {

		if (file == null || file.isFile()) {
			return;
		}

		if (file.exists()) {
			throw new Csv2RdfException("Parameter " + param + " does not point to a file: " + file.getAbsolutePath());
		}
		throw new Csv2RdfException("File for parameter " + param + " does not exist: " + file.getAbsolutePath());
	}

	/**
	 *
	 * Check if a directory exist and optionally try to create it.
	 *
	 * @param directory
	 * @param param     parameter name appears in the exception message
	 * @param create    try to create the directory and parent directories if true
	 * @throws Csv2RdfException if it is not a directory or does not exist.
	 */
	// visible for testing
	static void validateDirectoryParam(@NonNull File directory, @NonNull String param, boolean create) {

		if (directory.isDirectory()) {
			return;
		}

		if (directory.exists()) {
			throw new Csv2RdfException(
					"Parameter " + param + " does not point to a directory: " + directory.getAbsolutePath());
		}
		if (!create) {
			throw new Csv2RdfException(
					"Directory for parameter " + param + " does not exist: " + directory.getAbsolutePath());
		}
		if (!directory.mkdirs()) {
			throw new Csv2RdfException(
					"Directory for parameter " + param + " could not be created: " + directory.getAbsolutePath());
		}
	}

	/**
	 *
	 * Version provider for picocli. Version number is read from a text file on the
	 * classpath.
	 *
	 */
	@Slf4j
	// visible for picocli
	static class MavenVersionProvider implements IVersionProvider {
		@Override
		public String[] getVersion() {

			try {
				return new String[] { getResourceAsString(VERSION_RESOURCE) };
			} catch (Exception e) {
				log.error("Could not read version information.", e);
				throw new Csv2RdfException(
						"Could not read version information. Please see log file for details: " + LOG_FILE);
			}
		}
	}
}

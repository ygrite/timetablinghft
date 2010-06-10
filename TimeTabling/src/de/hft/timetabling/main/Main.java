package de.hft.timetabling.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import de.hft.timetabling.common.IProblemInstance;
import de.hft.timetabling.eliminator.Eliminator;
import de.hft.timetabling.evaluator.Evaluator;
import de.hft.timetabling.generator.Generator;
import de.hft.timetabling.generator.PooledMtGenerator;
import de.hft.timetabling.genetist.CrazyGenetist;
import de.hft.timetabling.genetist.ValidatorImpl;
import de.hft.timetabling.reader.Reader;
import de.hft.timetabling.services.IEliminatorService;
import de.hft.timetabling.services.IReaderService;
import de.hft.timetabling.services.ISolutionTableService;
import de.hft.timetabling.services.IWriterService;
import de.hft.timetabling.services.ServiceLocator;
import de.hft.timetabling.solutiontable.SolutionTable;
import de.hft.timetabling.writer.Writer;

/**
 * The main class contains the main function that's needed to start the program.
 * 
 * @author Alexander Weickmann
 */
public final class Main {

	/**
	 * The number of iterations to perform until the best solution will be
	 * printed.
	 */
	private static int iterations = 1000;

	/**
	 * Runs the program.
	 * 
	 * @param args
	 *            1) The first argument is the name of the problem instance file
	 *            to solve. 2) If a second argument is available it is treated
	 *            as the number of iterations to perform. 3) If a third argument
	 *            is available it's treated as the amount of milliseconds to
	 *            sleep between each iteration. 4) If a fourth argument is
	 *            provided it is treated as the name of the directory where
	 *            initial solutions shall be read from.
	 * 
	 * @throws IllegalArgumentException
	 *             If the length of <tt>args</tt> is smaller than 1.
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			throw new IllegalArgumentException(
					"The program's first argument must either be the name of the problem instance file to solve or 'ALL' to run all instances.");
		}

		long sleepTime = 0;
		String initialSolutionDirectory = null;

		if (args.length >= 2) {
			iterations = Integer.valueOf(args[1]);
			if (args.length >= 3) {
				sleepTime = Long.valueOf(args[2]);
				if (args.length == 4) {
					initialSolutionDirectory = args[3];
				}
			}
		}

		setUpServices();

		try {
			if (args[0].equals("ALL")) {
				runAllInstances(initialSolutionDirectory);
			} else {
				run(args[0], initialSolutionDirectory, sleepTime);
			}
		} catch (IOException e) {
			handleException(e);
		}

		// MR: band-aid fix until I find out why the app does not terminate
		System.exit(0);
	}

	private static void handleException(Exception e) {
		e.printStackTrace();
	}

	/**
	 * At first, the individual sub systems must be initialized and registered
	 * with the service locator.
	 */
	private static void setUpServices() {
		ServiceLocator serviceLocator = ServiceLocator.getInstance();
		serviceLocator.setReaderService(new Reader());
		serviceLocator.setSolutionTableService(new SolutionTable());
		serviceLocator.setWriterService(new Writer());
		serviceLocator.setGeneratorService(new PooledMtGenerator());
		serviceLocator.setValidatorService(new ValidatorImpl());
		serviceLocator.setCrazyGenetistService(new CrazyGenetist());
		serviceLocator.setEvaluatorService(new Evaluator());
		serviceLocator.setEliminatorService(new Eliminator());
	}

	/**
	 * Runs the main loop of the program. Returns the duration of the program in
	 * milliseconds.
	 */
	private static long run(String fileName, String initialSolutionDirectory,
			long sleepMilliSeconds) throws IOException {

		long startTime = System.currentTimeMillis();
		CrazyGenetist.success = 0;
		CrazyGenetist.failure = 0;
		Generator.success = 0;
		Generator.failure = 0;

		ServiceLocator locator = ServiceLocator.getInstance();
		IReaderService reader = locator.getReaderService();
		IProblemInstance instance = (initialSolutionDirectory == null) ? reader
				.readInstance(fileName) : reader
				.readInstanceUsingInitialSolutionDirectory(fileName,
						initialSolutionDirectory);

		ServiceLocator.getInstance().getSolutionTableService().clear();

		for (int i = 0; i < iterations; i++) {
			System.out.println("");
			System.out.println("------ ITERATION " + i + " ------");

			callGenerator(instance);

			callEvaluator();

			updateSolutionTable();

			callCrazyGenetist();

			callEvaluator();

			updateSolutionTable();

			callEliminator();

			printBestSolution();
			printFairestSolution();
			printWorstSolution();
			printUnfairestSolution();

			shortSleep(sleepMilliSeconds);
		}

		printGeneratorStats();
		writeBestSolution();

		System.out.println("Genetist success: " + CrazyGenetist.success);
		System.out.println("Genetist failure: " + CrazyGenetist.failure);

		return System.currentTimeMillis() - startTime;
	}

	private static void callGenerator(IProblemInstance instance) {

		long startMillis = System.currentTimeMillis();
		ServiceLocator.getInstance().getGeneratorService().fillSolutionTable(
				instance);
		long time = System.currentTimeMillis() - startMillis;
		System.out.println("GENERATOR: Finished after " + time + "ms.");
	}

	private static void callCrazyGenetist() {
		long startMillis = System.currentTimeMillis();
		ServiceLocator.getInstance().getCrazyGenetistService()
				.recombineAndMutate();
		long time = System.currentTimeMillis() - startMillis;
		System.out.println("CRAZY GENETIST: Finished after " + time + "ms.");
	}

	private static void callEvaluator() {
		long startMillis = System.currentTimeMillis();
		ServiceLocator.getInstance().getEvaluatorService().evaluateSolutions();
		long time = System.currentTimeMillis() - startMillis;
		System.out.println("EVALUATOR: Finished after " + time + "ms.");
	}

	private static void callEliminator() {
		long startMillis = System.currentTimeMillis();
		IEliminatorService eliminatorService = ServiceLocator.getInstance()
				.getEliminatorService();
		eliminatorService.eliminateSolutions();
		long time = System.currentTimeMillis() - startMillis;
		System.out.println("ELIMINATOR: Finished after " + time + "ms.");
	}

	private static void writeBestSolution() throws IOException {
		IWriterService writer = ServiceLocator.getInstance().getWriterService();
		writer.outputBestSolution();
	}

	private static void updateSolutionTable() {
		ISolutionTableService solutionTable = ServiceLocator.getInstance()
				.getSolutionTableService();
		solutionTable.update();
	}

	private static void printBestSolution() {
		ISolutionTableService solutionTable = ServiceLocator.getInstance()
				.getSolutionTableService();

		System.out.println("----------------------------");
		System.out.println("-- Best Penalty Solution: Penalty: "
				+ solutionTable.getBestPenaltySolutionPenalty()
				+ ", Fairness: "
				+ solutionTable.getBestPenaltySolutionFairness());
	}

	private static void printFairestSolution() {
		ISolutionTableService solutionTable = ServiceLocator.getInstance()
				.getSolutionTableService();

		System.out.print("-- Best Fairness Solution: Penalty: "
				+ solutionTable.getBestFairnessSolutionPenalty()
				+ ", Fairness: "
				+ solutionTable.getBestFairnessSolutionFairness() + "\n");
	}

	private static void printWorstSolution() {
		ISolutionTableService solutionTable = ServiceLocator.getInstance()
				.getSolutionTableService();
		System.out.println("-- Worst Penalty Solution: Penalty: "
				+ solutionTable.getWorstPenaltySolutionPenalty()
				+ ", Fairness: "
				+ solutionTable.getWorstPenaltySolutionFairness());
	}

	private static void printUnfairestSolution() {
		ISolutionTableService solutionTable = ServiceLocator.getInstance()
				.getSolutionTableService();

		System.out.print("-- Worst Fairness Solution: Penalty: "
				+ solutionTable.getWorstFairnessSolutionPenalty()
				+ ", Fairness: "
				+ solutionTable.getWorstFairnessSolutionFairness() + "\n");
	}

	private static void printGeneratorStats() {
		System.out.println("----------------------------");
		System.out.println("-- Generator stats: Success: " + Generator.success
				+ ", Failure: " + Generator.failure);
	}

	private static void shortSleep(final long sleepMilliSeconds) {
		try {
			Thread.sleep(sleepMilliSeconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void runAllInstances(String initialSolutionsDirectory)
			throws IOException {

		final String logFileName = "allinstances.log";

		final File logFile = new File(logFileName);
		if (logFile.exists()) {
			logFile.delete();
			logFile.createNewFile();
		}

		final File instancesDir = new File("instances");
		final File[] instanceFiles = instancesDir
				.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.endsWith(".ctt");
					}
				});

		final BufferedWriter writer = new BufferedWriter(
				new FileWriter(logFile));

		createLogFileHeader(writer);

		for (int i = 0; i < instanceFiles.length; i++) {
			long duration = run(instanceFiles[i].getName(),
					initialSolutionsDirectory, 0);
			writeResult(writer, instanceFiles[i], duration);
		}

		writer.close();
	}

	private static void createLogFileHeader(final BufferedWriter writer)
			throws IOException {

		writer.write("Log file created at " + new Date());
		writer.newLine();
		writer.write("--------------------------------------------------");
		writer.newLine();
		writer.newLine();
		writer.write("Table size: " + ISolutionTableService.TABLE_SIZE);
		writer.newLine();
		writer.write("Iterations: " + iterations);
		writer.newLine();
		writer.newLine();

		writer.flush();
	}

	private static void writeResult(final BufferedWriter writer,
			final File instanceFile, long duration) throws IOException {

		final ISolutionTableService solutionTable = ServiceLocator
				.getInstance().getSolutionTableService();

		writer.write(instanceFile.getName());

		final long hours = TimeUnit.MILLISECONDS.toHours(duration);
		final long minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
				- TimeUnit.HOURS.toMinutes(hours);
		final long seconds = TimeUnit.MILLISECONDS.toSeconds(duration)
				- TimeUnit.MINUTES.toSeconds(minutes);
		final long msecs = duration - TimeUnit.SECONDS.toMillis(seconds);

		writer.write(String.format(" (Duration: %d h, %d m, %d s, %d ms):",
				hours, minutes, seconds, msecs));
		writer.newLine();
		writer.write("--------------");
		writer.newLine();
		writer.write("Best penalty/penalty: "
				+ solutionTable.getBestPenaltySolutionPenalty());
		writer.newLine();
		writer.write("Best penalty/fairness: "
				+ solutionTable.getBestPenaltySolutionFairness());
		writer.newLine();
		writer.write("Best fairness/penalty: "
				+ solutionTable.getBestFairnessSolutionPenalty());
		writer.newLine();
		writer.write("Best fairness/fairness: "
				+ solutionTable.getBestFairnessSolutionFairness());
		writer.newLine();
		writer.write("Worst penalty/penalty: "
				+ solutionTable.getWorstPenaltySolutionPenalty());
		writer.newLine();
		writer.write("Worst penalty/fairness: "
				+ solutionTable.getWorstPenaltySolutionFairness());
		writer.newLine();
		writer.write("Worst fairness/penalty: "
				+ solutionTable.getWorstFairnessSolutionPenalty());
		writer.newLine();
		writer.write("Worst fairness/fairness: "
				+ solutionTable.getWorstFairnessSolutionFairness());
		writer.newLine();
		writer.write("Generator success: " + Generator.success);
		writer.newLine();
		writer.write("Generator failure: " + Generator.failure);
		writer.newLine();
		writer
				.write("Mutation/recombination success: "
						+ CrazyGenetist.success);
		writer.newLine();
		writer
				.write("Mutation/recombination failure: "
						+ CrazyGenetist.failure);
		writer.newLine();
		writer.newLine();
		writer.newLine();

		writer.flush();
	}

}
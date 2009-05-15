package com.neuri.trinidad;

import java.io.*;
import java.lang.annotation.*;
import java.util.List;

import org.junit.runner.Description;
import org.junit.runner.notification.*;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.*;

import com.neuri.trinidad.fitnesserunner.*;

import fit.Counts;

public class FitnesseSuite extends ParentRunner<Test> {

	private Class<?> suiteClass;
	private String suiteName;
	private FitNesseRepository repository;
	private FolderTestResultRepository resultRepository;
	private FitTestEngine trinidadRunner;
	private List<Test> tests;
	private SuiteResult suiteResult;

	/**
	 * The <code>Name</code> annotation specifies the name of the
	 * Fitnesse suite to be run, e.g.: MySuite.MySubSuite
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Name {
		public String value();
	}

	/**
	 * The <code>FitnesseDir</code> annotation specifies the absolute or relative path
	 * to the directory in which FitNesseRoot can be found
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface FitnesseDir {
		public String value();
	}

	/**
	 * The <code>OutputDir</code> annotation specifies where the html reports
	 * of run suites and tests will be found after running them.
	 * You can either specify a relative or absolute path directly, e.g.: <code>@OutputDir("/tmp/trinidad-results")</code>,
	 * or you can specify a system property the content of which will be taken as base dir and optionally give a path extension,
	 * e.g.: <code>@OutputDir(systemProperty = "java.io.tmpdir", pathExtension = "trinidad-results")</code>
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface OutputDir {
		public String value() default "";

		public String systemProperty() default "";

		public String pathExtension() default "";
	}

	/**
	 * Used by JUnit
	 */
	public FitnesseSuite(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError {
		super(suiteClass);
		this.suiteClass = suiteClass;
		try {
			suiteName = getSuiteName(suiteClass);
			repository = new FitNesseRepository(getFitnesseDir(suiteClass));
			resultRepository = new FolderTestResultRepository(getOutputDir(suiteClass));
			trinidadRunner = new FitTestEngine();
			tests = repository.getSuite(suiteName);
		} catch (IOException e) {
			new InitializationError(e.getMessage());
		}
	}

	private static String getOutputDir(Class<?> klass) throws InitializationError {
		OutputDir outputDirAnnotation = klass.getAnnotation(OutputDir.class);
		if (outputDirAnnotation == null) {
			throw new InitializationError("There must be a @OutputDir annotation");
		}
		if (!"".equals(outputDirAnnotation.value())) {
			return outputDirAnnotation.value();
		}
		if (!"".equals(outputDirAnnotation.systemProperty())) {
			String baseDir = System.getProperty(outputDirAnnotation.systemProperty());
			File outputDir = new File(baseDir, outputDirAnnotation.pathExtension());
			return outputDir.getAbsolutePath();
		}
		throw new InitializationError("In annotation @OutputDir you have to specify either 'value' or 'systemProperty'");
	}

	private static String getFitnesseDir(Class<?> klass) throws InitializationError {
		FitnesseDir fitnesseDirAnnotation = klass.getAnnotation(FitnesseDir.class);
		if (fitnesseDirAnnotation == null) {
			throw new InitializationError("There must be a @FitnesseDir annotation");
		}
		return fitnesseDirAnnotation.value();
	}

	private static String getSuiteName(Class<?> klass) throws InitializationError {
		Name nameAnnotation = klass.getAnnotation(Name.class);
		if (nameAnnotation == null) {
			throw new InitializationError("There must be a @Name annotation");
		}
		return nameAnnotation.value();
	}

	@Override
	protected Description describeChild(Test child) {
		return Description.createTestDescription(suiteClass, child.getName());
	}

	@Override
	protected List<Test> getChildren() {
		return tests;
	}

	@Override
	public void run(final RunNotifier notifier) {
		try {
			repository.prepareResultRepository(resultRepository);
			suiteResult = new SuiteResult(suiteName);
			super.run(notifier);
			resultRepository.recordTestResult(suiteResult);
		} catch (IOException e) {
			notifier.fireTestFailure(new Failure(getDescription(), e));
		}
	}

	@Override
	protected void runChild(Test test, RunNotifier notifier) {
		Description testDescription = describeChild(test);
		notifier.fireTestStarted(testDescription);
		TestResult tr = trinidadRunner.runTest(test);
		notifyTestResult(notifier, testDescription, tr);
		suiteResult.append(tr);
		try {
			resultRepository.recordTestResult(tr);
		} catch (IOException e) {
			notifier.fireTestFailure(new Failure(testDescription, e));
		}
	}

	private void notifyTestResult(RunNotifier notifier, Description testDescription, TestResult tr) {
		Counts counts = tr.getCounts();
		if (counts.wrong == 0 && counts.exceptions == 0) {
			notifier.fireTestFinished(testDescription);
		} else {
			notifier.fireTestFailure(new Failure(testDescription, new AssertionError("wrong: " + counts.wrong + " exceptions: " + counts.exceptions + "\n" + tr.getContent())));
		}
	}

	@Override
	protected String getName() {
		return suiteName;
	}

}

package org.jberet.test.integration;

import org.jberet.cdi.JobScoped;

import java.util.Set;

import javax.annotation.PostConstruct;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.context.JobContext;
import javax.ejb.Stateless;
import javax.inject.Inject;

@Stateless
public class BatchBean {

	private JobOperator jobOperator;
	@Inject
	private JobContext jobContext;

	@PostConstruct
	public void postConstruct() {
		this.jobOperator = BatchRuntime.getJobOperator();
	}

	public boolean hasJobOperator() {
		return this.jobOperator != null;
	}

	public long startTestJob() {
		return this.jobOperator.start("test", null);
	}

	public Set<String> getJobNames() {
		return this.jobOperator.getJobNames();
	}
}

package org.wildfly.extension.batch.job.repository;

import org.jberet.job.model.Job;
import org.jberet.repository.*;
import org.jberet.runtime.*;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import javax.batch.runtime.JobExecution;
import javax.batch.runtime.JobInstance;
import javax.batch.runtime.StepExecution;
import javax.sql.DataSource;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * A service which provides a JDBC job repository which delegates to a {@link JobRepository} throwing an {@link IllegalStateException} if the
 * service has been stopped.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JobRepositoryService implements JobRepository, Service<JobRepository> {

    private final InjectedValue<DataSource> dataSourceValue = new InjectedValue<DataSource>();
    private final InjectedValue<ExecutorService> executor = new InjectedValue<ExecutorService>();
    private volatile JobRepository jobRepository;
    private volatile boolean started;

    @Override
    public final void start(final StartContext context) throws StartException {
        startJobRepository(context);
        started = true;
    }

    @Override
    public final void stop(final StopContext context) {
        jobRepository = null;
        started = false;
    }

    @Override
    public final JobRepository getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public void addJob(final ApplicationAndJobName applicationAndJobName, final Job job) {
        getAndCheckDelegate().addJob(applicationAndJobName, job);
    }

    @Override
    public void removeJob(final String jobId) {
        getAndCheckDelegate().removeJob(jobId);
    }

    @Override
    public Job getJob(final ApplicationAndJobName applicationAndJobName) {
        return getAndCheckDelegate().getJob(applicationAndJobName);
    }

    @Override
    public Set<String> getJobNames() {
        return getAndCheckDelegate().getJobNames();
    }

    @Override
    public boolean jobExists(final String jobName) {
        return getAndCheckDelegate().jobExists(jobName);
    }

    @Override
    public JobInstanceImpl createJobInstance(final Job job, final String applicationName, final ClassLoader classLoader) {
        return getAndCheckDelegate().createJobInstance(job, applicationName, classLoader);
    }

    @Override
    public void removeJobInstance(final long jobInstanceId) {
        getAndCheckDelegate().removeJobInstance(jobInstanceId);
    }

    @Override
    public JobInstance getJobInstance(final long jobInstanceId) {
        return getAndCheckDelegate().getJobInstance(jobInstanceId);
    }

    @Override
    public List<JobInstance> getJobInstances(final String jobName) {
        return getAndCheckDelegate().getJobInstances(jobName);
    }

    @Override
    public int getJobInstanceCount(final String jobName) {
        return getAndCheckDelegate().getJobInstanceCount(jobName);
    }

    @Override
    public JobExecutionImpl createJobExecution(final JobInstanceImpl jobInstance, final Properties jobParameters) {
        return getAndCheckDelegate().createJobExecution(jobInstance, jobParameters);
    }

    @Override
    public JobExecution getJobExecution(final long jobExecutionId) {
        return getAndCheckDelegate().getJobExecution(jobExecutionId);
    }

    @Override
    public List<JobExecution> getJobExecutions(final JobInstance jobInstance) {
        return getAndCheckDelegate().getJobExecutions(jobInstance);
    }

    @Override
    public void updateJobExecution(final JobExecutionImpl jobExecution, final boolean fullUpdate, final boolean saveJobParameters) {
        getAndCheckDelegate().updateJobExecution(jobExecution, fullUpdate, saveJobParameters);
    }

    @Override
    public List<Long> getRunningExecutions(final String jobName) {
        return getAndCheckDelegate().getRunningExecutions(jobName);
    }

    @Override
    public void removeJobExecutions(final JobExecutionSelector jobExecutionSelector) {
        getAndCheckDelegate().removeJobExecutions(jobExecutionSelector);
    }

    @Override
    public List<StepExecution> getStepExecutions(final long jobExecutionId, final ClassLoader classLoader) {
        return getAndCheckDelegate().getStepExecutions(jobExecutionId, classLoader);
    }

    @Override
    public StepExecutionImpl createStepExecution(final String stepName) {
        return getAndCheckDelegate().createStepExecution(stepName);
    }

    @Override
    public void addStepExecution(final JobExecutionImpl jobExecution, final StepExecutionImpl stepExecution) {
        getAndCheckDelegate().addStepExecution(jobExecution, stepExecution);
    }

    @Override
    public void updateStepExecution(final StepExecution stepExecution) {
        getAndCheckDelegate().updateStepExecution(stepExecution);
    }

    @Override
    public StepExecutionImpl findOriginalStepExecutionForRestart(final String stepName, final JobExecutionImpl jobExecutionToRestart, final ClassLoader classLoader) {
        return getAndCheckDelegate().findOriginalStepExecutionForRestart(stepName, jobExecutionToRestart, classLoader);
    }

    @Override
    public int countStepStartTimes(final String stepName, final long jobInstanceId) {
        return getAndCheckDelegate().countStepStartTimes(stepName, jobInstanceId);
    }

    @Override
    public void addPartitionExecution(final StepExecutionImpl enclosingStepExecution, final PartitionExecutionImpl partitionExecution) {
        getAndCheckDelegate().addPartitionExecution(enclosingStepExecution, partitionExecution);
    }

    @Override
    public List<PartitionExecutionImpl> getPartitionExecutions(final long stepExecutionId, final StepExecutionImpl stepExecution, final boolean notCompletedOnly, final ClassLoader classLoader) {
        return getAndCheckDelegate().getPartitionExecutions(stepExecutionId, stepExecution, notCompletedOnly, classLoader);
    }

    @Override
    public void savePersistentData(final JobExecution jobExecution, final AbstractStepExecution stepOrPartitionExecution) {
        getAndCheckDelegate().savePersistentData(jobExecution, stepOrPartitionExecution);
    }

    private JobRepository getAndCheckDelegate() {
        final JobRepository delegate = getDelegate();
        if (started && delegate != null) {
            return delegate;
        }
        throw new IllegalStateException("The service JobOperatorService has been stopped and cannot execute operations.");
    }

    public void startJobRepository(final StartContext context) throws StartException {

        final ExecutorService service = executor.getValue();
        try {
            service.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (JobRepositoryFactory.getInstance().requiresJndiName()) {
                            // Currently in jBeret tables are created in the constructor which is why this is done asynchronously
                            jobRepository = new JdbcRepository(dataSourceValue.getValue());
                        } else {
                            jobRepository = InMemoryRepository.getInstance();
                        }
                        context.complete();
                    } catch (Exception e) {
                        context.failed( new StartException("Failed to create JDBC job repository."));
                    }
                }
            });
        } finally {
            context.asynchronous();
        }
    }

    protected JobRepository getDelegate() {
        return jobRepository;
    }
    public InjectedValue<DataSource> getDataSourceInjector() {
        return dataSourceValue;
    }
    public Injector<ExecutorService> getExecutorServiceInjector() {
        return executor;
    }

}


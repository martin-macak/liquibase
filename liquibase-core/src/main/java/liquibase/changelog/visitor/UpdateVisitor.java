package liquibase.changelog.visitor;

import liquibase.changelog.ChangeSet;
import liquibase.changelog.ChangeSet.ExecType;
import liquibase.changelog.ChangeSet.RunStatus;
import liquibase.changelog.ChangeSetAsyncLevel;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.filter.ChangeSetFilterResult;
import liquibase.database.Database;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.exception.LiquibaseException;
import liquibase.exception.MigrationFailedException;
import liquibase.logging.LogFactory;
import liquibase.logging.Logger;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public class UpdateVisitor implements ChangeSetVisitor {

    private Database database;

    private Logger log = LogFactory.getLogger();

    private ChangeExecListener execListener;

    private final ForkJoinPool threadPool = new ForkJoinPool(10);

    /**
     * @deprecated - please use the constructor with ChangeExecListener, which can be null.
     */
    @Deprecated
    public UpdateVisitor(Database database) {
        this.database = database;
    }

    public UpdateVisitor(Database database, ChangeExecListener execListener) {
        this(database);
        this.execListener = execListener;
    }

    @Override
    public Direction getDirection() {
        return ChangeSetVisitor.Direction.FORWARD;
    }

    @Override
    public VisitResult visit(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database database, Set<ChangeSetFilterResult> filterResults) throws LiquibaseException {
        VisitResult result;
        if (changeSet.getAsyncLevel() == ChangeSetAsyncLevel.ASYNCBLOCK || changeSet.getAsyncLevel() == ChangeSetAsyncLevel.PARALLEL) {
            final CompletableFuture future = new CompletableFuture();
            result = () -> future;
            threadPool.execute(() -> {
                try {
                    doVisit(changeSet, databaseChangeLog, database, filterResults);
                    future.complete(null);
                } catch (LiquibaseException e) {
                    future.completeExceptionally(e);
                }
            });
        } else {
            doVisit(changeSet, databaseChangeLog, database, filterResults);
            result = VisitResult.completed();
        }
        return result;
    }

    private void doVisit(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database database, Set<ChangeSetFilterResult> filterResults) throws LiquibaseException {
        ChangeSet.RunStatus runStatus = this.database.getRunStatus(changeSet);
        log.debug("Running Changeset:" + changeSet);
        fireWillRun(changeSet, databaseChangeLog, database, runStatus);
        ExecType execType = null;
        ObjectQuotingStrategy previousStr = this.database.getObjectQuotingStrategy();
        try {
            execType = changeSet.execute(databaseChangeLog, execListener, this.database);
        } catch (MigrationFailedException e) {
            fireRunFailed(changeSet, databaseChangeLog, database, e);
            throw e;
        }
        if (!runStatus.equals(ChangeSet.RunStatus.NOT_RAN)) {
            execType = ChangeSet.ExecType.RERAN;
        }
        fireRan(changeSet, databaseChangeLog, database, execType);
        // reset object quoting strategy after running changeset
        this.database.setObjectQuotingStrategy(previousStr);
        this.database.markChangeSetExecStatus(changeSet, execType);

        this.database.commit();
    }

    protected void fireRunFailed(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database database, MigrationFailedException e) {
        if (execListener != null) {
            execListener.runFailed(changeSet, databaseChangeLog, database, e);
        }
    }

    protected void fireWillRun(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database database2, RunStatus runStatus) {
        if (execListener != null) {
            execListener.willRun(changeSet, databaseChangeLog, database, runStatus);
        }
    }

    protected void fireRan(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database database2, ExecType execType) {
        if (execListener != null) {
            execListener.ran(changeSet, databaseChangeLog, database, execType);
        }
    }
}

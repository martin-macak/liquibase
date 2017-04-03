package liquibase.changelog.visitor;

import java.util.concurrent.CompletableFuture;

/**
 * Created by maca on 03/04/2017.
 */
public interface VisitResult {
    CompletableFuture<Void> getCompletion();

    static VisitResult completed() {
        return CompletedResult.INSTANCE;
    }

    class CompletedResult implements VisitResult {

        private static final CompletedResult INSTANCE = new CompletedResult();

        @Override
        public CompletableFuture<Void> getCompletion() {
            return CompletableFuture.completedFuture(null);
        }
    }
}

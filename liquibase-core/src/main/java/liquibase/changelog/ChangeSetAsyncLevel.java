package liquibase.changelog;

/**
 * Created by maca on 06/04/2017.
 */
public enum ChangeSetAsyncLevel {
    SYNCHRONOUS,
    ASYNCBLOCK,
    PARALLEL;

    public static ChangeSetAsyncLevel fromString(String str) {
        return ChangeSetAsyncLevel.valueOf(str.toUpperCase());
    }
}

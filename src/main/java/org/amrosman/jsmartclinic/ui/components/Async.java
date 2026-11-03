package org.amrosman.jsmartclinic.ui.components;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs database work away from the JavaFX application thread. Success and failure
 * callbacks are invoked on the FX thread by {@link Task}.
 */
public final class Async {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "jsmartclinic-db");
        thread.setDaemon(true);
        return thread;
    });

    private Async() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess) {
        run(work, onSuccess, Dialogs::error);
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            try {
                onSuccess.accept(task.getValue());
            } catch (RuntimeException e) {
                Dialogs.error(e);
            }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            if (onError == null) {
                Dialogs.error(error);
            } else {
                onError.accept(error);
            }
        });
        EXECUTOR.execute(task);
    }

    /** Fire-and-forget background work with a default error dialog. */
    public static void run(Runnable work) {
        run(() -> {
            work.run();
            return null;
        }, ignored -> {
        });
    }
}

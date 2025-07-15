package com.netflix.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StructuredConcurrencyServiceTest {

    private StructuredConcurrencyService service;

    @BeforeEach
    void setUp() {
        service = new StructuredConcurrencyService();
    }

    @Test
    void shouldExecuteWithStructuredConcurrencySuccessfully() throws Exception {
        // Given
        Supplier<String> task = () -> "test-result";

        // When
        String result = service.executeWithStructuredConcurrency(task);

        // Then
        assertEquals("test-result", result);
    }

    @Test
    void shouldExecuteAsyncWithStructuredConcurrencySuccessfully() {
        // Given
        Supplier<String> task = () -> "async-result";

        // When
        CompletableFuture<String> future = service.executeAsyncWithStructuredConcurrency(task);

        // Then
        assertDoesNotThrow(() -> {
            String result = future.get();
            assertEquals("async-result", result);
        });
    }

    @Test
    void shouldExecuteWithTimeoutSuccessfully() throws Exception {
        // Given
        Supplier<String> task = () -> {
            try {
                Thread.sleep(100); // Short delay
                return "timeout-result";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        // When
        String result = service.executeWithTimeout(task, 1000); // 1 second timeout

        // Then
        assertEquals("timeout-result", result);
    }

    @Test
    void shouldThrowExceptionOnTimeout() {
        // Given
        Supplier<String> slowTask = () -> {
            try {
                Thread.sleep(2000); // 2 second delay
                return "slow-result";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        // When & Then
        assertThrows(Exception.class, () -> {
            service.executeWithTimeout(slowTask, 100); // 100ms timeout
        });
    }

    @Test
    void shouldExecuteFederatedQuerySuccessfully() throws Exception {
        // Given
        List<Supplier<Object>> tasks = Arrays.asList(
            () -> "movie-data",
            () -> "review-data",
            () -> "user-data"
        );
        
        // When
        String result = service.executeFederatedQuery(tasks, results -> {
            return String.join(",", results.stream().map(Object::toString).toArray(String[]::new));
        });

        // Then
        assertEquals("movie-data,review-data,user-data", result);
    }

    @Test
    void shouldCancelRemainingTasksOnFederatedQueryFailure() {
        // Given
        List<Supplier<Object>> tasks = Arrays.asList(
            () -> "success-data",
            () -> { throw new RuntimeException("Service failure"); },
            () -> "should-not-execute"
        );

        // When & Then
        assertThrows(Exception.class, () -> {
            service.executeFederatedQuery(tasks, results -> results);
        });
    }

    @Test
    void shouldExecuteBatchSuccessfully() throws Exception {
        // Given
        List<Supplier<String>> tasks = Arrays.asList(
            () -> "batch-1",
            () -> "batch-2",
            () -> "batch-3"
        );

        // When
        List<String> results = service.executeBatch(tasks, Duration.ofSeconds(5));

        // Then
        assertEquals(3, results.size());
        assertTrue(results.contains("batch-1"));
        assertTrue(results.contains("batch-2"));
        assertTrue(results.contains("batch-3"));
    }

    @Test
    void shouldHandleBatchTimeout() {
        // Given
        List<Supplier<String>> tasks = Arrays.asList(
            () -> {
                try {
                    Thread.sleep(2000); // 2 second delay
                    return "slow-batch";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        );

        // When & Then
        assertThrows(Exception.class, () -> {
            service.executeBatch(tasks, Duration.ofMillis(100)); // 100ms timeout
        });
    }

    @Test
    void shouldExecuteWithRetrySuccessfully() throws Exception {
        // Given
        Supplier<String> task = () -> "retry-success";
        StructuredConcurrencyService.RetryPolicy policy = 
            StructuredConcurrencyService.RetryPolicy.of(3, Duration.ofMillis(10), 2.0);

        // When
        String result = service.executeWithRetry(task, policy);

        // Then
        assertEquals("retry-success", result);
    }

    @Test
    void shouldRetryFailedTasksWithExponentialBackoff() {
        // Given
        final int[] attemptCount = {0};
        Supplier<String> flakyTask = () -> {
            attemptCount[0]++;
            if (attemptCount[0] < 3) {
                throw new RuntimeException("Attempt " + attemptCount[0] + " failed");
            }
            return "retry-success-after-failures";
        };
        
        StructuredConcurrencyService.RetryPolicy policy = 
            StructuredConcurrencyService.RetryPolicy.of(3, Duration.ofMillis(10), 2.0);

        // When & Then
        assertDoesNotThrow(() -> {
            String result = service.executeWithRetry(flakyTask, policy);
            assertEquals("retry-success-after-failures", result);
            assertEquals(3, attemptCount[0]); // Should have made 3 attempts
        });
    }

    @Test
    void shouldFailAfterMaxRetryAttempts() {
        // Given
        Supplier<String> alwaysFailingTask = () -> {
            throw new RuntimeException("Always fails");
        };
        
        StructuredConcurrencyService.RetryPolicy policy = 
            StructuredConcurrencyService.RetryPolicy.of(2, Duration.ofMillis(10), 2.0);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.executeWithRetry(alwaysFailingTask, policy);
        });
        
        assertTrue(exception.getMessage().contains("Task failed after 2 attempts"));
    }

    @Test
    void shouldExecuteWithMetricsSuccessfully() throws Exception {
        // Given
        Supplier<String> task = () -> "metrics-result";

        // When
        String result = service.executeWithMetrics("test-operation", task);

        // Then
        assertEquals("metrics-result", result);
    }

    @Test
    void shouldHandleMetricsForFailedOperations() {
        // Given
        Supplier<String> failingTask = () -> {
            throw new RuntimeException("Metrics test failure");
        };

        // When & Then
        assertThrows(Exception.class, () -> {
            service.executeWithMetrics("failing-operation", failingTask);
        });
    }

    @Test
    void shouldExecuteFederatedQueryWithGracefulDegradation() throws Exception {
        // Given
        List<Supplier<Object>> criticalTasks = Arrays.asList(
            () -> "critical-movie-data",
            () -> "critical-user-data"
        );
        
        List<Supplier<Object>> optionalTasks = Arrays.asList(
            () -> "optional-review-data",
            () -> { throw new RuntimeException("Optional service down"); } // This should fail gracefully
        );

        // When
        String result = service.executeFederatedQueryWithGracefulDegradation(
            criticalTasks,
            optionalTasks,
            results -> {
                return results.stream()
                    .filter(r -> r != null)
                    .map(Object::toString)
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);
            },
            Duration.ofSeconds(5)
        );

        // Then
        assertTrue(result.contains("critical-movie-data"));
        assertTrue(result.contains("critical-user-data"));
        assertTrue(result.contains("optional-review-data"));
        // The failing optional task should not appear in results
    }

    @Test
    void shouldFailWhenCriticalTasksFailInGracefulDegradation() {
        // Given
        List<Supplier<Object>> criticalTasks = Arrays.asList(
            () -> "critical-data",
            () -> { throw new RuntimeException("Critical service failure"); }
        );
        
        List<Supplier<Object>> optionalTasks = Arrays.asList(
            () -> "optional-data"
        );

        // When & Then
        assertThrows(Exception.class, () -> {
            service.executeFederatedQueryWithGracefulDegradation(
                criticalTasks,
                optionalTasks,
                results -> results,
                Duration.ofSeconds(5)
            );
        });
    }

    @Test
    void shouldCreateDefaultRetryPolicy() {
        // When
        StructuredConcurrencyService.RetryPolicy policy = 
            StructuredConcurrencyService.RetryPolicy.defaultPolicy();

        // Then
        assertEquals(3, policy.maxAttempts());
        assertEquals(Duration.ofMillis(100), policy.backoffDelay());
        assertEquals(2.0, policy.backoffMultiplier());
    }

    @Test
    void shouldCreateCustomRetryPolicy() {
        // When
        StructuredConcurrencyService.RetryPolicy policy = 
            StructuredConcurrencyService.RetryPolicy.of(5, Duration.ofMillis(200), 1.5);

        // Then
        assertEquals(5, policy.maxAttempts());
        assertEquals(Duration.ofMillis(200), policy.backoffDelay());
        assertEquals(1.5, policy.backoffMultiplier());
    }

    @Test
    void shouldHandleInterruptedRetry() {
        // Given
        Supplier<String> task = () -> {
            throw new RuntimeException("Test failure");
        };
        
        StructuredConcurrencyService.RetryPolicy policy = 
            StructuredConcurrencyService.RetryPolicy.of(3, Duration.ofMillis(100), 2.0);

        // Interrupt the current thread to test interrupt handling
        Thread.currentThread().interrupt();

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.executeWithRetry(task, policy);
        });
        
        // Verify thread interrupt status is preserved
        assertTrue(Thread.interrupted()); // This also clears the interrupt status
    }

    @Test
    void shouldHandleEmptyTaskLists() throws Exception {
        // Given
        List<Supplier<Object>> emptyTasks = Arrays.asList();

        // When
        String result = service.executeFederatedQuery(emptyTasks, results -> "empty-result");

        // Then
        assertEquals("empty-result", result);
    }

    @Test
    void shouldHandleNullResultsInGracefulDegradation() throws Exception {
        // Given
        List<Supplier<Object>> criticalTasks = Arrays.asList(
            () -> "critical-data"
        );
        
        List<Supplier<Object>> optionalTasks = Arrays.asList(
            () -> null // Explicitly return null
        );

        // When
        List<Object> result = service.executeFederatedQueryWithGracefulDegradation(
            criticalTasks,
            optionalTasks,
            results -> results,
            Duration.ofSeconds(5)
        );

        // Then
        assertEquals(1, result.size()); // Only critical data should be present
        assertEquals("critical-data", result.get(0));
    }
}
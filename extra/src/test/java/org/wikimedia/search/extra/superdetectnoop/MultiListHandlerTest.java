package org.wikimedia.search.extra.superdetectnoop;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class MultiListHandlerTest {
    private static final List<String> A = ImmutableList.of(
            "A/foo", "A/bar");
    private static final List<String> B1 = ImmutableList.of(
            "B/something");
    private static final List<String> B2 = ImmutableList.of(
            "B/otherthing");
    private static final List<String> U = ImmutableList.of(
            "unnamed", "also.unnamed");

    @Test
    public void testSingleClassNullable() {
        testCase(null, A, A);
        testCase(A, null, null);
        testCase(B1, B2, B2);
        testCaseCloseEnough(A, A);
        testCaseCloseEnough(B1, B1);
        testCaseCloseEnough(U, U);
        testCase(Collections.emptyList(), A, A);
    }

    @Test
    public void testMultiClass() {
        testCase(null, concat(A, B1), concat(A, B1));
        testCase(concat(A, B1), null, null);
        testCase(A, B1, concat(A, B1));
        testCaseCloseEnough(concat(A, B1), A);
        testCaseCloseEnough(concat(A, B1), B1);
        testCase(concat(A, B1), B2, concat(A, B2));
        testCase(concat(A, B2), B1, concat(A, B1));
    }

    @Test
    public void testUnnamedGroups() {
        testCase(A, U, concat(A, U));
        testCase(U, A, concat(A, U));
        testCase(A, concat(A, U), concat(A, U));
        testCase(U, concat(A, U), concat(A, U));

        testCaseCloseEnough(concat(A, U), U);
        testCaseCloseEnough(concat(A, B1, U), A);
        testCaseCloseEnough(concat(A, B1, U), B1);
        testCaseCloseEnough(concat(A, B1, U), U);
        testCaseCloseEnough(concat(A, B1, U), concat(A, U));

        testCase(concat(B1, U), B2, concat(B2, U));
        testCase(concat(A, B2, U), B1, concat(A, B1, U));
    }

    private List<String> deleteList(String group) {
        // Arguments must be of the form "A/", including the delimiter.
        // This allows representing the unnamed group with "".
        return ImmutableList.of(group + MultiListHandler.DELETE);
    }

    @Test
    public void testDelete() {
        testCaseCloseEnough(A, deleteList("B/"));
        testCase(A, deleteList("A/"), Collections.emptyList());
        testCase(concat(A, U), deleteList(""), A);
        testCase(concat(A, U), deleteList("A/"), U);
        testCase(concat(A, B1, U), concat(B2, deleteList("A/")), concat(B2, U));
    }

    @Test
    public void testAwkwardInputs() {
        testFailureCase(0, 1);
        testFailureCase(A, 4);
        testFailureCase(A, ImmutableList.of(5));
        testFailureCase(U, ImmutableList.of("Something", 5, "Otherthing"));
        testFailureCase(77, A);
        testFailureCase(ImmutableList.of("Words", 5), B1);
        testFailureCase(A, Collections.emptyList());
    }

    // Test takes ~100ms, give 10x margin for slower machine / over-busy CI
    @Test(timeout = 1000)
    public void testOversizedInputs() {
        List<String> C = IntStream.range(0, 100000)
            .mapToObj(i -> "B/" + i)
            .collect(toList());
        // Comparing the list to itself gives us the worst-case performance, all
        // values must be compared with no opportunity for early-exit.
        testCaseCloseEnough(C, C);
    }

    private void testCaseCloseEnough(@Nullable List<String> oldValue, @Nullable List<String> newValue) {
        testCase(oldValue, newValue, null, true);
    }

    private void testCase(@Nullable List<String> oldValue, @Nullable List<String> newValue, @Nullable List<String> expected) {
        testCase(oldValue, newValue, expected, false);
    }

    @SuppressWarnings("unchecked")
    private void testCase(
            @Nullable List<String> oldValue, @Nullable List<String> newValue,
            @Nullable List<String> expected, boolean isCloseEnough
    ) {
        ChangeHandler.Result result = MultiListHandler.INSTANCE.handle(oldValue, newValue);
        assertThat(result.isCloseEnough()).isEqualTo(isCloseEnough);
        if (expected != null && result.newValue() instanceof List) {
            List<Object> resultList = (List<Object>)result.newValue();
            assertThat(resultList).containsExactlyInAnyOrder(expected.toArray());
        } else {
            assertThat(result.newValue()).isEqualTo(expected);
        }
        assertThat(result.isDocumentNooped()).isEqualTo(false);
    }

    private void testFailureCase(@Nullable Object oldValue, @Nullable Object newValue) {
        try {
            MultiListHandler.INSTANCE.handle(oldValue, newValue);
        } catch (IllegalArgumentException e) {
            return;
        }
        failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
    }

    @SafeVarargs
    private final <T> List<T> concat(List<T>... lists) {
        return Stream.of(lists).flatMap(List::stream).collect(toList());
    }
}

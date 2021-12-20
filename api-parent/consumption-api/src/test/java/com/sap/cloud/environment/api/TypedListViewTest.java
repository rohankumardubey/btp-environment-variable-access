/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */

package com.sap.cloud.environment.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sap.cloud.environment.api.exception.ValueCastException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TypedListViewTest {

    private static final Collection<Object> PRIMITIVE_VALUES = new ArrayList<>();
    private static final Collection<Method> TYPED_ACCESSORS = new ArrayList<>();

    @BeforeAll
    static void beforeAll() throws NoSuchMethodException {
        PRIMITIVE_VALUES.add(null);
        PRIMITIVE_VALUES.add(true);
        PRIMITIVE_VALUES.add(42);
        PRIMITIVE_VALUES.add(13.37d);
        PRIMITIVE_VALUES.add(BigDecimal.valueOf(Long.MAX_VALUE, Integer.MAX_VALUE));
        PRIMITIVE_VALUES.add("Value");

        TYPED_ACCESSORS.add(TypedListView.class.getDeclaredMethod("getBoolean", int.class));
        TYPED_ACCESSORS.add(TypedListView.class.getDeclaredMethod("getInteger", int.class));
        TYPED_ACCESSORS.add(TypedListView.class.getDeclaredMethod("getDouble", int.class));
        TYPED_ACCESSORS.add(TypedListView.class.getDeclaredMethod("getNumber", int.class));
        TYPED_ACCESSORS.add(TypedListView.class.getDeclaredMethod("getString", int.class));
        TYPED_ACCESSORS.add(TypedListView.class.getDeclaredMethod("getListView", int.class));
        TYPED_ACCESSORS.add(TypedListView.class.getDeclaredMethod("getMapView", int.class));
    }

    @Test
    void create() {
        final Collection<Object> collection = new ArrayList<>(PRIMITIVE_VALUES);
        collection.add(mock(TypedMapView.class));
        collection.add(mock(TypedListView.class));

        final TypedListView sut = TypedListView.of(collection);

        assertThat(sut).isNotNull();

        assertThat(sut.getSize()).isEqualTo(8);

        assertThat(sut.get(0)).isNull();
        assertThat(sut.getBoolean(1)).isTrue();
        assertThat(sut.getInteger(2)).isEqualTo(42);
        assertThat(sut.getDouble(3)).isEqualTo(13.37d);
        assertThat(sut.getNumber(4)).isEqualTo(BigDecimal.valueOf(Long.MAX_VALUE, Integer.MAX_VALUE));
        assertThat(sut.getString(5)).isEqualTo("Value");
        assertThat(sut.getMapView(6)).isNotNull();
        assertThat(sut.getListView(7)).isNotNull();
    }

    @Test
    void createTransformsNestedMapToMapView() {
        final TypedListView sut = TypedListView.of(Collections.singletonList(Collections.singletonMap("Key", "Value")));

        assertThat(sut.getMapView(0)).isNotNull();
        assertThat(sut.getMapView(0).getKeys()).containsExactlyInAnyOrder("Key");
    }

    @Test
    void createTransformsNestedListToListView() {
        final TypedListView sut = TypedListView.of(Collections.singletonList(PRIMITIVE_VALUES));

        assertThat(sut.getListView(0)).isNotNull();
        assertThat(sut.getListView(0).getSize()).isEqualTo(6);
    }

    @Test
    void getBoolean() throws NoSuchMethodException {
        final TypedListView sut = TypedListView.of(Collections.singletonList(true));

        expectValueCastExceptionForAllBut(sut, 0,
                TypedListView.class.getDeclaredMethod("getBoolean", int.class));
    }

    @Test
    void getInteger() throws NoSuchMethodException {
        final TypedListView sut = TypedListView.of(Collections.singletonList(42));

        expectValueCastExceptionForAllBut(sut, 0,
                TypedListView.class.getDeclaredMethod("getInteger", int.class),
                TypedListView.class.getDeclaredMethod("getDouble", int.class),
                TypedListView.class.getDeclaredMethod("getNumber", int.class));
    }

    @Test
    void getDouble() throws NoSuchMethodException {
        final TypedListView sut = TypedListView.of(Collections.singletonList(13.37d));

        expectValueCastExceptionForAllBut(sut, 0,
                TypedListView.class.getDeclaredMethod("getInteger", int.class),
                TypedListView.class.getDeclaredMethod("getDouble", int.class),
                TypedListView.class.getDeclaredMethod("getNumber", int.class));
    }

    @Test
    void getNumber() throws NoSuchMethodException {
        final TypedListView sut = TypedListView.of(Collections.singletonList(BigDecimal.valueOf(Long.MAX_VALUE, Integer.MAX_VALUE)));

        expectValueCastExceptionForAllBut(sut, 0,
                TypedListView.class.getDeclaredMethod("getInteger", int.class),
                TypedListView.class.getDeclaredMethod("getDouble", int.class),
                TypedListView.class.getDeclaredMethod("getNumber", int.class));
    }

    @Test
    void getString() throws NoSuchMethodException {
        final TypedListView sut = TypedListView.of(Collections.singletonList("Value"));

        expectValueCastExceptionForAllBut(sut, 0,
                TypedListView.class.getDeclaredMethod("getString", int.class));
    }

    @Test
    void getMapView() throws NoSuchMethodException {
        final TypedListView sut = TypedListView.of(Collections.singletonList(mock(TypedMapView.class)));

        expectValueCastExceptionForAllBut(sut, 0,
                TypedListView.class.getDeclaredMethod("getMapView", int.class));
    }

    @Test
    void getListView() throws NoSuchMethodException {
        final TypedListView sut = TypedListView.of(Collections.singletonList(mock(TypedListView.class)));

        expectValueCastExceptionForAllBut(sut, 0,
                TypedListView.class.getDeclaredMethod("getListView", int.class));
    }

    private static void expectValueCastExceptionForAllBut(@Nonnull final TypedListView sut,
                                                          final int index,
                                                          @Nonnull final Method... methods) {

        final List<Method> expectedWorkingMethods = Arrays.asList(methods);
        for (final Method typedAccessor : TYPED_ACCESSORS) {
            if (expectedWorkingMethods.contains(typedAccessor)) {
                assertThatNoException().isThrownBy(() -> typedAccessor.invoke(sut, index));
            } else {
                assertThatThrownBy(() -> typedAccessor.invoke(sut, index)).hasCauseExactlyInstanceOf(ValueCastException.class);
            }
        }
    }
}
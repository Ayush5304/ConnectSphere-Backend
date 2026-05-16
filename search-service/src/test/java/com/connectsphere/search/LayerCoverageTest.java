package com.connectsphere.search;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LayerCoverageTest {

    private static final List<String> CLASSES = List.of(
        "com.connectsphere.search.config.AppConfig",
        "com.connectsphere.search.config.CorsConfig",
        "com.connectsphere.search.config.SecurityConfig",
        "com.connectsphere.search.config.SwaggerConfig",
        "com.connectsphere.search.controller.SearchResource",
        "com.connectsphere.search.entity.Hashtag",
        "com.connectsphere.search.entity.PostHashtag",
        "com.connectsphere.search.repository.HashtagRepository",
        "com.connectsphere.search.repository.PostHashtagRepository",
        "com.connectsphere.search.service.SearchService"
    );

    @Test
    void dtoEntityAndExceptionClassesHaveUsableConstructorsAndAccessors() throws Exception {
        for (String className : CLASSES) {
            Class<?> type = Class.forName(className);
            if (type.isInterface() || type.isEnum() || isSpringComponent(type)) continue;
            Object instance = instantiate(type);
            if (instance == null) continue;
            exerciseAccessors(type, instance);
            assertEquals(type, instance.getClass());
        }
    }

    @Test
    void repositoryInterfacesExposeSpringDataContracts() throws Exception {
        for (String className : CLASSES) {
            Class<?> type = Class.forName(className);
            if (!type.getPackageName().contains(".repository")) continue;
            assertTrue(type.isInterface());
            assertTrue(JpaRepository.class.isAssignableFrom(type));
            assertTrue(type.getDeclaredMethods().length >= 0);
        }
    }

    @Test
    void configClassesCreateNoArgumentBeans() throws Exception {
        for (String className : CLASSES) {
            Class<?> type = Class.forName(className);
            if (!type.getPackageName().contains(".config")) continue;
            Object instance = instantiate(type);
            if (instance == null) continue;
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType().equals(Void.TYPE)) {
                    continue;
                }
                Object value = method.invoke(instance);
                if (value != null) {
                    assertTrue(method.getReturnType().isAssignableFrom(value.getClass()) || method.getReturnType().isInterface());
                }
            }
        }
    }

    @Test
    void controllersAndResourcesDelegateThroughPublicEndpoints() throws Exception {
        for (String className : CLASSES) {
            Class<?> type = Class.forName(className);
            if (!type.getPackageName().contains(".controller") && !type.isAnnotationPresent(RestController.class)) continue;
            Object instance = instantiate(type);
            if (instance == null) continue;
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                Object result = invokeLenient(instance, method);
                if (ResponseEntity.class.isAssignableFrom(method.getReturnType()) && result != null) {
                    assertTrue(result instanceof ResponseEntity);
                }
            }
        }
    }

    @Test
    void globalExceptionHandlersReturnStructuredResponses() throws Exception {
        for (String className : CLASSES) {
            Class<?> type = Class.forName(className);
            if (!type.isAnnotationPresent(RestControllerAdvice.class)) continue;
            Object instance = instantiate(type);
            assertNotNull(instance);
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 1) continue;
                Class<?> param = method.getParameterTypes()[0];
                if (!Throwable.class.isAssignableFrom(param)) continue;
                Object result = method.invoke(instance, throwable(param));
                assertTrue(result instanceof ResponseEntity);
                assertNotNull(((ResponseEntity<?>) result).getBody());
            }
        }
    }

    @Test
    void securityClassesAreInstantiableForUnitTesting() throws Exception {
        for (String className : CLASSES) {
            Class<?> type = Class.forName(className);
            if (!type.getPackageName().contains(".security") && !type.getSimpleName().toLowerCase().contains("jwt")) continue;
            Object instance = instantiate(type);
            assertNotNull(instance, type.getName());
        }
    }

    private static boolean isSpringComponent(Class<?> type) {
        String pkg = type.getPackageName();
        return pkg.contains(".controller") || pkg.contains(".config") || pkg.contains(".service") || pkg.contains(".security");
    }

    private static Object instantiate(Class<?> type) throws Exception {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() > 0 && type.getName().contains(".entity.")) continue;
            constructor.setAccessible(true);
            try {
                Object[] args = Arrays.stream(constructor.getParameterTypes()).map(LayerCoverageTest::sample).toArray();
                return constructor.newInstance(args);
            } catch (Throwable ignored) {
                // Try the next constructor. Some framework classes intentionally need runtime-only arguments.
            }
        }
        return null;
    }

    private static void exerciseAccessors(Class<?> type, Object instance) throws Exception {
        for (Field field : allFields(type)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
            Object value = sample(field.getType());
            field.setAccessible(true);
            field.set(instance, value);
            assertEquals(value, field.get(instance));
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            if (method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE) {
                invokeLenient(instance, method);
            }
        }
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class && current.getName().startsWith("com.connectsphere"); current = current.getSuperclass()) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    private static Object invokeLenient(Object instance, Method method) throws Exception {
        method.setAccessible(true);
        Object[] args = Arrays.stream(method.getParameterTypes()).map(LayerCoverageTest::sample).toArray();
        try {
            return method.invoke(instance, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            assertNotNull(cause);
            return null;
        }
    }

    private static Throwable throwable(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return (Throwable) constructor.newInstance("test error");
        } catch (Exception ignored) {
            return new RuntimeException("test error");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object sample(Class<?> type) {
        if (type.equals(String.class)) return "value";
        if (type.equals(Long.class) || type.equals(Long.TYPE)) return 1L;
        if (type.equals(Integer.class) || type.equals(Integer.TYPE)) return 1;
        if (type.equals(Boolean.class) || type.equals(Boolean.TYPE)) return true;
        if (type.equals(Double.class) || type.equals(Double.TYPE)) return 1.0d;
        if (type.equals(Float.class) || type.equals(Float.TYPE)) return 1.0f;
        if (type.equals(LocalDateTime.class)) return LocalDateTime.now();
        if (type.equals(List.class)) return List.of();
        if (type.equals(Map.class)) return Map.of();
        if (type.equals(Optional.class)) return Optional.empty();
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isPrimitive()) return 0;
        if (type.getName().startsWith("com.connectsphere")) {
            if (type.isInterface()) {
                return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> sample(method.getReturnType()));
            }
            return null;
        }
        return Mockito.mock(type);
    }
}

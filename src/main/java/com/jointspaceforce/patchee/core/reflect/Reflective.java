package com.jointspaceforce.patchee.core.reflect;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.material.Material;

/**
 * The reflection plumbing every feature shares, so no feature hand-rolls it:
 * optional class lookup, declared static field access, append-once list editing
 * and static Material[] extension.
 */
public final class Reflective {

    /**
     * @return the class on the classpath, or {@code null} when it is not there.
     *         A missing mod or a half-loaded one is normal on a real pack and is
     *         the caller's cue to skip quietly.
     */
    public Class<?> find(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    /**
     * A declared static field of the class, made accessible.
     *
     * @throws NoSuchFieldException when the patched mod no longer has it — the
     *                              caller's fail-soft decorator reports that as a hard failure.
     */
    public Field staticField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public Object staticValue(Field field) throws IllegalAccessException {
        return field.get(null);
    }

    public void setStatic(Field field, Object value) throws IllegalAccessException {
        field.set(null, value);
    }

    /** The static field read as a class list, or {@code null} when it is not one. */
    @SuppressWarnings("unchecked")
    public List<Class<?>> staticClassList(Field field) throws IllegalAccessException {
        Object raw = staticValue(field);
        return raw instanceof List ? (List<Class<?>>) raw : null;
    }

    /**
     * Appends {@code value} to {@code list} unless it is already there — the
     * dedupe every patch needs to stay idempotent.
     *
     * @return true when the value was added
     */
    public <T> boolean appendIfAbsent(List<T> list, T value) {
        if (value == null || list.contains(value)) {
            return false;
        }
        list.add(value);
        return true;
    }

    /**
     * A copy of {@code current} with every missing entry of {@code extras}
     * appended in order; {@code current} itself when nothing was missing, so an
     * already-patched array is left alone.
     */
    public Material[] extendMaterials(Material[] current, Material[] extras) {
        List<Material> merged = new ArrayList<Material>(Arrays.asList(current));
        boolean changed = false;
        for (Material material : extras) {
            changed |= appendIfAbsent(merged, material);
        }
        return changed ? merged.toArray(new Material[0]) : current;
    }
}
